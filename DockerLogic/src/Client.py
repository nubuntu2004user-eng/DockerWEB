import json
import subprocess
import docker

client = docker.from_env()

open_shells = {}

def get_stats():
    result = []
    containers = client.containers.list(all=True)
    for dockerContainer in containers:
        stats = dockerContainer.stats(stream=False)
        stats["status"] = dockerContainer.status
        result.append(stats)
    output = json.dumps(result)
    return output


def stop_container(id: str):
    container = client.containers.get(container_id=id)
    container.stop()


def restart_container(id: str):
    container = client.containers.get(container_id=id)
    container.restart()


def pause_container(id: str):
    container = client.containers.get(container_id=id)
    container.pause()


def unpause_container(id: str):
    container = client.containers.get(container_id=id)
    container.unpause()


def get_logs(id: str):
    container = client.containers.get(container_id=id)
    logs = container.logs().decode(encoding="utf-8")
    result = json.dumps(logs)
    return result


def remove_container(id: str):
    container = client.containers.get(container_id=id)
    container.remove(force=True)


def shell(id: str, cmd: str):
    container = client.containers.get(container_id=id)
    return container.exec_run(cmd=cmd , stream=True).output


def inspect_container(id: str):
    container = client.containers.get(container_id=id)
    return json.dumps(container.attrs, indent=2, default=str)


def prune_containers():
    return client.containers.prune()


def add_container(name: str):
    client.containers.run(image=name, detach=True)


def get_images():
    result = []
    containers = client.containers.list(all=True)
    image_usage_map = {}
    for c in containers:
        img_id = getattr(c.image, 'id', None) or c.attrs.get('Image', '')
        if img_id not in image_usage_map:
            image_usage_map[img_id] = []
        image_usage_map[img_id].append({
            "name": c.name,
            "status": c.status,
            "id": c.short_id if hasattr(c, 'short_id') else c.id[:12]
        })

    images = client.images.list(all=True)
    for img in images:
        img_id = img.id
        short_id = img.short_id.replace('sha256:', '') if hasattr(img, 'short_id') else img.id.replace('sha256:', '')[:12]
        size = img.attrs.get('Size', 0)
        created = img.attrs.get('Created', '')
        tags = img.tags
        used_by = image_usage_map.get(img_id, [])

        if not tags:
            result.append({
                "id": img_id,
                "short_id": short_id,
                "repository": "<none>",
                "tag": "<none>",
                "size": size,
                "created": created,
                "dangling": True,
                "used_by": used_by
            })
        else:
            for repo_tag in tags:
                if ":" in repo_tag:
                    repo, tag = repo_tag.rsplit(":", 1)
                else:
                    repo, tag = repo_tag, "latest"
                result.append({
                    "id": img_id,
                    "short_id": short_id,
                    "repository": repo,
                    "tag": tag,
                    "size": size,
                    "created": created,
                    "dangling": False,
                    "used_by": used_by
                })
    return json.dumps(result)


def tag_image(id: str, repo: str, tag: str = None):
    img = client.images.get(id)
    return img.tag(repository=repo, tag=tag)


def remove_image(id: str, force: bool = False):
    return client.images.remove(image=id, force=force)


def prune_images(dangling_only: bool = False):
    filters = {'dangling': True} if dangling_only else None
    return client.images.prune(filters=filters)


def inspect_image(id: str):
    img = client.images.get(id)
    return json.dumps(img.attrs, indent=2, default=str)


def get_image_history(id: str):
    img = client.images.get(id)
    return json.dumps(img.history(), indent=2, default=str)


def import_image(file_path: str):
    with open(file_path, "rb") as f:
        return client.images.load(f.read())


def pull_image_stream(name: str, tag: str = None):
    if tag:
        stream = client.api.pull(name, tag=tag, stream=True, decode=True)
    else:
        stream = client.api.pull(name, stream=True, decode=True)
    for line in stream:
        yield line


def host_shell(cmd: str):
    process = subprocess.Popen(
        cmd,
        shell=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    open_shells["Host"] = process
    try:
        for line in process.stdout:
            yield line
    finally:
        process.stdout.close()
        process.wait()


def close_shell(id: str):
    proc_or_stream = open_shells.pop(id, None)
    if proc_or_stream is None:
        return
    if hasattr(proc_or_stream, 'kill'):
        proc_or_stream.kill()
    else:
        proc_or_stream.close()

if __name__ == "__main__":
    print(get_stats())
