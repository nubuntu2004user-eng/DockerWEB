import json

import docker

client = docker.from_env()


def get_stats():

    result = []
    containers = client.containers.list(all=True)

    for dockerContainer in containers:
        stats = dockerContainer.stats( stream=False)
        result.append(stats)
    output = json.dumps(result)
    return output

def stop_container(id:str):
    container = client.containers.get(container_id= id)
    container.stop()

def restart_container(id:str):
    container = client.containers.get(container_id=id)
    container.restart()

def pause_container(id:str):
    container = client.containers.get(container_id=id)
    container.pause()

def unpause_container(id:str):
    container = client.containers.get(container_id=id)
    container.unpause()

def get_logs(id:str):
    container = client.containers.get(container_id=id)
    logs = container.logs().decode(encoding="utf-8")
    result = json.dumps(logs)
    return result

def remove_container(id:str):
    container = client.containers.get(container_id=id)
    container.remove()

def shell(id:str , cmd:str): #first iteration enable streaming later
    container = client.containers.get(container_id=id)
    result = container.exec_run(cmd=cmd , stream=False).output.decode("utf-8")
    output = json.dumps(result)
    return output

def add_container(name:str):
    client.containers.run(image=name , detach=True)

if __name__ == "__main__":
    print(get_stats())