#!/usr/bin/env python3
"""Offline feasibility experiment on generated, nonprivate cards; not an Android dependency."""

import argparse
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import threading
import time

from PIL import Image, ImageDraw, ImageFont


def candidates(text):
    """Abstain on ambiguous identifiers; keep strings/leading zeros and require human review."""
    identifiers = set(re.findall(r"Member ID\s*:\s*([A-Z0-9-]+)", text, re.IGNORECASE))
    expiries = set(re.findall(r"Expires\s*:\s*(\d{2}/\d{4})", text, re.IGNORECASE))
    return {
        "identifier": next(iter(identifiers)) if len(identifiers) == 1 else None,
        "expiry": next(iter(expiries)) if len(expiries) == 1 else None,
    }


def run_engine(image, memory_file, cancel=None):
    started = time.monotonic()
    process = subprocess.Popen(
        [sys.executable, "-c",
         "import pathlib,resource,subprocess,sys; "
         "r=subprocess.run(['tesseract',sys.argv[1],'stdout','-l','eng','--psm','6'],capture_output=True,text=True); "
         "pathlib.Path(sys.argv[2]).write_text(str(resource.getrusage(resource.RUSAGE_CHILDREN).ru_maxrss)); "
         "sys.stdout.write(r.stdout); sys.stderr.write(r.stderr); sys.exit(r.returncode)",
         str(image), str(memory_file)],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, start_new_session=True,
    )
    try:
        while True:
            if (cancel is not None and cancel.is_set()) or time.monotonic() - started > 20:
                os.killpg(process.pid, signal.SIGKILL)
                process.communicate()
                return None
            try:
                stdout, stderr = process.communicate(timeout=0.02)
                if process.returncode:
                    raise RuntimeError("Synthetic OCR process failed: " + stderr[:200])
                return stdout, (time.monotonic() - started) * 1000
            except subprocess.TimeoutExpired:
                continue
    finally:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGKILL)
            process.communicate()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    if any(args.output.iterdir()):
        raise SystemExit("Choose an empty output directory; existing evidence is preserved")
    version = subprocess.check_output(["tesseract", "--version"], text=True).splitlines()[0]
    font = ImageFont.truetype("DejaVuSans.ttf", 48)
    results = []
    for index in range(1, 5):
        identifier = f"00000{index}" if index == 2 else f"KURA-TEST-{index:04d}"
        expected = {"identifier": identifier, "expiry": "12/2030"}
        image = Image.new("RGB", (1200, 760), "#f7f5ec")
        draw = ImageDraw.Draw(image)
        draw.multiline_text((65, 80), f"KURA SYNTHETIC CLUB\n\nMember ID: {identifier}\nExpires: 12/2030\n\nNOT A REAL CARD", font=font, fill="#14221e", spacing=14)
        if index == 4:
            image = image.rotate(2, fillcolor="white")
        path = args.output / f"synthetic-card-{index}.png"
        image.save(path)
        image.close()
        memory = args.output / f"synthetic-card-{index}.rss"
        text, elapsed = run_engine(path, memory)
        actual = candidates(text)
        results.append({
            "fixture": path.name, "expected": expected, "suggested": actual,
            "correct_fields": sum(actual[k] == expected[k] for k in expected),
            "fields": len(expected), "elapsed_ms": round(elapsed, 2),
            "process_peak_rss_kib": int(memory.read_text().strip()),
        })
    # Deliberately conflicting OCR must not select an arbitrary card identifier.
    ambiguous = candidates("Member ID: 000001\nMember ID: 000002\nExpires: 12/2030")
    assert ambiguous["identifier"] is None
    # Corrections are explicit draft edits, not automatic writes to a wallet.
    corrected = dict(ambiguous)
    corrected["identifier"] = "000001"
    assert corrected["identifier"] == "000001" and ambiguous["identifier"] is None
    assert candidates("Member ID: 000001")["identifier"] == "000001"
    cancel = threading.Event()
    timer = threading.Timer(0.001, cancel.set)
    timer.start()
    try:
        cancelled = run_engine(args.output / "synthetic-card-1.png", args.output / "cancel.rss", cancel)
    finally:
        timer.join()
    assert cancelled is None
    summary = {
        "engine": version, "engine_role": "Linux research tool only; not bundled in Kura",
        "languages_tested": ["English", "Latin identifiers/numerals"],
        "languages_not_tested": ["Japanese"], "image_source": "generated synthetic cards only",
        "results": results, "correct_fields": sum(r["correct_fields"] for r in results),
        "total_fields": sum(r["fields"] for r in results),
        "ambiguous_identifier_abstention": True, "leading_zeros_retained": True,
        "manual_correction_isolated_draft": True, "cancelled_process_no_result": True,
        "limitations": "Four printed synthetic cards are feasibility evidence only. No real-camera, Japanese, mobile latency/memory, recognition confidence calibration or production integration claim.",
    }
    (args.output / "results.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
