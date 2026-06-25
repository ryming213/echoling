package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.Sentence
import com.echoling.app.domain.repository.SentenceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncSentencesUseCase @Inject constructor(
    private val sentenceRepository: SentenceRepository
) {
    suspend operator fun invoke(sentences: List<Sentence>) {
        sentenceRepository.syncSentences(sentences)
    }
}
