FROM gradle:jdk21 AS build
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY ./DockerWebUI .
RUN chmod +x gradlew && ./gradlew :webApp:wasmJsBrowserDistribution --no-daemon

FROM python:3.14
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates && rm -rf /var/lib/apt/lists/*
ADD https://astral.sh/uv/install.sh /uv-installer.sh
RUN sh /uv-installer.sh && rm /uv-installer.sh

WORKDIR /app

COPY ./pyproject.toml /app/pyproject.toml
RUN /root/.local/bin/uv sync
ENV PATH="/app/.venv/bin:$PATH"

COPY ./BackendLogic /app/BackendLogic
COPY --from=build /app/webApp/build/dist/wasmJs/productionExecutable /app/DockerWebUI/webApp/build/dist/wasmJs/productionExecutable
RUN mv /app/DockerWebUI/webApp/build/dist/wasmJs/productionExecutable/index.html /app/DockerWebUI/webApp/build/dist/wasmJs/productionExecutable/dockerweb.html

CMD ["uvicorn", "BackendLogic.src.main:app", "--host", "0.0.0.0", "--port", "9456"]
