import asyncio
import base64
import hashlib
import hmac
import json
import os
import secrets
import time
from collections.abc import AsyncIterable
from fastapi import Depends, FastAPI, HTTPException, Request, Response
from fastapi.responses import RedirectResponse
from fastapi.sse import EventSourceResponse, ServerSentEvent
from pydantic import BaseModel
from starlette.middleware.cors import CORSMiddleware
from starlette.staticfiles import StaticFiles

from BackendLogic.src import Client

client = Client
app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Load config.json from project root
config_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "config.json"))
if not os.path.exists(config_path):
    config_path = "config.json"

SESSION_COOKIE_NAME = "dockerweb_session"
# Ephemeral per-process secret ensures server/container restarts invalidate prior sessions.
_SESSION_SECRET = secrets.token_bytes(32)
_config_mtime: float | None = None


def _load_config_data() -> dict:
    global _config_mtime
    data = {"host": "0.0.0.0", "port": 9456, "user": "admin", "password": "admin"}
    try:
        if os.path.exists(config_path):
            _config_mtime = os.path.getmtime(config_path)
            with open(config_path, "r") as f:
                loaded = json.load(f)
                if isinstance(loaded, dict):
                    data.update(loaded)
    except Exception as e:
        print(f"Failed to load config.json: {e}")
    return data


config_data = _load_config_data()


def _refresh_config_if_changed() -> dict:
    global config_data, _config_mtime
    try:
        if os.path.exists(config_path):
            mtime = os.path.getmtime(config_path)
            if _config_mtime is None or mtime != _config_mtime:
                config_data = _load_config_data()
    except Exception as e:
        print(f"Failed to refresh config.json: {e}")
    return config_data


def _get_auth_credentials() -> tuple[str, str]:
    data = _refresh_config_if_changed()
    raw_user = data.get("user") if "user" in data else data.get("username", "")
    raw_password = data.get("password", "")
    return str(raw_user or ""), str(raw_password or "")


def _b64url_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _b64url_decode(val: str) -> bytes:
    padding = "=" * (-len(val) % 4)
    return base64.urlsafe_b64decode(val + padding)


def _create_session_token(username: str) -> str:
    payload = json.dumps(
        {"user": username, "iat": int(time.time()), "nonce": secrets.token_hex(8)},
        separators=(",", ":"),
    ).encode("utf-8")
    payload_b64 = _b64url_encode(payload)
    sig = hmac.new(_SESSION_SECRET, payload_b64.encode("ascii"), hashlib.sha256).digest()
    return f"{payload_b64}.{_b64url_encode(sig)}"


def _verify_session_token(token: str | None) -> str | None:
    if not token or "." not in token:
        return None
    try:
        payload_b64, sig_b64 = token.split(".", 1)
        expected_sig = hmac.new(_SESSION_SECRET, payload_b64.encode("ascii"), hashlib.sha256).digest()
        actual_sig = _b64url_decode(sig_b64)
        if not hmac.compare_digest(expected_sig, actual_sig):
            return None
        payload = json.loads(_b64url_decode(payload_b64).decode("utf-8"))
        user = payload.get("user")
        return user if isinstance(user, str) and user else None
    except Exception:
        return None


def require_auth(request: Request) -> str:
    token = request.cookies.get(SESSION_COOKIE_NAME)
    user = _verify_session_token(token)
    if not user:
        raise HTTPException(status_code=401, detail="Authentication required")
    return user


AUTH_DEPS = [Depends(require_auth)]


class LoginRequest(BaseModel):
    user: str | None = None
    username: str | None = None
    password: str


