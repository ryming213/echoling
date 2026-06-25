package com.echoling.app.domain.usecase

import com.echoling.app.domain.repository.SentenceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateSentenceCompletedUseCase @Inject constructor(
    private val sentenceRepository: SentenceRepository
) {
    suspend operator fun invoke(courseId: String, sentenceId: Int, isCompleted: Boolean) {
        sentenceRepository.updateCompletedStatus(courseId, sentenceId, isCompleted)
    }
}
