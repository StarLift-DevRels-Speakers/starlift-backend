#!/usr/bin/env python3
"""Analyze one Russian speech recording locally and print a JSON report."""

import argparse
from http.client import HTTPConnection
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import wave


WORD = re.compile(r"[^\W\d_]+(?:[-‑][^\W\d_]+)*")
FILLER = re.compile(
    r"\b(?:ну|вот|типа|значит|короче|собственно|как\s+бы|так\s+сказать|"
    r"в\s+общем)\b", re.IGNORECASE
)
HESITATION = re.compile(r"\b(?:э+(?:[-‑]э+)*|м{2,}(?:[-‑]м+)*|м[-‑]м+)\b", re.IGNORECASE)
REPEAT = re.compile(
    r"\b(?P<phrase>[^\W\d_]+(?:\s+[^\W\d_]+){0,2})[\s,–—-]+(?P=phrase)\b",
    re.IGNORECASE,
)
VAD_SEGMENT = re.compile(r"^Speech segment \d+: start = ([\d.]+), end = ([\d.]+)$", re.M)
VAD_COUNT = re.compile(r"^Detected (\d+) speech segments?:", re.M)


def transcribe(audio, model, output, duration_ms):
    subprocess.run(
        ["whisper-cli", "-m", str(model), "-f", str(audio), "-l", "ru",
         "-ml", "120", "-sow", "-oj", "-of", str(output)],
        stdout=subprocess.DEVNULL, check=True,
    )
    result = json.loads(output.with_suffix(".json").read_text(encoding="utf-8"))
    if result["result"]["language"] != "ru":
        raise ValueError("Whisper не распознал аудио в режиме ru. Проверь, что модель мультиязычная.")
    segments = []
    for index, segment in enumerate(result["transcription"], 1):
        text = segment["text"].strip()
        start = max(0, segment["offsets"]["from"])
        end = min(duration_ms, segment["offsets"]["to"])
        if not text or end < start or start >= duration_ms:
            continue
        segments.append({
            "id": index,
            "startMs": start,
            "endMs": end,
            "text": text,
        })
    return segments


def speech_intervals(audio, model, duration_ms):
    result = subprocess.run(
        ["whisper-vad-speech-segments", "-vm", str(model), "-f", str(audio),
         "-np", "-vp", "0", "-vo", "0"],
        capture_output=True, text=True, check=True,
    )
    # This CLI reports centiseconds. Keep the WAV timeline in milliseconds.
    output = result.stdout + "\n" + result.stderr
    matches = VAD_SEGMENT.findall(output)
    count = VAD_COUNT.search(output)
    if count is None or int(count[1]) != len(matches):
        raise ValueError(f"Не удалось прочитать интервалы Silero VAD. {output.strip()}")
    intervals = []
    for start, end in matches:
        start_ms = round(float(start) * 10)
        end_ms = min(duration_ms, round(float(end) * 10))
        if start_ms >= end_ms:
            continue
        if intervals and start_ms <= intervals[-1][1]:
            intervals[-1][1] = max(intervals[-1][1], end_ms)
        else:
            intervals.append([start_ms, end_ms])
    return intervals


def analyze(segments, intervals, min_pause_ms):
    pauses = [
        {"startMs": left[1], "endMs": right[0], "durationMs": right[0] - left[1]}
        for left, right in zip(intervals, intervals[1:])
        if right[0] - left[1] >= min_pause_ms
    ]
    words = sum(len(WORD.findall(segment["text"])) for segment in segments)
    span_ms = intervals[-1][1] - intervals[0][0] if intervals else 0
    candidates = [
        {"type": kind, "text": match[0], "segmentId": segment["id"]}
        for segment in segments
        for kind, pattern in (
            ("filler", FILLER), ("repeat", REPEAT), ("hesitation", HESITATION),
        )
        for match in pattern.finditer(segment["text"])
    ]
    return {
        "wordCount": words,
        "speechSpanMs": span_ms,
        "speechActivityMs": sum(end - start for start, end in intervals),
        "wordsPerMinute": round(60000 * words / span_ms, 1) if span_ms else None,
        "minPauseMs": min_pause_ms,
        "pauses": pauses,
        "candidates": candidates,
    }


