#!/bin/bash
# Docker services plus a native macOS worker, preserving Metal acceleration.
set -euo pipefail

speech_root="$(cd "$(dirname "$0")/../.." && pwd)"
speech_assets="$HOME/.local/share/speakers-audio-analysis"
speech_state="$speech_assets/local"
speech_label=club.speakers.audio-analysis.local
speech_service="gui/$(id -u)/$speech_label"
speech_plist="$speech_state/worker.plist"
export PATH="$speech_assets/whisper/build/bin:/opt/homebrew/bin:/usr/local/bin:$PATH"

mkdir -p "$speech_state"
if [[ ! -f "$speech_state/worker.token" ]]; then
    (umask 077; python3 -c 'import secrets; print(secrets.token_hex(32))' > "$speech_state/worker.token")
fi
export AUDIO_ANALYSIS_WORKER_TOKEN="$(cat "$speech_state/worker.token")"

compose() {
    docker compose --env-file /dev/null -p starlift-local -f "$speech_root/compose.local.yaml" "$@"
}

case "${1:-up}" in
    up)
        [[ "$(uname -s)" == Darwin ]] || { echo "This launcher requires macOS for the Metal worker." >&2; exit 1; }
        docker info >/dev/null 2>&1 || { echo "Start Docker Desktop first." >&2; exit 1; }
        python3 "$speech_root/tools/audio-analysis/launchd.py" \
            --label "$speech_label" --base-url http://localhost:18081 \
            --model "$speech_assets/models/ggml-large-v3-turbo-q5_0.bin" \
            --vad-model "$speech_assets/models/ggml-silero-v6.2.0.bin" \
            --token-file "$speech_state/worker.token" --log-dir "$speech_state" > "$speech_plist"
        compose up -d --build
        python3 - <<'PY'
import time
from http.client import HTTPException
from urllib.request import urlopen
for attempt in range(120):
    try:
        with urlopen('http://localhost:18081/health', timeout=2) as response:
            if response.status == 200:
                break
    except (OSError, HTTPException):
        pass
    time.sleep(1)
else:
    raise SystemExit('Backend did not become ready. Run: tools/audio-analysis/local.sh logs')
PY
        if ! launchctl print "$speech_service" >/dev/null 2>&1; then
            launchctl bootstrap "gui/$(id -u)" "$speech_plist"
        fi
        launchctl print "$speech_service" >/dev/null
        echo "StarLift: http://localhost:5174/speaker/media"
        echo "Worker log: $speech_state/audio-worker.log"
        ;;
    down)
        if launchctl print "$speech_service" >/dev/null 2>&1; then
            launchctl bootout "$speech_service"
        fi
        compose down
        ;;
    status)
        compose ps
        launchctl print "$speech_service" | sed -n '/state =/p; /pid =/p'
        ;;
    logs)
        compose logs --tail=60 app web
        [[ ! -f "$speech_state/audio-worker.log" ]] || tail -60 "$speech_state/audio-worker.log"
        ;;
    *) echo "Usage: $0 [up|down|status|logs]" >&2; exit 2 ;;
esac
