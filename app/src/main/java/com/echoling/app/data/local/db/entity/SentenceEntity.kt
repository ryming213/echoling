package com.echoling.app.data.local.db.entity

import androidx.room.Entity

@Entity(
    tableName = "sentences",
    primaryKeys = ["courseId", "sentenceId"]
)
data class SentenceEntity(
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
