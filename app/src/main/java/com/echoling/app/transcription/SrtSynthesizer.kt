package com.echoling.app.transcription

/**
 * Pure-Kotlin converter: a list of [VoskSegment]s → a valid SRT
 * subtitle file body. No Android imports — unit-testable on JVM.
 *
 * **Why this lives in its own object** (not as a member of the
 * worker): the timestamp-redistribution logic is the kind of thing
 * that's easy to break in a refactor and painful to test through a
 * full WorkManager + Hilt stack. Pulling it into a pure object lets
 * us cover it with 12 unit tests that run in <1s.
 *
 * **Why we don't use a Python port verbatim** (see
 * [c:/Users/MING/myagent/split_srt_sentences.py]): Vosk's endpoint
 * detection already cuts at ~700ms silence, so we don't need the
 * Python `merge_close_segments` pass that assumes Whisper-style
 * silence behavior. The redistribution we DO need is the
 * `redistribute_timestamps` step, ported to Kotlin here.
 */
object SrtSynthesizer {

    /**
     * Pad the end of every segment by 400ms before writing the SRT.
     *
     * Why 400? Vosk commits a segment when it sees ~700ms of silence.
     * The last word in the segment is at `endMs`; the audio continues
     * for ~400ms after the recognizer fires the endpoint (room
     * reverberation, mic lag). 400ms is a soft compromise — short
     * enough not to swallow the next segment's start, long enough to
     * feel natural in playback.
     */
    private const val END_PAD_MS = 400L

    /**
     * Hard cap on a single SRT cue. Past this, the text and audio
     * drift out of sync visually (the subtitle sits on screen for
     * >8s, which is hard to read). Cues longer than this are split
     * into multiple cues with proportional time distribution.
     */
    private const val MAX_SEGMENT_DURATION_MS = 8_000L

    /**
     * Hard cap on words per cue. 12 words ≈ 5–6s of natural speech;
     * past that the subtitle scrolls off the screen before the user
     * finishes reading.
     */
    private const val MAX_SEGMENT_WORDS = 12

    /**
     * Overlap between adjacent redistributed cues, in ms. The previous
     * cue's end is pushed forward by [OVERLAP_MS] past the next cue's
     * natural start, producing 750ms of visual overlap so the user
     * doesn't see a gap when fast speech makes the redistribution
     * push the second cue's start slightly later than expected.
     */
    private const val OVERLAP_MS = 750L

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
            val pieces = redistributeTimestamps(
                startMs = segment.startMs,
                endMs = paddedEnd,
                text = segment.text,
                maxDurationMs = MAX_SEGMENT_DURATION_MS,
                maxWords = MAX_SEGMENT_WORDS,
            )
            for (piece in pieces) {
                if (cueIndex > 1) sb.append('\n')
                sb.append(cueIndex).append('\n')
                sb.append("${formatTimestamp(piece.startMs)} --> ${formatTimestamp(piece.endMs)}\n")
                sb.append(piece.text).append('\n')
                cueIndex++
            }
        }
        return sb.toString()
    }

    /**
     * Format milliseconds as an SRT timestamp: `HH:MM:SS,mmm`.
     * Hours are NOT zero-clamped (a 25-hour transcript would render
     * as 25:00:00,000) — SRT allows ≥24h for very long media.
     */
    fun formatTimestamp(ms: Long): String {
        val h = ms / 3_600_000
        val m = (ms % 3_600_000) / 60_000
        val s = (ms % 60_000) / 1_000
        val milli = (ms % 1_000).toInt()
        return "%02d:%02d:%02d,%03d".format(h, m, s, milli)
    }

    private data class RedistributedPiece(
        val startMs: Long,
        val endMs: Long,
        val text: String,
    )

    /**
     * Split a segment into multiple SRT cues if it exceeds the
     * duration or word limit. Time is distributed proportionally
     * across words. Adjacent pieces overlap by [OVERLAP_MS] to
     * keep fast speech connected.
     */
    private fun redistributeTimestamps(
        startMs: Long,
        endMs: Long,
        text: String,
        maxDurationMs: Long,
        maxWords: Int,
    ): List<RedistributedPiece> {
        val words = text.split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        val duration = endMs - startMs
        val needsSplitByDuration = duration > maxDurationMs
        val needsSplitByWords = words.size > maxWords
        if (!needsSplitByDuration && !needsSplitByWords) {
            return listOf(RedistributedPiece(startMs, endMs, text))
        }

        // Determine the number of pieces: max of the two splits,
        // rounded up. E.g. 15 words / 12 max → 2; 12 sec / 8 max → 2.
        val pieceCount = maxOf(
            if (needsSplitByWords) (words.size + maxWords - 1) / maxWords else 1,
            if (needsSplitByDuration) ((duration + maxDurationMs - 1) / maxDurationMs).toInt() else 1,
        )

        val perPiece = (words.size + pieceCount - 1) / pieceCount
        val perPieceMs = duration / pieceCount

        val pieces = ArrayList<RedistributedPiece>(pieceCount)
        for (i in 0 until pieceCount) {
            val from = i * perPiece
            // Guard BEFORE subList: when pieceCount is driven by duration
            // (not words), `from` can exceed words.size (e.g. 5 words over
            // 30s → pieceCount=4, perPiece=2, at i=3 from=6 > 5). subList(6, 5)
            // would throw IllegalArgumentException. break (not continue)
            // because subsequent i values only produce larger `from`.
            if (from >= words.size) break
            val to = minOf(from + perPiece, words.size)
            val pieceWords = words.subList(from, to)
            if (pieceWords.isEmpty()) continue
            val pieceStart = startMs + i * perPieceMs
            var pieceEnd = startMs + (i + 1) * perPieceMs
            if (i > 0) {
                // Overlap with previous piece: pull start back by OVERLAP_MS.
                pieces[i - 1] = pieces[i - 1].copy(endMs = pieceStart + OVERLAP_MS)
            }
            if (i == pieceCount - 1) pieceEnd = endMs  // last piece keeps full tail
            pieces.add(RedistributedPiece(pieceStart, pieceEnd, pieceWords.joinToString(" ")))
        }
        return pieces
    }
}