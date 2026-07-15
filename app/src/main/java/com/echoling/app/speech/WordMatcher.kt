package com.echoling.app.speech

/**
 * Position-independent fuzzy word matcher for STT transcription comparison.
 *
 * Compares an expected (original) sentence against the user's transcription
 * using a greedy "any orig word can match any trans word" strategy with
 * Levenshtein-based fuzzy equivalence, so a single misrecognized word does
 * NOT cascade into marking subsequent correct words as wrong.
 *
 * ## Why position-independent?
 *
 * The previous version of this matcher required (a) exact same word count
 * and (b) exact equality at every position. The user reported that this
 * was too strict in two ways:
 *   1. Subjunctive mood (e.g. "If I were you" vs. "If I was you") and
 *      similar inflection differences were marked as wrong.
 *   2. Once the first word didn't match, every subsequent (correct) word
 *      was ALSO marked wrong because the loop returned early on first
 *      mismatch.
 *
 * This rewrite addresses both:
 *   1. Fuzzy equivalence via Levenshtein distance (threshold scales with
 *      word length) plus a small [COMMON_ALTERNATES] set for cases that
 *      pure edit distance would reject (were↔was).
 *   2. Greedy multiset-style alignment: for every orig word, find the
 *      earliest unused trans word that's fuzzy-equal, and mark both as
 *      "matched". An orig word that finds no match is wrong; a trans word
 *      that isn't consumed by any orig is extra. Either way, a wrong
 *      word at position i no longer affects the verdict for position j.
 *
 * No stemming, no synonym matching, no real fuzzy phonetics — the user
 * can still edit the transcription in [TranscriptionEditor] if STT
 * misrecognized a word.
 */
