package com.echoling.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.domain.model.Word
import com.echoling.app.domain.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VocabularyUiState(
    val words: List<Word> = emptyList(),
    val isLoading: Boolean = true,
    val showMastered: Boolean = false
)

@HiltViewModel
class VocabularyViewModel @Inject constructor(
    private val wordRepository: WordRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VocabularyUiState())
    val uiState: StateFlow<VocabularyUiState> = _uiState.asStateFlow()

    init {
        loadWords()
    }

    private fun loadWords() {
        viewModelScope.launch {
            wordRepository.getAllWords().collect { words ->
                _uiState.value = _uiState.value.copy(
                    words = words,
                    isLoading = false
                )
            }
        }
    }

    fun toggleMastered(word: Word) {
        viewModelScope.launch {
            val updatedWord = word.copy(
                isMastered = !word.isMastered,
                nextReviewTime = if (!word.isMastered) System.currentTimeMillis() + 86400000 else 0
            )
            wordRepository.updateWord(updatedWord)
        }
    }

    fun deleteWord(word: Word) {
        viewModelScope.launch {
            wordRepository.deleteWord(word.word)
        }
    }

    fun setShowMastered(show: Boolean) {
        _uiState.value = _uiState.value.copy(showMastered = show)
        viewModelScope.launch {
            val flow = if (show) wordRepository.getAllWords() else wordRepository.getUnmasteredWords()
            flow.collect { words ->
                _uiState.value = _uiState.value.copy(words = words)
            }
        }
    }
}
