package com.echoling.app.domain.model

/**
 * A word saved to the user's vocabulary book. May have been saved from
 * either the practice screen long-press flow or the flashcard study
 * screen — both paths route through the same Room table.
 *
 * Fields:
 *  - [phonetic] / [pos] — populated from the bundled
 *    [com.echoling.app.domain.model.DictEntry] when the save comes
 *    from the offline dictionary. Blank when the user types a word
 *    manually that wasn't in the dictionary.
 *  - [translation] — user-editable Chinese gloss; defaults to the
 *    dictionary value when the save path provides one.
 *  - [sourceCourseId] / [sourceSentenceId] — link back to the practice
 *    sentence the word came from, when applicable. Empty/0 for words
 *    saved from the study screen.
 */
data class Word(
    val word: String,
    val phonetic: String = "",
    val pos: String = "",
    val translation: String,
    val exampleSentence: String = "",
    val sourceCourseId: String = "",
    val sourceSentenceId: Int = 0,
    val isMastered: Boolean = false,
    val collectedAt: Long,
    val reviewCount: Int = 0,
    val nextReviewTime: Long,
)
