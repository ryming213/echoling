package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.Sentence
import com.echoling.app.domain.repository.SentenceRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetCourseSentencesUseCase @Inject constructor(
    private val sentenceRepository: SentenceRepository
) {
    fun getByCourseId(courseId: String): Flow<List<Sentence>> =
        sentenceRepository.getSentencesByCourseId(courseId)

    suspend fun getByCourseIdSync(courseId: String): List<Sentence> =
        sentenceRepository.getSentencesByCourseIdSync(courseId)
}
