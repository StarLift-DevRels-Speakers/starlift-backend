FROM python:3.12-slim-bookworm AS build
RUN apt-get update && apt-get install -y --no-install-recommends build-essential cmake git ca-certificates \
    && rm -rf /var/lib/apt/lists/*
RUN git clone --depth 1 --branch v1.9.1 https://github.com/ggml-org/whisper.cpp.git /src/whisper
RUN cpu_variants=""; \
    if [ "$(uname -m)" = x86_64 ]; then cpu_variants="-DGGML_BACKEND_DL=ON -DGGML_CPU_ALL_VARIANTS=ON"; fi; \
    cmake -S /src/whisper -B /src/whisper/build -DCMAKE_BUILD_TYPE=Release -DCMAKE_INSTALL_LIBDIR=lib \
      -DGGML_NATIVE=OFF -DGGML_METAL=OFF $cpu_variants \
      -DWHISPER_BUILD_TESTS=OFF -DWHISPER_CURL=OFF \
    && cmake --build /src/whisper/build --parallel 4 \
    && cmake --install /src/whisper/build --prefix /opt/whisper

FROM python:3.12-slim-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg curl ca-certificates libgomp1 \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app worker \
    && mkdir -p /app /models && chown worker /models
COPY --from=build /opt/whisper /opt/whisper
ENV PATH="/opt/whisper/bin:${PATH}" LD_LIBRARY_PATH=/opt/whisper/lib PYTHONUNBUFFERED=1
WORKDIR /app
COPY tools/audio-analysis/worker.py tools/audio-analysis/analyze.py tools/audio-analysis/container-entrypoint.sh ./
USER worker
HEALTHCHECK --interval=10s --timeout=3s --start-period=15m --retries=3 \
    CMD python3 -c 'from pathlib import Path; import time,sys; p=Path("/tmp/audio-worker.ready"); sys.exit(not (p.exists() and time.time()-p.stat().st_mtime < 840))'
ENTRYPOINT ["/bin/sh", "/app/container-entrypoint.sh"]
