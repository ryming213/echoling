package com.echoling.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.domain.model.LearningProgress
import com.echoling.app.domain.repository.CourseRepository
import com.echoling.app.domain.repository.LearningProgressRepository
import com.echoling.app.domain.repository.WordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val isLoading: Boolean = true
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val progressRepository: LearningProgressRepository,
    private val wordRepository: WordRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    init {
        loadStatistics()
    }

    private fun loadStatistics() {
        viewModelScope.launch {
            combine(
                progressRepository.getAllProgress(),
                wordRepository.getAllWords()
            ) { progressList, words ->
                val totalLearnTime = progressList.sumOf { it.totalLearnTimeMs }
                val coursesLearned = progressList.count { it.finishRate >= 1.0f }
                val wordsCollected = words.size
                val wordsMastered = words.count { it.isMastered }

                // Calculate streak
                val sortedProgress = progressList.sortedByDescending { it.lastLearnTime }
                val streak = calculateStreak(sortedProgress)

                // Calculate daily stats (last 7 days)
                val dailyStats = calculateDailyStats(progressList)

                // Calculate average daily time
                val avgDailyTime = if (dailyStats.isNotEmpty()) {
                    dailyStats.sumOf { it.learnTimeMs } / dailyStats.size
                } else 0L

                StatisticsUiState(
                    totalLearnTimeMs = totalLearnTime,
                    totalCoursesLearned = coursesLearned,
                    totalWordsCollected = wordsCollected,
                    totalWordsMastered = wordsMastered,
                    averageDailyTimeMs = avgDailyTime,
                    currentStreak = streak,
                    dailyStats = dailyStats,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
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

    private fun calculateDailyStats(progressList: List<LearningProgress>): List<DailyStatistic> {
        val stats = mutableListOf<DailyStatistic>()
        val calendar = Calendar.getInstance()

        for (i in 6 downTo 0) {
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

            val dayLabel = when (i) {
                0 -> "Today"
                1 -> "Yesterday"
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
