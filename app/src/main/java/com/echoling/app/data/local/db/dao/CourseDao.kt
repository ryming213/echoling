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

    // (2026-07-18) Reactive single-row lookup. Emits on every UPDATE
    // to this row (status / progress / subtitleUri flips) so the
    // PracticeViewModel can re-resolve subtitle readiness after the
    // background worker flips status PENDING/IN_PROGRESS → READY
    // (the one-shot getCourseById suspend call only sees the row at
    // call time, so a worker completing AFTER loadCourse would never
    // unblock the SubtitleNotReadyView).
    @Query("SELECT * FROM courses WHERE courseId = :courseId")
    fun observeCourseById(courseId: String): Flow<CourseEntity?>

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

    // (2026-07-18) Was 'PENDING' — but PENDING semantically means
    // "user chose 稍后转字幕, transcription not started yet". Once
    // the worker actually starts running (called from
    // CourseDetailViewModel.retryAutoSubtitle AND from the worker's
    // own doWork entry on resume-after-process-death), the status
    // must flip to IN_PROGRESS so the UI can render the top progress
    // line (CourseListItem IN_PROGRESS branch — `autoStatus ==
    // AutoSubtitleStatus.IN_PROGRESS`).
    //
    // Before this fix the status stayed PENDING throughout the entire
    // run, the progress line never rendered, and the user saw the old
    // funnel chip indefinitely after manually returning from Import.
    // markTranscriptionCompleted / markTranscriptionFailed below
    // transition the terminal states (READY / FAILED) unchanged.
    @Query("UPDATE courses SET autoSubtitleStatus = 'IN_PROGRESS', autoSubtitleErrorMessage = NULL, autoSubtitleProgress = 0 WHERE courseId = :courseId")
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