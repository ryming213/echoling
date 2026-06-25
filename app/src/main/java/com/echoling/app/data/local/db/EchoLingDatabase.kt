package com.echoling.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.echoling.app.data.local.db.dao.CourseDao
import com.echoling.app.data.local.db.dao.LearningProgressDao
import com.echoling.app.data.local.db.dao.ReciteProgressDao
import com.echoling.app.data.local.db.dao.SentenceDao
import com.echoling.app.data.local.db.dao.WordDao
import com.echoling.app.data.local.db.entity.CourseEntity
import com.echoling.app.data.local.db.entity.LearningProgressEntity
import com.echoling.app.data.local.db.entity.ReciteProgressEntity
import com.echoling.app.data.local.db.entity.SentenceEntity
import com.echoling.app.data.local.db.entity.WordEntity

/**
 * Schema version history:
 *  - v1 → v2: per CLAUDE.md baseline
 *  - v2 → v3: per CLAUDE.md baseline
 *  - v3 → v4: added [WordEntity.pos] so the vocabulary book can render
 *    the part-of-speech chip next to the translation. Schema change
 *    drops the existing user-saved words (per CLAUDE.md §9.2,
 *    `fallbackToDestructiveMigration()` is the current fallback).
 *  - v4 → v5: added [ReciteProgressEntity] so the "记单词" flashcard
 *    study screens can persist per-category `currentIndex` /
 *    `knownCount` / `unknownCount` / `lastStudiedAt` across process
 *    death, app cold-starts, and tab switches — without this the
 *    user re-enters each category from card 0 every time. The new
 *    table is empty on first install (no data to migrate).
 */
@Database(
    entities = [
        CourseEntity::class,
        SentenceEntity::class,
        LearningProgressEntity::class,
        WordEntity::class,
        ReciteProgressEntity::class,
    ],
    version = 5,
    exportSchema = false
)
abstract class EchoLingDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun sentenceDao(): SentenceDao
    abstract fun learningProgressDao(): LearningProgressDao
    abstract fun wordDao(): WordDao
    abstract fun reciteProgressDao(): ReciteProgressDao
}
