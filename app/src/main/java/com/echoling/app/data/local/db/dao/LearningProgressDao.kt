package com.echoling.app.data.local.db.dao

import androidx.room.*
import com.echoling.app.data.local.db.entity.LearningProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningProgressDao {
    @Query("SELECT * FROM learning_progress WHERE courseId = :courseId")
    suspend fun getProgressByCourseId(courseId: String): LearningProgressEntity?

    @Query("SELECT * FROM learning_progress ORDER BY lastLearnTime DESC")
    fun getAllProgress(): Flow<List<LearningProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateProgress(progress: LearningProgressEntity)

    @Delete
    suspend fun deleteProgress(progress: LearningProgressEntity)

    @Query("DELETE FROM learning_progress WHERE courseId = :courseId")
    suspend fun deleteProgressByCourseId(courseId: String)
}
