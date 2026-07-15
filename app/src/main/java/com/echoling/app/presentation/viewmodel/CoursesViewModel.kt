package com.echoling.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.domain.model.CourseGroup
import com.echoling.app.domain.usecase.ContinueLearningItem
import com.echoling.app.domain.usecase.DeleteCourseUseCase
import com.echoling.app.domain.usecase.GetCourseGroupsUseCase
import com.echoling.app.domain.usecase.GetStatisticsUseCase
import com.echoling.app.domain.usecase.GetContinueLearningUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Aggregate UI state for the Courses tab. As of §12.19 the home page
 * shows *groups* of courses (folders), not individual lessons — tapping
 * a group navigates to the repurposed `CourseDetailScreen` which lists
 * the lessons in that group.
 */
data class CoursesUiState(
    val groups: List<CourseGroup> = emptyList(),
    val totalLearnTimeMs: Long = 0L,
    val totalWordsCollected: Int = 0,
    val continueLearning: ContinueLearningItem? = null,
    /**
     * Flips to true the first time Room emits into the combine. Used
     * to avoid flashing the "暂无练习" empty state during the first
     * frame, when the ViewModel's `init` coroutine hasn't yet started
     * collecting and the groups list is still the default empty list.
     */
    val hasLoadedOnce: Boolean = false,
)

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val getCourseGroups: GetCourseGroupsUseCase,
    private val getStatistics: GetStatisticsUseCase,
    private val getContinueLearning: GetContinueLearningUseCase,
    // The delete use case is kept here so it can be re-exposed later
    // (e.g. a long-press on a group in the future). Today delete lives
    // inside the category-detail screen — see `CourseDetailViewModel`.
    @Suppress("unused")
    private val deleteCourseUseCase: DeleteCourseUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoursesUiState())
    val uiState: StateFlow<CoursesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            combine(
                getCourseGroups(),
                getStatistics.getStatisticsFlow(),
                getContinueLearning(),
            ) { groups, stats, continueItem ->
                val totalTime = stats.progressList.sumOf { it.totalLearnTimeMs }
                CoursesUiState(
                    groups = groups,
                    totalLearnTimeMs = totalTime,
                    totalWordsCollected = stats.words.size,
                    continueLearning = continueItem,
                    hasLoadedOnce = true,
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
