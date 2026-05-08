package com.echoling.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.data.local.DatabaseSeeder
import com.echoling.app.domain.model.Course
import com.echoling.app.domain.model.LearningProgress
import com.echoling.app.domain.repository.CourseRepository
import com.echoling.app.domain.repository.LearningProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContinueLearningItem(
    val course: Course,
    val progress: LearningProgress
)

data class HomeUiState(
    val continueLearning: ContinueLearningItem? = null,
    val totalLearnTimeMs: Long = 0L,
    val totalCoursesLearned: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val progressRepository: LearningProgressRepository,
    private val databaseSeeder: DatabaseSeeder
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            databaseSeeder.seedIfEmpty()
        }
        loadHomeData()
    }

    private fun loadHomeData() {
        viewModelScope.launch {
            combine(
                courseRepository.getAllCourses(),
                progressRepository.getAllProgress()
            ) { courses, progressList ->
                val progressMap = progressList.associateBy { it.courseId }
                val continueItem = courses
                    .mapNotNull { course ->
                        progressMap[course.courseId]?.let { progress ->
                            ContinueLearningItem(course, progress)
                        }
                    }
                    .maxByOrNull { it.progress.lastLearnTime }

                val totalTime = progressList.sumOf { it.totalLearnTimeMs }
                val coursesLearned = progressList.count { it.finishRate > 0 }

                HomeUiState(
                    continueLearning = continueItem,
                    totalLearnTimeMs = totalTime,
                    totalCoursesLearned = coursesLearned,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
