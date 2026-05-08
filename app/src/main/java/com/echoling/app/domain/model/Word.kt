package com.echoling.app.domain.model

data class Word(
    val word: String,
    val phonetic: String,
    val translation: String,
    val exampleSentence: String,
    val sourceCourseId: String,
    val sourceSentenceId: Int,
    val isMastered: Boolean = false,
    val collectedAt: Long,
    val reviewCount: Int = 0,
    val nextReviewTime: Long
)
