package com.echoling.app.data.repository

import com.echoling.app.data.local.db.dao.LearningProgressDao
import com.echoling.app.data.local.db.entity.LearningProgressEntity
import com.echoling.app.domain.model.LearningProgress
import com.echoling.app.domain.repository.LearningProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningProgressRepositoryImpl @Inject constructor(
    private val learningProgressDao: LearningProgressDao
) : LearningProgressRepository {

    override suspend fun getProgressByCourseId(courseId: String): LearningProgress? {
        return learningProgressDao.getProgressByCourseId(courseId)?.toDomain()
    }

    override fun getAllProgress(): Flow<List<LearningProgress>> {
        return learningProgressDao.getAllProgress().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun saveProgress(progress: LearningProgress) {
        learningProgressDao.insertOrUpdateProgress(progress.toEntity())
    }

    override suspend fun deleteProgress(courseId: String) {
        learningProgressDao.deleteProgressByCourseId(courseId)
    }

    private fun LearningProgressEntity.toDomain(): LearningProgress = LearningProgress(
        courseId = courseId,
        currentPositionMs = currentPositionMs,
        currentSentenceId = currentSentenceId,
        learnedSentences = learnedSentences,
        totalLearnTimeMs = totalLearnTimeMs,
        lastLearnTime = lastLearnTime,
        finishRate = finishRate
    )

    private fun LearningProgress.toEntity(): LearningProgressEntity = LearningProgressEntity(
        courseId = courseId,
        currentPositionMs = currentPositionMs,
        currentSentenceId = currentSentenceId,
        learnedSentences = learnedSentences,
        totalLearnTimeMs = totalLearnTimeMs,
        lastLearnTime = lastLearnTime,
        finishRate = finishRate
    )
}
