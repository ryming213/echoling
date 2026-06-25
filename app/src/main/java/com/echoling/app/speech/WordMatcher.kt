package com.echoling.app.speech

/**
 * Strict sequential word matcher for STT transcription comparison.
 *
 * Normalizes both strings (lowercase, strip punctuation except apostrophes,
 * split on whitespace) then compares position-by-position. Same length and
 * all words match → passed. Any difference → failed with reason.
 *
 * No stemming, no synonym matching, no fuzzy matching — the user can edit
 * the transcription if STT misrecognized a word.
 */
object WordMatcher {
    data class MatchResult(
        val passed: Boolean,
        val origWords: List<String>,
        val transWords: List<String>,
        val reason: String  // "ok" | "empty_transcription" | "missing_word" | "extra_word" | "wrong_word"
    )

    private val NORMALIZE_REGEX = Regex("[^a-z0-9\\s']")

    fun match(original: String, transcribed: String): MatchResult {
        val orig = normalize(original)
        val trans = normalize(transcribed)
        if (trans.isEmpty()) return MatchResult(false, orig, trans, "empty_transcription")
        if (orig.size != trans.size) {
            val reason = if (trans.size > orig.size) "extra_word" else "missing_word"
            return MatchResult(false, orig, trans, reason)
        }
        for (i in orig.indices) {
            if (orig[i] != trans[i]) return MatchResult(false, orig, trans, "wrong_word")
        }
        return MatchResult(true, orig, trans, "ok")
    }

    private fun normalize(s: String): List<String> =
        s.lowercase()
            .replace(NORMALIZE_REGEX, " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
}