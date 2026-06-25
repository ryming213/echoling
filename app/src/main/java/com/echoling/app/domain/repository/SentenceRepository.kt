package com.echoling.app.domain.repository

import com.echoling.app.domain.model.Sentence
import kotlinx.coroutines.flow.Flow

interface SentenceRepository {
    fun getSentencesByCourseId(courseId: String): Flow<List<Sentence>>
    suspend fun getSentencesByCourseIdSync(courseId: String): List<Sentence>
    suspend fun getSentence(courseId: String, sentenceId: Int): Sentence?
    suspend fun updateSentence(sentence: Sentence)
    suspend fun updateCompletedStatus(courseId: String, sentenceId: Int, isCompleted: Boolean)
    suspend fun updateTestedStatus(courseId: String, sentenceId: Int, isTested: Boolean)
    suspend fun syncSentences(sentences: List<Sentence>)
}
