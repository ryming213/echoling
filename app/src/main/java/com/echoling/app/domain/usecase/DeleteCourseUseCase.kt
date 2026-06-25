package com.echoling.app.domain.usecase

import com.echoling.app.domain.repository.CourseRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteCourseUseCase @Inject constructor(
    private val courseRepository: CourseRepository
) {
    suspend operator fun invoke(courseId: String) {
        courseRepository.deleteCourse(courseId)
    }
}
