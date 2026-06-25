package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.CourseGroup
import com.echoling.app.domain.model.effectiveCourseName
import com.echoling.app.domain.repository.CourseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Surfaces the course table as a list of [CourseGroup]s for the
 * Courses-tab home page. Courses are grouped by [effectiveCourseName]
 * (user-supplied [com.echoling.app.domain.model.Course.courseName] with
 * a fallback to [com.echoling.app.domain.model.Course.title] for
 * legacy rows). Groups are sorted alphabetically by their name so the
 * list is stable across re-emissions.
 */
class GetCourseGroupsUseCase @Inject constructor(
    private val courseRepository: CourseRepository,
) {
    operator fun invoke(): Flow<List<CourseGroup>> =
        courseRepository.getAllCourses().map { courses ->
            courses
                .groupBy { it.effectiveCourseName }
                .map { (name, list) -> CourseGroup(name, list) }
                .sortedBy { it.courseName }
        }
}
