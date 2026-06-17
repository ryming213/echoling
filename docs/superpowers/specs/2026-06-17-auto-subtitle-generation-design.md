# Auto Subtitle Generation from Video — Design Spec

**Date:** 2026-06-17
**Status:** Approved
**Author:** Claude (brainstorming session)

## 1. Problem

Imported videos in the Echo Ling app have imprecise subtitle timing. When playing a single sentence, the audio's beginning or end is missing. Root cause: existing SRT files have sentence time intervals that drift from the actual spoken audio.

## 2. Goal

Generate SRT subtitle files whose timestamps are **word-level accurate** (not segment-level), derived directly from the video's audio track, so each subtitle entry covers exactly the words it transcribes.

## 3. Scope

**In scope:**
- 51 Peppa Pig Season 3 videos in `D:\English\Peppa Pig第三季\`
- English-only subtitles (no translation)
- Batch one-shot script (process all 51 files in one run)
- Output SRT files written next to each video, with `_no_sub` suffix replaced by `.srt`

**Out of scope:**
- Chinese translation (bilingual subtitles)
- ASS/LRC formats (only SRT, matching existing SrtParser)
- Real-time processing or GUI
- Re-transcription of already-existing accurate SRT files (script skips them)

## 4. Non-goals

- We are NOT building a general-purpose subtitle generation library
- We are NOT replacing the existing manual subtitle import flow
- We are NOT supporting languages other than English
- We are NOT attempting speaker diarization (which character is speaking)

## 5. Architecture

Single Python script: `scripts/generate_subtitles.py`. No new module structure.

```
main()
 ├── discover_videos(dir) → List[Path]
 │     filter: *.mp4 whose stem ends with "_no_sub"
 │     skip if <stem with "_no_sub" stripped>.srt already exists
 │     e.g., "S302 The Rainbow_no_sub.mp4" → output is
 │           "S302 The Rainbow.srt"; skip if that file exists
 │
 └── for each video:
       transcribe_to_srt(video_path) → .srt Path
         ├── ffmpeg: extract 16kHz mono wav to temp
         ├── whisper.transcribe(word_timestamps=True)
         │     → List[Segment{words: List[Word]}]
         ├── merge_close_segments(segments, ...)
         │     → List[SrtEntry{start_ms, end_ms, text}]
         └── format_srt(entries) → String
               write to <stem with "_no_sub" stripped>.srt
```

**Filename transformation example:**
- Input:  `D:\English\Peppa Pig第三季\S302 The Rainbow_no_sub.mp4`
- Output: `D:\English\Peppa Pig第三季\S302 The Rainbow.srt`

## 6. Components

### 6.1 ffmpeg audio extraction
- Binary: `/c/ffmpeg/bin/ffmpeg` (already installed)
- Command: extract first audio track as 16kHz mono PCM WAV
- Temp output: `D:\English\Peppa Pig第三季\_tmp_audio\<input_stem>.wav` where `<input_stem>` is the full filename minus `.mp4` (e.g., for `S302 The Rainbow_no_sub.mp4`, the temp wav is `_tmp_audio/S302 The Rainbow_no_sub.wav`)
- Cleanup: deleted after SRT write, even on failure

### 6.2 OpenAI Whisper transcription
- Package: `openai-whisper` (already installed, version 20250625)
- Model: `medium` (~5GB download on first run)
- Critical parameter: `word_timestamps=True` — enables word-level alignment via internal Wav2Vec2 forced alignment
- Device: CPU (no GPU assumed)
- Language: pinned to `en` to skip auto-detect overhead
- Output: in-memory `dict` with `segments[].words[]`

### 6.3 Segment merging
Function `merge_close_segments(segments, gap_s=0.5, max_chars=84, max_dur_s=7.0)`.

**Input:** Whisper segments (each with `words[]`)
**Output:** List of `SrtEntry` (each: `start_ms`, `end_ms`, `text`)

Algorithm:
1. **Flatten:** Collect all `Word` objects across all segments into one ordered list, sorted by `start`
2. **Group by segment:** Preserve Whisper's natural sentence boundaries (one segment ≈ one sentence) as initial groups
3. **Merge adjacent groups** if ALL of:
   - `gap = next_group[0].start - prev_group[-1].end < 0.5s`
   - combined character count ≤ 84
   - combined duration ≤ 7.0s
4. **Form SrtEntry:** `start_ms = first_word.start * 1000`, `end_ms = last_word.end * 1000`

### 6.4 SRT formatter
Function `format_srt(entries) → String`.

Standard SRT format:
```
1
00:00:01,234 --> 00:00:03,456
Hello, how are you?

