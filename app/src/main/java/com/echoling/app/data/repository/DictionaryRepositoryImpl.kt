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
 * and one entries-JSON per category on first use.
 *
 * **Two-phase lazy loading** (2026-07-04 refactor — replaced the
 * monolithic first-call-load to fix the slow first-open of the
 * 记单词 tab):
 *
 *  - **Phase 1 — manifest only**: [categories] parses the ~1.3 KB
 *    `vocab_manifest.json` and returns the lightweight
 *    `List<DictCategory>` with the `size` field populated from the
 *    manifest. The picker UI on the 记单词 tab uses this to render
 *    all 11 category cards without ever opening an entries file.
 *
 *  - **Phase 2 — entries (per-category)**: [wordsInCategory] lazily
 *    loads the requested category's JSON only, schema-sniffing on the
 *    first line so we never re-read the whole file. Each category is
 *    parsed at most once and cached in [entriesByCategory].
 *
 *  - **Phase 3 — full lookup (cross-category merge)**: [lookup] (and
 *    [allWords]) need every category's entries merged into one
 *    lowercased `HashMap`. The first call pays the 12 MB parse cost;
 *    subsequent calls are O(1). The full lookup is built by
 *    [buildLookup] which also fills [entriesByCategory] as a side
 *    effect, so navigating into a category after Phase 3 has run is
 *    instant.
 *
 *  - **Warmup**: [warmupAll] is called from `ReciteViewModel` right
 *    after Phase 1 completes. It kicks off Phase 3 on a background
 *    coroutine so the user, who is currently reading the picker, has
 *    the lookup pre-built by the time they long-press a word in the
 *    practice screen or tap into a category.
 *
 * **Filtering**: entries with `word.trim().length <= 1` (e.g. "a", "I")
 * and entries with empty translations are dropped at load time. They
 * are never seen by callers — saves the practice screen from surfacing
 * one-letter "words" as translatable tokens and saves the flashcard
 * study screen from rendering them. Note: the manifest's `size` field
 * reflects the **post-prune** count from `scripts/build_vocab_assets.py`,
 * which doesn't apply the `word.length > 1` filter, so the picker
 * chip is at most 1–3 entries higher than the actual study screen
 * count for any given category. This matches the historic behavior.
 *
 * **Cross-category merging**: when the same lowercased word is present
 * in two or more categories with **different** translations, the
 * [lookup] path returns a single merged entry whose `translation`
 * concatenates the distinct glosses with "；" so the user sees all
 * variants in one place (e.g. CET-6 "出现；浮现" + TOEFL "浮现；显露"
 * → "出现；浮现；显露" — "浮现" is deduped at the fragment level, not
 * just whole-string level; see [joinDistinctGlosses]). Per-category
 * entries are not merged across categories — they keep the
 * category-specific translation so studying 高中 vs CET-6 still shows
 * each category's own gloss.
 *
 * **Within-category merging**: a single source record can list multiple
 * POS groups in `content.word.content.trans[]` (e.g. "concordance"
 * has both an "n." gloss and a "vt." gloss). Each gloss is concatenated
 * into the entry's `translation` with "；" — the same separator used by
 * the cross-category merge — so the flashcard back face shows every
 * POS's translation instead of just the first one.
 *
 * **Concurrency**: three separate `Mutex`es (one per cache) so the
 * phases don't block each other. Phase 1 (`categories`) is independent
 * of Phase 2 (`wordsInCategory`) and Phase 3 (`lookup`); they only
 * interact via [entriesByCategory] which is itself mutex-guarded.
 * Lock acquisition order is always `manifestMutex` → `entriesMutex`
 * → `lookupMutex` (the last is only acquired from [ensureLookupLoaded]),
 * so there are no deadlock cycles.
 */
@Singleton
class DictionaryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : DictionaryRepository {

    // Phase 1 cache — manifest-derived lightweight list. Populated by
    // [loadManifest] on first call to [categories]. Never invalidated.
    @Volatile
    private var categoriesManifest: List<DictCategory>? = null

    // Parallel map from category id → entries-asset filename. Kept
    // private because [DictCategory] deliberately doesn't expose
    // `asset` to the domain layer (the picker UI only needs id / name
    // / description / size). Used by [wordsInCategory] and
    // [buildLookup] to locate the JSON without re-reading the manifest.
    @Volatile
    private var assetByCategory: Map<String, String>? = null

    // Phase 2 cache — per-category entries. Populated lazily by
    // [wordsInCategory] (one category at a time) or wholesale by
    // [buildLookup] when Phase 3 needs all of them.
    @Volatile
    private var entriesByCategory: Map<String, List<DictEntry>>? = null

    // Phase 3 cache — merged lowercased-key map for [lookup]. Built
    // once by [buildLookup]; the [allWords] snapshot is derived from it.
    @Volatile
    private var lookup: Map<String, DictEntry>? = null
    @Volatile
    private var allMerged: List<DictEntry>? = null

