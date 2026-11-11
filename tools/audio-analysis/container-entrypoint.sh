#!/bin/sh
set -eu
: "${AUDIO_ANALYSIS_WORKER_TOKEN:?Set the same worker token on app and audio-worker}"
rm -f /tmp/audio-worker.ready

case "${AUDIO_ANALYSIS_MODEL:-turbo}" in
    turbo)
        model=ggml-large-v3-turbo-q5_0.bin
        model_sha=394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2 ;;
    small)
        model=ggml-small.bin
        model_sha=1be3a9b2063867b937e64e2ec7483364a79917e157fa98c5d94b5c1fffea987b ;;
    *) echo "AUDIO_ANALYSIS_MODEL must be turbo or small" >&2; exit 1 ;;
esac

download() {
    file="/models/$1"
    if printf '%s  %s\n' "$2" "$file" | sha256sum --check --status 2>/dev/null; then return; fi
    echo "Downloading $1 (first startup or damaged model)"
    curl --silent --show-error --fail --location --retry 3 --connect-timeout 20 --max-time 900 \
        --output "$file.part" "$3"
    if ! printf '%s  %s\n' "$2" "$file.part" | sha256sum --check --status; then
        echo "Checksum mismatch: $1" >&2
        rm -f "$file.part"
        exit 1
    fi
    mv "$file.part" "$file"
}

download "$model" "$model_sha" "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$model"
download ggml-silero-v6.2.0.bin 2aa269b785eeb53a82983a20501ddf7c1d9c48e33ab63a41391ac6c9f7fb6987 \
    https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v6.2.0.bin

exec python3 /app/worker.py --base-url http://app:8080 --allow-insecure-http \
    --health-file /tmp/audio-worker.ready \
    --model "/models/$model" --vad-model /models/ggml-silero-v6.2.0.bin "$@"
