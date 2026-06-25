package com.echoling.app.domain.usecase

import com.echoling.app.data.local.db.dao.ReciteProgressDao
import com.echoling.app.data.local.db.entity.ReciteProgressEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Upserts the per-category flashcard progress row. Called by
 * [com.echoling.app.presentation.ui.screens.recite.CategoryStudyViewModel]
 * after every `markKnown` / `markUnknown` / `saveCurrentToVocabulary`
 * and on `resetSession` (to clear the counters back to 0).
 *
 * Insert strategy is `REPLACE` (see [ReciteProgressDao.upsert]) so
 * callers don't need a separate "exists?" check — writing once per
 * action is enough.
 */
@Singleton
class SaveReciteProgressUseCase @Inject constructor(
    private val dao: ReciteProgressDao,
) {
    suspend operator fun invoke(entity: ReciteProgressEntity) {
        dao.upsert(entity)
    }
}
