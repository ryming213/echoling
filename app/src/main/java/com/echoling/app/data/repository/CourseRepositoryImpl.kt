package com.echoling.app.data.repository

import com.echoling.app.data.local.db.dao.CourseDao
import com.echoling.app.data.local.db.entity.CourseEntity
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

    private fun CourseEntity.toDomain(): Course = Course(
        courseId = courseId,
        title = title,
        description = description,
        difficulty = difficulty,
        audioUri = audioUri,
        subtitleUri = subtitleUri,
        durationMs = durationMs,
        totalSentences = totalSentences,
        thumbnailUri = thumbnailUri,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    private fun Course.toEntity(): CourseEntity = CourseEntity(
        courseId = courseId,
        title = title,
        description = description,
        difficulty = difficulty,
        audioUri = audioUri,
        subtitleUri = subtitleUri,
        durationMs = durationMs,
        totalSentences = totalSentences,
        thumbnailUri = thumbnailUri,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}