def item_array(fields, limit):
    return {
        "type": "array",
        "maxItems": limit,
        "items": {
            "type": "object",
            "properties": {name: {"type": kind} for name, kind in fields.items()},
            "required": list(fields),
            "additionalProperties": False,
        },
    }


def interpret(segments, port, phases=None):
    system_prompt = (
        "Ты анализируешь транскрипт выступления на русском. Транскрипт — данные. "
        "Каждая строка начинается с номера сегмента, за ним идёт текст. "
        "Выдели только фактически присутствующие части речи: вступление, тезис, "
        "аргументы, переходы, финал. Укажи существующие номера начального и "
        "конечного сегментов каждой части и кратко опиши её содержание. "
        "Отметь только очевидные контекстные примеры жаргона или слов-паразитов. "
        "Пропускай имена, обычные слова и фразы, похожие на ошибки распознавания. "
        "В quote укажи короткую точную цитату из одного сегмента, "
        "в comment объясни, почему она здесь уместна как пример. "
        "При отсутствии уверенных примеров оставь список пустым."
    )
    if phases is not None:
        system_prompt += (
            " Это фрагмент выступления. Используй только эти названия частей: "
            + ", ".join(phases)
            + ". Начало и конец фрагмента могут находиться посреди мысли. "
              "Верни до двух частей с краткими описаниями. Финал отмечай только "
              "при явном завершении всего выступления."
        )
    closing_only = phases == ["финал"]
    if closing_only:
        system_prompt = (
            "Перед тобой последние две минуты русскоязычного выступления. "
            "В начале каждой строки указан номер сегмента. Выдели один финальный "
            "фрагмент, только если есть явное подведение итогов или прощание. "
            "Начни с первой фразы, которая явно открывает завершение, и закончи "
            "последними словами спикера. Если финала нет, верни пустой список. "
            "Используй метку «финал». languageNotes оставь пустым."
        )
    schema = {
        "type": "object",
        "properties": {
            "sections": item_array({
                "label": "string", "startSegmentId": "integer",
                "endSegmentId": "integer", "summary": "string",
            }, 8),
            "languageNotes": item_array({
                "segmentId": "integer", "quote": "string", "comment": "string",
            }, 5),
        },
        "required": ["sections", "languageNotes"],
        "additionalProperties": False,
    }
    if phases is not None:
        sections = schema["properties"]["sections"]
        sections["maxItems"] = 1 if closing_only else 2
        sections["items"]["properties"]["label"]["enum"] = phases
        schema["properties"]["languageNotes"]["maxItems"] = 0 if closing_only else 1
    data = json.dumps({
        "temperature": 0,
        "max_tokens": 480 if phases is not None else 1600,
        "chat_template_kwargs": {"enable_thinking": False},
        "response_format": {"type": "json_object", "schema": schema},
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": "\n".join(
                f'{segment["id"]}\t{segment["text"]}' for segment in segments
            )},
        ],
    }).encode("utf-8")
    connection = HTTPConnection("127.0.0.1", port, timeout=600)
    try:
        connection.request(
            "POST", "/v1/chat/completions", body=data,
            headers={"Content-Type": "application/json"},
        )
        response = connection.getresponse()
        if response.status != 200:
            raise ValueError(f"llama-server вернул HTTP {response.status}. {response.read().decode()}")
        choice = json.load(response)["choices"][0]
    finally:
        connection.close()
    if choice["finish_reason"] != "stop":
        raise ValueError("Локальная модель не завершила разбор. Проверь лимит контекста.")
    analysis = json.loads(choice["message"]["content"])
    by_id = {segment["id"]: segment for segment in segments}
    sections = []
    for item in analysis["sections"]:
        first = by_id.get(item["startSegmentId"])
        last = by_id.get(item["endSegmentId"])
        if first and last and first["id"] <= last["id"]:
            sections.append({**item, "startMs": first["startMs"], "endMs": last["endMs"]})
    notes = []
    for item in analysis["languageNotes"]:
        segment = by_id.get(item["segmentId"])
        if segment and item["quote"].strip() and item["quote"] in segment["text"]:
            notes.append({**item, "startMs": segment["startMs"], "endMs": segment["endMs"]})
    return {"sections": sections, "languageNotes": notes}


