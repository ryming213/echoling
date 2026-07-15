package com.echoling.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.domain.model.LearningProgress
import com.echoling.app.domain.usecase.GetStatisticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class DailyStatistic(
    val date: String,
    val learnTimeMs: Long,
    val sentencesLearned: Int
)

data class StatisticsUiState(
    val totalLearnTimeMs: Long = 0L,
    val totalCoursesLearned: Int = 0,
    val totalWordsCollected: Int = 0,
    val totalWordsMastered: Int = 0,
    val averageDailyTimeMs: Long = 0L,
    val currentStreak: Int = 0,
    val dailyStats: List<DailyStatistic> = emptyList(),
    /**
     * 30-day activity, day 0 = today. Same shape as [dailyStats] but
     * without "今天"/"昨天" labels — days 2..29 use the weekday
     * short name (Mon, Tue, ...) so the labels fit in the thin bar
     * chart.
     */
    val monthlyStats: List<DailyStatistic> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val getStatisticsUseCase: GetStatisticsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            getStatisticsUseCase.getStatisticsFlow().collect { (progressList, words) ->
                val totalLearnTime = progressList.sumOf { it.totalLearnTimeMs }
                // §12.21: count any course that has a progress row as
                // "learned". The old `finishRate >= 1.0f` rule was
                // almost never satisfied (a user rarely plays to the
                // exact last ms), so the counter was stuck at 0 for
                // every user. Combined with the eager-save on
                // loadCourse() (see PracticeViewModel §12.20), the
                // counter now reflects "courses I've started
                // practicing" — which matches the user's intent.
                val coursesLearned = progressList.size
                val wordsCollected = words.size
                val wordsMastered = words.count { it.isMastered }

                // Calculate streak
                val sortedProgress = progressList.sortedByDescending { it.lastLearnTime }
                val streak = calculateStreak(sortedProgress)

                // Calculate daily stats (last 7 days)
                val dailyStats = calculateDailyStats(progressList, days = 7)
                // §12.21: monthly stats (last 30 days) for the wider
                // chart. Same shape as [dailyStats] but a longer
                // window; the screen renders it as a thinner bar
                // chart with day labels every 5th day.
                val monthlyStats = calculateDailyStats(progressList, days = 30, showTodayLabel = false)

                // Calculate average daily time (over the 7-day window)
                val avgDailyTime = if (dailyStats.isNotEmpty()) {
                    dailyStats.sumOf { it.learnTimeMs } / dailyStats.size
                } else 0L

                _uiState.value = StatisticsUiState(
                    totalLearnTimeMs = totalLearnTime,
                    totalCoursesLearned = coursesLearned,
                    totalWordsCollected = wordsCollected,
                    totalWordsMastered = wordsMastered,
                    averageDailyTimeMs = avgDailyTime,
                    currentStreak = streak,
                    dailyStats = dailyStats,
                    monthlyStats = monthlyStats,
                    isLoading = false
                )
            }
        }
    }

    private fun calculateStreak(progressList: List<LearningProgress>): Int {
        if (progressList.isEmpty()) return 0

        val calendar = Calendar.getInstance()
        val today = calendar.timeInMillis

        var streak = 0
        var currentDay = calendar.apply {
            timeInMillis = today
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val learnedDays = progressList
            .filter { it.totalLearnTimeMs > 0 }
            .map { it.lastLearnTime }
            .toSet()

        // Check consecutive days
        while (true) {
            val dayStart = Calendar.getInstance().apply {
                timeInMillis = currentDay
            }.timeInMillis

            val dayEnd = dayStart + 86400000 // 24 hours

            if (learnedDays.any { it in dayStart until dayEnd }) {
                streak++
                currentDay -= 86400000
            } else {
                break
            }
        }

        return streak
    }

    private fun calculateDailyStats(
        progressList: List<LearningProgress>,
        days: Int,
        showTodayLabel: Boolean = true,
    ): List<DailyStatistic> {
        val stats = mutableListOf<DailyStatistic>()
        val calendar = Calendar.getInstance()

        for (i in (days - 1) downTo 0) {
            val dayCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayStart = dayCalendar.timeInMillis
            val dayEnd = dayStart + 86400000

            val dayProgress = progressList.filter { it.lastLearnTime in dayStart until dayEnd }
            val learnTime = dayProgress.sumOf { it.totalLearnTimeMs }
            val sentencesLearned = dayProgress.sumOf { it.learnedSentences }

            val dayLabel = when {
                showTodayLabel && i == 0 -> "今天"
                showTodayLabel && i == 1 -> "昨天"
                else -> {
                    calendar.timeInMillis = dayStart
                    calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, java.util.Locale.getDefault()) ?: ""
                }
            }

            stats.add(DailyStatistic(dayLabel, learnTime, sentencesLearned))
        }

        return stats
    }
}
