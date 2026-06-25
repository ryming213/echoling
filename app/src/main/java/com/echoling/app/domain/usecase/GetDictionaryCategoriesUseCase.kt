package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.DictCategory
import com.echoling.app.domain.repository.DictionaryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns the ordered list of bundled vocabulary categories (each with
 * its full entry list attached). Drives the category picker on the
 * "记单词" tab — the UI iterates the result to render one card per
 * category.
 *
 * Categories are returned in manifest-declaration order so the picker
 * shows 初中 → 高中 → CET-4 → CET-6 → TOEFL in the sequence the user
 * expects when scanning.
 */
@Singleton
class GetDictionaryCategoriesUseCase @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
) {
    suspend operator fun invoke(): List<DictCategory> = dictionaryRepository.categories()
}
