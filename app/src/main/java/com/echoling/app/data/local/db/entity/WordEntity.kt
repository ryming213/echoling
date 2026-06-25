package com.echoling.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "words")
data class WordEntity(
    @PrimaryKey
    val word: String,
    val phonetic: String,
    val pos: String,
    val translation: String,
    val exampleSentence: String,
    val sourceCourseId: String,
    val sourceSentenceId: Int,
    val isMastered: Boolean = false,
    val collectedAt: Long,
    val reviewCount: Int = 0,
    val nextReviewTime: Long,
)
