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
 *     list of available categories with their `size` (entry count, from
 *     the manifest) but **without** the entries attached; [wordsInCategory]
 *     returns just the entries for one slug. Per-category lists keep the
 *     category-specific translation, not the merged one, so studying
 *     高中 shows the high-school-specific gloss even if CET-6 has a
 *     different one.
 *
 * **Two-phase lazy loading** (2026-07-04 refactor — replaces the old
 * "parse all 12 MB on first call" path with a manifest-only first phase
 * so the 记单词 picker renders instantly):
 *
 *  - [categories] is now a fast path — it only parses the ~1 KB
 *    manifest and never opens any of the multi-MB entries files. The
 *    picker UI calls this on first tab open.
 *  - [wordsInCategory] lazily parses only the requested category's
 *    entries file when the user actually opens a flashcard study screen.
 *  - [lookup] (and [allWords]) lazily trigger a full parse of every
 *    category the first time they're called — i.e. on the first
 *    long-press translation. The first call pays the 12 MB cost; every
 *    subsequent call is O(1).
 *
 * Implementations should expose a `warmupAll()` method (see
 * [com.echoling.app.data.repository.DictionaryRepositoryImpl]) for
 * ViewModels to kick off the full parse in the background right after
 * the manifest is loaded — by the time the user navigates anywhere,
 * the lookup map is already warm.
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
     * Ordered list of every available category. Returns lightweight
     * [DictCategory] entries with id / name / description / size only —
     * the actual entries are NOT attached. Order matches the order in
     * the manifest JSON so the category picker UI renders in a
     * predictable sequence. Cheap to call — only parses the ~1 KB
     * `vocab_manifest.json`.
     */
    suspend fun categories(): List<DictCategory>

    /**
     * Entries for one category only. Returns an empty list when the
     * category id is unknown — callers can render an empty state rather
     * than throwing. Lazily parses the requested category's JSON on
     * first call; cached for subsequent calls.
     */
    suspend fun wordsInCategory(categoryId: String): List<DictEntry>

    /**
     * Pre-warm the full merged lookup map in the background so the
     * first call to [lookup] / [allWords] / [wordsInCategory] is
     * instant. Idempotent — a second call while the first is in
     * flight waits on the same mutex and returns the same map.
     *
     * ViewModels that already need [categories] for their first frame
     * (e.g. the picker) should kick off [warmupAll] right after their
     * first [categories] call returns — the user is staring at the
     * picker reading the "N 词" chips, so the heavy work is invisible.
     */
    suspend fun warmupAll()
}
