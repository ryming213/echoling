package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.Course
import com.echoling.app.domain.repository.CourseRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetCoursesUseCase @Inject constructor(
    private val courseRepository: CourseRepository
) {
    operator fun invoke(): Flow<List<Course>> = courseRepository.getAllCourses()
}