2
00:00:03,789 --> 00:00:05,012
I'm fine, thank you.
```

- Time format: `HH:MM:SS,mmm` (comma decimal separator — matches SrtParser)
- Index: 1-based, sequential
- Blank line between entries

## 7. Data Structures

```python
from dataclasses import dataclass

@dataclass
class SrtEntry:
    index: int          # 1-based
    start_ms: int       # first word's start
    end_ms: int         # last word's end
    text: str           # words joined with spaces
```

Whisper's `Word` and `Segment` types are used as-is from the openai-whisper package.

## 8. Data Flow

```
video.mp4
   │
   ▼ ffmpeg (subprocess, ~1s)
temp.wav (16kHz mono)
   │
   ▼ whisper.transcribe(word_timestamps=True) (CPU, ~1-2 min/episode)
[Segment{words: [Word{start, end, word, probability}, ...]}]
   │
   ▼ merge_close_segments(segments, 0.5s, 84 chars, 7s)
[SrtEntry{index, start_ms, end_ms, text}]
   │
   ▼ format_srt(entries)
"<srt content>"
   │
   ▼ write to <video_stem>.srt
final SRT file
```

## 9. Error Handling

| Failure | Handling |
|---|---|
| ffmpeg audio extraction fails | Skip video, append to `failed[]`, continue to next |
| Whisper load/inference fails | Retry once; if still fails, append to `failed[]` and continue |
| SRT write fails (permission/disk full) | **Stop script immediately** (avoid batch data loss) |
| Video has no dialogue (pure music/silence) | Normal: output empty SRT (0 entries), not a failure |
| Non-`_no_sub` mp4 file in directory | Ignored at discovery stage |
| `word_timestamps=True` returns segments with `words=[]` | Skip that segment, continue with remaining |

After all files processed, print summary:
```
=== 总结 ===
成功: 51 / 51
失败: 0
总耗时: 1h 42m
输出目录: D:\English\Peppa Pig第三季\
```

If `failed[]` is non-empty, print each failed file with its error reason.

## 10. CLI Interface

```
python scripts/generate_subtitles.py [--dir <path>] [--smoke <file>]

Defaults:
  --dir   D:\English\Peppa Pig第三季\
  --smoke <none>  (full batch mode)

Examples:
  # Full batch (default)
  python scripts/generate_subtitles.py

  # Smoke test on one file (print SRT to stdout, don't write)
  python scripts/generate_subtitles.py --smoke "D:\English\Peppa Pig第三季\S302 The Rainbow_no_sub.mp4"
```

## 11. Performance Estimates

- Per episode (5 min video, medium model, CPU): ~1-2 minutes
- 51 episodes total: ~1.5-2 hours
- Model download (first run only): medium model ~1.5GB

## 12. Trade-offs Accepted

1. **SRT granularity vs reading flow:** Merged entries may contain 2 sentences. This improves reading flow but reduces the granularity of "play single sentence" in 精听 mode. Accepted because:
   - User explicitly requested merging
   - 精听 mode can still play each merged entry; user can request re-splitting if granularity is critical

2. **CPU vs GPU:** CPU inference is ~5x slower than GPU. Accepted because:
   - No GPU assumed on dev machine
   - 51 episodes finish in 1.5-2 hours, which is acceptable for a one-shot script

3. **medium vs large-v3 model:** medium may have ~1% higher WER than large-v3 on noisy audio. Accepted because:
   - Peppa Pig has clean, clear children's cartoon audio
   - medium is significantly faster and still high quality

4. **One-shot vs resumable:** Script restarts from scratch on failure. Accepted because:
   - Script skips files where `<stem>.srt` already exists (idempotent)
   - Failed files are reported in summary, user can re-run after fixing

## 13. Testing Strategy

**Smoke test:** Run `--smoke` on one file first to verify:
- SRT content is well-formed (parser-compatible)
- Timestamps look reasonable (start < end, gaps make sense)
- Words match the actual audio dialogue

**Manual verification after batch:**
1. Spot-check 2-3 generated SRT files visually
2. Re-import one episode into the app, compare with previous subtitle
3. Test "play single sentence" in 精听 mode — verify audio coverage matches subtitle

**No automated tests:** Script is one-shot, output is judged by human listening. Unit testing the merge algorithm is over-engineering for a single-use tool.

## 14. Future Work (Out of Scope)

- Bilingual (English + Chinese) subtitle generation
- Word-level karaoke timing (highlighting each word as it's spoken)
- Speaker diarization (label "Peppa:", "Daddy Pig:" etc.)
- GUI / drag-and-drop interface
- GPU inference support for faster batch runs
