package com.echoling.app.domain.model

/**
 * One entry in a bundled vocabulary list.
 *
 * `phonetic` uses IPA notation (e.g. "/ðə; ðiː/"). `pos` is the part of
 * speech tag (e.g. "art.", "vt.", "n."). `translation` is the Chinese
 * gloss, which may include a leading POS prefix (e.g. "vt. 抛弃") — the
 * user is expected to clean it up in the editable dialog field before
 * saving to the vocabulary list.
 *
 * For the unified long-press lookup in the practice screen the same
 * `word` may surface with merged translations when the source data has
 * the word in multiple categories with different glosses — those are
 * joined with the "；" separator so the user sees all variants in one
 * place. Per-category flashcards iterate each category's own
 * [DictCategory.entries] list so the user sees the category-specific
 * translation when studying that category.
 */
data class DictEntry(
    val word: String,
    val phonetic: String,
    val pos: String,
    val translation: String,
)

/**
 * A named vocabulary category with its full word list.
 *
 * `id` is a stable slug used in nav routes and asset filenames (e.g.
 * "junior", "senior", "cet4", "cet6", "toefl"). `name` is the
 * user-visible Chinese label. `description` is a one-line subtitle
 * shown under the category card on the picker screen.
 *
 * The list is **already filtered** at load time — single-letter words
 * (e.g. "a", "I") and entries with empty translations are removed
 * before this data class is constructed, so the caller can iterate
 * without re-checking.
 */
data class DictCategory(
    val id: String,
    val name: String,
    val description: String,
    val entries: List<DictEntry>,
) {
    val size: Int get() = entries.size
}