object WordMatcher {
    data class MatchResult(
        val passed: Boolean,
        val origWords: List<String>,
        val transWords: List<String>,
        /**
         * Per-orig-word match status. `origMatched[i] == true` means
         * `origWords[i]` found an equivalent unused trans word during
         * alignment. Index aligns with [origWords].
         */
        val origMatched: BooleanArray,
        /**
         * Per-trans-word match status. `transMatched[j] == true` means
         * `transWords[j]` was consumed by some orig word. Index aligns
         * with [transWords]. Trans words that are `false` are "extra"
         * (no corresponding orig word).
         */
        val transMatched: BooleanArray,
        /** "ok" | "empty_transcription" | "wrong_word" */
        val reason: String
    ) {
        // BooleanArray doesn't have structural equals / hashCode,
        // so override to keep value semantics on the data class —
        // otherwise a state diff in a StateFlow wouldn't notice
        // when the matcher produces a fresh result with identical
        // contents, leading to spurious UI re-renders.
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is MatchResult) return false
            return passed == other.passed
                && origWords == other.origWords
                && transWords == other.transWords
                && origMatched.contentEquals(other.origMatched)
                && transMatched.contentEquals(other.transMatched)
                && reason == other.reason
        }

        override fun hashCode(): Int {
            var result = passed.hashCode()
            result = 31 * result + origWords.hashCode()
            result = 31 * result + transWords.hashCode()
            result = 31 * result + origMatched.contentHashCode()
            result = 31 * result + transMatched.contentHashCode()
            result = 31 * result + reason.hashCode()
            return result
        }
    }

    private val NORMALIZE_REGEX = Regex("[^a-z0-9\\s']")

    /**
     * (2026-07-10) STT output frequently contains disfluency fillers
     * — `um`, `uh`, `er` — that the user didn't consciously say but
     * Vosk hallucinated during silence. The original subtitles never
     * contain these, so leaving them in `transWords` would mark a
     * "spurious extra word" failure and turn what is otherwise a
     * near-perfect transcript into a Failed result.
     *
     * Filter is applied symmetrically to both `original` and
     * `transcribed` so the alignment length ratio stays sane if the
     * subtitle ever did contain one (it doesn't today, but defensive).
     *
     * Conservative: only pure disfluency tokens, NOT words that
     * could carry meaning (`like`, `well`, `so`, `right`) even
     * though they're sometimes used as fillers in conversational
     * English. Those carry semantic weight in subtitles and should
     * be required when the subtitle says them.
     */
    private val FILLER_WORDS: Set<String> = setOf(
        "um", "uh", "er", "erm", "hmm", "ah",
    )

    /**
     * Common alternates that aren't close enough under Levenshtein
     * to be considered equivalent, but ARE semantically interchangeable
     * in spoken English. Both directions are checked.
     *
     * (2026-07-10) The list grew from 4 to 12 entries after STT
     * regression testing on 小米 Mi 11 CN + vosk-model-small-en-us-0.15
     * revealed two systematic miss-patterns that Levenshtein's
     * length-scaled threshold cannot rescue:
     *
     *   1. **Short function-word homophones** (length 2-3). The fuzzy
     *      threshold is 0 for words of length ≤3, so Levenshtein("to",
     *      "two")=1 is rejected even though every English speaker
     *      treats them as interchangeable in speech. The same
     *      holds for "im" vs "i'm" — Vosk drops the apostrophe and
     *      Levenshtein=1, threshold=0, fail.
     *
     *   2. **Cross-dialect morphology** (were/was, got/gotten) which
     *      were already there.
     *
     * Adding entries to this set is the lowest-cost way to teach the
     * matcher about pairs whose semantic equivalence is well-attested
     * but whose spelling distance defeats pure edit-distance scoring.
     */
    private val COMMON_ALTERNATES: Set<Pair<String, String>> = setOf(
        // Cross-dialect / mood
        "were" to "was",   // subjunctive "If I were" vs. indicative "If I was"
        "was" to "were",
        "gotten" to "got", // AmE got vs. BrE gotten
        "got" to "gotten",
        // Short function-word homophones (Levenshtein=1, threshold=0,
        // so we MUST add them explicitly — no fuzzy rescue is possible)
        "to" to "too",
        "too" to "to",
        "to" to "two",
        "two" to "to",
        "too" to "two",
        "two" to "too",
        // Apostrophe-stripped contractions (Vosk often drops the `'`
        // even though we preserve it in the original via NORMALIZE_REGEX,
        // because the *user's* speaking rate and mic gain change whether
        // the apostrophe-bearing form makes it through)
        "im" to "i'm",
        "i'm" to "im",
    )

    fun match(original: String, transcribed: String): MatchResult {
        val orig = normalize(original)
        val trans = normalize(transcribed)
        if (trans.isEmpty()) {
            return MatchResult(
                passed = false,
                origWords = orig,
                transWords = trans,
                origMatched = BooleanArray(orig.size),  // all false
                transMatched = BooleanArray(0),
                reason = "empty_transcription"
            )
        }

        // Greedy alignment: scan origWords in order, for each one find
        // the earliest unused trans word that's fuzzy-equal. Mark both
        // as matched. If we can't find any, the orig word stays
        // unmatched (and will render as red on the orig row).
        val origMatched = BooleanArray(orig.size)
        val transMatched = BooleanArray(trans.size)
        for (i in orig.indices) {
            for (j in trans.indices) {
                if (!transMatched[j] && fuzzyEqual(orig[i], trans[j])) {
                    origMatched[i] = true
                    transMatched[j] = true
                    break
                }
            }
        }

        val passed = origMatched.all { it }
        return MatchResult(
            passed = passed,
            origWords = orig,
            transWords = trans,
            origMatched = origMatched,
            transMatched = transMatched,
            reason = if (passed) "ok" else "wrong_word"
        )
    }

    /**
     * (2026-07-10) Pick the best transcription candidate from a
     * Vosk n-best list. Used by [PracticeViewModel.stopStt] after
     * [VoskSpeechRecognizer.transcribeFileAlternatives] returns
     * up to N candidates (currently 3).
     *
     * Selection strategy:
     *
     *  1. **First PASS wins.** If any candidate fully matches the
     *     original (every orig word found an equivalent unused
     *     trans word), return it immediately. The "happy path" —
     *     the user's speech was clear enough that one of Vosk's
     *     top-N is the right answer.
     *
     *  2. **Most orig words matched.** If nothing fully passes,
     *     pick the candidate with the highest count of
     *     `origMatched.count { it }`. A candidate that got 4 of 5
     *     words right is more useful than one that got 2 of 5 —
     *     the user can hear the diff and self-correct on next
     *     attempt.
     *
     *  3. **Closest length to orig.** Tiebreak in (2) by
     *     `|transWords.size - origWords.size|`. A candidate with
     *     the right word count is more likely to be "the right
     *     number of words but wrong words" (actionable) than one
     *     that dropped half the words (which often means Vosk
     *     gave up mid-search and returned garbage).
     *
     * Returns the picked **text** (not a MatchResult) so the
     * caller can display it for the user to edit / accept. The
     * Pass/Fail verdict against the picked text is recomputed
     * later by [submitTranscription].
     *
     * Edge cases:
     *  - empty list → ""
     *  - single candidate → that candidate (no point running match)
     *  - all candidates empty → first candidate (preserves prior
     *    behavior of surfacing whatever Vosk gave)
     */
    fun bestOf(original: String, candidates: List<String>): String {
        if (candidates.isEmpty()) return ""
        if (candidates.size == 1) return candidates.first()
        // Track the best (matchedCount, -lengthDiff) seen so far.
        // Pair's compareTo is lexicographic on (first, second), so a
        // higher matched always wins; ties break on the second
        // component, and we store negative length diff so a
        // smaller diff = larger pair = wins.
        var bestText = candidates.first()
        var bestMatched = -1
        var bestNegDiff = Int.MIN_VALUE
        for (candidate in candidates) {
            val r = match(original, candidate)
            if (r.passed) return candidate  // rule (1): early exit
            val matched = r.origMatched.count { it }
            val lenDiff = kotlin.math.abs(r.transWords.size - r.origWords.size)
            val negDiff = -lenDiff
            if (matched > bestMatched || (matched == bestMatched && negDiff > bestNegDiff)) {
                bestMatched = matched
                bestNegDiff = negDiff
                bestText = candidate
            }
        }
        return bestText
    }

    /**
     * Two words are considered equivalent if:
     *  - they're identical, OR
     *  - they appear in [COMMON_ALTERNATES] (either order), OR
     *  - their Levenshtein distance is within a length-scaled
     *    threshold (1 edit for short words, up to 2 for longer ones).
     */
    private fun fuzzyEqual(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 2 || b.length < 2) return false
        if ((a to b) in COMMON_ALTERNATES) return true
        if ((b to a) in COMMON_ALTERNATES) return true
        val threshold = when (maxOf(a.length, b.length)) {
            in 0..3 -> 0   // "to", "a", "I" — must match exactly
            in 4..5 -> 1   // "were", "have" — tolerate 1 edit
            else -> 2      // "checks", "running" — tolerate 2 edits
        }
        if (threshold == 0) return false
        return levenshtein(a, b) <= threshold
    }

    /**
     * Iterative two-row Levenshtein. O(len(a) * len(b)) time, O(b)
     * space. Words in STT output are short (typically < 20 chars) so
     * the O(n²) worst case is fine.
     */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,       // insertion
                    prev[j] + 1,           // deletion
                    prev[j - 1] + cost     // substitution
                )
            }
            // Swap rows
            for (k in prev.indices) prev[k] = curr[k]
        }
        return prev[b.length]
    }

    private fun normalize(s: String): List<String> =
        s.lowercase()
            .replace(NORMALIZE_REGEX, " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() && it !in FILLER_WORDS }
}
