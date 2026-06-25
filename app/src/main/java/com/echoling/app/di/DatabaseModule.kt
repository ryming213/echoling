package com.echoling.app.di

import android.content.Context
import androidx.room.Room
import com.echoling.app.data.local.db.EchoLingDatabase
import com.echoling.app.data.local.db.MIGRATION_2_3
import com.echoling.app.data.local.db.dao.CourseDao
import com.echoling.app.data.local.db.dao.LearningProgressDao
import com.echoling.app.data.local.db.dao.ReciteProgressDao
import com.echoling.app.data.local.db.dao.SentenceDao
import com.echoling.app.data.local.db.dao.WordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EchoLingDatabase {
        return Room.databaseBuilder(
            context,
            EchoLingDatabase::class.java,
            "echo_ling_database"
        )
            // v2 → v3 introduces the `courses.courseName` column for the
            // grouped (folder-style) Courses tab. `fallbackToDestructive`
            // is kept as a safety net for any future schema drift.
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideCourseDao(database: EchoLingDatabase): CourseDao = database.courseDao()

    @Provides
    fun provideSentenceDao(database: EchoLingDatabase): SentenceDao = database.sentenceDao()

    @Provides
    fun provideLearningProgressDao(database: EchoLingDatabase): LearningProgressDao = database.learningProgressDao()

    @Provides
    fun provideWordDao(database: EchoLingDatabase): WordDao = database.wordDao()

    @Provides
    fun provideReciteProgressDao(database: EchoLingDatabase): ReciteProgressDao = database.reciteProgressDao()
}
