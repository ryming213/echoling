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


def merge_close_segments(
    segments: list,
    gap_s: float = GAP_S,
    max_chars: int = MAX_CHARS,
    max_dur_s: float = MAX_DUR_S,
) -> List[SrtEntry]:
    """Merge Whisper segments into SrtEntry list, combining close sentences.

    Algorithm:
      1. Group words by Whisper's natural segment boundaries
      2. Merge adjacent groups IF all of:
         - gap between groups < gap_s seconds
         - combined character count <= max_chars
         - combined duration <= max_dur_s seconds
      3. Form SrtEntry using word-level timestamps (first word's start,
         last word's end) -- this is what makes the timing precise.
    """
    # Step 1: group words by segment
    groups = []
    for seg in segments:
        words = [
            w for w in (seg.words or [])
            if w.start is not None and w.end is not None
        ]
        if words:
            groups.append(words)

    # Step 2: merge adjacent groups
    merged: List[list] = []
    for group in groups:
        if not merged:
            merged.append(group)
            continue
        prev = merged[-1]
        gap = group[0].start - prev[-1].end
        text_len = sum(len(w.word) for w in prev) + sum(len(w.word) for w in group)
        dur = group[-1].end - prev[0].start
        if gap < gap_s and text_len <= max_chars and dur <= max_dur_s:
            merged[-1] = prev + group
        else:
            merged.append(group)

    # Step 3: form SrtEntry
    entries = []
    for i, group in enumerate(merged):
        text = " ".join(w.word.strip() for w in group)
        entries.append(
            SrtEntry(
                index=i + 1,
                start_ms=int(round(group[0].start * 1000)),
                end_ms=int(round(group[-1].end * 1000)),
                text=text,
            )
        )
    return entries


def transcribe_to_srt(
    video_path: Path,
    model,
    temp_dir: Path,
) -> str:
    """Extract audio, transcribe with Whisper, merge, format as SRT.

    Cleans up the temp wav file even on failure.
    Returns the SRT text content (may be empty string if no dialogue).
    """
    wav_path = None
    try:
        wav_path = extract_audio(video_path, temp_dir)
        result = model.transcribe(
            str(wav_path),
            word_timestamps=True,
            language="en",
            verbose=False,
        )
        entries = merge_close_segments(result.get("segments", []))
        return format_srt(entries)
    finally:
        if wav_path is not None and wav_path.exists():
            try:
                wav_path.unlink()
            except OSError:
                pass  # best-effort cleanup


def run_smoke(video_path: Path, model) -> int:
    """Transcribe one file, print SRT to stdout, return 0."""
    print(f"[smoke] transcribing: {video_path.name}", file=sys.stderr)
    temp_dir = video_path.parent / TEMP_SUBDIR
    try:
        srt = transcribe_to_srt(video_path, model, temp_dir)
        sys.stdout.write(srt)
        return 0
    except Exception as e:
        print(f"[smoke] FAILED: {e}", file=sys.stderr)
        return 1
    finally:
        if temp_dir.exists():
            shutil.rmtree(temp_dir, ignore_errors=True)


def run_batch(target_dir: Path) -> int:
    """Process all pending videos, print summary, return 0 on full success."""
    print(f"Scanning: {target_dir}")
    videos = discover_videos(target_dir)
    if not videos:
        print("Nothing to do (all videos already have .srt).")
        return 0
    print(f"Found {len(videos)} videos to process\n")

    print(f"Loading Whisper model '{MODEL_NAME}' (first run may download ~1.5GB)...")
    t_load = time.time()
    model = whisper.load_model(MODEL_NAME)
    print(f"Model loaded in {time.time() - t_load:.1f}s\n")

    temp_dir = target_dir / TEMP_SUBDIR
    failed: list = []
    start = time.time()
    for i, video in enumerate(videos, 1):
        t0 = time.time()
        srt = None
        last_err = None
        # Per spec §9: retry once on Whisper failure
        for attempt in (1, 2):
            try:
                srt = transcribe_to_srt(video, model, temp_dir)
                last_err = None
                break
            except Exception as e:
                last_err = e
                if attempt == 1:
                    print(f"[{i}/{len(videos)}] {video.name}: attempt 1 failed ({e}), retrying...", file=sys.stderr)
        try:
            if srt is None:
                raise last_err  # type: ignore[misc]
            output = output_path_for(video)
            output.write_text(srt, encoding="utf-8")
            elapsed = time.time() - t0
            entry_count = srt.count("-->")
            print(
                f"[{i}/{len(videos)}] {video.name}\n"
                f"           -> {output.name} "
                f"({entry_count} entries, {elapsed:.1f}s)"
            )
        except Exception as e:
            elapsed = time.time() - t0
            print(f"[{i}/{len(videos)}] FAILED {video.name} ({elapsed:.1f}s): {e}")
            failed.append((video, str(e)))

    # Cleanup temp dir
    if temp_dir.exists():
        shutil.rmtree(temp_dir, ignore_errors=True)

    total = time.time() - start
    print(f"\n=== 总结 ===")
    print(f"成功: {len(videos) - len(failed)} / {len(videos)}")
    print(f"失败: {len(failed)}")
    print(f"总耗时: {total / 60:.1f}m")
    print(f"输出目录: {target_dir}")
    if failed:
        print("\n失败文件:")
        for video, err in failed:
            print(f"  - {video.name}: {err}")
    return 0 if not failed else 1


def main() -> int:
    args = parse_args()
    if args.smoke:
        smoke_path = Path(args.smoke)
        if not smoke_path.is_file():
            print(f"Smoke file not found: {smoke_path}", file=sys.stderr)
            return 1
        # Lazy model load for smoke mode
        model = whisper.load_model(MODEL_NAME)
        return run_smoke(smoke_path, model)
    return run_batch(Path(args.dir))


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
    sys.exit(main())
