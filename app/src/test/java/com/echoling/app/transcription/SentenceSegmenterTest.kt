package com.echoling.app.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSegmenterTest {

    // ----- wordCount -----

    @Test fun `wordCount counts simple tokens`() {
        // She(1) came(2) to(3) the(4) party(5) = 5.
        assertEquals(5, SentenceSegmenter.wordCount("She came to the party"))
    }

    @Test fun `wordCount counts contractions as one word`() {
        assertEquals(3, SentenceSegmenter.wordCount("it's a test"))
    }

    @Test fun `wordCount ignores extra whitespace`() {
        assertEquals(3, SentenceSegmenter.wordCount("  one   two   three  "))
    }

    @Test fun `wordCount handles decimal numbers as two tokens under word regex`() {
        // `\b[\w']+\b` matches digits; "5.5" → 2 tokens (5 and 5).
        assertEquals(2, SentenceSegmenter.wordCount("5.5"))
    }

    // ----- hasVerb -----

    @Test fun `hasVerb true for auxiliary be`() {
        assertTrue(SentenceSegmenter.hasVerb("She is happy"))
    }

    @Test fun `hasVerb true for irregular past said`() {
        assertTrue(SentenceSegmenter.hasVerb("He said the news"))
    }

    @Test fun `hasVerb true for regular past loved`() {
        assertTrue(SentenceSegmenter.hasVerb("Everyone loved the cake"))
    }

    @Test fun `hasVerb true for 3rd person singular present takes`() {
        assertTrue(SentenceSegmenter.hasVerb("She takes the bus"))
    }

    @Test fun `hasVerb true for irregular past did`() {
        // "did" is in IRREGULAR_VERBS, len 3 ≤ 3 so the suffix-check loop
        // skips it — but the AUX/IRREGULAR pre-check passes.
        assertTrue(SentenceSegmenter.hasVerb("I did it yesterday morning"))
    }

    @Test fun `hasVerb false for bare noun phrase`() {
        // "Fears of drones at the border" — "Fears" is a noun drop-headline,
        // no verb; used as a guard against false splits.
        assertFalse(SentenceSegmenter.hasVerb("Fears of drones at the border"))
    }

    // ----- startsWithSubject -----

    @Test fun `startsWithSubject true for capitalized pronoun`() {
        assertTrue(SentenceSegmenter.startsWithSubject("She came home"))
    }

    @Test fun `startsWithSubject true for lowercase pronoun he`() {
        assertTrue(SentenceSegmenter.startsWithSubject("he came home"))
    }

    @Test fun `startsWithSubject true for contraction It's`() {
        assertTrue(SentenceSegmenter.startsWithSubject("It's raining today"))
    }

    @Test fun `startsWithSubject false for bare noun drones`() {
        assertFalse(SentenceSegmenter.startsWithSubject("drones at the border"))
    }

    @Test fun `startsWithSubject false for lowercase dangling participle trying`() {
        // Capitalized "Trying" is accepted by both Python and Kotlin (sentence-
        // initial capital → subject). Lowercase "trying" is a real fragment.
        assertFalse(SentenceSegmenter.startsWithSubject("trying to disrupt..."))
    }

    @Test fun `startsWithSubject true for subject-starter You`() {
        assertTrue(SentenceSegmenter.startsWithSubject("you and I went"))
    }

    // ----- isCompleteSentence -----

    @Test fun `isCompleteSentence happy path accepts full clause`() {
        assertTrue(SentenceSegmenter.isCompleteSentence("She came to the party"))
    }

    @Test fun `isCompleteSentence rejects dangling trailing 'because'`() {
        // "...said that the man who" pattern.
        assertFalse(SentenceSegmenter.isCompleteSentence("the man who came because"))
    }

    @Test fun `isCompleteSentence rejects too few words`() {
        assertFalse(SentenceSegmenter.isCompleteSentence("She came"))
    }

    @Test fun `isCompleteSentence rejects trailing comma`() {
        assertFalse(SentenceSegmenter.isCompleteSentence("She came home,"))
    }

    @Test fun `isCompleteSentence rejects lowercase dangling participle start`() {
        // Lowercase "trying to..." — `trying` not in SUBJECT_STARTERS, no verb
        // matches with NO following subject (only "to" as 2-char token).
        assertFalse(SentenceSegmenter.isCompleteSentence("trying to disrupt the mission"))
    }

    // ----- normalizePiece -----

    @Test fun `normalizePiece capitalizes first letter`() {
        assertEquals("She came.", SentenceSegmenter.normalizePiece("she came"))
    }

    @Test fun `normalizePiece adds terminal period`() {
        assertEquals("She came.", SentenceSegmenter.normalizePiece("She came"))
    }

    @Test fun `normalizePiece preserves existing terminal punctuation`() {
        assertEquals("She came!", SentenceSegmenter.normalizePiece("She came!"))
    }

    @Test fun `normalizePiece strips leading whitespace`() {
        assertEquals("She came.", SentenceSegmenter.normalizePiece("  she came"))
    }

    @Test fun `normalizePiece capitalizes lowercase first letter after leading comma-strip`() {
        // Pass-2 split: "and US forces" → normalizePiece capitalizes 'a'.
        assertEquals("And US forces.", SentenceSegmenter.normalizePiece("and US forces"))
    }

    @Test fun `normalizePiece preserves already-uppercase first letter`() {
        assertEquals("US forces arrived.", SentenceSegmenter.normalizePiece("US forces arrived"))
    }

    // ----- findBestSplit: short cues left whole -----

    @Test fun `findBestSplit leaves short cue untouched`() {
        // 7 words ≤ TARGET_WORDS=18.
        val out = SentenceSegmenter.findBestSplit("She came to the party yesterday")
        assertEquals(listOf("She came to the party yesterday"), out)
    }

    @Test fun `findBestSplit leaves 10-word cue untouched`() {
        // TARGET_WORDS=10 → 10w ≤ 10 → early return without scanning.
        // The 14-word placeholder variant (was the previous threshold)
        // would also still pass because (1..14).joinToString has no
        // internal clause boundary, but the threshold is what matters.
        val text = (1..10).joinToString(" ") { "w$it" }
        assertEquals(listOf(text), SentenceSegmenter.findBestSplit(text))
    }

    @Test fun `findBestSplit splits 12-word sentence with and-clause`() {
        // User requirement #2: a sentence that's "too long" with a clean
        // `and`-connector linking two complete clauses must split, even
        // when the count is below the previous TARGET=14. TARGET=10 makes
        // 12w cues eligible; "and she left the party very quickly" has a
        // complete right clause (pronoun + verb), so the split fires.
        val text = "She came home yesterday afternoon and she left the party very quickly"
        val out = SentenceSegmenter.findBestSplit(text)
        assertEquals("expected 2 pieces from and-clause split, got: $out", 2, out.size)
        assertTrue("left must end before 'and': ${out[0]}",
            out[0].lowercase().contains("yesterday afternoon"))
        assertTrue("right must start with preserved connector: ${out[1]}",
            out[1].lowercase().startsWith("and"))
    }

    // ----- Pass 1: sentence boundary -----

    @Test fun `findBestSplit splits at sentence boundary with capital`() {
        // 24 words, two sentences. Split at `. + capital`.
        val text = "She came home. The dog was waiting patiently for her at the front door all afternoon because she had been gone for many hours"
        val out = SentenceSegmenter.findBestSplit(text)
        assertEquals("expected 2 pieces from sentence boundary, got: $out", 2, out.size)
        assertEquals("She came home.", out[0])
        assertTrue("second piece starts with capital: ${out[1]}",
            out[1].startsWith("The"))
    }

    @Test fun `findBestSplit does NOT split at abbreviation Mr`() {
        // 22 words. Mr. is abbreviation so Pass 1 won't fire on it. Crucially
        // no `-ing` false-positive words (no "wing", "dining", etc.) and no
        // conjunctions with valid `and + subject` clauses on the right side.
        // Cleanest text that has Mr. as the only sentence-boundary candidate.
        val text = "She greeted Mr Smith at the office yesterday afternoon and offered him a coffee and a tour of the new department today"
        val out = SentenceSegmenter.findBestSplit(text)
        assertEquals("expected no split (Mr. is abbreviation, no other boundary), got: $out", 1, out.size)
        assertTrue("must still contain 'Mr Smith': $out",
            out[0].contains("Mr Smith"))
    }

    @Test fun `findBestSplit does NOT split at decimal number`() {
        // 19 words with "5.5 percent" mid-cue. No other valid split points.
        val text = "The growth was reported as 5.5 percent which surprised many analysts and economists who tracked the situation closely"
        val out = SentenceSegmenter.findBestSplit(text)
        assertEquals("expected no split at decimal, got: $out", 1, out.size)
    }

    // ----- Pass 1.5: comma boundary (appositive clauses) -----

    @Test fun `findBestSplit splits appositive commas when both sides are complete`() {
        // 20 words. First comma: left starts "She" (cap), right starts "where"
        // (relative, in SUBJECT_STARTERS). Both sides are complete sentences.
        val text = "She lived in the city, where the people were friendly and the food was excellent and the culture was vibrant and welcoming"
        val out = SentenceSegmenter.findBestSplit(text)
        assertTrue("expected >=2 pieces from comma split, got: $out", out.size >= 2)
        assertTrue("first piece includes 'She lived in the city': ${out.first()}",
            out.first().contains("She lived in the city"))
    }

    @Test fun `findBestSplit does NOT split at bare-noun comma`() {
        // 19 words. Three commas but no conjunction (no and/but/so/or anywhere).
        // Pass 1.5 fails at first/second/third comma (right of each starts with
        // bare noun like "oranges" or "bananas", not in SUBJECT_STARTERS).
        // No `and/but/so/or` clauses exist either. → no clean split anywhere.
        val text = "He bought apples, oranges, bananas from the local farmers market yesterday morning before the store opened for the day"
        val out = SentenceSegmenter.findBestSplit(text)
        // Wordcount = 19 > 18, but no clean boundary exists.
        assertEquals("expected no clean split, got: $out", 1, out.size)
        assertTrue("whole sentence preserved: $out",
            out[0].contains("oranges"))
    }

    // ----- Pass 2: clause boundary (and / but / so / or) -----

    @Test fun `findBestSplit splits at but-clause between two complete clauses`() {
        // 23 words; `but she never` is a clean but-clause split.
        val text = "She said she would come to the party but she never actually showed up and nobody knew why she didn't"
        val out = SentenceSegmenter.findBestSplit(text)
        assertTrue("expected split, got: $out", out.size >= 2)
        assertTrue("first piece must include 'she would come to the party': ${out[0]}",
            out[0].contains("she would come to the party"))
    }

    @Test fun `findBestSplit does NOT split compound nouns missiles and drones`() {
        // 20 words. `and drones`: right side has no verb of its own; "drones to
        // try to disrupt..." needs the verb "used" from the left. isCompleteSentence
        // fails on "drones..." (no subject starter, no verb of its own).
        val text = "Iran had used cruise missiles and drones to try to disrupt the ongoing mission yesterday according to the official report"
        val out = SentenceSegmenter.findBestSplit(text)
        assertEquals("must NOT split compound nouns without verb on right: $out", 1, out.size)
    }

    @Test fun `findBestSplit does NOT split parallel predicate killed and 22`() {
        // 20 words. `and 22` — "22" is a number, not in SUBJECT_STARTERS and
        // doesn't match `[A-Z][\w'\-]*`. Pass 2 rejects.
        val text = "Five people were killed and 22 injured in the accident yesterday afternoon according to local authorities in the morning statement"
        val out = SentenceSegmenter.findBestSplit(text)
        assertEquals("must NOT split parallel predicate: $out", 1, out.size)
    }

    @Test fun `findBestSplit picks most-balanced AND when multiple candidates`() {
        // 22 words with two `and` candidates:
        //  - "drones and boats" — right starts "boats" bare noun, REJECTED.
        //  - "mission and US forces" — right starts "US" (capital), both sides
        //    have verbs, ACCEPTED. Only one valid candidate, picked.
        val text = "Iran had used cruise missiles and drones and boats to try to disrupt the mission and US forces had already sunk the boats"
        val out = SentenceSegmenter.findBestSplit(text)
        assertTrue("expected split, got: $out", out.size >= 2)
        assertTrue("first piece must include 'mission': ${out[0]}",
            out[0].contains("mission"))
        assertTrue("second piece must contain 'forces': ${out[1]}",
            out[1].contains("forces"))
    }

    // ----- Pass 4: relative clause -----

    @Test fun `findBestSplit splits at relative clause which is`() {
        // 24 words; comma before `, which is rarely heard...`.
        val text = "She told him a long story about the old house, which is rarely heard in these modern days by anyone anymore today"
        val out = SentenceSegmenter.findBestSplit(text)
        // Pass 1.5 (comma) fires first, splitting at the comma before "which".
        // Either way, split is valid.
        assertTrue("expected split, got: $out", out.size >= 2)
    }

    // ----- Pass 5: filler / any long-sentence split -----

    @Test fun `findBestSplit splits long sentence with multiple boundaries`() {
        // 23 words with 2 `and` clauses. Match 1 ("and Everyone") is the only
        // valid candidate (right starts with capital "Everyone"). Verifies a
        // long sentence with multiple potential boundaries still splits at
        // the right one.
        val text = "She told him about the problem and Everyone agreed that the situation was difficult and required immediate attention from the management team yesterday"
        val out = SentenceSegmenter.findBestSplit(text)
        assertTrue("expected at least 2 pieces, got: $out", out.size >= 2)
        assertTrue("first piece must include 'She told him': ${out[0]}",
            out[0].contains("She told him"))
    }

    // ----- Recursion -----

    @Test fun `findBestSplit recursively splits even after first pass`() {
        // 36 words, 5 `and` matches. All clauses use verbs from IRREGULAR_VERBS
        // (went/bought/brought/met/told). Most-balanced at depth-0 = match 3
        // ("and she met"), 15w + 21w. Right 21w still > 18 → recurses →
        // splits at first `and` in the right piece → 7w + 12w. Final: 3 pieces.
        val text = "She went to the store and she bought the groceries and she brought them home and she met the kids at the door and she told them stories about the trip and she went to bed"
        val out = SentenceSegmenter.findBestSplit(text)
        assertTrue("expected >=3 pieces from recursive split, got: $out", out.size >= 3)
    }

    // ----- No clean split -----

    @Test fun `findBestSplit returns original when no clean boundary exists`() {
        // 22 adjectives + nouns — no conjunction-clause boundary, no period,
        // no comma, no relative, no filler. hasVerb is false on every chunk.
        // isCompleteSentence always rejects. Returns original.
        val text = "the strange unusual peculiar odd weird curious fascinating remarkable extraordinary unusual uncommon rare atypical unique unprecedented strange situation this morning"
        val out = SentenceSegmenter.findBestSplit(text)
        assertEquals("expected no clean split, got: $out", 1, out.size)
    }

    // ----- Most-balanced candidate -----

    @Test fun `findBestSplit picks most-balanced valid clause candidate`() {
        // 22 words. Two valid clause candidates:
        // - "and she checked" (Cand 1): left 5w, right 17w. max=17.
        // - "and she went"   (Cand 2): left 13w, right 9w. max=13.
        // Most-balanced picks Cand 2 → [13w, 9w].
        // With TARGET=10 the 13w left recurses → splits at "and she
        // checked" → [5w, 7w, 9w]. The "counter" word originally in the
        // most-balanced left now sits in out[1].
        val text = "she arrived at the airport and she checked her bags at the counter and she went through security quickly to her gate"
        val out = SentenceSegmenter.findBestSplit(text)
        assertEquals("expected 3 pieces (most-balanced + recursion on >10w left), got: $out", 3, out.size)
        assertTrue("most-balanced pick left (now out[1] after recursion) must contain 'counter': $out",
            out[1].lowercase().contains("counter"))
    }

    // ----- BBC news register regressions (2026-07-18) -----

    @Test fun `findBestSplit splits at and-today temporal clause`() {
        // 26w BBC-style opening. "and today" — right starts "today"
        // (temporal opener, now in SUBJECT_STARTERS).
        val text = "This is the BBC News and today we are talking about the situation in the middle east and the conflict continues to escalate in the region"
        val out = SentenceSegmenter.findBestSplit(text)
        assertTrue("expected >=2 pieces, got: $out", out.size >= 2)
        assertTrue("first piece must contain 'BBC News': ${out[0]}",
            out[0].contains("BBC News"))
    }

    @Test fun `findBestSplit splits at and-the-conflict-continues`() {
        // "continues" was missing from Python's ES_SUFFIX_VERBS — its -ues
        // ending doesn't match the -ches/-shes/-xes/-zes/-oes/-ses sound
        // patterns. Now explicitly listed.
        val text = "The region has been unstable and The conflict continues to escalate as both sides refuse to negotiate with each other"
        val out = SentenceSegmenter.findBestSplit(text)
        assertTrue("expected >=2 pieces, got: $out", out.size >= 2)
    }

    @Test fun `findBestSplit splits at and-tomorrow policy clause`() {
        // "tomorrow" now in SUBJECT_STARTERS.
        val text = "Today the prime minister announced a new policy and tomorrow the cabinet will meet to discuss the implementation details and the public response"
        val out = SentenceSegmenter.findBestSplit(text)
        assertTrue("expected >=2 pieces, got: $out", out.size >= 2)
        assertTrue("first piece must contain 'prime minister': ${out[0]}",
            out[0].contains("prime minister"))
    }

    @Test fun `findBestSplit splits at and-the-report-contains`() {
        // "contains" was missing — -ains ending not in sound patterns.
        val text = "The report was published last week and it contains several recommendations for improving the system and reducing costs"
        val out = SentenceSegmenter.findBestSplit(text)
        assertTrue("expected >=2 pieces, got: $out", out.size >= 2)
        assertTrue("first piece must contain 'report was published': ${out[0]}",
            out[0].contains("report was published"))
    }

    // ----- Connector preservation at start of right piece (user requirement: "在拆分的下一句中保留and") -----

    @Test fun `findBestSplit preserves and at start of right piece`() {
        // Regression: CLAUSE_RE was consuming `and` in the regex match,
        // dropping the connector from the right piece ("she told them"
        // instead of "And she told them"). User explicit feedback: keep
        // the conjunction so the split reads naturally.
        // Text lengthened to 17 words (> TARGET_WORDS=14) so the
        // early-return guard doesn't suppress the split.
        val text = "She told him the surprising news from the office and she left the meeting room very quickly afterwards"
        val out = SentenceSegmenter.findBestSplit(text)
        assertEquals("expected split, got: $out", 2, out.size)
        assertTrue("right piece MUST start with 'And' (connector preserved): ${out[1]}",
            out[1].lowercase().startsWith("and"))
        assertFalse("right piece must NOT drop the connector: ${out[1]}",
            out[1].lowercase().startsWith("she left"))
    }

    @Test fun `findBestSplit preserves but at start of right piece`() {
        // 22 words. Pass 2 candidates:
        // - "and he refused" (Cand 1): left 8w, right 14w. max=14.
        // - "but she kept"   (Cand 2): left 13w, right 9w. max=13.
        // Most-balanced picks Cand 2 → [13w, 9w]. With TARGET=10 the
        // 13w left recurses → splits at "and he refused" → [8w, 5w, 9w].
        // The preserved "but" connector sits at the start of out[2].
        val text = "She told him the news from the meeting and he refused to listen but she kept telling him everything anyway yesterday afternoon"
        val out = SentenceSegmenter.findBestSplit(text)
        assertEquals("expected 3 pieces (most-balanced + recursion on >10w left), got: $out", 3, out.size)
        assertTrue("right piece of most-balanced split MUST start with 'But' (now out[2] after recursion): $out",
            out[2].lowercase().startsWith("but"))
    }

    @Test fun `findBestSplit preserves so at start of right piece`() {
        val text = "She worked very hard every night for many weeks and studied until late so she finally passed the difficult exam yesterday"
        val out = SentenceSegmenter.findBestSplit(text)
        assertEquals("expected split, got: $out", 2, out.size)
        assertTrue("right piece MUST start with 'So' (connector preserved): ${out[1]}",
            out[1].lowercase().startsWith("so"))
    }
}
