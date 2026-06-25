package com.echoling.app.speech

import com.echoling.app.domain.model.ScoreResult
import com.echoling.app.domain.model.Sentence
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Grades pronunciation by comparing audio features against a reference.
 *
 * Stub — implementation pending.
 */
@Singleton
class PronunciationGrader @Inject constructor() {
    fun grade(sentence: Sentence, filePath: String): Result<ScoreResult> {
        return Result.success(ScoreResult(total = 0))
    }
}