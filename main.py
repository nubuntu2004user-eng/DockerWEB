import asyncio

from fastapi import FastAPI
from collections.abc import AsyncIterable, Iterable
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
async def getstats()-> AsyncIterable[ServerSentEvent]:
    while True:
        payload = await asyncio.to_thread(client.get_stats)
        yield ServerSentEvent(raw_data=payload)
        await asyncio.sleep(2)
@app.post("/stopcontainer")
async def stop(id:str):
    client.stop_container(id)

@app.post("/restartcontainer")
async def restart(id:str):
    client.restart_container(id)
@app.post("/pausecontainer")
async def pause(id:str):
    client.pause_container(id)
@app.post("/unpausecontainer")
async def unpause(id:str):
    client.unpause_container(id)
@app.get("/getlogs")
async def logs(id:str):
    return client.get_logs(id)
@app.post("/removecontainer")
async def remove(id:str):
     client.remove_container(id)
@app.post("/shell")
async def shell(id:str , cmd:str):
    return client.shell(id, cmd)
@app.post("/addcontainer")
async def add(name:str):
    client.add_container(name=name)