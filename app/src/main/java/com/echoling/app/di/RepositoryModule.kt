package com.echoling.app.di

import com.echoling.app.data.repository.CourseRepositoryImpl
import com.echoling.app.data.repository.LearningProgressRepositoryImpl
import com.echoling.app.data.repository.SentenceRepositoryImpl
import com.echoling.app.data.repository.WordRepositoryImpl
import com.echoling.app.domain.repository.CourseRepository
import com.echoling.app.domain.repository.LearningProgressRepository
import com.echoling.app.domain.repository.SentenceRepository
import com.echoling.app.domain.repository.WordRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCourseRepository(impl: CourseRepositoryImpl): CourseRepository

    @Binds
    @Singleton
    abstract fun bindSentenceRepository(impl: SentenceRepositoryImpl): SentenceRepository

    @Binds
    @Singleton
    abstract fun bindLearningProgressRepository(impl: LearningProgressRepositoryImpl): LearningProgressRepository

    @Binds
    @Singleton
    abstract fun bindWordRepository(impl: WordRepositoryImpl): WordRepository
}
