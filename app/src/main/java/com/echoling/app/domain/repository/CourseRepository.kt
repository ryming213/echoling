package com.echoling.app.domain.repository

import com.echoling.app.domain.model.Course
import kotlinx.coroutines.flow.Flow

interface CourseRepository {
    fun getAllCourses(): Flow<List<Course>>
    suspend fun getCourseById(courseId: String): Course?
    // (2026-07-18) Reactive single-row lookup — emits on every
    // UPDATE to this row. Used by PracticeViewModel to react to the
    // background worker flipping status PENDING/IN_PROGRESS → READY
    // after the screen has already entered the NotReady empty state.
    fun observeCourseById(courseId: String): Flow<Course?>
    suspend fun insertCourse(course: Course)
    suspend fun deleteCourse(courseId: String)

    // (2026-07-15) Auto-subtitle worker writes (spec §5.2).
    suspend fun markTranscriptionStarted(courseId: String)
    suspend fun markTranscriptionCompleted(
        courseId: String,
        srtPath: String,
        totalSentences: Int,
    )
    suspend fun markTranscriptionFailed(courseId: String, errorMessage: String)
    suspend fun updateTranscriptionProgress(courseId: String, progress: Int)
}