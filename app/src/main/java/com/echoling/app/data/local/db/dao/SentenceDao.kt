package com.echoling.app.data.local.db.dao

import androidx.room.*
import com.echoling.app.data.local.db.entity.SentenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SentenceDao {
    @Query("SELECT * FROM sentences WHERE courseId = :courseId ORDER BY sentenceId ASC")
    fun getSentencesByCourseId(courseId: String): Flow<List<SentenceEntity>>

    @Query("SELECT * FROM sentences WHERE courseId = :courseId ORDER BY sentenceId ASC")
    suspend fun getSentencesByCourseIdSync(courseId: String): List<SentenceEntity>

    @Query("SELECT * FROM sentences WHERE courseId = :courseId AND sentenceId = :sentenceId")
    suspend fun getSentence(courseId: String, sentenceId: Int): SentenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSentences(sentences: List<SentenceEntity>)

    @Update
    suspend fun updateSentence(sentence: SentenceEntity)

    @Query("DELETE FROM sentences WHERE courseId = :courseId")
    suspend fun deleteSentencesByCourseId(courseId: String)
}
