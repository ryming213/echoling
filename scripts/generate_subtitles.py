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
FFMPEG_BIN = "/c/ffmpeg/bin/ffmpeg"
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
    # Other tasks add functions and the real main(); for now this just
    # exercises the parser.
    args = parse_args()
    print(f"dir={args.dir}  smoke={args.smoke}")
