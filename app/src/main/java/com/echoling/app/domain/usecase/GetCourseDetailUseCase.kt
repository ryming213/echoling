package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.Course
import com.echoling.app.domain.model.LearningProgress
import com.echoling.app.domain.repository.CourseRepository
import com.echoling.app.domain.repository.LearningProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

data class CourseDetail(
    val course: Course?,
    val progress: LearningProgress?
)

@Singleton
class GetCourseDetailUseCase @Inject constructor(
    private val courseRepository: CourseRepository,
    private val progressRepository: LearningProgressRepository
) {
    suspend operator fun invoke(courseId: String): CourseDetail {
        val course = courseRepository.getCourseById(courseId)
        val progress = progressRepository.getProgressByCourseId(courseId)
        return CourseDetail(course, progress)
    }
}
