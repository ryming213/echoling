package com.echoling.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.echoling.app.data.local.db.dao.CourseDao
import com.echoling.app.data.local.db.dao.LearningProgressDao
import com.echoling.app.data.local.db.dao.SentenceDao
import com.echoling.app.data.local.db.dao.WordDao
import com.echoling.app.data.local.db.entity.CourseEntity
import com.echoling.app.data.local.db.entity.LearningProgressEntity
import com.echoling.app.data.local.db.entity.SentenceEntity
import com.echoling.app.data.local.db.entity.WordEntity

@Database(
    entities = [
        CourseEntity::class,
        SentenceEntity::class,
        LearningProgressEntity::class,
        WordEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EchoLingDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun sentenceDao(): SentenceDao
    abstract fun learningProgressDao(): LearningProgressDao
    abstract fun wordDao(): WordDao
}
