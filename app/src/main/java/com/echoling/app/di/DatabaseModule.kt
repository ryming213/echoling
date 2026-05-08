package com.echoling.app.di

import android.content.Context
import androidx.room.Room
import com.echoling.app.data.local.db.EchoLingDatabase
import com.echoling.app.data.local.db.dao.CourseDao
import com.echoling.app.data.local.db.dao.LearningProgressDao
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
        ).build()
    }

    @Provides
    fun provideCourseDao(database: EchoLingDatabase): CourseDao = database.courseDao()

    @Provides
    fun provideSentenceDao(database: EchoLingDatabase): SentenceDao = database.sentenceDao()

    @Provides
    fun provideLearningProgressDao(database: EchoLingDatabase): LearningProgressDao = database.learningProgressDao()

    @Provides
    fun provideWordDao(database: EchoLingDatabase): WordDao = database.wordDao()
}
