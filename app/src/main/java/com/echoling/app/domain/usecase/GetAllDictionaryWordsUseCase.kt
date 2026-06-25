package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.DictEntry
import com.echoling.app.domain.repository.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns every word in the bundled local dictionary as a stable,
 * alphabetically-sorted snapshot. Drives the flashcard iteration in the
 * Learning screen — the lookup flow ([LookupWordUseCase]) only ever
 * touches a single word, so this is the read path that needs an
 * explicit "give me the lot" entry point.
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
