package com.echoling.app.data.repository

import com.echoling.app.data.local.db.dao.SentenceDao
import com.echoling.app.data.local.db.entity.SentenceEntity
import com.echoling.app.domain.model.Sentence
import com.echoling.app.domain.repository.SentenceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SentenceRepositoryImpl @Inject constructor(
    private val sentenceDao: SentenceDao
) : SentenceRepository {

    override fun getSentencesByCourseId(courseId: String): Flow<List<Sentence>> {
        return sentenceDao.getSentencesByCourseId(courseId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getSentencesByCourseIdSync(courseId: String): List<Sentence> {
        return sentenceDao.getSentencesByCourseIdSync(courseId).map { it.toDomain() }
    }

    override suspend fun getSentence(courseId: String, sentenceId: Int): Sentence? {
        return sentenceDao.getSentence(courseId, sentenceId)?.toDomain()
    }

    override suspend fun updateSentence(sentence: Sentence) {
        sentenceDao.updateSentence(sentence.toEntity())
    }

    override suspend fun updateCompletedStatus(courseId: String, sentenceId: Int, isCompleted: Boolean) {
        sentenceDao.updateCompletedStatus(courseId, sentenceId, isCompleted)
    }

    override suspend fun updateTestedStatus(courseId: String, sentenceId: Int, isTested: Boolean) {
        sentenceDao.updateTestedStatus(courseId, sentenceId, isTested)
    }

    override suspend fun syncSentences(sentences: List<Sentence>) {
        sentenceDao.insertSentencesIfNotExists(sentences.map { it.toEntity() })
    }

    private fun SentenceEntity.toDomain(): Sentence = Sentence(
        courseId = courseId,
        sentenceId = sentenceId,
        contentEn = contentEn,
        contentCn = contentCn,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        isLearned = isLearned,
        isRead = isRead,
        readScore = readScore,
        isCompleted = isCompleted,
        isTested = isTested
    )

    private fun Sentence.toEntity(): SentenceEntity = SentenceEntity(
        courseId = courseId,
        sentenceId = sentenceId,
        contentEn = contentEn,
        contentCn = contentCn,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        isLearned = isLearned,
        isRead = isRead,
        readScore = readScore,
        isCompleted = isCompleted,
        isTested = isTested
    )
}
