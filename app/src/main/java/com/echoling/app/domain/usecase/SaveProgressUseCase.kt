package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.LearningProgress
import com.echoling.app.domain.repository.LearningProgressRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveProgressUseCase @Inject constructor(
    private val progressRepository: LearningProgressRepository
) {
    suspend operator fun invoke(progress: LearningProgress) {
        progressRepository.saveProgress(progress)
    }

    suspend fun getProgressByCourseId(courseId: String): LearningProgress? =
        progressRepository.getProgressByCourseId(courseId)
}
