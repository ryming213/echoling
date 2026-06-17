#!/usr/bin/env python3
"""Generate word-level-accurate SRT subtitles from video files.

Pipeline: ffmpeg (audio extract) -> openai-whisper (transcribe with word
timestamps) -> gap-based merge (combine close sentences) -> SRT format.

Usage:
    python scripts/generate_subtitles.py                    # full batch
    python scripts/generate_subtitles.py --smoke <file>     # one file, stdout
"""
import argparse
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import List

import whisper
from whisper.utils import get_writer  # noqa: F401  (kept for future use)

# Constants
DEFAULT_DIR = r"D:\English\Peppa Pig第三季"
MODEL_NAME = "medium"
NO_SUB_SUFFIX = "_no_sub"
GAP_S = 0.5
MAX_CHARS = 84
MAX_DUR_S = 7.0
FFMPEG_BIN = r"C:\ffmpeg\bin\ffmpeg.exe"
TEMP_SUBDIR = "_tmp_audio"


@dataclass
class SrtEntry:
    index: int
    start_ms: int
    end_ms: int
    text: str


def output_path_for(video_path: Path) -> Path:
    """Convert '<stem>_no_sub.mp4' -> '<stem>.srt' next to the video.

    Example:
        'D:/.../S302 The Rainbow_no_sub.mp4'
            -> 'D:/.../S302 The Rainbow.srt'
    """
    stem = video_path.stem
    if not stem.endswith(NO_SUB_SUFFIX):
        raise ValueError(
            f"Not a _no_sub file (missing suffix): {video_path.name}"
        )
    new_stem = stem[: -len(NO_SUB_SUFFIX)]
    return video_path.with_name(new_stem + ".srt")


def discover_videos(dir: Path) -> List[Path]:
    """Return sorted list of videos that need subtitles.

    Filters:
      - Only *.mp4 files
      - Stem must end with '_no_sub'
      - Skip if the corresponding .srt already exists
    """
    if not dir.is_dir():
        raise FileNotFoundError(f"Directory not found: {dir}")
    candidates = sorted(
        p for p in dir.glob("*.mp4") if p.stem.endswith(NO_SUB_SUFFIX)
    )
    todo = []
    for video in candidates:
        srt = output_path_for(video)
        if srt.exists():
            print(f"  [skip] srt exists: {srt.name}")
            continue
        todo.append(video)
    return todo


def extract_audio(video_path: Path, temp_dir: Path) -> Path:
    """Extract 16kHz mono PCM WAV from the first audio track of video.

    Returns the path to the temporary wav file. Caller is responsible
    for deleting it after use.
    """
    temp_dir.mkdir(parents=True, exist_ok=True)
    wav_path = temp_dir / f"{video_path.stem}.wav"
    cmd = [
        FFMPEG_BIN,
        "-y",                 # overwrite without prompt
        "-i", str(video_path),
        "-vn",                # no video
        "-ac", "1",           # mono
        "-ar", "16000",       # 16kHz (Whisper expected rate)
        "-f", "wav",
        str(wav_path),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        stderr_tail = (result.stderr or "")[-500:]
        raise RuntimeError(
            f"ffmpeg failed for {video_path.name} (rc={result.returncode}): "
            f"{stderr_tail}"
        )
    if not wav_path.exists() or wav_path.stat().st_size == 0:
        raise RuntimeError(f"ffmpeg produced empty wav for {video_path.name}")
    return wav_path


def format_timestamp(ms: int) -> str:
    """Format milliseconds as SRT timestamp 'HH:MM:SS,mmm'."""
    if ms < 0:
        ms = 0
    hours = ms // 3_600_000
    minutes = (ms // 60_000) % 60
    seconds = (ms // 1_000) % 60
    millis = ms % 1_000
    return f"{hours:02d}:{minutes:02d}:{seconds:02d},{millis:03d}"


def format_srt(entries: List[SrtEntry]) -> str:
    """Format SrtEntry list as standard SRT text. Entries separated by
    a single blank line, no trailing blank line."""
    if not entries:
        return ""
    blocks = []
    for entry in entries:
        blocks.append(
            f"{entry.index}\n"
            f"{format_timestamp(entry.start_ms)} --> "
            f"{format_timestamp(entry.end_ms)}\n"
            f"{entry.text}"
        )
    return "\n\n".join(blocks) + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Generate SRT subtitles from mp4 videos using Whisper."
    )
    parser.add_argument(
        "--dir",
        default=DEFAULT_DIR,
        help=f"Directory containing _no_sub.mp4 files (default: {DEFAULT_DIR})",
    )
    parser.add_argument(
        "--smoke",
        metavar="FILE",
        default=None,
        help="Smoke test mode: transcribe one file, print SRT to stdout, don't write.",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    print(f"dir={args.dir}  smoke={args.smoke}")
