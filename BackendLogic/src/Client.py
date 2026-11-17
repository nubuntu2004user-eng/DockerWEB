from concurrent.futures import ThreadPoolExecutor
import json
import os
import re
import subprocess
import tarfile
import tempfile
import threading
import time
import docker

client = docker.from_env()

open_shells = {}

_prev_cpu_lock = threading.Lock()
_prev_cpu_samples: dict[str, tuple[int, int]] = {}

_df_cache_lock = threading.Lock()
_df_volumes_cache: dict[str, int] = {}
_df_cache_ts: float = 0.0
_DF_CACHE_TTL_SECONDS: float = 30.0
_ANONYMOUS_VOLUME_RE = re.compile(r'^[0-9a-f]{64}$', re.IGNORECASE)


def invalidate_volume_df_cache():
    """Invalidate the cached docker system df volume size map."""
    global _df_cache_ts
    with _df_cache_lock:
        _df_cache_ts = 0.0


def _get_cached_volume_sizes() -> dict[str, int]:
    """Return cached volume disk usage sizes, refreshing via client.df() at most once per TTL."""
    global _df_volumes_cache, _df_cache_ts
    now = time.monotonic()
    with _df_cache_lock:
        if now - _df_cache_ts < _DF_CACHE_TTL_SECONDS and _df_cache_ts > 0.0:
            return dict(_df_volumes_cache)

    fresh: dict[str, int] = {}
    try:
        df_data = client.df()
        for v in df_data.get('Volumes', []) or []:
            if 'Name' in v:
                usage = v.get('UsageData') or {}
                size = usage.get('Size', 0)
                if size is not None and size >= 0:
                    fresh[v['Name']] = size
    except Exception:
        with _df_cache_lock:
            return dict(_df_volumes_cache)

    with _df_cache_lock:
        _df_volumes_cache = fresh
        _df_cache_ts = time.monotonic()
        return dict(_df_volumes_cache)


def _compute_cpu_percent(container_id: str, stats: dict) -> float:
    """Compute container CPU usage percentage using consecutive one-shot samples."""
    cpu_stats = stats.get("cpu_stats") or {}
    cpu_usage = cpu_stats.get("cpu_usage") or {}
    total_usage = int(cpu_usage.get("total_usage") or 0)
    system_usage = int(cpu_stats.get("system_cpu_usage") or 0)
    online_cpus = (
        cpu_stats.get("online_cpus")
        or len(cpu_usage.get("percpu_usage") or [])
        or 1
    )

    with _prev_cpu_lock:
        prev_sample = _prev_cpu_samples.get(container_id)
        _prev_cpu_samples[container_id] = (total_usage, system_usage)

    if prev_sample is None:
        precpu = stats.get("precpu_stats") or {}
        prev_total = int((precpu.get("cpu_usage") or {}).get("total_usage") or 0)
        prev_system = int(precpu.get("system_cpu_usage") or 0)
    else:
        prev_total, prev_system = prev_sample

    cpu_delta = total_usage - prev_total
    system_delta = system_usage - prev_system
    if system_delta > 0 and cpu_delta > 0 and (prev_sample is not None or prev_system > 0):
        pct = (cpu_delta / system_delta) * online_cpus * 100.0
        return round(pct, 2)
    return 0.0


def _extract_memory_usage(stats: dict) -> tuple[int, int]:
    """Return (memory_usage_bytes, memory_limit_bytes) excluding page cache."""
    mem_stats = stats.get("memory_stats") or {}
    raw_usage = int(mem_stats.get("usage") or 0)
    mem_detail = mem_stats.get("stats") or {}
    cache = 0
    for key in ("inactive_file", "total_inactive_file", "cache"):
        val = mem_detail.get(key)
        if val is not None:
            cache = int(val)
            break
    usage = max(0, raw_usage - cache)
    limit = int(mem_stats.get("limit") or 0)
    return usage, limit


