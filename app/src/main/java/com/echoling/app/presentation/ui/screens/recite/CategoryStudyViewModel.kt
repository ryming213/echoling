package com.echoling.app.presentation.ui.screens.recite

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.data.local.db.entity.ReciteProgressEntity
import com.echoling.app.domain.model.DictCategory
import com.echoling.app.domain.model.DictEntry
import com.echoling.app.domain.model.Word
import com.echoling.app.domain.usecase.GetDictionaryCategoriesUseCase
import com.echoling.app.domain.usecase.GetDictionaryWordsInCategoryUseCase
import com.echoling.app.domain.usecase.GetReciteProgressUseCase
import com.echoling.app.domain.usecase.SaveReciteProgressUseCase
import com.echoling.app.domain.usecase.SaveWordUseCase
import com.echoling.app.player.TtsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the per-category flashcard study screen.
 *
 *  - [categoryName] — name (e.g. "高中英语词汇") to render in the page
 *    header. Empty until the categories manifest has loaded.
 *  - [words] / [currentIndex] / [isFlipped] / [knownCount] /
 *    [unknownCount] / [showSaveButton] / [lastSaved] — same shape as
 *    the original learning VM. See `LearningUiState` doc for
 *    per-field semantics.
 *  - [isLoading] — true while the category entries are still being
 *    fetched from the bundled assets.
 *  - [ttsUnavailableMessage] — non-null when the user just tapped the
 *    speaker but the device has no TTS engine installed (e.g. Xiaomi
 *    CN without Google services). The screen reads this and shows a
 *    snackbar instructing the user to install Google TTS / iFlytek.
 *    Cleared by [CategoryStudyViewModel.consumeTtsUnavailableMessage].
 */
data class CategoryStudyUiState(
    val categoryName: String = "",
    val words: List<DictEntry> = emptyList(),
    val currentIndex: Int = -1,
    val isFlipped: Boolean = false,
    val knownCount: Int = 0,
    val unknownCount: Int = 0,
    val showSaveButton: Boolean = false,
    val isLoading: Boolean = true,
    val lastSaved: String? = null,
    val ttsUnavailableMessage: String? = null,
) {
    val totalCount: Int get() = words.size
    val currentWord: DictEntry? get() = words.getOrNull(currentIndex)
}

/**
 * State holder for the per-category flashcard study screen.
 *
 * Reads the `categoryId` route argument via [SavedStateHandle] (Hilt
 * populates it from the nav entry) and lazily loads that category's
 * word list. Behavioural mirrors of the original LearningViewModel —
 * same `flipCard` / `markKnown` / `markUnknown` / `saveCurrentToVocabulary`
 * / `skipToNext` / `skipToPrevious` / `resetSession` semantics — so the
 * existing flashcard UX is unchanged.
 *
 * **Persistence (DB v5 / `recite_progress` table):**
 *  - On [load] we read the saved progress row for this category and
 *    seed `currentIndex` / `knownCount` / `unknownCount` from it.
 *    Without this, process death / app cold-start / tab switch would
 *    drop the user back on card 0 every time.
 *  - After every `markKnown` / `markUnknown` / `saveCurrentToVocabulary`
 *    / `skipToNext` / `skipToPrevious` / `resetSession` we write the
 *    new state back via [SaveReciteProgressUseCase]. The `currentIndex`
 *    we persist is the *destination* of the action (i.e. the card
 *    the user is now looking at), not the one they just left, so
 *    re-entering lands on the right card.
 *
 * When the user taps "加入单词本" the resulting [Word] is saved with
 * the entry's full data (word, phonetic, pos, translation) so the
 * vocabulary book renders the part-of-speech chip correctly.
 */
