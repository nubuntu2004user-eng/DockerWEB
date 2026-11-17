# DockerWEB

A web interface for managing a local Docker engine. Built with a Compose Multiplatform (WebAssembly) frontend and a Python FastAPI backend communicating via the Docker socket and Server-Sent Events (SSE).

## Demos

<details open>
<summary><b>1. Application Overview</b> (walkthrough of all main views)</summary>
<br>

![App Overview](docs/assets/demo1.gif)
</details>

<details>
<summary><b>2. Container Management</b> (running, managing, and removing containers)</summary>
<br>

![Container Lifecycle](docs/assets/demo2.gif)
</details>

<details>
<summary><b>3. Self-Removal & Error Handling</b> (removing the active DockerWEB container and browser connection error)</summary>
<br>

![Container Removal and Browser Error](docs/assets/demo3.gif)
</details>

## Features

- **Containers:** View status metrics, start, stop, restart, pause, view logs, inspect JSON configuration, and run interactive shell commands.
- **Images:** Pull images with real-time download status, view layer history, tag, run, delete, and prune unused images.
- **Volumes:** Create local volumes, browse volume file trees, backup to host directories, clone data between volumes, and inspect configs.
- **Networks:** Create custom bridge networks, inspect IP/subnet assignments, and connect or disconnect running containers.

## Quickstart

### Option 1: Docker Compose

```bash
docker compose up --build
```

### Option 2: Docker CLI

```bash
docker build -t dockerweb .

docker run -d \
  -p 9456:9456 \
  -v /var/run/docker.sock:/var/run/docker.sock \
  --name dockerweb \
  dockerweb
```

Access the UI at `http://127.0.0.1:9456` (redirects automatically to `dockerweb.html`).

> [!WARNING]
> DockerWEB mounts the Docker socket and exposes shell execution endpoints with no authentication.
> Run it only in trusted local environments. For host-only exposure, bind to `127.0.0.1`:
> ```bash
> docker run -d -p 127.0.0.1:9456:9456 -v /var/run/docker.sock:/var/run/docker.sock --name dockerweb dockerweb
> ```
> Do not expose port `9456` to untrusted networks without an authenticating reverse proxy.

### Local Development

#### 1. Backend (FastAPI)

Requires Python 3.14+ and `uv`.

```bash
uv sync
uv run uvicorn BackendLogic.src.main:app --port 9456 --reload
```

#### 2. Frontend (Compose Wasm)

Requires JDK 21+.

```bash
cd DockerWebUI

# Dev server with live reload:
./gradlew wasmJsBrowserDevelopmentRun

# Or build static assets for the backend to serve:
./gradlew :webApp:wasmJsBrowserDistribution
```
