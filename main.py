import asyncio
import json
from collections.abc import AsyncIterable
from fastapi import FastAPI, HTTPException
from fastapi.sse import EventSourceResponse, ServerSentEvent
from starlette.middleware.cors import CORSMiddleware

from DockerLogic.src import Client

client = Client
app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
async def root():
    return {"message": "Hello World"}


@app.get("/stats", response_class=EventSourceResponse)
async def getstats() -> AsyncIterable[ServerSentEvent]:
    while True:
        payload = await asyncio.to_thread(client.get_stats)
        yield ServerSentEvent(raw_data=payload)
        await asyncio.sleep(2)


@app.get("/imagestats", response_class=EventSourceResponse)
async def getimagestats() -> AsyncIterable[ServerSentEvent]:
    while True:
        payload = await asyncio.to_thread(client.get_images)
        yield ServerSentEvent(raw_data=payload)
        await asyncio.sleep(2)


@app.get("/pullimage", response_class=EventSourceResponse)
async def pullimage(image: str) -> AsyncIterable[ServerSentEvent]:
    try:
        gen = client.pull_image_stream(image)
        while True:
            event = await asyncio.to_thread(next, gen, None)
            if event is None:
                break
            yield ServerSentEvent(raw_data=json.dumps(event))
            await asyncio.sleep(0)
    except Exception as e:
        yield ServerSentEvent(raw_data=json.dumps({"error": str(e)}))


@app.post("/stopcontainer")
async def stop(id: str):
    try:
        await asyncio.to_thread(client.stop_container, id)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/restartcontainer")
async def restart(id: str):
    try:
        await asyncio.to_thread(client.restart_container, id)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/pausecontainer")
async def pause(id: str):
    try:
        await asyncio.to_thread(client.pause_container, id)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/unpausecontainer")
async def unpause(id: str):
    try:
        await asyncio.to_thread(client.unpause_container, id)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/getlogs")
async def logs(id: str):
    try:
        return await asyncio.to_thread(client.get_logs, id)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/removecontainer")
async def remove(id: str):
    try:
        await asyncio.to_thread(client.remove_container, id)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/shell" , response_class=EventSourceResponse)
async def shell(id: str, cmd: str)-> AsyncIterable[ServerSentEvent]:
        try:
            stream = await asyncio.to_thread(client.shell, id, cmd)
            client.open_shells[id] = stream
            for chunk in stream:
                text = chunk.decode("utf-8", errors="replace").replace('\r\n', '\n').replace('\r', '\n').rstrip('\n')
                yield ServerSentEvent(data=text)
                await asyncio.sleep(0)
        finally:
            client.open_shells.pop(id, None)

@app.get("/shellhost" , response_class=EventSourceResponse)
async def shellhost(cmd: str) -> AsyncIterable[ServerSentEvent]:
    gen = client.host_shell(cmd)
    try:
        while True:
            chunk = await asyncio.to_thread(next, gen, None)
            if chunk is None:
                break
            text = chunk.decode("utf-8", errors="replace").replace('\r\n', '\n').replace('\r', '\n').rstrip('\n')
            yield ServerSentEvent(data=text)
    finally:
        client.open_shells.pop("Host", None)
@app.get("/inspectcontainer")
async def inspect(id: str):
    try:
        return await asyncio.to_thread(client.inspect_container, id)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/prunecontainers")
async def prune():
    try:
        return await asyncio.to_thread(client.prune_containers)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/addcontainer")
async def add(name: str):
    try:
        await asyncio.to_thread(client.add_container, name)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))

@app.post("/closeshell")
async def close(id:str):
    client.close_shell(id)


@app.post("/tagimage")
async def tagimage(id: str, repo: str, tag: str = "latest"):
    try:
        await asyncio.to_thread(client.tag_image, id, repo, tag)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/removeimage")
async def removeimage(id: str, force: bool = False):
    try:
        await asyncio.to_thread(client.remove_image, id, force)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/pruneimages")
async def pruneimages(dangling_only: bool = False):
    try:
        res = await asyncio.to_thread(client.prune_images, dangling_only)
        return {"status": "ok", "result": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/inspectimage")
async def inspectimage(id: str):
    try:
        return await asyncio.to_thread(client.inspect_image, id)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/imagehistory")
async def imagehistory(id: str):
    try:
        return await asyncio.to_thread(client.get_image_history, id)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/importimage")
async def importimage(path: str):
    try:
        await asyncio.to_thread(client.import_image, path)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/runimage")
async def runimage(image: str, name: str = None, command: str = None):
    try:
        kwargs = {"image": image, "detach": True}
        if name:
            kwargs["name"] = name
        if command:
            kwargs["command"] = command
        await asyncio.to_thread(client.client.containers.run, **kwargs)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/volumestats", response_class=EventSourceResponse)
async def getvolumestats() -> AsyncIterable[ServerSentEvent]:
    while True:
        payload = await asyncio.to_thread(client.get_volumes)
        yield ServerSentEvent(raw_data=payload)
        await asyncio.sleep(2)


@app.post("/createvolume")
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
        return {"status": "ok", "volume": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/removevolume")
async def removevolume(name: str, force: bool = False):
    try:
        await asyncio.to_thread(client.remove_volume, name, force)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/prunevolumes")
async def prunevolumes():
    try:
        res = await asyncio.to_thread(client.prune_volumes)
        return {"status": "ok", "result": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/inspectvolume")
async def inspectvolume(name: str):
    try:
        return await asyncio.to_thread(client.inspect_volume, name)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/browsevolume")
async def browsevolume(name: str, path: str = "/"):
    try:
        return await asyncio.to_thread(client.browse_volume, name, path)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/clonevolume")
async def clonevolume(source_name: str, target_name: str):
    try:
        res = await asyncio.to_thread(client.clone_volume, source_name, target_name)
        return res
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/backupvolume")
async def backupvolume(name: str, dest_dir: str = None):
    try:
        res = await asyncio.to_thread(client.backup_volume, name, dest_dir)
        return res
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/networkstats", response_class=EventSourceResponse)
async def getnetworkstats() -> AsyncIterable[ServerSentEvent]:
    while True:
        payload = await asyncio.to_thread(client.get_networks)
        yield ServerSentEvent(raw_data=payload)
        await asyncio.sleep(2)


@app.post("/createnetwork")
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
        return {"status": "ok", "network": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/removenetwork")
async def removenetwork(id: str):
    try:
        await asyncio.to_thread(client.remove_network, id)
        return {"status": "ok"}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/prunenetworks")
async def prunenetworks():
    try:
        res = await asyncio.to_thread(client.prune_networks)
        return {"status": "ok", "result": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/inspectnetwork")
async def inspectnetwork(id: str):
    try:
        return await asyncio.to_thread(client.inspect_network, id)
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/connectnetwork")
async def connectnetwork(network_id: str, container_id: str, ipv4_address: str = None, ipv6_address: str = None):
    try:
        res = await asyncio.to_thread(
            client.connect_network,
            network_id=network_id,
            container_id=container_id,
            ipv4_address=ipv4_address.strip() if ipv4_address and ipv4_address.strip() else None,
            ipv6_address=ipv6_address.strip() if ipv6_address and ipv6_address.strip() else None
        )
        return {"status": "ok", "result": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.post("/disconnectnetwork")
async def disconnectnetwork(network_id: str, container_id: str, force: bool = False):
    try:
        res = await asyncio.to_thread(
            client.disconnect_network,
            network_id=network_id,
            container_id=container_id,
            force=force
        )
        return {"status": "ok", "result": res}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))