package com.echoling.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.domain.model.Word
import com.echoling.app.domain.usecase.DeleteWordUseCase
import com.echoling.app.domain.usecase.GetWordsUseCase
import com.echoling.app.player.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VocabularyUiState(
    val words: List<Word> = emptyList(),
    val isLoading: Boolean = true,
    /**
     * Non-null when the user just tapped a speaker button but the
     * device has no TTS engine installed. Screen reads this and shows
     * a snackbar instructing the user to install Google TTS / iFlytek.
     * Cleared by [VocabularyViewModel.consumeTtsUnavailableMessage].
     */
    val ttsUnavailableMessage: String? = null,
)

@HiltViewModel
class VocabularyViewModel @Inject constructor(
    private val getWordsUseCase: GetWordsUseCase,
    private val deleteWordUseCase: DeleteWordUseCase,
    private val ttsManager: TtsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VocabularyUiState())
    val uiState: StateFlow<VocabularyUiState> = _uiState.asStateFlow()

    init {
        loadWords()
    }

    private fun loadWords() {
        viewModelScope.launch {
            getWordsUseCase.getAllWords().collect { words ->
                _uiState.value = _uiState.value.copy(
                    words = words,
                    isLoading = false
                )
            }
        }
    }

    fun deleteWord(word: Word) {
        viewModelScope.launch {
            deleteWordUseCase(word.word)
        }
    }

    /**
     * Speak [word] aloud via the system TTS engine. Side-effect —
     * no DB / no UseCase. Safe to call before TTS init completes;
     * [TtsManager] queues the call internally.
     *
     * If no TTS engine is installed on the device (e.g. CN Xiaomi
     * without Google services), sets
     * [VocabularyUiState.ttsUnavailableMessage] instead of speaking —
     * the screen observes this and shows a snackbar telling the user
     * how to install a TTS engine.
     */
    fun pronounce(word: Word) {
        if (!ttsManager.isAvailable.value) {
            _uiState.value = _uiState.value.copy(
                ttsUnavailableMessage = "设备未安装 TTS 引擎，请到应用商店安装「Google 文字转语音」或「讯飞语音」后再试"
            )
            return
        }
        ttsManager.speak(word.word)
    }

    /**
     * Clear [VocabularyUiState.ttsUnavailableMessage] after the screen
     * has shown the snackbar, so a subsequent tap re-fires it.
     */
    fun consumeTtsUnavailableMessage() {
        if (_uiState.value.ttsUnavailableMessage != null) {
            _uiState.value = _uiState.value.copy(ttsUnavailableMessage = null)
        }
    }
}
