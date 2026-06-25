package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.Word
import com.echoling.app.domain.repository.WordRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SaveWordUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    suspend operator fun invoke(word: Word) {
        val existingWord = wordRepository.getWord(word.word)
        if (existingWord == null) {
            wordRepository.insertWord(word)
        }
    }
}
