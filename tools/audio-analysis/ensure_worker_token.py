#!/usr/bin/env python3
"""Persist a worker token in the deployment dotenv file without printing secrets."""
import argparse
import os
from pathlib import Path
import re
import secrets
import tempfile


def ensure_token(path):
    path = Path(path).resolve()
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    content = path.read_text() if path.exists() else ""
    matches = list(re.finditer(r"(?m)^[ \t]*(?:export[ \t]+)?AUDIO_ANALYSIS_WORKER_TOKEN[ \t]*=[^\r\n]*", content))
    match = matches[-1] if matches else None
    value = match[0].split("=", 1)[1].strip() if match else ""
    # Compose supports quoted empty values and a comment after an empty assignment.
    value = value.split(" #", 1)[0].strip()
    if value and value not in {"''", '""'} and not value.startswith("#"):
        path.chmod(0o600)
        return
    line = "AUDIO_ANALYSIS_WORKER_TOKEN=" + secrets.token_hex(32)
    if match:
        content = content[:match.start()] + line + content[match.end():]
    else:
        content = content.rstrip("\n") + ("\n" if content else "") + line + "\n"
    # Atomic replacement avoids truncating other credentials if the runner is interrupted.
    with tempfile.NamedTemporaryFile(mode="w", dir=path.parent, delete=False) as output:
        temporary = Path(output.name)
        try:
            output.write(content)
            output.close()
            os.replace(temporary, path)
        finally:
            temporary.unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("env_file", type=Path)
    ensure_token(parser.parse_args().env_file)