class UnifiedSSEBroadcaster:
    """Shares a single Docker polling loop across all connected /events SSE clients."""

    def __init__(self, poll_interval: float = 2.0, keepalive_interval: float = 15.0):
        self._poll_interval = poll_interval
        self._keepalive_interval = keepalive_interval
        self._subscribers: set[asyncio.Queue[str | None]] = set()
        self._task: asyncio.Task | None = None
        self._refresh_event = asyncio.Event()
        self._last_section_json: dict[str, str] = {}
        self._latest_full_json: str | None = None
        self._last_emit_ts: float = 0.0

    def subscribe(self) -> asyncio.Queue[str | None]:
        queue: asyncio.Queue[str | None] = asyncio.Queue(maxsize=4)
        self._subscribers.add(queue)
        if self._latest_full_json is not None:
            queue.put_nowait(self._latest_full_json)
        if self._task is None or self._task.done():
            self._task = asyncio.create_task(self._run_loop())
        return queue

    def unsubscribe(self, queue: asyncio.Queue[str | None]) -> None:
        self._subscribers.discard(queue)
        if not self._subscribers and self._task is not None and not self._task.done():
            self._task.cancel()
            self._task = None
            self._last_section_json.clear()
            self._latest_full_json = None

    def notify_refresh(self) -> None:
        """Wake the collector loop immediately after a state-changing API call."""
        self._refresh_event.set()

    def _enqueue(self, item: str | None) -> None:
        for queue in list(self._subscribers):
            if queue.full():
                try:
                    queue.get_nowait()
                except asyncio.QueueEmpty:
                    pass
            try:
                queue.put_nowait(item)
            except asyncio.QueueFull:
                pass

    async def _run_loop(self) -> None:
        try:
            while self._subscribers:
                self._refresh_event.clear()
                try:
                    snapshot = await asyncio.to_thread(client.get_unified_snapshot)
                except Exception as exc:
                    print(f"Error collecting unified Docker snapshot: {exc}")
                    snapshot = None

                now = time.monotonic()
                if snapshot is not None:
                    delta: dict[str, list[dict]] = {}
                    for section, items in snapshot.items():
                        serialized = json.dumps(items, separators=(",", ":"))
                        if self._last_section_json.get(section) != serialized:
                            self._last_section_json[section] = serialized
                            delta[section] = items

                    self._latest_full_json = json.dumps(snapshot, separators=(",", ":"))

                    if delta:
                        payload = json.dumps(delta, separators=(",", ":"))
                        self._enqueue(payload)
                        self._last_emit_ts = now
                    elif now - self._last_emit_ts >= self._keepalive_interval:
                        self._enqueue(None)
                        self._last_emit_ts = now

                try:
                    await asyncio.wait_for(self._refresh_event.wait(), timeout=self._poll_interval)
                except asyncio.TimeoutError:
                    pass
        except asyncio.CancelledError:
            pass


broadcaster = UnifiedSSEBroadcaster()


@app.get("/config.json")
async def get_config():
    cfg = _refresh_config_if_changed()
    return {
        "host": cfg.get("host", "0.0.0.0"),
        "port": cfg.get("port", 9456),
    }


@app.get("/auth/status")
async def auth_status(request: Request):
    user = _verify_session_token(request.cookies.get(SESSION_COOKIE_NAME))
    if not user:
        return {"authenticated": False}
    return {"authenticated": True, "user": user}


@app.post("/auth/login")
async def login(body: LoginRequest, request: Request, response: Response):
    expected_user, expected_password = _get_auth_credentials()
    if not expected_user or not expected_password:
        raise HTTPException(status_code=503, detail="Authentication credentials are not configured")

    submitted_user = body.user if body.user is not None else (body.username or "")
    user_ok = hmac.compare_digest(submitted_user.encode("utf-8"), expected_user.encode("utf-8"))
    password_ok = hmac.compare_digest(body.password.encode("utf-8"), expected_password.encode("utf-8"))
    if not (user_ok and password_ok):
        raise HTTPException(status_code=401, detail="Invalid username or password")

    token = _create_session_token(expected_user)
    response.set_cookie(
        key=SESSION_COOKIE_NAME,
        value=token,
        httponly=True,
        samesite="lax",
        path="/",
        secure=(request.url.scheme == "https"),
    )
    return {"authenticated": True, "user": expected_user}


@app.post("/auth/logout")
async def logout(response: Response):
    response.delete_cookie(key=SESSION_COOKIE_NAME, path="/", samesite="lax")
    return {"authenticated": False}


@app.get("/events", response_class=EventSourceResponse, dependencies=AUTH_DEPS)
async def events_stream(request: Request) -> AsyncIterable[ServerSentEvent]:
    queue = broadcaster.subscribe()
    try:
        while True:
            if await request.is_disconnected():
                break
            try:
                payload = await asyncio.wait_for(queue.get(), timeout=15.0)
            except asyncio.TimeoutError:
                yield ServerSentEvent(comment="keepalive")
                continue

            if payload is None:
                yield ServerSentEvent(comment="keepalive")
            else:
                yield ServerSentEvent(raw_data=payload)
    finally:
        broadcaster.unsubscribe(queue)


@app.get("/pullimage", response_class=EventSourceResponse, dependencies=AUTH_DEPS)
async def pullimage(image: str) -> AsyncIterable[ServerSentEvent]:
    try:
        gen = client.pull_image_stream(image)
        while True:
            event = await asyncio.to_thread(next, gen, None)
            if event is None:
                break
            yield ServerSentEvent(raw_data=json.dumps(event))
            await asyncio.sleep(0)
        broadcaster.notify_refresh()
    except Exception as e:
        yield ServerSentEvent(raw_data=json.dumps({"error": str(e)}))


