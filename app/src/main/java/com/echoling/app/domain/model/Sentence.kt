package com.echoling.app.domain.model

data class Sentence(
    val courseId: String,
    val sentenceId: Int,
    val contentEn: String,
    val contentCn: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val isLearned: Boolean = false,
    val isRead: Boolean = false,
    val readScore: Int = 0
)
