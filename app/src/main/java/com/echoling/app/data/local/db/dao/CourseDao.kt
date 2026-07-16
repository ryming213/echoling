package com.echoling.app.data.local.db.dao

import androidx.room.*
import com.echoling.app.data.local.db.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses ORDER BY createdAt DESC")
    fun getAllCourses(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE courseId = :courseId")
    suspend fun getCourseById(courseId: String): CourseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: CourseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Delete
    suspend fun deleteCourse(course: CourseEntity)

    @Query("DELETE FROM courses WHERE courseId = :courseId")
    suspend fun deleteCourseById(courseId: String)

    // (2026-07-15) Auto-subtitle worker state writes (spec §5.2).
    // Narrow @Query UPDATEs instead of a full entity replace — workers
    // only mutate one column at a time and this keeps the SQL explicit.

    @Query("UPDATE courses SET autoSubtitleStatus = :status WHERE courseId = :courseId")
    suspend fun updateAutoSubtitleStatus(courseId: String, status: String?)

    @Query("UPDATE courses SET autoSubtitleProgress = :progress WHERE courseId = :courseId")
    suspend fun updateAutoSubtitleProgress(courseId: String, progress: Int)

    @Query("UPDATE courses SET autoSubtitleStatus = 'PENDING', autoSubtitleErrorMessage = NULL, autoSubtitleProgress = 0 WHERE courseId = :courseId")
    suspend fun markTranscriptionStarted(courseId: String)

    @Query("UPDATE courses SET subtitleUri = :srtPath, totalSentences = :totalSentences, autoSubtitleStatus = 'READY', autoSubtitleErrorMessage = NULL, autoSubtitleProgress = 100, updatedAt = :updatedAt WHERE courseId = :courseId")
    suspend fun markTranscriptionCompleted(
        courseId: String,
        srtPath: String,
        totalSentences: Int,
        updatedAt: Long,
    )

    @Query("UPDATE courses SET autoSubtitleStatus = 'FAILED', autoSubtitleErrorMessage = :errorMessage WHERE courseId = :courseId")
    suspend fun markTranscriptionFailed(courseId: String, errorMessage: String)
}