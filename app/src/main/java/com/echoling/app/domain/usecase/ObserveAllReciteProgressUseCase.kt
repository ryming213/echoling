package com.echoling.app.domain.usecase

import com.echoling.app.data.local.db.dao.ReciteProgressDao
import com.echoling.app.data.local.db.entity.ReciteProgressEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Streams every per-category progress row. Drives the "记单词" tab
 * category picker — the [com.echoling.app.presentation.ui.screens.recite.ReciteViewModel]
 * combines this with the bundled category list to render the
 * "已学 X / 1801 词" sub-label and the "上次学习于…" line on each
 * card. The flow re-emits whenever a study action persists a new
 * row, so the picker updates instantly as the user finishes a card
 * on a sub-page.
 */
@Singleton
class ObserveAllReciteProgressUseCase @Inject constructor(
    private val dao: ReciteProgressDao,
) {
    operator fun invoke(): Flow<List<ReciteProgressEntity>> = dao.observeAll()
}
