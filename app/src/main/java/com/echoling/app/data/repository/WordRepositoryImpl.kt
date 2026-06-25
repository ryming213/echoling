package com.echoling.app.data.repository

import com.echoling.app.data.local.db.dao.WordDao
import com.echoling.app.data.local.db.entity.WordEntity
import com.echoling.app.domain.model.Word
import com.echoling.app.domain.repository.WordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordRepositoryImpl @Inject constructor(
    private val wordDao: WordDao
) : WordRepository {

    override fun getAllWords(): Flow<List<Word>> {
        return wordDao.getAllWords().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getUnmasteredWords(): Flow<List<Word>> {
        return wordDao.getUnmasteredWords().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getWord(word: String): Word? {
        return wordDao.getWord(word)?.toDomain()
    }

    override suspend fun insertWord(word: Word) {
        wordDao.insertWord(word.toEntity())
    }

    override suspend fun updateWord(word: Word) {
        wordDao.updateWord(word.toEntity())
    }

    override suspend fun deleteWord(word: String) {
        wordDao.deleteWordByText(word)
    }

    private fun WordEntity.toDomain(): Word = Word(
        word = word,
        phonetic = phonetic,
        pos = pos,
        translation = translation,
        exampleSentence = exampleSentence,
        sourceCourseId = sourceCourseId,
        sourceSentenceId = sourceSentenceId,
        isMastered = isMastered,
        collectedAt = collectedAt,
        reviewCount = reviewCount,
        nextReviewTime = nextReviewTime,
    )

    private fun Word.toEntity(): WordEntity = WordEntity(
        word = word,
        phonetic = phonetic,
        pos = pos,
        translation = translation,
        exampleSentence = exampleSentence,
        sourceCourseId = sourceCourseId,
        sourceSentenceId = sourceSentenceId,
        isMastered = isMastered,
        collectedAt = collectedAt,
        reviewCount = reviewCount,
        nextReviewTime = nextReviewTime,
    )
}