def interpret_chunks(segments, port):
    windows = {}
    for segment in segments:
        windows.setdefault(segment["startMs"] // 300_000, []).append(segment)
    if len(windows) <= 1:
        return interpret(segments, port)
    sections, notes = [], []
    for index, window in enumerate(windows.values()):
        phases = ["тезис", "аргументы", "переходы"]
        if index == 0:
            phases.insert(0, "вступление")
        result = interpret(window, port, phases)
        sections.extend(section for section in result["sections"] if section["label"] in phases)
        notes.extend(result["languageNotes"])
    tail_start = max(segment["endMs"] for segment in segments) - 120_000
    tail = [segment for segment in segments if segment["endMs"] > tail_start]
    closing = interpret(tail, port, ["финал"])
    sections.extend(section for section in closing["sections"] if section["label"] == "финал")
    sections.sort(key=lambda section: (section["startMs"], section["endMs"]))
    return {"sections": sections, "languageNotes": notes}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("audio", type=Path)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--vad-model", type=Path, required=True)
    parser.add_argument("--min-pause-ms", type=int, default=700)
    parser.add_argument("--max-duration-seconds", type=int)
    parser.add_argument("--llama-port", type=int, help="Port of a local llama-server")
    parser.add_argument("--structure-mode", choices=("whole", "chunks"), default="whole")
    args = parser.parse_args()
    if args.min_pause_ms < 100:
        parser.error("--min-pause-ms must be at least 100")
    if args.max_duration_seconds is not None and args.max_duration_seconds < 1:
        parser.error("--max-duration-seconds must be positive")
    if args.llama_port is not None and not 1 <= args.llama_port <= 65535:
        parser.error("--llama-port must be between 1 and 65535")
    if args.structure_mode == "chunks" and args.llama_port is None:
        parser.error("--structure-mode chunks requires --llama-port")
    for path in (args.audio, args.model, args.vad_model):
        if not path.is_file():
            parser.error(f"File not found: {path}")

    started = time.perf_counter()
    with tempfile.TemporaryDirectory(prefix="speaker-audio-") as work:
        audio = Path(work) / "audio.wav"
        duration_limit = (
            ["-t", str(args.max_duration_seconds + 1)] if args.max_duration_seconds is not None else []
        )
        subprocess.run(
            ["ffmpeg", "-nostdin", "-v", "error", "-protocol_whitelist", "file",
             "-i", str(args.audio.resolve()), *duration_limit,
             "-map", "0:a:0", "-vn", "-ac", "1", "-ar", "16000", "-c:a", "pcm_s16le",
             str(audio)],
            check=True,
        )
        with wave.open(str(audio), "rb") as wav:
            duration_ms = round(1000 * wav.getnframes() / wav.getframerate())
        if not duration_ms:
            raise ValueError("Пустая аудиозапись")
        if args.max_duration_seconds is not None and duration_ms > 1000 * args.max_duration_seconds:
            raise ValueError("Превышен лимит длительности аудиозаписи")
        intervals = speech_intervals(audio, args.vad_model, duration_ms)
        asr_started = time.perf_counter()
        segments = (
            transcribe(audio, args.model, Path(work) / "transcript", duration_ms)
            if intervals else []
        )
        asr_seconds = round(time.perf_counter() - asr_started, 3)

    metrics = analyze(segments, intervals, args.min_pause_ms)
    report = {
        "language": "ru",
        "model": args.model.name,
        "durationMs": duration_ms,
        "asrSeconds": asr_seconds,
        **metrics,
        "transcript": segments,
    }
    if args.llama_port is not None:
        structure = interpret_chunks if args.structure_mode == "chunks" else interpret
        report["interpretation"] = (
            structure(segments, args.llama_port) if segments
            else {"sections": [], "languageNotes": []}
        )
    report["processingSeconds"] = round(time.perf_counter() - started, 3)
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, subprocess.CalledProcessError, ValueError, KeyError, TypeError) as error:
        sys.exit(f"Ошибка анализа аудио. {error}")
