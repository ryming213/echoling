package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.DictEntry
import com.echoling.app.domain.repository.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns the entries for one vocabulary category by its slug
 * (e.g. "junior", "senior", "cet4", "cet6", "toefl"). Drives the
 * per-category flashcard study screen — the iteration sees the
 * **category-specific** translation (not the cross-category merge),
 * so studying 高中 shows the high-school-specific gloss even when CET-6
 * has a different one for the same word.
 *
 * Returns an empty list when the slug is unknown; the study screen
 * should render an empty-state message in that case rather than
 * crashing.
 */
@Singleton
class GetDictionaryWordsInCategoryUseCase @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
) {
    suspend operator fun invoke(categoryId: String): List<DictEntry> =
        dictionaryRepository.wordsInCategory(categoryId)
}
