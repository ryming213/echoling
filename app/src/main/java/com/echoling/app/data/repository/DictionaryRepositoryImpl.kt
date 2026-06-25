package com.echoling.app.data.repository

import android.content.Context
import android.util.Log
import com.echoling.app.domain.model.DictCategory
import com.echoling.app.domain.model.DictEntry
import com.echoling.app.domain.repository.DictionaryRepository
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the bundled vocabulary manifest (`assets/vocab_manifest.json`)
 * and one entries-JSON per category on first use. The whole manifest +
 * entries set is loaded lazily on the first call to [lookup] or
 * [categories] (under a [Mutex] so concurrent first-callers don't
 * double-load) and then cached for the rest of the process lifetime.
 *
 * **Filtering**: entries with `word.trim().length <= 1` (e.g. "a", "I")
 * and entries with empty translations are dropped at load time. They
 * are never seen by callers — saves the practice screen from surfacing
 * one-letter "words" as translatable tokens and saves the flashcard
 * study screen from rendering them.
 *
 * **Cross-category merging**: when the same lowercased word is present
 * in two or more categories with **different** translations, the
 * [lookup] path returns a single merged entry whose `translation`
 * concatenates the distinct glosses with "；" so the user sees all
 * variants in one place (e.g. CET-6 "出现；浮现" + TOEFL "出现；暴露"
 * → "出现；浮现；出现；暴露"). Per-category entries are not merged —
 * they keep the category-specific translation so studying 高中 vs
 * CET-6 still shows each category's own gloss.
 */
@Singleton
class DictionaryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DictionaryRepository {

    @Volatile
    private var cache: DictCache? = null

    private val mutex = Mutex()
    private val gson = Gson()

    override suspend fun lookup(word: String): DictEntry? {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return null
        val c = ensureLoaded()
        val hit = c.lookup[key]
        Log.d(TAG, "lookup('$word') -> ${if (hit != null) "HIT" else "MISS"}")
        return hit
    }

    override suspend fun allWords(): List<DictEntry> {
        val c = ensureLoaded()
        return c.allMerged
    }

    override suspend fun categories(): List<DictCategory> {
        val c = ensureLoaded()
        return c.categories
    }

    override suspend fun wordsInCategory(categoryId: String): List<DictEntry> {
        val c = ensureLoaded()
        return c.byCategory[categoryId] ?: emptyList()
    }

    private suspend fun ensureLoaded(): DictCache =
        cache ?: withContext(Dispatchers.IO) {
            mutex.withLock {
                cache ?: loadFromAssets().also { cache = it }
            }
        }