def _fetch_running_container_stat(container) -> dict:
    """Fetch one-shot stats for a single running container and return only UI-required fields."""
    os_type = (container.attrs or {}).get("Platform") or "linux"
    try:
        raw = client.api.stats(container.id, stream=False, one_shot=True)
        os_type = raw.get("os_type") or os_type
        cpu_percent = _compute_cpu_percent(container.id, raw)
        mem_usage, mem_limit = _extract_memory_usage(raw)
    except Exception:
        cpu_percent = 0.0
        mem_usage = 0
        mem_limit = 0

    return {
        "id": container.id,
        "name": container.name,
        "status": container.status,
        "os_type": os_type,
        "cpu_percent": cpu_percent,
        "memory_usage": mem_usage,
        "memory_limit": mem_limit,
    }


def _build_containers_data(containers: list) -> list[dict]:
    """Build slimmed container entries, polling stats concurrently only for running containers."""
    running = [c for c in containers if c.status == "running"]
    running_ids = {c.id for c in running}

    with _prev_cpu_lock:
        stale_ids = [cid for cid in _prev_cpu_samples if cid not in running_ids]
        for cid in stale_ids:
            del _prev_cpu_samples[cid]

    running_map: dict[str, dict] = {}
    if running:
        workers = min(8, len(running))
        with ThreadPoolExecutor(max_workers=workers) as pool:
            for entry in pool.map(_fetch_running_container_stat, running):
                running_map[entry["id"]] = entry

    result = []
    for c in containers:
        if c.id in running_map:
            result.append(running_map[c.id])
        else:
            result.append({
                "id": c.id,
                "name": c.name,
                "status": c.status,
                "os_type": (c.attrs or {}).get("Platform") or "linux",
                "cpu_percent": 0.0,
                "memory_usage": 0,
                "memory_limit": 0,
            })
    return result


def get_stats():
    containers = client.containers.list(all=True)
    return json.dumps(_build_containers_data(containers))


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


def _build_images_data(containers: list) -> list[dict]:
    """Build slimmed image entries using a shared container list."""
    result = []
    image_usage_map: dict[str, list[dict]] = {}
    for c in containers:
        try:
            img_id = c.image.id if c.image else c.attrs.get('Image', '')
        except docker.errors.ImageNotFound:
            img_id = c.attrs.get('Image', '')
        image_usage_map.setdefault(img_id, []).append({
            "name": c.name,
            "status": c.status,
        })

    images = client.images.list(all=True)
    for img in images:
        img_id = img.id
        size = img.attrs.get('Size', 0)
        created = img.attrs.get('Created', '')
        tags = img.tags
        used_by = image_usage_map.get(img_id, [])

        if not tags:
            result.append({
                "id": img_id,
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
                    "repository": repo,
                    "tag": tag,
                    "size": size,
                    "created": created,
                    "dangling": False,
                    "used_by": used_by
                })
    return result


def get_images():
    containers = client.containers.list(all=True)
    return json.dumps(_build_images_data(containers))


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
        reader = getattr(process.stdout, 'read1', process.stdout.read)
        for chunk in iter(lambda: reader(4096), b''):
            yield chunk
    finally:
        process.stdout.close()
        process.wait()


def _build_volumes_data(containers: list) -> list[dict]:
    """Build slimmed volume entries using a shared container list and TTL-cached df sizes."""
    result = []
    volume_usage_map: dict[str, list[dict]] = {}
    for c in containers:
        mounts = c.attrs.get('Mounts', [])
        for m in mounts:
            v_name = m.get('Name')
            if v_name:
                volume_usage_map.setdefault(v_name, []).append({
                    "name": c.name,
                    "status": c.status,
                })

    df_volumes = _get_cached_volume_sizes()

    volumes = client.volumes.list()
    for vol in volumes:
        v_name = vol.name
        attrs = vol.attrs or {}
        driver = attrs.get('Driver', 'local')
        mountpoint = attrs.get('Mountpoint', '')
        created = attrs.get('CreatedAt', '') or attrs.get('Created', '')
        labels = attrs.get('Labels') or {}
        size = df_volumes.get(v_name, 0)
        is_anonymous = bool(_ANONYMOUS_VOLUME_RE.match(v_name))
        used_by = volume_usage_map.get(v_name, [])

        result.append({
            "name": v_name,
            "driver": driver,
            "mountpoint": mountpoint,
            "created": created,
            "labels": labels,
            "size": size,
            "anonymous": is_anonymous,
            "used_by": used_by
        })
    return result


