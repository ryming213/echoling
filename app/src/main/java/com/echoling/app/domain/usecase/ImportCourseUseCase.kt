package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.Course
import com.echoling.app.domain.repository.CourseRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportCourseUseCase @Inject constructor(
    private val courseRepository: CourseRepository
) {
    suspend operator fun invoke(course: Course) {
        courseRepository.insertCourse(course)
    }
}
