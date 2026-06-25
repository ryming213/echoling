package com.echoling.app.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-category progress in the "记单词" flashcard study screens.
 *
 * One row per vocabulary category, keyed by its manifest slug
 * (`junior` / `senior` / `cet4` / `cet6` / `toefl`). Holds only the
 * minimal state needed to resume a study session — the actual word
 * list lives in the bundled `assets/vocab_*.json` files, so this
 * table is tiny and self-contained.
 * self-contained.
 *
 * Persisted fields:
 *  - [currentIndex] — 0-based index of the card the user was last
 *    looking at. `-1` means "no progress yet, start from the first
 *    card".
 *  - [knownCount] / [unknownCount] — running tallies. Reset to 0 when
 *    the user taps the refresh icon (see [CategoryStudyViewModel.resetSession]).
 *  - [lastStudiedAt] — epoch ms of the last `markKnown` /
 *    `markUnknown` / `saveCurrentToVocabulary` action. Drives the
 *    "上次学习于…" sub-label on the category picker card so the user
 *    can see at a glance which categories they've touched recently.
 *
 * The picker screen on the "记单词" tab reads this table to render
 * the per-card progress sub-line. The per-category study screen
 * reads it on `load()` to seed its UI state and writes it on every
 * score action.
 *
 * Schema note: introduced at DB v5 (after the v4 `WordEntity.pos`
 * bump). `fallbackToDestructiveMigration()` means a fresh install on
 * v5 has an empty table — users start from `currentIndex = -1`,
 * `knownCount = 0`, `unknownCount = 0`, which is the expected
 * "first-time" state.
 */
@Entity(tableName = "recite_progress")
data class ReciteProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "current_index")
    val currentIndex: Int = -1,
    @ColumnInfo(name = "known_count")
    val knownCount: Int = 0,
    @ColumnInfo(name = "unknown_count")
    val unknownCount: Int = 0,
    @ColumnInfo(name = "last_studied_at")
    val lastStudiedAt: Long = 0L,
)
