package com.echoling.app.domain.repository

import com.echoling.app.domain.model.Sentence
import kotlinx.coroutines.flow.Flow

interface SentenceRepository {
    fun getSentencesByCourseId(courseId: String): Flow<List<Sentence>>
    suspend fun getSentencesByCourseIdSync(courseId: String): List<Sentence>
    suspend fun getSentence(courseId: String, sentenceId: Int): Sentence?
    suspend fun updateSentence(sentence: Sentence)
}
