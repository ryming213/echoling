package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.DictEntry
import com.echoling.app.domain.repository.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local-first lookup against the bundled Gaokao 3500-word dictionary.
 *
 * Thin pass-through today, but kept as a use case (rather than calling
 * [DictionaryRepository] directly from `PracticeViewModel`) to:
 *
 *  - match the rest of the VM's use-case-based dependency style, and
 *  - keep a clean seam for future enhancements — suffix-stripping,
 *    fuzzy match, or frequency-based ranking would all live here.
 *
 * Returns `null` when the word is not in the bundled list; the caller
 * is expected to fall back to the network translation API in that case.
 */
@Singleton
class LookupWordUseCase @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
) {
    // TODO: add suffix-stripping / fuzzy match here when hit rate warrants it.
    suspend operator fun invoke(word: String): DictEntry? =
        dictionaryRepository.lookup(word)
}
