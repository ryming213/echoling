package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.DictEntry
import com.echoling.app.domain.repository.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns every word in the bundled local dictionary as a stable,
 * alphabetically-sorted snapshot. Drives the flashcard iteration in
 * the Recite screen. [LookupWordUseCase] is the single-word lookup
 * companion (used by the long-press translate flow in practice
 * pages); this is the "give me the lot" read path that
 * [CategoryStudyViewModel] needs to iterate flashcards.
 *
 * The repository's lazy load + HashMap cache means the first call
 * pays the one-time asset parse (tens of ms), and subsequent calls
 * (including on tab re-entry) are a no-op sort over an in-memory map.
 */
@Singleton
class GetAllDictionaryWordsUseCase @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
) {
    suspend operator fun invoke(): List<DictEntry> = dictionaryRepository.allWords()
}
