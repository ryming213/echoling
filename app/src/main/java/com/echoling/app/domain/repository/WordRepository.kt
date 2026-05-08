package com.echoling.app.domain.repository

import com.echoling.app.domain.model.Word
import kotlinx.coroutines.flow.Flow

interface WordRepository {
    fun getAllWords(): Flow<List<Word>>
    fun getUnmasteredWords(): Flow<List<Word>>
    suspend fun getWord(word: String): Word?
    suspend fun insertWord(word: Word)
    suspend fun updateWord(word: Word)
    suspend fun deleteWord(word: String)
}
