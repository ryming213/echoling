package com.echoling.app.presentation.ui.screens.recite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.data.local.db.entity.ReciteProgressEntity
import com.echoling.app.domain.model.DictCategory
import com.echoling.app.domain.usecase.GetDictionaryCategoriesUseCase
import com.echoling.app.domain.usecase.ObserveAllReciteProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the "记单词" tab landing.
 *
 *  - [categories] — ordered list of bundled vocabulary categories
 *    (id, name, description, entries). Renders one card per entry.
 *  - [progressByCategory] — `categoryId → progress row` map of every
 *    persisted study session. Drives the per-card sub-label that
 *    shows "已学 27 / 2340" and "上次学习于…" — without this the
 *    picker always reads "已学 0 / N" which made the user feel the
 *    app wasn't tracking anything. Keyed by category id so the
 *    `ReciteScreen` does a single `categories.associate { ... }` to
 *    pair manifest data with persisted progress.
 *  - [isLoading] — true until the first load completes; the screen
 *    shows a centered [CircularProgressIndicator] while loading.
 */
data class ReciteUiState(
    val categories: List<DictCategory> = emptyList(),
    val progressByCategory: Map<String, ReciteProgressEntity> = emptyMap(),
    val isLoading: Boolean = true,
)

/**
 * State holder for the "记单词" tab landing.
 *
 * Loads the manifest's category list once on construction and
 * subscribes to the `recite_progress` table via
 * [ObserveAllReciteProgressUseCase] — every time the user marks a
 * card on a sub-page, the corresponding row is upserted and this
 * flow re-emits, refreshing the picker's sub-labels live.
 *
 * The picker never has to know the DAO/UseCase split — it just
 * reads `uiState.progressByCategory[cat.id]`. When the row is null
 * the card renders "未开始学习"; otherwise it renders the persisted
 * counts and the relative "last studied" timestamp.
 */
@HiltViewModel
class ReciteViewModel @Inject constructor(
    private val getDictionaryCategories: GetDictionaryCategoriesUseCase,
    private val observeAllReciteProgress: ObserveAllReciteProgressUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReciteUiState())
    val uiState: StateFlow<ReciteUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        observeProgress()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            val cats = getDictionaryCategories()
            _uiState.value = _uiState.value.copy(
                categories = cats,
                isLoading = false,
            )
        }
    }

    private fun observeProgress() {
        viewModelScope.launch {
            observeAllReciteProgress().collect { rows ->
                _uiState.value = _uiState.value.copy(
                    progressByCategory = rows.associateBy { it.categoryId },
                )
            }
        }
    }
}
