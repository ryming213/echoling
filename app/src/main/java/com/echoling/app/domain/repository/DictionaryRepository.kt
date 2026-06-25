package com.echoling.app.domain.repository

import com.echoling.app.domain.model.DictCategory
import com.echoling.app.domain.model.DictEntry

/**
 * Read-only local dictionary used as a fast offline source for both:
 *
 *  1. The long-press-to-translate flow in the practice screen —
 *     [lookup] does a case-insensitive O(1) `HashMap.get` against an
 *     in-memory map keyed by lowercased word. Entries with the same
 *     word but different translations across categories are merged
 *     here with the "；" separator so the user sees all variants.
 *
 *  2. The flashcard study screen — [categories] returns the ordered
 *     list of available categories with their entries; [wordsInCategory]
 *     returns just the entries for one slug. Per-category lists keep the
 *     category-specific translation, not the merged one, so studying
 *     高中 shows the high-school-specific gloss even if CET-6 has a
 *     different one.
 *
 * Single-letter entries (e.g. "a", "I") are filtered at load time — see
 * [com.echoling.app.data.repository.DictionaryRepositoryImpl].
 */
interface DictionaryRepository {
    /** Case-insensitive lookup of [word] across all categories. */
    suspend fun lookup(word: String): DictEntry?

    /**
     * Snapshot of every entry in the dictionary, in alphabetical order.
     * Used as a fallback when callers need a single flat list (e.g. the
     * legacy "all words" flashcard path). Returns entries with
     * **merged translations** where a word exists in multiple categories
     * with different glosses.
     */
    suspend fun allWords(): List<DictEntry>

    /**
     * Ordered list of every available category (with its entries
     * attached). Order matches the order in the manifest JSON so the
     * category picker UI renders in a predictable sequence.
     */
    suspend fun categories(): List<DictCategory>

    /**
     * Entries for one category only. Returns an empty list when the
     * category id is unknown — callers can render an empty state rather
     * than throwing.
     */
    suspend fun wordsInCategory(categoryId: String): List<DictEntry>
}
