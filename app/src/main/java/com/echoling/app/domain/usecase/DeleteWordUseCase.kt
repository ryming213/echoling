package com.echoling.app.domain.usecase

import com.echoling.app.domain.repository.WordRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeleteWordUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(wordText: String) {
        wordRepository.deleteWord(wordText)
    }
}
