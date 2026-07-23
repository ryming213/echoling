package com.echoling.app.transcription

/**
 * Pure-Kotlin converter: a list of [VoskSegment]s → a valid SRT
 * subtitle file body. No Android imports — unit-testable on JVM.
 *
 * **Pipeline per segment** — mirrors `split_srt_sentences.py`'s
 * `split_cue()` + `redistribute_timestamps()`:
 *  1. **Smart split** ([SentenceSegmenter.findBestSplit]) — sentences,
 *     clauses, commas, fillers, all 5 valid passes with subject +
 *     verb validation. Returns `[text]` if no clean split exists, in
 *     which case we keep the original cue untouched (no normalize —
 *     preserves casing / punctuation).
 *  2. **Normalize** each piece only when a split actually happened
 *     (capitalize first letter, add terminal period if missing) —
 *     matches Python's `pieces = [normalize_piece(p) for p in pieces]`.
 *     Without this the first piece of a chopped cue looks like a
 *     mid-sentence fragment.
 *  3. **Redistribute timestamps** proportionally by word count,
 *     clamped to [MIN_CUE_MS, MAX_CUE_MS], with OVERLAP_MS overlap
 *     between consecutive cues so shadowing / dictation learners see
 *     a brief look-ahead.
 *
 * **Why a separate Kotlin port of the Python ruleset** rather than
 * running the Python on the device: ffmpeg-kit is already a heavy
 * native dep + WorkManager + Hilt setup. Loading CPython + the split
 * script + already-pip-installed dependencies into an Android Worker
 * would add ~50 MB of native libs and a ProcessBuilder per cue. The
 * port is ~400 lines of pure Kotlin, fully unit-testable on JVM.
 *
 * **Behavior parity** with `split_srt_sentences.py` is contract: any
 * case the Python splits, this splits; any case the Python leaves,
 * this leaves. Drift should only ever be a Kotlin bug fix.
 */
object SrtSynthesizer {

    /**
     * Pad the end of every segment by 400ms before writing the SRT.
     * Vosk commits a segment when it sees ~700ms of silence; the last
     * word in the segment is at `endMs` and the audio continues for
     * ~400ms after the recognizer fires the endpoint (room
     * reverberation, mic lag).
     */
    private const val END_PAD_MS = 400L

    /**
     * Build an SRT file body. Returns "" for an empty input (a valid
     * degenerate case — see spec §8 "Vosk returns 0 segments").
     *
     * The body ends with a single trailing newline (after the last
     * cue's text line). Between cues we emit one blank separator line
     * (so `text\n\nN` joins adjacent cues).
     */
    fun toSrt(segments: List<VoskSegment>): String {
        val sb = StringBuilder()
        var cueIndex = 1
        for (segment in segments) {
            val paddedEnd = segment.endMs + END_PAD_MS
            // Step 1: smart split (or no-op if short).
            val rawPieces = SentenceSegmenter.findBestSplit(segment.text)
            // Step 2: normalize only when a split actually happened
            // (Python parity — keep original casing / no terminal period
            // when the cue is left whole).
            val pieces = if (rawPieces.size == 1) {
                rawPieces
            } else {
                rawPieces.map { SentenceSegmenter.normalizePiece(it) }
            }
            // Step 3: redistribute timestamps per Python's algorithm.
            val times = redistributeTimestamps(
                startMs = segment.startMs,
                endMs = paddedEnd,
                pieces = pieces,
            )
            for ((i, piece) in pieces.withIndex()) {
                val (s, e) = times[i]
                if (cueIndex > 1) sb.append('\n')
                sb.append(cueIndex).append('\n')
                sb.append("${formatTimestamp(s)} --> ${formatTimestamp(e)}\n")
                sb.append(piece).append('\n')
                cueIndex++
            }
        }
        return sb.toString()
    }

    /**
     * Format milliseconds as an SRT timestamp: `HH:MM:SS,mmm`.
     * Hours are NOT zero-clamped (a 25-hour transcript renders as
     * 25:00:00,000) — SRT allows ≥24h for very long media.
     */
    fun formatTimestamp(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1_000
        val milli = (ms % 1_000).toInt()
        return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
    }

    /**
     * Allocate start/end times for [pieces] proportionally to word
     * count, clamped to [MIN_CUE_MS, MAX_CUE_MS], with [OVERLAP_MS]
     * of overlap between consecutive cues.
     *
     * Port of `redistribute_timestamps` in `split_srt_sentences.py`.
     *
     * Math: with N pieces and (N-1) overlaps of [OVERLAP_MS] each,
     * the total content-display budget is `(end - start) + (N-1) *
     * OVERLAP_MS`. Each piece's display duration is proportional to
     * its word count, clamped to [MIN_CUE_MS, MAX_CUE_MS]. If clamping
     * shrinks the total below `content_duration`, the last piece is
     * extended to absorb the slack; if clamping stretches it past
     * `content_duration`, every piece is scaled down proportionally.
     * The last piece's end is forced to equal `endMs` so the cue
     * chain terminates on the original SRT timeline.
     */
    private fun redistributeTimestamps(
        startMs: Long,
        endMs: Long,
        pieces: List<String>,
    ): List<Pair<Long, Long>> {
        val n = pieces.size
        val totalDuration = endMs - startMs
        if (n == 0) return emptyList()
        if (n == 1 || totalDuration <= 0) {
            return List(n) { startMs to endMs }
        }

        val contentDuration = (totalDuration + (n - 1) * SentenceSegmenter.OVERLAP_MS).toDouble()
        val weights = pieces.map { maxOf(SentenceSegmenter.wordCount(it), 1) }
        val totalW = weights.sum()

        val raw = weights.map { (it.toDouble() / totalW) * contentDuration }
        val minMs = SentenceSegmenter.MIN_CUE_MS.toDouble()
        val maxMs = SentenceSegmenter.MAX_CUE_MS.toDouble()
        val clamped = raw.map { it.coerceIn(minMs, maxMs) }.toMutableList()

        val actualTotal = clamped.sum()
        if (actualTotal > contentDuration) {
            val scale = contentDuration / actualTotal
            for (i in clamped.indices) clamped[i] = clamped[i] * scale
        } else if (actualTotal < contentDuration) {
            clamped[clamped.size - 1] += (contentDuration - actualTotal)
        }

        val result = mutableListOf<Pair<Long, Long>>()
        var cursor = startMs.toDouble()
        for (d in clamped) {
            val s = cursor.toLong()
            val e = (cursor + d).toLong()
            result.add(s to e)
            cursor = (e - SentenceSegmenter.OVERLAP_MS).toDouble()
        }
        // Force the last cue to end at the original endMs so the cue
        // chain stays aligned with the audio timeline.
        if (result.isNotEmpty() && result.last().second != endMs) {
            val last = result.removeAt(result.size - 1)
            result.add(last.first to endMs)
        }
        return result
    }
}
