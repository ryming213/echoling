package com.echoling.app.domain.model

/**
 * Lifecycle of an auto-generated subtitle for a course. Mirrors the
 * three nullable columns on CourseEntity (see spec §5.1).
 *
 * null means "user provided a subtitle file, no auto-subtitle needed".
 * Stored as the all-caps [dbValue] string in the SQLite column.
 */
enum class AutoSubtitleStatus(val dbValue: String) {
    PENDING("PENDING"),
    IN_PROGRESS("IN_PROGRESS"),
    READY("READY"),
    FAILED("FAILED");

    val hasAutoSubtitleIssue: Boolean
        get() = this != READY

    companion object {
        fun fromDbString(value: String?): AutoSubtitleStatus? {
            if (value == null) return null
            return entries.firstOrNull { it.dbValue == value }
        }
    }
}