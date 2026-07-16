package com.echoling.app.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtSynthesizerTest {

    @Test
    fun `empty segments produce an empty SRT body`() {
        val srt = SrtSynthesizer.toSrt(emptyList())
        assertEquals("", srt)
    }

    @Test
    fun `single segment produces one cue`() {
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
    fun `long segment is split when word count exceeds maxWords`() {
        // 13 words → 2 cues by words (12 max → split into 7+6)
        val text = (1..13).joinToString(" ") { "w$it" }
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 12000, text))
        )
        // count cues by "^\d+$" at start of line
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertEquals(2, cueCount)
    }

    @Test
    fun `long segment is split when duration exceeds 8 seconds`() {
        val text = "one two three four five six seven eight nine ten"
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 10000, text))
        )
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertEquals(2, cueCount)
    }

    @Test
    fun `very long segment is split into 2 or more cues`() {
        // 15 words, 12 seconds → both limits exceeded → 2+ cues
        val text = (1..15).joinToString(" ") { "w$it" }
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 12000, text))
        )
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertTrue("expected >=2 cues, got $cueCount", cueCount >= 2)
    }

    @Test
    fun `special characters pass through verbatim`() {
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
        // Two segments where end1 > start2 — the synthesizer must NOT
        // merge them. They are independent cues.
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
    fun `long duration split with few words does not crash when words run out`() {
        // 5 words over 30 seconds: duration-driven split (pieceCount = 4)
        // would normally try to slice words[6..5] — must not crash.
        val text = "one two three four five"
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 30_000, text))
        )
        // Must produce a valid SRT (cue count <= 5 — no crash, no extra empty cues)
        val cueCount = srt.lines().count { it.matches(Regex("^\\d+$")) }
        assertTrue("expected >=1 cue, got $cueCount", cueCount >= 1)
        assertTrue("expected <=5 cues, got $cueCount", cueCount <= 5)
        // The text content must be present (in some form)
        assertTrue("missing any of the words: $srt",
            srt.contains("one") || srt.contains("five"))
    }

    @Test
    fun `adjacent redistributed pieces overlap by OVERLAP_MS`() {
        // 13 words over 8s → duration split, 2 cues
        val text = (1..13).joinToString(" ") { "w$it" }
        val srt = SrtSynthesizer.toSrt(
            listOf(VoskSegment(0, 8000, text))
        )
        // Parse cues back out: each cue is "<index>\n<start> --> <end>\n<text>\n"
        val cueTimes = Regex("""(\d{2}):(\d{2}):(\d{2}),(\d{3}) --> (\d{2}):(\d{2}):(\d{2}),(\d{3})""")
            .findAll(srt)
            .map { m ->
                val (sH, sM, sS, sMs, eH, eM, eS, eMs) = m.destructured
                val startMs = sH.toLong()*3_600_000 + sM.toLong()*60_000 + sS.toLong()*1_000 + sMs.toLong()
                val endMs = eH.toLong()*3_600_000 + eM.toLong()*60_000 + eS.toLong()*1_000 + eMs.toLong()
                startMs to endMs
            }
            .toList()
        assertEquals("expected 2 cues, got: $srt", 2, cueTimes.size)
        val (cue1Start, cue1End) = cueTimes[0]
        val (cue2Start, cue2End) = cueTimes[1]
        // Adjacent cues should overlap: cue1End > cue2Start
        val overlap = cue1End - cue2Start
        assertTrue("expected ~750ms overlap, got ${overlap}ms (cue1End=$cue1End, cue2Start=$cue2Start)",
            overlap in 700L..800L)
    }
}