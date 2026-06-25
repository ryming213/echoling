package com.echoling.app.domain.model

/**
 * Result of pronunciation grading via DTW on energy envelope.
 *
 * Stub — implementation pending.
 */
data class ScoreResult(
    val total: Int = 0,
    val accuracy: Int = 0,
    val fluency: Int = 0,
    val completeness: Int = 0,
    val prosody: Int = 0,
)