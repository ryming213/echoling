package com.echoling.app.domain.usecase

import com.echoling.app.data.local.db.entity.ReciteProgressEntity
import com.echoling.app.data.local.db.dao.ReciteProgressDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Returns the persisted study progress for a single vocabulary
 * category (slug, e.g. "junior"). Returns `null` when the user has
 * never studied this category — callers should treat that as
 * "start from the first card" (`currentIndex = -1` and counts = 0).
 *
 * Used by [com.echoling.app.presentation.ui.screens.recite.CategoryStudyViewModel]
 * on `load(categoryId)` to seed its in-memory UI state so the user
 * resumes from where they left off after process death, app
 * cold-start, or tab switch.
 */
@Singleton
class GetReciteProgressUseCase @Inject constructor(
    private val dao: ReciteProgressDao,
) {
    suspend operator fun invoke(categoryId: String): ReciteProgressEntity? =
        dao.getByCategory(categoryId)
}