@app.post("/stopcontainer", dependencies=AUTH_DEPS)
async def stop(id: str):
    try:
        await asyncio.to_thread(client.stop_container, id)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/restartcontainer", dependencies=AUTH_DEPS)
async def restart(id: str):
    try:
        await asyncio.to_thread(client.restart_container, id)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/pausecontainer", dependencies=AUTH_DEPS)
async def pause(id: str):
    try:
        await asyncio.to_thread(client.pause_container, id)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/unpausecontainer", dependencies=AUTH_DEPS)
async def unpause(id: str):
    try:
        await asyncio.to_thread(client.unpause_container, id)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/getlogs", dependencies=AUTH_DEPS)
async def logs(id: str):
    try:
        return await asyncio.to_thread(client.get_logs, id)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/removecontainer", dependencies=AUTH_DEPS)
async def remove(id: str):
    try:
        await asyncio.to_thread(client.remove_container, id)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/shell", response_class=EventSourceResponse, dependencies=AUTH_DEPS)
async def shell(id: str, cmd: str) -> AsyncIterable[ServerSentEvent]:
    try:
        stream = await asyncio.to_thread(client.shell, id, cmd)
        client.open_shells[id] = stream
        while True:
            chunk = await asyncio.to_thread(next, stream, None)
            if chunk is None:
                break
            text = chunk.decode("utf-8", errors="replace").replace('\r\n', '\n').replace('\r', '\n').strip('\n')
            text = text.replace('\n', '  ')
            yield ServerSentEvent(raw_data=text)
    finally:
        client.open_shells.pop(id, None)

@app.get("/shellhost", response_class=EventSourceResponse, dependencies=AUTH_DEPS)
async def shellhost(cmd: str) -> AsyncIterable[ServerSentEvent]:
    gen = client.host_shell(cmd)
    try:
        while True:
            chunk = await asyncio.to_thread(next, gen, None)
            if chunk is None:
                break
            text = chunk.decode("utf-8", errors="replace").replace('\r\n', '\n').replace('\r', '\n').strip('\n')
            text = text.replace('\n', '  ')
            yield ServerSentEvent(raw_data=text)
    finally:
        client.open_shells.pop("Host", None)
@app.get("/inspectcontainer", dependencies=AUTH_DEPS)
async def inspect(id: str):
    try:
        return await asyncio.to_thread(client.inspect_container, id)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/prunecontainers", dependencies=AUTH_DEPS)
async def prune():
    try:
        res = await asyncio.to_thread(client.prune_containers)
        broadcaster.notify_refresh()
        return res
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/addcontainer", dependencies=AUTH_DEPS)
async def add(name: str):
    try:
        await asyncio.to_thread(client.add_container, name)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/closeshell", dependencies=AUTH_DEPS)
async def close(id:str):
    client.close_shell(id)


@app.post("/tagimage", dependencies=AUTH_DEPS)
async def tagimage(id: str, repo: str, tag: str = "latest"):
    try:
        await asyncio.to_thread(client.tag_image, id, repo, tag)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/removeimage", dependencies=AUTH_DEPS)
async def removeimage(id: str, force: bool = False):
    try:
        await asyncio.to_thread(client.remove_image, id, force)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/pruneimages", dependencies=AUTH_DEPS)
