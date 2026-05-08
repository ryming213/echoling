package com.echoling.app.domain.repository

import com.echoling.app.domain.model.LearningProgress
import kotlinx.coroutines.flow.Flow

interface LearningProgressRepository {
    suspend fun getProgressByCourseId(courseId: String): LearningProgress?
    fun getAllProgress(): Flow<List<LearningProgress>>
    suspend fun saveProgress(progress: LearningProgress)
    suspend fun deleteProgress(courseId: String)
}
