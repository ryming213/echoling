package com.echoling.app.domain.model

data class LearningProgress(
    val courseId: String,
    val currentPositionMs: Long,
    val currentSentenceId: Int,
    val learnedSentences: Int,
    val totalLearnTimeMs: Long,
    val lastLearnTime: Long,
    val finishRate: Float
)
