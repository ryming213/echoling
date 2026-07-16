package com.echoling.app.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room row for [com.echoling.app.domain.model.Course]. The `courseName`
 * column was added in schema version 3 — see `Migrations.kt` /
 * `MIGRATION_2_3`. Default empty string preserves the migration path for
 * pre-existing rows; the app resolves the effective group name at read
 * time via `Course.effectiveCourseName`.
 */
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey
    val courseId: String,
    val courseName: String = "",
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
    val updatedAt: Long,
    // (2026-07-15) Auto-subtitle state (spec §5.1, migration 5→6).
    // autoSubtitleStatus uses AutoSubtitleStatus.dbValue; null = "user
    // provided a subtitle, no auto-subtitle needed".
    val autoSubtitleStatus: String? = null,
    val autoSubtitleErrorMessage: String? = null,
    val autoSubtitleProgress: Int = 0,
)