async def pruneimages(dangling_only: bool = False):
    try:
        res = await asyncio.to_thread(client.prune_images, dangling_only)
        broadcaster.notify_refresh()
        return {"status": "ok", "result": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/inspectimage", dependencies=AUTH_DEPS)
async def inspectimage(id: str):
    try:
        return await asyncio.to_thread(client.inspect_image, id)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/imagehistory", dependencies=AUTH_DEPS)
async def imagehistory(id: str):
    try:
        return await asyncio.to_thread(client.get_image_history, id)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/importimage", dependencies=AUTH_DEPS)
async def importimage(path: str):
    try:
        await asyncio.to_thread(client.import_image, path)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/runimage", dependencies=AUTH_DEPS)
async def runimage(image: str, name: str = None, command: str = None):
    try:
        kwargs = {"image": image, "detach": True}
        if name:
            kwargs["name"] = name
        if command:
            kwargs["command"] = command
        await asyncio.to_thread(client.client.containers.run, **kwargs)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/createvolume", dependencies=AUTH_DEPS)
async def createvolume(name: str, driver: str = "local", labels: str = None):
    try:
        parsed_labels = {}
        if labels:
            try:
                parsed_labels = json.loads(labels)
            except Exception:
                # support key=value, key2=value2 format
                for pair in labels.split(","):
                    if "=" in pair:
                        k, v = pair.split("=", 1)
                        parsed_labels[k.strip()] = v.strip()
                    elif pair.strip():
                        parsed_labels[pair.strip()] = ""
        res = await asyncio.to_thread(client.create_volume, name, driver, parsed_labels)
        broadcaster.notify_refresh()
        return {"status": "ok", "volume": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/removevolume", dependencies=AUTH_DEPS)
async def removevolume(name: str, force: bool = False):
    try:
        await asyncio.to_thread(client.remove_volume, name, force)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/prunevolumes", dependencies=AUTH_DEPS)
async def prunevolumes():
    try:
        res = await asyncio.to_thread(client.prune_volumes)
        broadcaster.notify_refresh()
        return {"status": "ok", "result": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/inspectvolume", dependencies=AUTH_DEPS)
async def inspectvolume(name: str):
    try:
        return await asyncio.to_thread(client.inspect_volume, name)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/browsevolume", dependencies=AUTH_DEPS)
async def browsevolume(name: str, path: str = "/"):
    try:
        return await asyncio.to_thread(client.browse_volume, name, path)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/clonevolume", dependencies=AUTH_DEPS)
async def clonevolume(source_name: str, target_name: str):
    try:
        res = await asyncio.to_thread(client.clone_volume, source_name, target_name)
        broadcaster.notify_refresh()
        return res
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/backupvolume", dependencies=AUTH_DEPS)
async def backupvolume(name: str, dest_dir: str = None):
    try:
        res = await asyncio.to_thread(client.backup_volume, name, dest_dir)
        return res
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/createnetwork", dependencies=AUTH_DEPS)
async def createnetwork(name: str, driver: str = "bridge", subnet: str = None, gateway: str = None, labels: str = None):
    try:
        parsed_labels = {}
        if labels:
            try:
                parsed_labels = json.loads(labels)
            except Exception:
                for pair in labels.split(","):
                    if "=" in pair:
                        k, v = pair.split("=", 1)
                        parsed_labels[k.strip()] = v.strip()
                    elif pair.strip():
                        parsed_labels[pair.strip()] = ""
        res = await asyncio.to_thread(
            client.create_network,
            name=name,
            driver=driver,
            subnet=subnet.strip() if subnet and subnet.strip() else None,
            gateway=gateway.strip() if gateway and gateway.strip() else None,
            labels=parsed_labels if parsed_labels else None
        )
        broadcaster.notify_refresh()
        return {"status": "ok", "network": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/removenetwork", dependencies=AUTH_DEPS)
async def removenetwork(id: str):
    try:
        await asyncio.to_thread(client.remove_network, id)
        broadcaster.notify_refresh()
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/prunenetworks", dependencies=AUTH_DEPS)
async def prunenetworks():
    try:
        res = await asyncio.to_thread(client.prune_networks)
        broadcaster.notify_refresh()
        return {"status": "ok", "result": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/inspectnetwork", dependencies=AUTH_DEPS)
async def inspectnetwork(id: str):
    try:
        return await asyncio.to_thread(client.inspect_network, id)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/connectnetwork", dependencies=AUTH_DEPS)
async def connectnetwork(network_id: str, container_id: str, ipv4_address: str = None, ipv6_address: str = None):
    try:
        res = await asyncio.to_thread(
            client.connect_network,
            network_id=network_id,
            container_id=container_id,
            ipv4_address=ipv4_address.strip() if ipv4_address and ipv4_address.strip() else None,
            ipv6_address=ipv6_address.strip() if ipv6_address and ipv6_address.strip() else None
        )
        broadcaster.notify_refresh()
        return {"status": "ok", "result": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/disconnectnetwork", dependencies=AUTH_DEPS)
async def disconnectnetwork(network_id: str, container_id: str, force: bool = False):
    try:
        res = await asyncio.to_thread(
            client.disconnect_network,
            network_id=network_id,
            container_id=container_id,
            force=force
        )
        broadcaster.notify_refresh()
        return {"status": "ok", "result": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.get("/", include_in_schema=False)
async def root():
    return RedirectResponse(url="/dockerweb.html")


app.mount("/" , StaticFiles(directory="DockerWebUI/webApp/build/dist/wasmJs/productionExecutable") , name="webApp")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("BackendLogic.src.main:app", host=config_data.get("host", "0.0.0.0"), port=config_data.get("port", 9456), reload=True)
