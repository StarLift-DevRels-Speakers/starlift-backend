#!/usr/bin/env python3
"""Run one audio analysis job at a time using the Spring worker API."""

import argparse
from http.client import HTTPException
import json
import logging
import math
import os
from pathlib import Path
import shutil
import signal
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError
from urllib.parse import urlsplit
from urllib.request import HTTPRedirectHandler, ProxyHandler, Request, build_opener, urlopen

MAX_BYTES = 50 * 1024 * 1024
MAX_SECONDS = 3600
LOGGER = logging.getLogger("audio-worker")


class InvalidAudio(Exception):
    pass


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


WORKER_API = build_opener(ProxyHandler({}), NoRedirect())


def valid_base_url(url):
    try:
        parsed = urlsplit(url)
        return (parsed.hostname and not parsed.username and not parsed.password
                and not parsed.query and not parsed.fragment
                and (parsed.scheme == "https" or (
                    parsed.scheme == "http" and parsed.hostname in {"localhost", "127.0.0.1", "::1"}
                )))
    except ValueError:
        return False


def request(base, token, path, method, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = Request(base + "/internal/audio-analyses" + path, data=data, method=method,
                  headers={"X-Audio-Worker-Token": token, "Content-Type": "application/json"})
    with WORKER_API.open(req, timeout=15) as response:
        return None if response.status == 204 else json.load(response)


def download(url, audio):
    deadline = time.monotonic() + 60
    with urlopen(url, timeout=15) as response, audio.open("wb") as out:
        content_length = response.headers.get("Content-Length")
        expected_size = int(content_length) if content_length and content_length.isdecimal() else None
        if expected_size is not None and expected_size > MAX_BYTES:
            raise InvalidAudio()
        size = 0
        while chunk := response.read1(256 * 1024):
            size += len(chunk)
            if size > MAX_BYTES:
                raise InvalidAudio()
            if time.monotonic() >= deadline:
                raise TimeoutError()
            out.write(chunk)
        if expected_size is not None and size != expected_size:
            raise OSError("Incomplete audio download")


def check_audio(audio):
    try:
        probe = subprocess.run(
            ["ffprobe", "-v", "error", "-protocol_whitelist", "file",
             "-format_whitelist", "wav,mp3,ogg,mov", "-show_streams", "-show_format",
             "-of", "json", str(audio)],
            capture_output=True, check=True, timeout=15,
        )
        metadata = json.loads(probe.stdout)
        duration = float(metadata["format"]["duration"])
        streams = metadata["streams"]
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, ValueError, KeyError) as error:
        raise InvalidAudio() from error
    if not math.isfinite(duration) or not 0 < duration <= MAX_SECONDS:
        raise InvalidAudio()
    if not any(stream["codec_type"] == "audio" for stream in streams):
        raise InvalidAudio()
    if any(stream["codec_type"] == "video" and not stream.get("disposition", {}).get("attached_pic")
           for stream in streams):
        raise InvalidAudio()


def analyze(audio, args):
    command = [
        sys.executable, str(Path(__file__).with_name("analyze.py")), str(audio),
        "--model", str(args.model), "--vad-model", str(args.vad_model),
        "--max-duration-seconds", str(MAX_SECONDS),
    ]
    env = dict(os.environ, TMPDIR=str(audio.parent))
    with subprocess.Popen(command, stdout=subprocess.PIPE, start_new_session=True, env=env) as process:
        try:
            output, _ = process.communicate(timeout=12 * 60)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
            process.communicate()
            raise
        if process.returncode:
            raise subprocess.CalledProcessError(process.returncode, command)
    return json.loads(output)


def process_job(job, args):
    with tempfile.TemporaryDirectory(prefix="audio-worker-") as folder:
        audio = Path(folder) / "input"
        try:
            download(job["audioUrl"], audio)
        except InvalidAudio:
            return {"errorCode": "INVALID_AUDIO"}
        except (OSError, HTTPException):
            return {"errorCode": "DOWNLOAD_FAILED"}

        try:
            check_audio(audio)
            return {"report": analyze(audio, args)}
        except InvalidAudio:
            return {"errorCode": "INVALID_AUDIO"}
        except subprocess.TimeoutExpired:
            return {"errorCode": "ANALYSIS_TIMEOUT"}
        except (OSError, subprocess.CalledProcessError, ValueError, KeyError):
            return {"errorCode": "ANALYSIS_FAILED"}


def finish(base, token, job, result):
    payload = {"leaseId": job["leaseId"], **result}
    for attempt in range(3):
        try:
            request(base, token, f'/{job["analysisId"]}/result', "PUT", payload)
            return True
        except HTTPError as error:
            if error.code == 409:
                LOGGER.info("Задача %s завершена или аренда истекла", job["analysisId"])
                return True
            if error.code < 500:
                LOGGER.error("Spring отклонил результат задачи %s: HTTP %s", job["analysisId"], error.code)
                return False
        except (OSError, HTTPException):
            pass
        if attempt < 2:
            time.sleep(3)
    LOGGER.error("Не удалось передать результат задачи %s", job["analysisId"])
    return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--vad-model", type=Path, required=True)
    parser.add_argument("--token-file", type=Path, help="Read the worker token from a private file")
    parser.add_argument("--once", action="store_true", help="Claim and process at most one job")
    args = parser.parse_args()
    if args.token_file:
        try:
            if args.token_file.stat().st_mode & 0o077:
                parser.error("Worker token file must be private (chmod 600)")
            token = args.token_file.read_text(encoding="utf-8").strip()
        except (OSError, UnicodeError) as error:
            parser.error(str(error))
    else:
        token = os.environ.get("AUDIO_ANALYSIS_WORKER_TOKEN")
    if not token or not token.strip():
        parser.error("Set AUDIO_ANALYSIS_WORKER_TOKEN or provide --token-file")
    for model in (args.model, args.vad_model):
        if not model.is_file():
            parser.error(f"Model not found: {model}")
    for tool in ("ffmpeg", "ffprobe", "whisper-cli", "whisper-vad-speech-segments"):
        if not shutil.which(tool):
            parser.error(f"Tool not found on PATH: {tool}")

    base = args.base_url.rstrip("/")
    if not valid_base_url(base):
        parser.error("--base-url requires HTTPS or loopback HTTP")
    logging.basicConfig(level=logging.INFO)
    while True:
        try:
            job = request(base, token, "/claim", "POST")
        except HTTPError as error:
            if error.code < 500 or args.once:
                sys.exit(f"Spring отклонил запрос воркера: HTTP {error.code}")
            LOGGER.warning("Spring вернул HTTP %s при получении задачи", error.code)
            job = None
        except (OSError, HTTPException):
            if args.once:
                sys.exit("Spring недоступен")
            LOGGER.warning("Spring недоступен")
            job = None
        if job:
            LOGGER.info("Анализ задачи %s", job["analysisId"])
            result = process_job(job, args)
            if not finish(base, token, job, result) and args.once:
                sys.exit(1)
        if args.once:
            return
        if not job:
            time.sleep(3)


def stop(_signal, _frame):
    raise KeyboardInterrupt


if __name__ == "__main__":
    signal.signal(signal.SIGTERM, stop)
    try:
        main()
    except KeyboardInterrupt:
        sys.exit(130)
