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
 * `exampleSentenceEn` / `exampleSentenceCn` carry the first example
 * sentence from the source JSON's `content.word.content.sentence.sentences[0]`
 * (English / Chinese pair). The English side may contain `<b>...</b>` tags
 * around the headword from `sContent_eng`; the UI decides whether to
 * render bold via `AnnotatedString` or strip the tags. Both default to
 * empty when the source has no example, so the flashcard back face can
 * use `isNotBlank()` to gate the sentence block.
 *
 * For the unified long-press lookup in the practice screen the same
 * `word` may surface with merged translations when the source data has
 * the word in multiple categories with different glosses — those are
 * joined with the "；" separator so the user sees all variants in one
 * place. Per-category flashcards iterate that category's own entries
 * (loaded on demand via
 * [com.echoling.app.domain.repository.DictionaryRepository.wordsInCategory])
 * so the user sees the category-specific translation when studying
 * that category.
 */
data class DictEntry(
    val word: String,
    val phonetic: String,
    val pos: String,
    val translation: String,
    val exampleSentenceEn: String = "",
    val exampleSentenceCn: String = "",
)

/**
 * A named vocabulary category.
 *
 * `id` is a stable slug used in nav routes and asset filenames (e.g.
 * "junior", "senior", "cet4", "cet6", "toefl"). `name` is the
 * user-visible Chinese label. `description` is a one-line subtitle
 * shown under the category card on the picker screen. `size` is the
 * post-prune entry count for the category — it's read straight from
 * `assets/vocab_manifest.json` so the picker UI can render "N 词"
 * without paying the cost of parsing the multi-MB entries files.
 *
 * **The entries list is no longer attached** (2026-07-04 lazy-load
 * refactor). Pre-refactor this data class carried `entries: List<DictEntry>`
 * which forced every caller of [com.echoling.app.domain.repository.DictionaryRepository.categories]
 * to pay for a full 12 MB parse of the bundled vocab JSONs before the
 * picker could render a single card. The entries are now fetched on
 * demand via
 * [com.echoling.app.domain.repository.DictionaryRepository.wordsInCategory]
 * — the picker only needs id / name / description / size and is
 * therefore fast on first tab open.
 *
 * `size` reflects the post-prune entry count (matches what the script
 * `scripts/build_vocab_assets.py` writes into the manifest after
 * running). At runtime the long-press / category-study paths apply an
 * additional `word.length > 1` filter that drops a handful of one-letter
 * tokens per category, so the actual study-screen count may be 1–3
 * lower than `size`; this is intentional and matches the historic
 * behavior the picker UI has always shown.
 */
data class DictCategory(
    val id: String,
    val name: String,
    val description: String,
    val size: Int,
)
