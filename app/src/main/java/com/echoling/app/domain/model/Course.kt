package com.echoling.app.domain.model

/**
 * A learning course — a single lesson that the user can drill into
 * (Practice flow). Courses are organized into parent groups by
 * [courseName] (e.g. "新概念英语第一册"). See `effectiveCourseName` for
 * the resolution rule that keeps legacy data working without a
 * destructive migration.
 */
data class Course(
    val courseId: String,
    /**
     * Parent group name (folder). Empty string means "no group assigned".
     * New imports require the user to fill this in; old data rows from
     * before the column existed have it set to "" and resolve to [title]
     * via [effectiveCourseName].
     */
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
    val updatedAt: Long
) {
    fun hasVideoContent(): Boolean = !videoUri.isNullOrBlank()
    fun hasAudioContent(): Boolean = !audioUri.isNullOrBlank()
}

/**
 * The group this course belongs to. Uses [Course.courseName] if the user
 * set one, otherwise falls back to [Course.title] so legacy rows (which
 * pre-date the courseName column) still group themselves — they just
 * show up as one-entry folders under their own lesson title.
 */
val Course.effectiveCourseName: String
    get() = courseName.ifBlank { title }