@HiltViewModel
class CategoryStudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDictionaryCategories: GetDictionaryCategoriesUseCase,
    private val getDictionaryWordsInCategory: GetDictionaryWordsInCategoryUseCase,
    private val saveWordUseCase: SaveWordUseCase,
    private val getReciteProgress: GetReciteProgressUseCase,
    private val saveReciteProgress: SaveReciteProgressUseCase,
    private val ttsManager: TtsManager,
) : ViewModel() {

    private val initialCategoryId: String =
        savedStateHandle.get<String>(ARG_CATEGORY_ID)?.decodeFromRoute().orEmpty()

    private val _uiState = MutableStateFlow(CategoryStudyUiState())
    val uiState: StateFlow<CategoryStudyUiState> = _uiState.asStateFlow()

    fun load(categoryId: String = initialCategoryId) {
        if (categoryId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            // Resolve the human-readable name from the categories list
            // (so the header can show "高中英语词汇" rather than "senior").
            // Fall back to the slug if the manifest can't be loaded —
            // unlikely but keeps the screen usable.
            val categories = runCatching { getDictionaryCategories() }.getOrDefault(emptyList())
            val name = categories.firstOrNull { it.id == categoryId }?.name ?: categoryId
            val words = getDictionaryWordsInCategory(categoryId)
            // Resume from the persisted progress row if any. Without
            // this the user lands on card 0 every time they re-open
            // the category. The `coerceIn` clamps the index into the
            // valid range so a stale row that points past the new
            // word-list size (e.g. asset got smaller in a future
            // update) doesn't leave the screen permanently stuck on
            // the empty-state branch.
            val saved = getReciteProgress(categoryId)
            val maxValid = (words.size - 1).coerceAtLeast(-1)
            val resumedIndex = saved?.currentIndex?.coerceIn(-1, maxValid) ?: -1
            _uiState.value = CategoryStudyUiState(
                categoryName = name,
                words = words,
                currentIndex = if (words.isEmpty()) -1 else resumedIndex.coerceAtLeast(0),
                isFlipped = false,
                knownCount = saved?.knownCount ?: 0,
                unknownCount = saved?.unknownCount ?: 0,
                showSaveButton = false,
                isLoading = false,
            )
        }
    }

    fun flipCard() {
        // isFlipped is purely UI — no DB write (we don't want to
        // persist transient per-card state on every tap).
        _uiState.value = _uiState.value.copy(isFlipped = !_uiState.value.isFlipped)
    }

    fun markKnown() {
        val s = _uiState.value
        if (s.currentWord == null) return
        val next = s.copy(
            knownCount = s.knownCount + 1,
            showSaveButton = false,
            isFlipped = false,
            currentIndex = (s.currentIndex + 1).mod(s.words.size.coerceAtLeast(1)),
        )
        _uiState.value = next
        persist(initialCategoryId, next)
    }

    fun markUnknown() {
        val s = _uiState.value
        if (s.currentWord == null) return
        val next = s.copy(
            unknownCount = s.unknownCount + 1,
            showSaveButton = true,
        )
        _uiState.value = next
        persist(initialCategoryId, next)
    }

    fun saveCurrentToVocabulary() {
        val entry = _uiState.value.currentWord ?: return
        viewModelScope.launch {
            saveWordUseCase(
                Word(
                    word = entry.word,
                    phonetic = entry.phonetic,
                    pos = entry.pos,
                    translation = entry.translation,
                    exampleSentence = "",
                    sourceCourseId = "",
                    sourceSentenceId = 0,
                    collectedAt = System.currentTimeMillis(),
                    nextReviewTime = System.currentTimeMillis(),
                )
            )
            val s = _uiState.value
            val next = s.copy(
                showSaveButton = false,
                isFlipped = false,
                lastSaved = entry.word,
                currentIndex = (s.currentIndex + 1).mod(s.words.size.coerceAtLeast(1)),
            )
            _uiState.value = next
            persist(initialCategoryId, next)
        }
    }

    fun skipToNext() {
        val s = _uiState.value
        if (s.currentWord == null) return
        val next = s.copy(
            showSaveButton = false,
            isFlipped = false,
            currentIndex = (s.currentIndex + 1).mod(s.words.size.coerceAtLeast(1)),
        )
        _uiState.value = next
        persist(initialCategoryId, next)
    }

    fun skipToPrevious() {
        val s = _uiState.value
        if (s.currentWord == null) return
        val next = s.copy(
            showSaveButton = false,
            isFlipped = false,
            currentIndex = if (s.currentIndex <= 0) s.words.size - 1 else s.currentIndex - 1,
        )
        _uiState.value = next
        persist(initialCategoryId, next)
    }

    fun resetSession() {
        val s = _uiState.value
        val next = s.copy(
            currentIndex = if (s.words.isEmpty()) -1 else 0,
            knownCount = 0,
            unknownCount = 0,
            isFlipped = false,
            showSaveButton = false,
        )
        _uiState.value = next
        persist(initialCategoryId, next)
    }

    fun consumeLastSaved() {
        if (_uiState.value.lastSaved != null) {
            _uiState.value = _uiState.value.copy(lastSaved = null)
        }
    }

    /**
     * Speak the current word aloud via the system TTS engine. No-op
     * if there is no current word (empty state). Safe to call before
     * TTS init completes — [TtsManager] queues the call internally.
     *
     * If no TTS engine is installed on the device (e.g. CN Xiaomi
     * without Google services), sets [CategoryStudyUiState.ttsUnavailableMessage]
     * instead of speaking — the screen observes this and shows a
     * snackbar telling the user how to install a TTS engine.
     */
    fun pronounceCurrent() {
        if (!ttsManager.isAvailable.value) {
            _uiState.value = _uiState.value.copy(
                ttsUnavailableMessage = "设备未安装 TTS 引擎，请到应用商店安装「Google 文字转语音」或「讯飞语音」后再试"
            )
            return
        }
        _uiState.value.currentWord?.let { word ->
            ttsManager.speak(word.word)
        }
    }

    /**
     * Clear [CategoryStudyUiState.ttsUnavailableMessage] after the
     * screen has shown the snackbar, so a subsequent tap re-fires it.
     */
    fun consumeTtsUnavailableMessage() {
        if (_uiState.value.ttsUnavailableMessage != null) {
            _uiState.value = _uiState.value.copy(ttsUnavailableMessage = null)
        }
    }

    /**
     * Persist the current in-memory state for [categoryId] to the
     * `recite_progress` table. Called after every score / navigation
     * action so the user resumes exactly where they left off. Writes
     * are fire-and-forget on `viewModelScope` — the in-memory
     * `_uiState` is the source of truth for the running screen.
     */
    private fun persist(categoryId: String, state: CategoryStudyUiState) {
        if (categoryId.isBlank()) return
        viewModelScope.launch {
            saveReciteProgress(
                ReciteProgressEntity(
                    categoryId = categoryId,
                    currentIndex = state.currentIndex,
                    knownCount = state.knownCount,
                    unknownCount = state.unknownCount,
                    lastStudiedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    companion object {
        /** Nav argument name; kept here so the VM and the nav graph stay in sync. */
        const val ARG_CATEGORY_ID = "categoryId"
    }
}

/** Decoded from a URL-encoded nav route — mirrors `Screen.encodeForRoute`. */
private fun String.decodeFromRoute(): String =
    java.net.URLDecoder.decode(this, "UTF-8")
