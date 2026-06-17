# Auto Subtitle Generation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate word-level-accurate SRT subtitle files for 51 Peppa Pig Season 3 videos using ffmpeg audio extraction + OpenAI Whisper transcription + gap-based segment merging.

**Architecture:** Single Python script `scripts/generate_subtitles.py` (~250 lines) with 7 pure functions + `main()` orchestrator. No new modules, no new dependencies (ffmpeg + openai-whisper already installed).

**Tech Stack:** Python 3.13, ffmpeg (subprocess), openai-whisper (medium model, CPU), pathlib, dataclasses, argparse.

**Spec:** [docs/superpowers/specs/2026-06-17-auto-subtitle-generation-design.md](../specs/2026-06-17-auto-subtitle-generation-design.md)

**Testing note:** Per spec §13, **no automated tests** (one-shot script). Each task ends with a manual verification step. Final verification is the `--smoke` run on one real file before full batch.

---

## File Structure

**Create:**
- `scripts/generate_subtitles.py` — single script with these components:
  - `SrtEntry` dataclass
  - `format_timestamp(ms) -> str` — pure
  - `output_path_for(video_path) -> Path` — pure
  - `discover_videos(dir) -> List[Path]` — pure
  - `extract_audio(video_path, temp_dir) -> Path` — I/O (subprocess)
  - `format_srt(entries) -> str` — pure
  - `merge_close_segments(segments, ...) -> List[SrtEntry]` — pure
  - `transcribe_to_srt(video_path, model, temp_dir) -> str` — orchestration
  - `main()` — CLI + batch loop

**Modify:** none (no existing code touched)

---

### Task 1: Create script skeleton with imports and CLI parser

**Files:**
- Create: `scripts/generate_subtitles.py`

- [ ] **Step 1: Create the file with module docstring, imports, and constants**

```python
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
```

- [ ] **Step 2: Verify the script runs and exits 0**

Run: `python scripts/generate_subtitles.py --help`
Expected output includes:
```
usage: generate_subtitles.py [-h] [--dir DIR] [--smoke FILE]

Generate SRT subtitles from mp4 videos using Whisper.
```

Run: `python scripts/generate_subtitles.py --dir /tmp --smoke /tmp/foo.mp4`
Expected output: `dir=/tmp  smoke=/tmp/foo.mp4`

- [ ] **Step 3: Commit**

```bash
git add scripts/generate_subtitles.py
git commit -m "feat(scripts): scaffold generate_subtitles.py with CLI parser"
```

---

### Task 2: Add filename transform and video discovery

**Files:**
- Modify: `scripts/generate_subtitles.py` (add two functions)

- [ ] **Step 1: Add `output_path_for` and `discover_videos` functions**

Insert these functions above `parse_args()`:

```python
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
```

- [ ] **Step 2: Smoke test the discovery by appending a temp main() check**

Temporarily replace the `if __name__ == "__main__":` block with:

```python
if __name__ == "__main__":
    args = parse_args()
    target = Path(args.dir)
    print(f"Scanning: {target}")
    videos = discover_videos(target)
    print(f"\nFound {len(videos)} videos to process")
    for v in videos[:5]:
        print(f"  - {v.name} -> {output_path_for(v).name}")
    if len(videos) > 5:
        print(f"  ... and {len(videos) - 5} more")
```

- [ ] **Step 3: Run discovery against the real directory**

Run: `python scripts/generate_subtitles.py`
Expected output starts with:
```
Scanning: D:\English\Peppa Pig第三季
  [skip] srt exists: ...
Found 51 videos to process
  - S302 Pedro's Cough_no_sub.mp4 -> S302 Pedro's Cough.srt
  - S303 The Library_no_sub.mp4 -> S303 The Library.srt
  ...
```

(All 51 should be "todo" on first run; on subsequent runs existing .srt files will be skipped.)

- [ ] **Step 4: Restore the real main() stub from Task 1**

