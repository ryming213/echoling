package com.echoling.app.data.repository

import com.echoling.app.data.local.db.dao.CourseDao
import com.echoling.app.data.local.db.entity.CourseEntity
import com.echoling.app.domain.model.AutoSubtitleStatus
import com.echoling.app.domain.model.Course
import com.echoling.app.domain.repository.CourseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CourseRepositoryImpl @Inject constructor(
    private val courseDao: CourseDao
) : CourseRepository {

    override fun getAllCourses(): Flow<List<Course>> {
        return courseDao.getAllCourses().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCourseById(courseId: String): Course? {
        return courseDao.getCourseById(courseId)?.toDomain()
    }

    override suspend fun insertCourse(course: Course) {
        courseDao.insertCourse(course.toEntity())
    }

    override suspend fun deleteCourse(courseId: String) {
        courseDao.deleteCourseById(courseId)
    }

    override suspend fun markTranscriptionStarted(courseId: String) {
        courseDao.markTranscriptionStarted(courseId)
    }

    override suspend fun markTranscriptionCompleted(
        courseId: String,
        srtPath: String,
        totalSentences: Int,
    ) {
        courseDao.markTranscriptionCompleted(
            courseId = courseId,
            srtPath = srtPath,
            totalSentences = totalSentences,
            updatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun markTranscriptionFailed(courseId: String, errorMessage: String) {
        courseDao.markTranscriptionFailed(courseId, errorMessage)
    }

    override suspend fun updateTranscriptionProgress(courseId: String, progress: Int) {
        courseDao.updateAutoSubtitleProgress(courseId, progress)
    }

    private fun CourseEntity.toDomain(): Course = Course(
        courseId = courseId,
        courseName = courseName,
        title = title,
        description = description,
        difficulty = difficulty,
        audioUri = audioUri,
        videoUri = videoUri,
        subtitleUri = subtitleUri,
        durationMs = durationMs,
        totalSentences = totalSentences,
        thumbnailUri = thumbnailUri,
        createdAt = createdAt,
        updatedAt = updatedAt,
        autoSubtitleStatus = AutoSubtitleStatus.fromDbString(autoSubtitleStatus),
        autoSubtitleErrorMessage = autoSubtitleErrorMessage,
        autoSubtitleProgress = autoSubtitleProgress,
    )

    private fun Course.toEntity(): CourseEntity = CourseEntity(
        courseId = courseId,
        courseName = courseName,
        title = title,
        description = description,
        difficulty = difficulty,
        audioUri = audioUri,
        videoUri = videoUri,
        subtitleUri = subtitleUri,
        durationMs = durationMs,
        totalSentences = totalSentences,
        thumbnailUri = thumbnailUri,
        createdAt = createdAt,
        updatedAt = updatedAt,
        autoSubtitleStatus = autoSubtitleStatus?.dbValue,
        autoSubtitleErrorMessage = autoSubtitleErrorMessage,
        autoSubtitleProgress = autoSubtitleProgress,
    )
}
