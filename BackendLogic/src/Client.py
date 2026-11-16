import json
import os
import re
import subprocess
import tarfile
import tempfile
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


def get_volumes():
    result = []
    containers = client.containers.list(all=True)
    volume_usage_map = {}
    for c in containers:
        mounts = c.attrs.get('Mounts', [])
        for m in mounts:
            v_name = m.get('Name')
            if v_name:
                if v_name not in volume_usage_map:
                    volume_usage_map[v_name] = []
                volume_usage_map[v_name].append({
                    "name": c.name,
                    "status": c.status,
                    "id": c.short_id if hasattr(c, 'short_id') else c.id[:12]
                })

    df_volumes = {}
    try:
        df_data = client.df()
        for v in df_data.get('Volumes', []) or []:
            if 'Name' in v:
                usage = v.get('UsageData') or {}
                size = usage.get('Size', 0)
                if size is not None and size >= 0:
                    df_volumes[v['Name']] = size
    except Exception:
        pass

    volumes = client.volumes.list()
    for vol in volumes:
        v_name = vol.name
        driver = vol.attrs.get('Driver', 'local')
        mountpoint = vol.attrs.get('Mountpoint', '')
        created = vol.attrs.get('CreatedAt', '') or vol.attrs.get('Created', '')
        labels = vol.attrs.get('Labels') or {}
        scope = vol.attrs.get('Scope', 'local')
        size = df_volumes.get(v_name, 0)
        is_anonymous = bool(re.match(r'^[0-9a-f]{64}$', v_name, re.IGNORECASE))
        used_by = volume_usage_map.get(v_name, [])

        result.append({
            "name": v_name,
            "driver": driver,
            "mountpoint": mountpoint,
            "created": created,
            "labels": labels,
            "scope": scope,
            "size": size,
            "anonymous": is_anonymous,
            "used_by": used_by
        })
    return json.dumps(result)


def create_volume(name: str, driver: str = "local", labels: dict = None, driver_opts: dict = None):
    kwargs = {"name": name, "driver": driver}
    if labels:
        kwargs["labels"] = labels
    if driver_opts:
        kwargs["driver_opts"] = driver_opts
    vol = client.volumes.create(**kwargs)
    return vol.attrs


def remove_volume(name: str, force: bool = False):
    vol = client.volumes.get(name)
    return vol.remove(force=force)


def prune_volumes(filters: dict = None):
    return client.volumes.prune(filters=filters)


def inspect_volume(name: str):
    vol = client.volumes.get(name)
    return json.dumps(vol.attrs, indent=2, default=str)


def browse_volume(name: str, path: str = "/"):
    clean_path = "/" + path.strip("/")
    # Run a lightweight helper container to inspect directory contents
    cmd = f"ls -la '{clean_path}' 2>/dev/null || ls -la /vol 2>/dev/null"
    try:
        output = client.containers.run(
            image="alpine",
            command=["sh", "-c", f"ls -la /vol{clean_path if clean_path != '/' else ''}"],
            volumes={name: {"bind": "/vol", "mode": "ro"}},
            remove=True
        ).decode("utf-8", errors="replace")
        
        entries = []
        lines = output.strip().splitlines()
        for line in lines:
            parts = line.split()
            if len(parts) >= 9 and parts[0] != "total":
                perms = parts[0]
                size = parts[4]
                entry_name = " ".join(parts[8:])
                if entry_name in [".", ".."]:
                    continue
                is_dir = perms.startswith("d")
                entries.append({
                    "name": entry_name,
                    "type": "dir" if is_dir else "file",
                    "size": size,
                    "permissions": perms,
                    "modified": f"{parts[5]} {parts[6]} {parts[7]}"
                })
        return json.dumps({"path": clean_path, "entries": entries, "raw": output})
    except Exception as e:
        # Fallback if container cannot be run or alpine not present
        return json.dumps({"path": clean_path, "entries": [], "error": str(e), "raw": str(e)})


def clone_volume(source_name: str, target_name: str):
    # Ensure source volume exists
    client.volumes.get(source_name)
    # Create target volume
    client.volumes.create(name=target_name)
    try:
        # Copy files from source to target volume using alpine container
        client.containers.run(
            image="alpine",
            command=["sh", "-c", "cp -a /source/. /target/"],
            volumes={
                source_name: {"bind": "/source", "mode": "ro"},
                target_name: {"bind": "/target", "mode": "rw"}
            },
            remove=True
        )
        return {"status": "ok", "source": source_name, "target": target_name}
    except Exception as e:
        # Cleanup target volume if copy failed
        try:
            client.volumes.get(target_name).remove(force=True)
        except Exception:
            pass
        raise e


