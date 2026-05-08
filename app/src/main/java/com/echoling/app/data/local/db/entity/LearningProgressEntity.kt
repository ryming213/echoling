package com.echoling.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "learning_progress")
data class LearningProgressEntity(
    @PrimaryKey
    val courseId: String,
    val currentPositionMs: Long,
    val currentSentenceId: Int,
    val learnedSentences: Int,
    val totalLearnTimeMs: Long,
    val lastLearnTime: Long,
    val finishRate: Float
)
