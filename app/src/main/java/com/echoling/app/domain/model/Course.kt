package com.echoling.app.domain.model

data class Course(
    val courseId: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val audioUri: String,
    val subtitleUri: String?,
    val durationMs: Long,
    val totalSentences: Int,
    val thumbnailUri: String?,
    val createdAt: Long,
    val updatedAt: Long
)
