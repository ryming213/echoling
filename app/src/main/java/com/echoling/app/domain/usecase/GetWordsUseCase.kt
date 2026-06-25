package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.Word
import com.echoling.app.domain.repository.WordRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetWordsUseCase @Inject constructor(
    private val wordRepository: WordRepository
) {
    fun getAllWords(): Flow<List<Word>> = wordRepository.getAllWords()

    fun getUnmasteredWords(): Flow<List<Word>> = wordRepository.getUnmasteredWords()
}