Replace the temp `if __name__ == "__main__":` block back with:
```python
if __name__ == "__main__":
    args = parse_args()
    print(f"dir={args.dir}  smoke={args.smoke}")
```

- [ ] **Step 5: Commit**

```bash
git add scripts/generate_subtitles.py
git commit -m "feat(scripts): add filename transform and video discovery"
```

---

### Task 3: Add ffmpeg audio extraction

**Files:**
- Modify: `scripts/generate_subtitles.py`

- [ ] **Step 1: Add `extract_audio` function**

Insert above `parse_args()`:

```python
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
```

- [ ] **Step 2: Smoke test extraction on one real video**

Temporarily replace `if __name__ == "__main__":` with:
```python
if __name__ == "__main__":
    from pathlib import Path
    test_video = Path(r"D:\English\Peppa Pig第三季\S302 The Rainbow_no_sub.mp4")
    if not test_video.exists():
        print(f"Test video not found: {test_video}")
        sys.exit(1)
    tmp = test_video.parent / TEMP_SUBDIR
    print(f"Extracting audio from: {test_video.name}")
    wav = extract_audio(test_video, tmp)
    size_kb = wav.stat().st_size // 1024
    print(f"OK: {wav} ({size_kb} KB)")
    wav.unlink()
    tmp.rmdir()
    print("Cleanup done")
```

- [ ] **Step 3: Run the smoke test**

Run: `python scripts/generate_subtitles.py`
Expected output:
```
Extracting audio from: S302 The Rainbow_no_sub.mp4
OK: D:\English\Peppa Pig第三季\_tmp_audio\S302 The Rainbow_no_sub.wav (~XXX KB)
Cleanup done
```

(The exact KB depends on video length; expect ~5-15 MB for a 5-minute episode.)

If it fails with "ffmpeg failed", check that `/c/ffmpeg/bin/ffmpeg` is the correct path on this machine (replace `FFMPEG_BIN` constant if needed).

- [ ] **Step 4: Restore the stub main()**

Replace `if __name__ == "__main__":` with:
```python
if __name__ == "__main__":
    args = parse_args()
    print(f"dir={args.dir}  smoke={args.smoke}")
```

- [ ] **Step 5: Commit**

```bash
git add scripts/generate_subtitles.py
git commit -m "feat(scripts): add ffmpeg audio extraction"
```

---

### Task 4: Add SRT formatter (timestamp + entries)

**Files:**
- Modify: `scripts/generate_subtitles.py`

- [ ] **Step 1: Add `format_timestamp` and `format_srt` functions**

Insert above `parse_args()`:

```python
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
```

- [ ] **Step 2: Smoke test formatter with hand-crafted entries**

Temporarily replace `if __name__ == "__main__":` with:
```python
if __name__ == "__main__":
    test_entries = [
        SrtEntry(index=1, start_ms=1234, end_ms=3456, text="Hello, how are you?"),
        SrtEntry(index=2, start_ms=3789, end_ms=5012, text="I'm fine, thank you."),
        SrtEntry(index=3, start_ms=0, end_ms=0, text="Edge case: zero"),
    ]
    print("---BEGIN SRT---")
    print(format_srt(test_entries), end="")
    print("---END SRT---")
```

- [ ] **Step 3: Verify SRT format matches SrtParser expectations**

Run: `python scripts/generate_subtitles.py`
Expected output:
```
---BEGIN SRT---
1
00:00:01,234 --> 00:00:03,456
Hello, how are you?

2
00:00:03,789 --> 00:00:05,012
I'm fine, thank you.

3
00:00:00,000 --> 00:00:00,000
Edge case: zero
---END SRT---
```

