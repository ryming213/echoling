package com.echoling.app.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtSynthesizerTest {

    @Test
    fun `empty segments produce an empty SRT body`() {
        val srt = SrtSynthesizer.toSrt(emptyList())
        assertEquals("", srt)
    }

    @Test
    fun `single short segment produces one cue with original casing`() {
        // "hello world" → 2 words ≤ TARGET_WORDS=18 → no smart split,
        // no normalize (Python parity: normalize only on actual split).
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(startMs = 0, endMs = 1500, text = "hello world"))
        )
        // 0 + END_PAD_MS(400) = 1900
        assertEquals(
            """
            1
            00:00:00,000 --> 00:00:01,900
            hello world

            """.trimIndent(),
            srt
        )
    }

    @Test
    fun `multi-segment cues are numbered sequentially`() {
        val srt = SrtSynthesizer.toSrt(
            listOf(
                VoskSegment(0, 1000, "first"),
                VoskSegment(2000, 3000, "second"),
                VoskSegment(4000, 5000, "third"),
            )
        )
        // Cue 1: 0 → 1400; cue 2: 2000 → 3400; cue 3: 4000 → 5400
        assertTrue("cue 1 not found: $srt", srt.contains("1\n00:00:00,000 --> 00:00:01,400\nfirst"))
        assertTrue("cue 2 not found: $srt", srt.contains("2\n00:00:02,000 --> 00:00:03,400\nsecond"))
        assertTrue("cue 3 not found: $srt", srt.contains("3\n00:00:04,000 --> 00:00:05,400\nthird"))
    }

    @Test
    fun `END_PAD_MS extends the last segment by 400ms`() {
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(5000, 7000, "padded"))
        )
        // 7000 + 400 = 7400
        assertTrue(srt.contains("00:00:05,000 --> 00:00:07,400"))
    }

    @Test
    fun `long cue with smart split produces multiple normalized cues`() {
        // 19 words, clean `and` clause in the middle. Expects smart split
        // into 2 pieces, each capitalized + period added (normalize).
        // Use a known good split candidate (She ..., and she ...).
        val text = "She came to the party and she brought her famous chocolate cake that everyone loved so much yesterday evening"
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 12000, text))
        )
        // Two cues (numbered 1 and 2).
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertEquals("expected 2 cues from smart split, got:\n$srt", 2, cueCount)
        // First cue: "She came to the party." (normalize adds capital + period).
        assertTrue("missing 'She came to the party.': $srt",
            srt.contains("She came to the party."))
        // Second cue: connector is preserved at start (user requirement
        // "在拆分的下一句中保留and"). normalizePiece capitalizes the first
        // letter of the piece → "And she brought..." with lowercase 'she'
        // (the original text after the conjunction).
        assertTrue("missing normalized second piece with preserved 'And': $srt",
            srt.contains("And she brought her famous chocolate cake"))
    }

    @Test
    fun `special characters pass through verbatim when no split`() {
        val srt = SrtSynthesizer.toSrt(
            listOf(
                VoskSegment(0, 1000, "it's a test"),
                VoskSegment(2000, 3000, "what (ever) you & me"),
            )
        )
        assertTrue("apostrophe: $srt", srt.contains("it's a test"))
        assertTrue("brackets: $srt", srt.contains("what (ever) you & me"))
    }

    @Test
    fun `formatTimestamp zero is 00 00 00 000`() {
        assertEquals("00:00:00,000", SrtSynthesizer.formatTimestamp(0))
    }

    @Test
    fun `formatTimestamp 3661500ms is 01 01 01 500`() {
        // 3,661,500 ms = 1 h 1 m 1 s 500 ms
        assertEquals("01:01:01,500", SrtSynthesizer.formatTimestamp(3_661_500))
    }

    @Test
    fun `formatTimestamp 99ms is 00 00 00 099 (no truncation)`() {
        // Regression: %03d zero-pads to 3 digits; %d would lose the leading 0.
        assertEquals("00:00:00,099", SrtSynthesizer.formatTimestamp(99))
    }

    @Test
    fun `segments with overlapping windows each get their own cues`() {
        // Two short segments — each becomes 1 cue, no merge.
        val srt = SrtSynthesizer.toSrt(
            listOf(
                VoskSegment(0, 5000, "alpha bravo"),
                VoskSegment(3000, 7000, "charlie delta"),
            )
        )
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertEquals(2, cueCount)
        assertTrue(srt.contains("alpha bravo"))
        assertTrue(srt.contains("charlie delta"))
    }

    @Test
    fun `long duration with short text does not split or crash`() {
        // 5 words over 30 seconds: old code used to crash on words[6..5]
        // subList. New code: 5 words ≤ 18 → no smart split → 1 cue,
        // redistribute passthrough.
        val text = "one two three four five"
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 30_000, text))
        )
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertEquals("expected exactly 1 cue (no split), got $cueCount", 1, cueCount)
        assertTrue("missing 'one': $srt", srt.contains("one"))
        assertTrue("missing 'five': $srt", srt.contains("five"))
    }

    @Test
    fun `smart split pieces overlap by OVERLAP_MS`() {
        // 19 words, clean AND-split. Redistribute should produce 2 cues
        // with ~750ms overlap.
        val text = "She came to the party and she brought her famous chocolate cake that everyone loved so much yesterday evening"
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 8000, text))
        )
        val cueTimes = Regex("""(\d{2}):(\d{2}):(\d{2}),(\d{3}) --> (\d{2}):(\d{2}):(\d{2}),(\d{3})""")
            .findAll(srt)
            .map { m ->
                val (sH, sM, sS, sMs, eH, eM, eS, eMs) = m.destructured
                val startMs = sH.toLong() * 3_600_000 + sM.toLong() * 60_000 + sS.toLong() * 1_000 + sMs.toLong()
                val endMs = eH.toLong() * 3_600_000 + eM.toLong() * 60_000 + eS.toLong() * 1_000 + eMs.toLong()
                startMs to endMs
            }
            .toList()
        assertEquals("expected 2 cues, got: $srt", 2, cueTimes.size)
        val (cue1Start, cue1End) = cueTimes[0]
        val (cue2Start, cue2End) = cueTimes[1]
        val overlap = cue1End - cue2Start
        assertTrue("expected ~750ms overlap, got ${overlap}ms", overlap in 700L..800L)
        // Last cue ends at segment endMs + END_PAD_MS = 8000 + 400 = 8400.
        assertEquals("last cue end should be 8400ms, got $cue2End", 8400L, cue2End)
    }

    @Test
    fun `no-split short cue preserves original casing and lacks terminal period`() {
        // "hello world" stays "hello world" (no terminal period). The Python
        // script also keeps non-split cues verbatim.
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 1500, "hello world"))
        )
        assertFalse("must NOT add terminal period when no split: $srt",
            srt.contains("hello world."))
        assertTrue("must keep original casing: $srt",
            srt.contains("hello world\n"))
    }

    @Test
    fun `cue with embedded abbreviation Mr is not split at the period`() {
        // "She greeted Mr. Smith warmly..." — Mr is abbreviation, no split.
        // 18 words if we count, but no clause boundary — should leave alone.
        val text = "She greeted Mr Smith warmly at the door and invited him in for tea"
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 4000, text))
        )
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertEquals("expected 1 cue (no clause boundary), got $cueCount", 1, cueCount)
        assertTrue("missing original text: $srt", srt.contains("Mr Smith warmly"))
    }

    @Test
    fun `compound-noun and-clause like drones and missiles does not split`() {
        // "Iran had used cruise missiles and drones to try to disrupt the mission"
        // — `and drones`: right side has no verb of its own, can't stand alone.
        // Old behavior: dumb word-count split. New behavior: leave as-is.
        val text = "Iran had used cruise missiles and drones to try to disrupt the mission according to the report yesterday"
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 12000, text))
        )
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertEquals("must NOT split compound noun 'missiles and drones', got $cueCount cues:\n$srt", 1, cueCount)
        assertTrue("whole sentence preserved: $srt",
            srt.contains("Iran had used cruise missiles and drones to try to disrupt the mission"))
    }
}