    private fun loadFromAssets(): DictCache {
        // 1. Manifest — defines the category list and per-category asset.
        val manifest: Manifest = try {
            context.assets.open(MANIFEST_ASSET)
                .bufferedReader()
                .use { reader -> gson.fromJson(reader, Manifest::class.java) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $MANIFEST_ASSET — aborting load", e)
            return DictCache(
                categories = emptyList(),
                byCategory = emptyMap(),
                lookup = emptyMap(),
                allMerged = emptyList(),
            )
        }

        // 2. Per-category entries, parsed + filtered.
        //
        //    The bundled JSONs use **two different schemas** depending
        //    on their source:
        //
        //    A. Flat schema (the 高中 / gaokao_3500 list and any other
        //       hand-curated file) — top-level keys are
        //       `{word, phonetic, pos, translation}` and the entry maps
        //       directly to [DictEntry].
        //
        //    B. Nested schema (the 4 wordbook exports — 初中 / CET-4 /
        //       CET-6 / 托福) — 4 layers deep with arrays of objects:
        //       `wordRank → headWord → content.word.content.trans[]`.
        //       Trying to deserialize this into `Array<DictEntry>`
        //       produces nothing but empty entries and was the cause
        //       of the cold-start crash on the 记单词 tab.
        //
        //    We auto-detect by looking at the first non-whitespace
        //    character: a JSON array starting with `[{"word":` is
        //    schema A; `[{"wordRank":` is schema B. The detection is
        //    intentionally cheap (a 32-byte sniff) — we never want to
        //    load the whole multi-MB file just to peek at its shape.
        //    A failure in any single category is isolated so the
        //    other 4 still load.
        val categories = manifest.categories.map { meta ->
            val (filtered, rawCount) = try {
                // Sniff the first line to detect schema — 64 chars is
                // plenty to see either `[{"word":` or `[{"wordRank":`.
                // `readLine()` stops at the first `\n` so we never load
                // more than the first record's worth of bytes.
                val firstLine = context.assets.open(meta.asset)
                    .bufferedReader()
                    .use { it.readLine().orEmpty() }
                val isNested = firstLine.contains("\"wordRank\"")
                if (isNested) {
                    val rawNested: List<NestedWordEntry> = context.assets.open(meta.asset)
                        .bufferedReader()
                        .use { reader ->
                            gson.fromJson(reader, Array<NestedWordEntry>::class.java)
                                ?.toList()
                                .orEmpty()
                        }
                    rawNested.mapNotNull(::nestedToFlat).filter(::isUsable) to rawNested.size
                } else {
                    val rawFlat: List<DictEntry> = context.assets.open(meta.asset)
                        .bufferedReader()
                        .use { reader ->
                            gson.fromJson(reader, Array<DictEntry>::class.java)
                                ?.toList()
                                .orEmpty()
                        }
                    rawFlat.filter(::isUsable) to rawFlat.size
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse assets/${meta.asset} — category will be empty", e)
                emptyList<DictEntry>() to 0
            }
            Log.d(
                TAG,
                "Loaded ${filtered.size}/$rawCount entries from assets/${meta.asset}",
            )

            DictCategory(
                id = meta.id,
                name = meta.name,
                description = meta.description,
                entries = filtered,
            )
        }

        // 3. Per-category lookup map (for the study screen — keeps the
        //    category-specific translation, NOT the merged one).
        val byCategory = categories.associate { cat ->
            cat.id to cat.entries
        }

        // 4. Unified lookup map (for the practice screen) — merges
        //    entries that have the same lowercased word but different
        //    translations across categories. Same-word+same-translation
        //    duplicates collapse to one entry; same-word+different-
        //    translation produce a single merged-translation entry.
        val lookup = HashMap<String, DictEntry>(categories.sumOf { it.entries.size })
        for (cat in categories) {
            for (entry in cat.entries) {
                val key = entry.word.lowercase()
                val existing = lookup[key]
                lookup[key] = when {
                    existing == null -> entry
                    existing.translation == entry.translation -> existing
                    existing.translation.contains(entry.translation) -> existing
                    entry.translation.contains(existing.translation) -> entry
                    else -> existing.copy(
                        // Join with "；" — distinct glosses get
                        // deduped by Set so the same string doesn't
                        // appear twice in the merged result.
                        translation = (existing.translation.split("；") + entry.translation)
                            .toSet()
                            .joinToString("；"),
                    )
                }
            }
        }

        // 5. Flat alphabetical snapshot for [allWords] — driven by the
        //    merged lookup so legacy callers see the merged translations.
        val allMerged = lookup.values.sortedBy { it.word.lowercase() }
        Log.d(TAG, "Merged lookup: ${lookup.size} unique words across ${categories.size} categories")

        return DictCache(
            categories = categories,
            byCategory = byCategory,
            lookup = lookup,
            allMerged = allMerged,
        )
    }

    private fun isUsable(entry: DictEntry): Boolean {
        // Single-letter entries like "a", "I" are valid English tokens
        // but useless as flashcards and noisy in the long-press lookup
        // — filter them out. Empty translations are filtered too because
        // they would render an empty card face.
        val word = entry.word.trim()
        return word.length > 1 && entry.translation.isNotBlank()
    }

    /**
     * Convert the source schema's deeply-nested entry to a flat
     * [DictEntry]. Returns `null` for entries that can't be reasonably
     * filled (e.g. missing `headWord`, missing `trans[]`) so they get
     * dropped silently rather than producing empty cards.
     *
     * Field mapping:
     *  - `word`       ← `headWord` (top level, e.g. "cancel")
     *  - `phonetic`   ← `usphone` inside `content.word.content` (IPA)
     *  - `pos`        ← `trans[0].pos` (e.g. "v", "n")
     *  - `translation`← `trans[0].tranCn` (Chinese gloss)
     *
     * The positional arrays under `trans[]` / `sentence.sentences[]`
     * are themselves nullable on every intermediate step — Gson handles
     * absent branches without throwing.
     */
    private fun nestedToFlat(n: NestedWordEntry): DictEntry? {
        val word = n.headWord?.trim().orEmpty()
        if (word.isEmpty()) return null
        val wc = n.content?.word?.content
        val phonetic = wc?.usphone?.trim().orEmpty()
        val firstTrans = wc?.trans?.firstOrNull()
        val pos = firstTrans?.pos?.trim().orEmpty()
        // `tranCn` can have a leading space (e.g. " 取消，废除") — trim it.
        val translation = firstTrans?.tranCn?.trim().orEmpty()
        if (translation.isEmpty()) return null
        return DictEntry(
            word = word,
            phonetic = phonetic,
            pos = pos,
            translation = translation,
        )
    }

    private data class DictCache(
        val categories: List<DictCategory>,
        val byCategory: Map<String, List<DictEntry>>,
        val lookup: Map<String, DictEntry>,
        val allMerged: List<DictEntry>,
    )

    private data class Manifest(
        @SerializedName("categories") val categories: List<ManifestCategory>,
    )

    private data class ManifestCategory(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("asset") val asset: String,
        @SerializedName("description") val description: String,
    )

    /**
     * Mirrors the source JSON schema so Gson can deserialise without
     * reflective guesswork. Every intermediate is nullable because the
     * source files are inconsistent (some entries lack `syno`, some
     * lack `phrase`, etc) — better to skip than to crash.
     *
     * Real shape (abbreviated):
     * ```
     * {
     *   "wordRank": 1,
     *   "headWord": "cancel",
     *   "content": {
     *     "word": {
     *       "wordHead": "cancel",
     *       "content": {
     *         "usphone": "'kænsl",
     *         "ukphone": "'kænsl",
     *         "trans": [
     *           { "tranCn": " 取消，废除", "pos": "v", ... }
     *         ]
     *       }
     *     }
     *   }
     * }
     * ```
     */
    private data class NestedWordEntry(
        @SerializedName("wordRank") val wordRank: Int? = null,
        @SerializedName("headWord") val headWord: String? = null,
        @SerializedName("content") val content: NestedContent? = null,
    )
    private data class NestedContent(
        @SerializedName("word") val word: NestedWord? = null,
    )
    private data class NestedWord(
        @SerializedName("wordHead") val wordHead: String? = null,
        @SerializedName("content") val content: NestedWordContent? = null,
    )
    private data class NestedWordContent(
        @SerializedName("usphone") val usphone: String? = null,
        @SerializedName("ukphone") val ukphone: String? = null,
        @SerializedName("trans") val trans: List<NestedTrans>? = null,
    )
    private data class NestedTrans(
        @SerializedName("tranCn") val tranCn: String? = null,
        @SerializedName("pos") val pos: String? = null,
    )

    private companion object {
        const val TAG = "DictionaryRepository"
        const val MANIFEST_ASSET = "vocab_manifest.json"
    }
}