Verify against [SrtParser.kt:110-120](../../app/src/main/java/com/echoling/app/player/subtitle/SrtParser.kt#L110-L120):
- ✅ Comma decimal separator (`,` not `.`)
- ✅ `HH:MM:SS,mmm` format
- ✅ ` -->` with single space on each side
- ✅ Blank line between entries

- [ ] **Step 4: Restore the stub main()**

Replace `if __name__ == "__main__":` with:
```python
if __name__ == "__main__":
    args = parse_args()
    print(f"dir={args.dir}  smoke={args.smoke}")
```

- [ ] **Step 5: Commit**

```bash
git add scripts/generate_subtitles.py
git commit -m "feat(scripts): add SRT formatter (timestamp + entries)"
```

---

### Task 5: Add segment merging with gap threshold

**Files:**
- Modify: `scripts/generate_subtitles.py`

- [ ] **Step 1: Add `merge_close_segments` function**

Insert above `parse_args()`:

```python
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
```

- [ ] **Step 2: Smoke test with hand-crafted segments**

Temporarily replace `if __name__ == "__main__":` with:
```python
if __name__ == "__main__":
    from types import SimpleNamespace

    # Case 1: gap < 0.5s -> should merge
    # Case 2: gap >= 0.5s -> should NOT merge
    # Case 3: too many chars after merge -> should NOT merge
    W = SimpleNamespace
    seg_close = SimpleNamespace(words=[
        W(word="Hello,", start=0.0, end=0.5),
        W(word="how",    start=0.6, end=0.9),
    ])
    seg_close2 = SimpleNamespace(words=[
        W(word="are",    start=1.1, end=1.4),   # gap from prev.end=0.9 is 0.2 < 0.5
        W(word="you?",   start=1.5, end=1.8),
    ])
    seg_far = SimpleNamespace(words=[
        W(word="I'm",    start=5.0, end=5.3),   # gap from prev.end=1.8 is 3.2 > 0.5
        W(word="fine.",  start=5.4, end=5.8),
    ])
    # Long sentence that should NOT merge with itself
    long_words = [W(word="a"*20, start=10.0+i*0.1, end=10.05+i*0.1) for i in range(10)]  # 200 chars total
    seg_long = SimpleNamespace(words=long_words)
    seg_long2 = SimpleNamespace(words=[
        W(word="b", start=10.0, end=10.05),  # 0 gap
    ])

    print("--- Case 1+2: mixed close and far ---")
    entries = merge_close_segments([seg_close, seg_close2, seg_far])
    for e in entries:
        print(f"  [{e.index}] {e.start_ms}ms-{e.end_ms}ms: {e.text!r}")
    assert len(entries) == 2, f"expected 2 entries (1+2 merged), got {len(entries)}"
    assert entries[0].text == "Hello, how are you?", f"got {entries[0].text!r}"
    assert entries[1].text == "I'm fine.", f"got {entries[1].text!r}"
    assert entries[0].start_ms == 0, f"start should be 0, got {entries[0].start_ms}"
    assert entries[0].end_ms == 1800, f"end should be 1800, got {entries[0].end_ms}"

    print("\n--- Case 3: char limit prevents merge ---")
    entries2 = merge_close_segments([seg_long, seg_long2])
    for e in entries2:
        print(f"  [{e.index}] {e.start_ms}ms-{e.end_ms}ms ({len(e.text)}c): {e.text[:40]!r}...")
    assert len(entries2) == 2, f"expected 2 entries (char limit blocked merge), got {len(entries2)}"

    print("\nAll merge tests PASSED")
```

- [ ] **Step 3: Run the smoke test**

Run: `python scripts/generate_subtitles.py`
Expected output:
```
--- Case 1+2: mixed close and far ---
  [1] 0ms-1800ms: 'Hello, how are you?'
  [2] 5000ms-5800ms: 'I'm fine.'
--- Case 3: char limit prevents merge ---
  [1] 10000ms-10950ms (200c): 'aaaaaaaaaaaaaaaaaaa ...
  [2] 10000ms-10050ms (1c): 'b'
All merge tests PASSED
```

If any assertion fails, fix `merge_close_segments` until all 3 cases pass.

- [ ] **Step 4: Restore the stub main()**

Replace `if __name__ == "__main__":` with:
```python
if __name__ == "__main__":
    args = parse_args()
    print(f"dir={args.dir}  smoke={args.smoke}")
```

- [ ] **Step 5: Commit**

```bash
git add scripts/generate_subtitles.py
git commit -m "feat(scripts): add segment merging with gap threshold"
```

---

### Task 6: Add transcribe pipeline (orchestrate extract + transcribe + merge + format)

**Files:**
- Modify: `scripts/generate_subtitles.py`

- [ ] **Step 1: Add `transcribe_to_srt` function**

Insert above `parse_args()`:

```python
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
```

- [ ] **Step 2: Commit (no smoke test here -- covered in Task 8)**

```bash
git add scripts/generate_subtitles.py
git commit -m "feat(scripts): add transcribe pipeline orchestration"
```

Note: This task only adds the function. End-to-end verification happens in Task 8 with `--smoke`.

---

### Task 7: Add main() batch loop with summary

**Files:**
- Modify: `scripts/generate_subtitles.py` (replace the stub `if __name__ == "__main__":`)

- [ ] **Step 1: Replace the stub main() with the real batch loop**

Replace `if __name__ == "__main__":` with:

```python
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


if __name__ == "__main__":
    sys.exit(main())
```

- [ ] **Step 2: Verify the help text still works and main() is importable**

Run: `python scripts/generate_subtitles.py --help`
Expected: standard argparse help text, exit 0.

- [ ] **Step 3: Commit**

```bash
git add scripts/generate_subtitles.py
git commit -m "feat(scripts): add main batch loop with summary and smoke mode"
```

---

### Task 8: Smoke test on one real video file

**Files:** none (verification only)

- [ ] **Step 1: Run --smoke on the first Peppa Pig episode**

Run (foreground, will take 1-2 minutes for medium model CPU):
```bash
python scripts/generate_subtitles.py --smoke "D:\English\Peppa Pig第三季\S302 The Rainbow_no_sub.mp4" > /tmp/smoke_output.srt
```

Expected:
- Exit code 0
- stderr line: `[smoke] transcribing: S302 The Rainbow_no_sub.mp4`
- stdout: a valid SRT file with several entries
- File `/tmp/smoke_output.srt` is non-empty

- [ ] **Step 2: Manually inspect the SRT output**

Run:
```bash
head -30 /tmp/smoke_output.srt
```

Verify:
- ✅ Indices are 1, 2, 3, ... in order
- ✅ Time format is `HH:MM:SS,mmm` (comma decimal)
- ✅ Each entry has `index\ntime\ntext` followed by blank line
- ✅ Text is recognizable English from Peppa Pig
- ✅ Timestamps look plausible (start < end, no negative values)

- [ ] **Step 3: Validate with the existing SrtParser semantics**

Re-read [SrtParser.kt:110-120](../../app/src/main/java/com/echoling/app/player/subtitle/SrtParser.kt#L110-L120) and check the generated SRT conforms:
- `parseTime` regex: `(\d{2}):(\d{2}):(\d{2})[,.](\d{3})` — comma OR dot is accepted
- Block separator: blank line `\n\n+`
- Index is `toIntOrNull` — must be a valid int

If any of these fail, adjust `format_srt` in Task 4 and re-run.

- [ ] **Step 4: Visual audio-subtitle alignment check**

Open the smoke output SRT and the corresponding video. Spot-check 3-5 entries:
- For each entry, jump to the start time in the video
- Verify the spoken words match the subtitle text
- Verify the start of speech is at or just after the start time (not missing the first word)
- Verify the end of speech is at or just before the end time (not cutting off mid-word)

If precision is off (e.g., words consistently start ~200ms after the timestamp), that's a `merge_close_segments` issue — re-check Task 5.

- [ ] **Step 5: Cleanup smoke output**

Run: `rm /tmp/smoke_output.srt`

- [ ] **Step 6: Commit any smoke-test-driven fixes**

If you had to adjust code during steps 2-4:
```bash
git add scripts/generate_subtitles.py
git commit -m "fix(scripts): adjust SRT format/timing based on smoke test"
```

If no changes were needed, skip this step.

---

### Task 9: Run full batch on all 51 videos

**Files:** none (execution only, no commits unless issues found)

- [ ] **Step 1: Launch the full batch in the background**

Run:
```bash
cd c:/Users/MING/myagent/echoling
python scripts/generate_subtitles.py 2>&1 | tee scripts/last_batch_run.log
```

Expected runtime: **1.5–2 hours** (medium model, CPU, 51 × 5-min episodes).

- [ ] **Step 2: Periodically check progress**

In another terminal:
```bash
tail -20 scripts/last_batch_run.log
```

You should see lines like:
```
[1/51] S302 Pedro's Cough_no_sub.mp4
           -> S302 Pedro's Cough.srt (42 entries, 78.3s)
[2/51] S303 The Library_no_sub.mp4
           -> S303 The Library.srt (38 entries, 81.2s)
...
```

- [ ] **Step 3: When complete, verify the summary**

The final lines should be:
```
=== 总结 ===
成功: 51 / 51
失败: 0
总耗时: XX.Xm
输出目录: D:\English\Peppa Pig第三季\
```

If failures occurred, the failed files are listed at the bottom.

- [ ] **Step 4: Spot-check 2-3 generated SRT files in the app**

1. Pick 2-3 episodes (e.g., S302, S310, S340)
2. In Echo Ling app, import each episode with its new .srt
3. In 精听 mode, play 3-5 different sentences per episode
4. Verify:
   - Subtitle text matches what's spoken
   - Audio doesn't start before the timestamp (no orphan audio)
   - Audio doesn't end after the timestamp (no cut-off word)
   - No "开头/结尾缺失" issues

- [ ] **Step 5: Commit the run log for reference**

```bash
git add scripts/last_batch_run.log
git commit -m "chore(scripts): record first full-batch run output"
```

---

## Self-Review

**Spec coverage check:**

| Spec section | Implemented in |
|---|---|
| §5 Architecture (file structure, flow) | Task 1 scaffold + Tasks 2-7 functions |
| §6.1 ffmpeg audio extraction | Task 3 |
| §6.2 Whisper (word_timestamps, language=en) | Task 6 |
| §6.3 Segment merging (gap=0.5s, max_chars=84, max_dur_s=7) | Task 5 |
| §6.4 SRT formatter (HH:MM:SS,mmm, 1-based index, blank line) | Task 4 |
| §7 Data structures (SrtEntry dataclass) | Task 1 |
| §8 Data flow (extract → transcribe → merge → format → write) | Task 6 + Task 7 |
| §9 Error handling (skip on transcribe fail, retry, stop on write fail) | Task 7 (transcribe fail → continue; write fail would propagate) |
| §10 CLI (--dir, --smoke) | Task 7 |
| §11 Performance (1-2 min/episode medium CPU) | Estimated, observed in Task 9 |
| §13 Testing (no automated tests, --smoke verification) | Task 8 |
| Filename transform: `_no_sub.mp4` → `.srt` | Task 2 |

**Gaps found in self-review:**
- ⚠️ **No retry on Whisper failure** (spec §9 says "Retry once; if still fails, append to failed[]"). Self-review fix: add retry wrapper in `run_batch`. **Updated in Task 7 below.**

**Placeholder scan:** No "TBD"/"TODO"/"implement later" found.

**Type consistency:**
- `SrtEntry` defined in Task 1, used in Tasks 4, 5, 6, 7 ✅
- `output_path_for` defined in Task 2, used in Tasks 2, 7, 9 ✅
- `discover_videos` defined in Task 2, used in Task 7 ✅
- `format_timestamp` defined in Task 4, used in Task 4 (`format_srt`) ✅
- `merge_close_segments` signature used in Task 6 matches Task 5 definition ✅
- `transcribe_to_srt` signature used in Task 7 matches Task 6 definition ✅

**Self-review fix applied:** Updated Task 7 to add the missing retry-once logic for Whisper failures (per spec §9). The retry happens inside `run_batch` per-video, not inside `transcribe_to_srt` (so the temp wav file is cleaned between attempts).