    private val manifestMutex = Mutex()
    private val entriesMutex = Mutex()
    private val lookupMutex = Mutex()
    private val gson = Gson()

    override suspend fun lookup(word: String): DictEntry? {
        val key = word.trim().lowercase()
        if (key.isEmpty()) return null
        val map = ensureLookupLoaded()
        val hit = map[key]
        Log.d(TAG, "lookup('$word') -> ${if (hit != null) "HIT" else "MISS"}")
        return hit
    }

    override suspend fun allWords(): List<DictEntry> {
        ensureLookupLoaded()
        return allMerged ?: emptyList()
    }

    override suspend fun categories(): List<DictCategory> {
        val cached = categoriesManifest
        if (cached != null) return cached
        return withContext(Dispatchers.IO) {
            manifestMutex.withLock {
                categoriesManifest ?: loadManifest()
                    .also { (cats, assets) ->
                        categoriesManifest = cats
                        assetByCategory = assets
                    }
                    .first
            }
        }
    }

    override suspend fun wordsInCategory(categoryId: String): List<DictEntry> {
        // Fast path — Phase 3 already populated `entriesByCategory`.
        // After a warmup this is the common case (user reads the
        // picker, warmup builds the full map, user taps a card).
        entriesByCategory?.let { return it[categoryId] ?: emptyList() }
        // Phase 1 must run first so we have the asset filename; this
        // is also a no-op after the first call because `categories`
        // returns from the cached `categoriesManifest` field.
        val assetMap = assetByCategory ?: run {
            categories()
            assetByCategory ?: return emptyList()
        }
        val asset = assetMap[categoryId] ?: return emptyList()
        // Slow path — load just this one category and cache it. If
        // Phase 3 kicks off in parallel it will see our entry and
        // only fill in the missing categories, not re-read this one.
        return withContext(Dispatchers.IO) {
            entriesMutex.withLock {
                val current = entriesByCategory
                if (current != null) {
                    current[categoryId] ?: emptyList()
                } else {
                    val entries = loadCategoryEntries(asset)
                    entriesByCategory = mapOf(categoryId to entries)
                    Log.d(TAG, "Loaded ${entries.size} entries from assets/$asset")
                    entries
                }
            }
        }
    }

    /**
     * Pre-warm the full lookup map in the background. Called by
     * [com.echoling.app.presentation.ui.screens.recite.ReciteViewModel]
     * right after Phase 1's `categories()` returns — the user is
     * staring at the picker reading "N 词" chips, so the heavy work
     * is invisible. By the time they tap into a category (or long-
     * press a word in the practice screen), [lookup] /
     * [wordsInCategory] usually hit the cache and return instantly.
     *
     * Idempotent: a second call while the first is still in flight
     * will block on [lookupMutex] (the inner `cache ?: buildLookup()`
     * pattern) until the in-flight build finishes, then return the
     * populated map. Safe to call from any coroutine context.
     */
    override suspend fun warmupAll() {
        ensureLookupLoaded()
    }

    private suspend fun ensureLookupLoaded(): Map<String, DictEntry> {
        val cached = lookup
        if (cached != null) return cached
        return withContext(Dispatchers.IO) {
            lookupMutex.withLock {
                lookup ?: buildLookup().also { map ->
                    lookup = map
                    allMerged = map.values.sortedBy { it.word.lowercase() }
                }
            }
        }
    }