def get_volumes():
    containers = client.containers.list(all=True)
    return json.dumps(_build_volumes_data(containers))


def create_volume(name: str, driver: str = "local", labels: dict = None, driver_opts: dict = None):
    kwargs = {"name": name, "driver": driver}
    if labels:
        kwargs["labels"] = labels
    if driver_opts:
        kwargs["driver_opts"] = driver_opts
    vol = client.volumes.create(**kwargs)
    invalidate_volume_df_cache()
    return vol.attrs


def remove_volume(name: str, force: bool = False):
    vol = client.volumes.get(name)
    res = vol.remove(force=force)
    invalidate_volume_df_cache()
    return res


def prune_volumes(filters: dict = None):
    res = client.volumes.prune(filters=filters)
    invalidate_volume_df_cache()
    return res


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
        invalidate_volume_df_cache()
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


def _build_networks_data(containers: list) -> list[dict]:
    """Build slimmed network entries using a shared container list."""
    result = []
    container_status_map = {c.id: c.status for c in containers}
    container_status_map.update({c.short_id: c.status for c in containers if hasattr(c, 'short_id')})
    container_name_map = {c.id: c.name for c in containers}
    container_name_map.update({c.short_id: c.name for c in containers if hasattr(c, 'short_id')})

    networks = client.networks.list()
    for net in networks:
        attrs = net.attrs or {}
        net_id = net.id
        name = net.name
        driver = attrs.get('Driver', 'bridge')
        scope = attrs.get('Scope', 'local')

        # System networks are the default pre-defined bridge, host, null/none networks
        is_system = name in ['bridge', 'host', 'none']

        # Extract IPAM config (Subnet, Gateway)
        ipam = attrs.get('IPAM') or {}
        ipam_config = ipam.get('Config') or []
        subnet = ""
        gateway = ""
        if ipam_config:
            subnet = ipam_config[0].get('Subnet') or ""
            gateway = ipam_config[0].get('Gateway') or ""

        # Extract attached containers (only UI-required fields)
        net_containers = attrs.get('Containers') or {}
        containers_list = []
        for cid, cinfo in net_containers.items():
            short_cid = cid[:12]
            c_name = cinfo.get('Name') or container_name_map.get(cid, short_cid)
            c_status = container_status_map.get(cid, container_status_map.get(short_cid, 'running'))
            ipv4 = cinfo.get('IPv4Address', '')
            containers_list.append({
                "id": short_cid,
                "name": c_name,
                "status": c_status,
                "ipv4": ipv4,
            })

        result.append({
            "id": net_id,
            "name": name,
            "driver": driver,
            "scope": scope,
            "is_system": is_system,
            "subnet": subnet,
            "gateway": gateway,
            "containers": containers_list,
        })
    return result


def get_networks():
    containers = client.containers.list(all=True)
    return json.dumps(_build_networks_data(containers))


def get_unified_snapshot() -> dict[str, list[dict]]:
    """Collect containers, images, volumes, and networks in a single pass."""
    containers = client.containers.list(all=True)
    return {
        "containers": _build_containers_data(containers),
        "images": _build_images_data(containers),
        "volumes": _build_volumes_data(containers),
        "networks": _build_networks_data(containers),
    }


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
