#!/usr/bin/env python3
"""Generate a macOS LaunchAgent for the native audio analysis worker."""

import argparse
import os
from pathlib import Path
import plistlib
import shutil
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--label", default="club.speakers.audio-analysis")
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--vad-model", type=Path, required=True)
    parser.add_argument("--token-file", type=Path, required=True)
    parser.add_argument("--log-dir", type=Path, required=True)
    args = parser.parse_args()
    for path in (args.model, args.vad_model, args.token_file):
        if not path.is_file():
            parser.error(f"File not found: {path}")
    if args.token_file.stat().st_mode & 0o077:
        parser.error("Worker token file must be private (chmod 600)")
    if not args.log_dir.is_dir():
        parser.error(f"Log directory not found: {args.log_dir}")
    executables = ("ffmpeg", "ffprobe", "whisper-cli", "whisper-vad-speech-segments")
    if any(not shutil.which(tool) for tool in executables):
        parser.error("FFmpeg and whisper.cpp executables must be available on PATH")
    log = args.log_dir.resolve() / "audio-worker.log"
    plistlib.dump({
        "Label": args.label,
        "ProgramArguments": [
            sys.executable, str(Path(__file__).resolve().with_name("worker.py")),
            "--base-url", args.base_url, "--model", str(args.model.resolve()),
            "--vad-model", str(args.vad_model.resolve()),
            "--token-file", str(args.token_file.resolve()),
        ],
        "EnvironmentVariables": {
            "PATH": os.pathsep.join(str(Path(p).resolve()) for p in os.get_exec_path() if p),
        },
        "RunAtLoad": True,
        "KeepAlive": True,
        "ThrottleInterval": 15,
        "StandardOutPath": str(log),
        "StandardErrorPath": str(log),
    }, sys.stdout.buffer)


if __name__ == "__main__":
    main()
