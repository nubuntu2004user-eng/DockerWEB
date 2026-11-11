from fastapi import FastAPI
from DockerLogic.src import Client

client = Client
app = FastAPI()


@app.get("/")
async def root():
    return {"message": "Hello World"}


@app.get("/stats")
async def get_stats():
    return client.get_stats()
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
