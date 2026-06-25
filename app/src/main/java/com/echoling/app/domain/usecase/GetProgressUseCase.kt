package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.LearningProgress
import com.echoling.app.domain.repository.LearningProgressRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetProgressUseCase @Inject constructor(
    private val progressRepository: LearningProgressRepository
) {
    fun getAllProgress(): Flow<List<LearningProgress>> = progressRepository.getAllProgress()

    suspend fun getProgressByCourseId(courseId: String): LearningProgress? =
        progressRepository.getProgressByCourseId(courseId)
}
