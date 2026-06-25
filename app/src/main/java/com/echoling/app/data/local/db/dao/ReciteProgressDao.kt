package com.echoling.app.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.echoling.app.data.local.db.entity.ReciteProgressEntity
import kotlinx.coroutines.flow.Flow

/**
 * Persistence boundary for the "记单词" flashcard progress.
 *
 * Operations are split into two surfaces:
 *  - [observeAll] — `Flow` that the [ReciteScreen] category picker
 *    collects to render per-card sub-labels (known/unknown counts and
 *    "上次学习于…"). One snapshot per row, so the picker refreshes
 *    instantly when the user finishes a card on a study sub-page.
 *  - [getByCategory] / [upsert] — suspend functions used by
 *    [CategoryStudyViewModel] to (a) seed its in-memory state on
 *    entry and (b) persist every `markKnown` / `markUnknown` /
 *    `saveCurrentToVocabulary` action.
 *
 * Conflict strategy on [upsert] is `REPLACE` so the same row can be
 * re-written with monotonically increasing `currentIndex` /
 * `knownCount` / `unknownCount` / `lastStudiedAt` without first
 * checking existence — keeps the VM mutation path single-call.
 */
@Dao
interface ReciteProgressDao {

    @Query("SELECT * FROM recite_progress")
    fun observeAll(): Flow<List<ReciteProgressEntity>>

    @Query("SELECT * FROM recite_progress WHERE category_id = :categoryId LIMIT 1")
    suspend fun getByCategory(categoryId: String): ReciteProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ReciteProgressEntity)
}