def backup_volume(name: str, dest_dir: str = None):
    # Create backup archive using helper container
    backup_dir = dest_dir or tempfile.gettempdir()
    os.makedirs(backup_dir, exist_ok=True)
    backup_file_name = f"{name}_backup.tar.gz"
    backup_full_path = os.path.join(backup_dir, backup_file_name)

    client.containers.run(
        image="alpine",
        command=["sh", "-c", f"tar -czf /backup/{backup_file_name} -C /vol ."],
        volumes={
            name: {"bind": "/vol", "mode": "ro"},
            backup_dir: {"bind": "/backup", "mode": "rw"}
        },
        remove=True
    )
    return {"status": "ok", "volume": name, "file_path": backup_full_path, "file_name": backup_file_name}


def get_networks():
    result = []
    containers = client.containers.list(all=True)
    container_status_map = {c.id: c.status for c in containers}
    container_status_map.update({c.short_id: c.status for c in containers if hasattr(c, 'short_id')})
    container_name_map = {c.id: c.name for c in containers}
    container_name_map.update({c.short_id: c.name for c in containers if hasattr(c, 'short_id')})

    networks = client.networks.list()
    for net in networks:
        attrs = net.attrs or {}
        net_id = net.id
        short_id = net.short_id.replace('sha256:', '') if hasattr(net, 'short_id') else net.id[:12]
        name = net.name
        driver = attrs.get('Driver', 'bridge')
        scope = attrs.get('Scope', 'local')
        internal = bool(attrs.get('Internal', False))
        attachable = bool(attrs.get('Attachable', False))
        ingress = bool(attrs.get('Ingress', False))
        created = attrs.get('Created', '')
        labels = attrs.get('Labels') or {}

        # System networks are the default pre-defined bridge, host, null/none networks
        is_system = name in ['bridge', 'host', 'none']

        # Extract IPAM config (Subnet, Gateway)
        ipam = attrs.get('IPAM') or {}
        ipam_config = ipam.get('Config') or []
        subnet = ""
        gateway = ""
        if ipam_config and len(ipam_config) > 0:
            subnet = ipam_config[0].get('Subnet') or ""
            gateway = ipam_config[0].get('Gateway') or ""

        # Extract attached containers
        net_containers = attrs.get('Containers') or {}
        containers_list = []
        for cid, cinfo in net_containers.items():
            short_cid = cid[:12]
            c_name = cinfo.get('Name') or container_name_map.get(cid, short_cid)
            c_status = container_status_map.get(cid, container_status_map.get(short_cid, 'running'))
            ipv4 = cinfo.get('IPv4Address', '')
            ipv6 = cinfo.get('IPv6Address', '')
            containers_list.append({
                "id": short_cid,
                "name": c_name,
                "status": c_status,
                "ipv4": ipv4,
                "ipv6": ipv6
            })

        result.append({
            "id": net_id,
            "short_id": short_id,
            "name": name,
            "driver": driver,
            "scope": scope,
            "internal": internal,
            "attachable": attachable,
            "ingress": ingress,
            "created": created,
            "is_system": is_system,
            "subnet": subnet,
            "gateway": gateway,
            "containers": containers_list,
            "labels": labels
        })
    return json.dumps(result)


def create_network(name: str, driver: str = "bridge", subnet: str = None, gateway: str = None, internal: bool = False, attachable: bool = True, labels: dict = None, options: dict = None):
    kwargs = {
        "name": name,
        "driver": driver,
        "internal": internal,
        "attachable": attachable,
        "check_duplicate": True
    }
    if labels:
        kwargs["labels"] = labels
    if options:
        kwargs["options"] = options
    if subnet:
        ipam_pool = docker.types.IPAMPool(
            subnet=subnet,
            gateway=gateway if gateway else None
        )
        ipam_config = docker.types.IPAMConfig(
            pool_configs=[ipam_pool]
        )
        kwargs["ipam"] = ipam_config
    net = client.networks.create(**kwargs)
    return net.attrs


def remove_network(id_or_name: str):
    net = client.networks.get(id_or_name)
    return net.remove()


def prune_networks(filters: dict = None):
    return client.networks.prune(filters=filters)


def inspect_network(id_or_name: str):
    net = client.networks.get(id_or_name)
    return json.dumps(net.attrs, indent=2, default=str)


def connect_network(network_id: str, container_id: str, ipv4_address: str = None, ipv6_address: str = None, aliases: list = None):
    net = client.networks.get(network_id)
    kwargs = {}
    if ipv4_address:
        kwargs["ipv4_address"] = ipv4_address
    if ipv6_address:
        kwargs["ipv6_address"] = ipv6_address
    if aliases:
        kwargs["aliases"] = aliases
    return net.connect(container_id, **kwargs)


def disconnect_network(network_id: str, container_id: str, force: bool = False):
    net = client.networks.get(network_id)
    return net.disconnect(container_id, force=force)


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
