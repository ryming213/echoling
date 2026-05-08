package com.echoling.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey
    val courseId: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val audioUri: String?,
    val videoUri: String?,
    val subtitleUri: String?,
    val durationMs: Long,
    val totalSentences: Int,
    val thumbnailUri: String?,
    val createdAt: Long,
    val updatedAt: Long
)
