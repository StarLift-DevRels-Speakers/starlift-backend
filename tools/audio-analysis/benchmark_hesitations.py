#!/usr/bin/env python3
"""Evaluate an experimental acoustic filled-pause classifier on paired WAV/EAF files."""

import argparse
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

import numpy as np


RATE = 16000
HOP = 160
FRAME = 480
WINDOW = 40
STEP = 10


def annotations(path):
    root = ET.parse(path).getroot()
    slots = {item.attrib["TIME_SLOT_ID"]: int(item.attrib["TIME_VALUE"])
             for item in root.findall("./TIME_ORDER/TIME_SLOT")
             if "TIME_VALUE" in item.attrib}
    events = []
    for entry in root.findall("./TIER[@TIER_ID='Pauses']/ANNOTATION/ALIGNABLE_ANNOTATION"):
        value = entry.findtext("ANNOTATION_VALUE", "")
        if not re.search(r"F\(", value):
            continue
        start = slots[entry.attrib["TIME_SLOT_REF1"]]
        end = slots[entry.attrib["TIME_SLOT_REF2"]]
        events.append((start, end))
    return events


def frames(path):
    pcm = subprocess.check_output([
        "ffmpeg", "-v", "error", "-i", str(path), "-map", "0:a:0",
        "-ac", "1", "-ar", str(RATE), "-f", "s16le", "-",
    ])
    samples = np.frombuffer(pcm, dtype="<i2").astype(float) / 32768
    duration = len(samples) / RATE
    windows = np.lib.stride_tricks.sliding_window_view(samples, FRAME)[::HOP]
    bins = np.searchsorted(np.fft.rfftfreq(1024, 1 / RATE),
                           [100, 250, 400, 600, 850, 1150, 1500, 2100, 3000, 4500, 7500])
    bands = []
    for start in range(0, len(windows), 512):
        chunk = windows[start:start + 512]
        power = np.abs(np.fft.rfft(chunk * np.hanning(FRAME), n=1024)) ** 2
        band = np.column_stack([power[:, a:b].mean(axis=1) for a, b in zip(bins, bins[1:])])
        log_band = np.log(band + 1e-7)
        rms = np.log(np.mean(chunk ** 2, axis=1) + 1e-9)
        correlation = np.fft.irfft(power, n=1024)[:, 40:200]
        energy = np.maximum(power[:, 0] + 2 * power[:, 1:-1].sum(axis=1) + power[:, -1], 1e-9)
        periodicity = 1024 * correlation.max(axis=1) / energy
        bands.append(np.column_stack((log_band - log_band.mean(axis=1, keepdims=True),
                                      rms, periodicity)))
    return np.concatenate(bands), duration


def summarize(features, centers):
    offsets = np.arange(-WINDOW // 2, WINDOW // 2)
    blocks = features[np.clip(centers[:, None] + offsets, 0, len(features) - 1)]
    return np.column_stack((blocks.mean(axis=1), blocks.std(axis=1),
                            np.abs(np.diff(blocks, axis=1)).mean(axis=1)))


def load(path):
    features, duration = frames(path.with_suffix(".wav"))
    events = annotations(path)
    centers = np.arange(WINDOW // 2, len(features) - WINDOW // 2, STEP)
    milliseconds = centers * 10 + 15
    positives = np.array([round(((a + b) / 2 - 15) / 10) for a, b in events], dtype=int)
    negative = np.ones(len(centers), dtype=bool)
    for start, end in events:
        negative &= (milliseconds < start - 200) | (milliseconds > end + 200)
    samples = summarize(features, centers)
    return samples, milliseconds, summarize(features, positives), samples[negative], events, duration


def fit(records):
    positive = np.concatenate([r[2] for r in records])
    negative = np.concatenate([r[3] for r in records])
    mean = (positive.mean(axis=0) + negative.mean(axis=0)) / 2
    scale = np.maximum(np.concatenate([positive, negative]).std(axis=0), 1e-6)
    pos, neg = (positive - mean) / scale, (negative - mean) / scale
    covariance = (pos.T @ pos / len(pos) + neg.T @ neg / len(neg)) / 2
    weights = np.linalg.solve(covariance + 0.5 * np.eye(len(mean)), pos.mean(axis=0))
    return mean, scale, weights


def detections(scores, centers, threshold):
    selected = []
    for index in np.argsort(-scores):
        if scores[index] < threshold:
            break
        time = int(centers[index])
        if all(abs(time - other) >= 450 for other in selected):
            selected.append(time)
    return selected


def hits(predicted, events):
    unmatched = list(events)
    matched = 0
    for point in predicted:
        candidates = [(abs(point - (a + b) / 2), i) for i, (a, b) in enumerate(unmatched)
                      if a - 150 <= point <= b + 150]
        if candidates:
            unmatched.pop(min(candidates)[1])
            matched += 1
    return matched


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("annotations", type=Path, nargs="+")
    args = parser.parse_args()
    if len(args.annotations) < 3:
        parser.error("At least three WAV/EAF pairs are required")
    records = [load(path) for path in args.annotations]
    if sum(bool(record[4]) for record in records) < 2:
        parser.error("At least two recordings must contain filled pauses")
    expected = sum(len(record[4]) for record in records)
    minutes = sum(record[5] for record in records) / 60
    for threshold in (0.45, 0.6, 0.75, 0.9):
        true, predicted = 0, 0
        for index, (samples, centers, _, _, events, _) in enumerate(records):
            mean, scale, weights = fit(records[:index] + records[index + 1:])
            found = detections((samples - mean) / scale @ weights, centers, threshold)
            true += hits(found, events)
            predicted += len(found)
        print(f"threshold={threshold:.2f} hits={true}/{expected} "
              f"precision={true / max(predicted, 1):.1%} recall={true / expected:.1%} "
              f"false_alarms_per_min={(predicted - true) / minutes:.1f}")
    print(f"{len(records)} recordings; {minutes:.1f} minutes; {expected} filled pauses")


if __name__ == "__main__":
    main()
