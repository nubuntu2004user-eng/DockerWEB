import asyncio
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
                yield ServerSentEvent(data=chunk.decode("utf-8", errors="replace"))
                await asyncio.sleep(0)
        finally:
            client.open_shells.pop(id, None)

@app.get("/shellhost" , response_class=EventSourceResponse)
async def shellhost(cmd: str) -> AsyncIterable[ServerSentEvent]:
    try:
        gen = client.host_shell(cmd)
        for chunk in gen:
            yield ServerSentEvent(data=chunk.decode("utf-8", errors="replace"))
            await asyncio.sleep(0)
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