# DockerWEB

A web interface for managing a local Docker engine. Built with a Compose Multiplatform (WebAssembly) frontend and a Python FastAPI backend communicating via the Docker socket and Server-Sent Events (SSE).

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

Access the UI at `http://localhost:9456`.

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