    private fun loadManifest(): Pair<List<DictCategory>, Map<String, String>> {
        val manifest: Manifest = try {
            context.assets.open(MANIFEST_ASSET)
                .bufferedReader()
                .use { reader -> gson.fromJson(reader, Manifest::class.java) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse $MANIFEST_ASSET — aborting load", e)
            return emptyList<DictCategory>() to emptyMap()
        }
        val categories = manifest.categories.map { meta ->
            DictCategory(
                id = meta.id,
                name = meta.name,
                description = meta.description,
                size = meta.size,
            )
        }
        val assets = manifest.categories.associate { it.id to it.asset }
        return categories to assets
    }

    /**
     * Parse every category's entries JSON, build the merged lookup
     * map, and as a side effect publish the per-category cache so
     * [wordsInCategory] can short-circuit once this returns.
     *
     * If [entriesByCategory] is already partially populated (e.g.
     * the user opened a category before [warmupAll] finished), we
     * load only the missing categories — never re-parses a file
     * that's already in the cache.
     */
    private suspend fun buildLookup(): Map<String, DictEntry> {
        // Phase 1 may not have run yet — `categories()` is idempotent
        // and populates both `categoriesManifest` and `assetByCategory`.
        val assetMap = assetByCategory ?: run {
            categories()
            assetByCategory ?: return emptyMap()
        }
        val byCat: Map<String, List<DictEntry>> = entriesMutex.withLock {
            val current = entriesByCategory ?: emptyMap()
            val missingIds = assetMap.keys.filter { it !in current }
            if (missingIds.isEmpty()) {
                current
            } else {
                val merged = HashMap(current)
                for (id in missingIds) {
                    val asset = assetMap[id] ?: continue
                    val entries = loadCategoryEntries(asset)
                    Log.d(TAG, "Loaded ${entries.size} entries from assets/$asset")
                    merged[id] = entries
                }
                entriesByCategory = merged
                merged
            }
        }

        val map = HashMap<String, DictEntry>(byCat.values.sumOf { it.size })
        for ((_, entries) in byCat) {
            for (entry in entries) {
                val key = entry.word.lowercase()
                val existing = map[key]
                map[key] = when {
                    existing == null -> entry
                    existing.translation == entry.translation -> existing
                    existing.translation.contains(entry.translation) -> existing
                    entry.translation.contains(existing.translation) -> entry
                    else -> existing.copy(
                        // Merge with "；" — [joinDistinctGlosses] splits
                        // BOTH sides on "；" before the Set dedup so a
                        // fragment inside one source's merged translation
                        // that also appears as a standalone fragment in
                        // the other source is collapsed (e.g.
                        // "出现；浮现" + "浮现；显露" → "出现；浮现；显露",
                        // not "出现；浮现；浮现；显露").
                        translation = joinDistinctGlosses(
                            listOf(existing.translation, entry.translation)
                        ),
                    )
                }
            }
        }
        Log.d(TAG, "Merged lookup: ${map.size} unique words across ${byCat.size} categories")
        return map
    }

    /**
     * Parse one category's entries JSON. Auto-detects schema (flat vs
     * nested 4-layer) by sniffing the first line — 64 chars is plenty
     * to see either `[{"word":` or `[{"wordRank":`. `readLine()` stops
     * at the first `\n` so we never load more than the first record's
     * worth of bytes just to peek at the shape.
     *
     * Returns an empty list on parse failure (the other categories
     * still load — partial failure is better than crashing the whole
     * lookup).
     */
    private fun loadCategoryEntries(asset: String): List<DictEntry> = try {
        val firstLine = context.assets.open(asset)
            .bufferedReader()
            .use { it.readLine().orEmpty() }
        val isNested = firstLine.contains("\"wordRank\"")
        if (isNested) {
            val rawNested: List<NestedWordEntry> = context.assets.open(asset)
                .bufferedReader()
                .use { reader ->
                    gson.fromJson(reader, Array<NestedWordEntry>::class.java)
                        ?.toList()
                        .orEmpty()
                }
            rawNested.mapNotNull(::nestedToFlat).filter(::isUsable)
        } else {
            val rawFlat: List<DictEntry> = context.assets.open(asset)
                .bufferedReader()
                .use { reader ->
                    gson.fromJson(reader, Array<DictEntry>::class.java)
                        ?.toList()
                        .orEmpty()
                }
            rawFlat.filter(::isUsable)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to parse assets/$asset — category will be empty", e)
        emptyList()
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
     *  - `word`              ← `headWord` (top level, e.g. "cancel")
     *  - `phonetic`          ← `usphone` inside `content.word.content` (IPA)
     *  - `pos`               ← `trans[0].pos` (e.g. "v", "n") — the lead POS,
     *                          because the flashcard back face shows one chip.
     *  - `translation`       ← ALL `trans[*].tranCn` joined with "；", deduped
     *                          and trimmed (matching the cross-category merge
     *                          convention used in [lookup]).
     *  - `exampleSentenceEn` ← `sentence.sentences[0].sContent_eng` (falls back
     *                          to `sContent` if the headword-highlighted form
     *                          is missing). May contain `<b>...</b>` tags —
     *                          kept verbatim, UI decides whether to render.
     *  - `exampleSentenceCn` ← `sentence.sentences[0].sCn`
     *
     * Why join all glosses: a single source record can list multiple
     * POS groups, e.g. "concordance" has both
     *   { pos: "n.",   tranCn: "索引；用语索引；对照…" }
     *   { pos: "vt.",  tranCn: "编纂索引；使协调" }
     * The flashcard back face previously showed only the first gloss,
     * which users read as "translation content is missing". Joining
     * everything via "；" mirrors the same convention the long-press
     * lookup uses when the same word appears in multiple categories —
     * so a flashcard that says "索引；用语索引；对照表；编纂索引；使协调"
     * and a long-press dialog that says "索引；用语索引；…；编纂索引；使协调"
     * read identically. Distinct glosses survive; duplicates are dropped.
     *
     * Only the **first** example sentence is kept (most words have a
     * single example in the source corpus; multi-sentence entries exist
     * but surfacing the 2nd-Nth on a small flashcard crowds the back
     * face). `isUsable` doesn't filter on sentence presence — a word
     * without an example is still a valid flashcard, the UI just skips
     * the sentence block.
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
        val transList = wc?.trans.orEmpty()
        // `tranCn` can have a leading space (e.g. " 取消，废除") —
        // trim each gloss and drop blanks. Dedupe (including
        // fragment-level dedupe across "；"-bearing source glosses) is
        // delegated to [joinDistinctGlosses] — same helper the
        // cross-category merge uses, so the dedup convention stays
        // identical between the two sites.
        val glosses = transList
            .mapNotNull { it.tranCn?.trim()?.takeIf { s -> s.isNotEmpty() } }
        if (glosses.isEmpty()) return null
        val translation = joinDistinctGlosses(glosses)
        // POS: keep the lead POS for the on-card chip. In practice the
        // lead POS is the most common one and the chip already
        // duplicates the front-of-translation text for many entries
        // (e.g. "n. 索引；…" renders the "n." chip redundantly), but
        // users have come to expect a POS tag on the back face so we
        // keep the slot stable.
        val pos = transList.firstOrNull()?.pos?.trim().orEmpty()
        // Example sentence: take the first item from `sentence.sentences[]`,
        // preferring `sContent_eng` (with `<b>...</b>` around the headword)
        // and falling back to `sContent` (plain English). Both are nullable
        // and may be blank — the flashcard back face gates on
        // `isNotBlank()` so an absent example is silently skipped.
        val firstSent = wc?.sentence?.sentences?.firstOrNull()
        val exampleEn = (firstSent?.sContentEng?.trim()?.takeIf { it.isNotEmpty() }
                ?: firstSent?.sContent?.trim()?.takeIf { it.isNotEmpty() }
                ?: "").orEmpty()
        val exampleCn = firstSent?.sCn?.trim().orEmpty()
        return DictEntry(
            word = word,
            phonetic = phonetic,
            pos = pos,
            translation = translation,
            exampleSentenceEn = exampleEn,
            exampleSentenceCn = exampleCn,
        )
    }

    private data class Manifest(
        @SerializedName("categories") val categories: List<ManifestCategory>,
    )

    private data class ManifestCategory(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("asset") val asset: String,
        @SerializedName("description") val description: String,
        // Post-prune entry count written by `scripts/build_vocab_assets.py`
        // (see its `write_manifest`). Defaults to 0 for back-compat with
        // pre-2026-07-04 manifests that predate this field — the picker
        // UI then renders "0 词" until the script is rerun, which is
        // a better signal than silently parsing the entries file.
        @SerializedName("size") val size: Int = 0,
    )

    /**
     * Split each source on "；" (the convention used by both the
     * within-category [nestedToFlat] join and the cross-category
     * [lookup] merge), trim and dedup, then rejoin with "；". This is
     * the single point where fragment-level Chinese deduplication
     * happens — callers must not concatenate translations with their
     * own join+dedup logic, or they will recreate the asymmetry that
     * previously caused duplicate gloss fragments when two sources each
     * contained overlapping fragments (e.g. "出现；浮现" + "浮现；显露"
     * would naively produce "出现；浮现；浮现；显露" if only the first
     * side were split before the Set dedup).
     *
     * The source corpus itself uses "；" as a within-POS sub-separator
     * (5746 individual `tranCn` strings in vocab_cet4.json contain "；"),
     * so fragment-level dedup is required, not optional.
     */
    private fun joinDistinctGlosses(sources: Collection<String>): String =
        sources
            .flatMap { it.split("；") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
            .joinToString("；")

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
        @SerializedName("sentence") val sentence: NestedSentence? = null,
    )
    private data class NestedTrans(
        @SerializedName("tranCn") val tranCn: String? = null,
        @SerializedName("pos") val pos: String? = null,
    )
    // Example sentence block under `content.word.content.sentence`.
    // `sentences` is the array of sentence items; `desc` is a fixed
    // label string ("例句") the runtime doesn't read. Items have three
    // nullable fields — `sContentEng` carries `<b>...</b>` highlighting
    // around the headword, `sContent` is the plain English fallback,
    // and `sCn` is the Chinese gloss. All three may be missing on a
    // given source entry; the runtime takes whichever is non-blank.
    private data class NestedSentence(
        @SerializedName("sentences") val sentences: List<NestedSentenceItem>? = null,
        @SerializedName("desc") val desc: String? = null,
    )
    private data class NestedSentenceItem(
        @SerializedName("sContent") val sContent: String? = null,
        @SerializedName("sContent_eng") val sContentEng: String? = null,
        @SerializedName("sCn") val sCn: String? = null,
    )

    private companion object {
        const val TAG = "DictionaryRepository"
        const val MANIFEST_ASSET = "vocab_manifest.json"
    }
}
