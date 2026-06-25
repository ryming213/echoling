package com.echoling.app.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 → v3: introduce the `courseName` column on the `courses` table to
 * support grouped / folder-style browsing on the Courses tab.
 *
 * Old rows are backfilled with the empty string — at read time the app
 * resolves the effective group name via `Course.effectiveCourseName`,
 * which falls back to the lesson `title` for blank values, so legacy
 * courses surface as their own one-entry folders under their own title
 * with no data loss.
 */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE courses ADD COLUMN courseName TEXT NOT NULL DEFAULT ''"
        )
    }
}
