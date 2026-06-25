package com.echoling.app.domain.usecase

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Updates the read score for a sentence after pronunciation grading.
 *
 * Stub — implementation pending.
 */
@Singleton
class UpdateSentenceReadScoreUseCase @Inject constructor() {
    suspend operator fun invoke(courseId: String, sentenceId: Int, score: Int) {
        // Stub — implementation pending
    }
}