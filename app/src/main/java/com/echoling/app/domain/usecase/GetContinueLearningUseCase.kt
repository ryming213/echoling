package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.Course
import com.echoling.app.domain.model.LearningProgress
import com.echoling.app.domain.repository.CourseRepository
import com.echoling.app.domain.repository.LearningProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Pair of a course and its in-progress learning state. */
data class ContinueLearningItem(
    val course: Course,
    val progress: LearningProgress,
)

/**
 * Reactive stream of the most recently touched course that has any progress.
 * Returns null when no progress has been recorded yet.
 */
@Singleton
class GetContinueLearningUseCase @Inject constructor(
    private val courseRepository: CourseRepository,
    private val progressRepository: LearningProgressRepository,
) {
    operator fun invoke(): Flow<ContinueLearningItem?> =
        combine(
            courseRepository.getAllCourses(),
            progressRepository.getAllProgress(),
        ) { courses, progressList ->
            val progressMap = progressList.associateBy { it.courseId }
            courses
                .mapNotNull { course ->
                    progressMap[course.courseId]?.let { progress ->
                        ContinueLearningItem(course, progress)
                    }
                }
                .maxByOrNull { it.progress.lastLearnTime }
        }
}