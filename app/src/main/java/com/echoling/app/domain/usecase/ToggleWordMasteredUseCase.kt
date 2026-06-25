package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.Word
import com.echoling.app.domain.repository.WordRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToggleWordMasteredUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(word: Word) {
        val updatedWord = word.copy(
            isMastered = !word.isMastered,
            nextReviewTime = if (!word.isMastered) System.currentTimeMillis() + 86400000 else 0
        )
        wordRepository.updateWord(updatedWord)
    }
}
