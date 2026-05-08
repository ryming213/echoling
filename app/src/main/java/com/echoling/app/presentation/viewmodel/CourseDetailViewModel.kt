package com.echoling.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.domain.model.Course
import com.echoling.app.domain.model.LearningProgress
import com.echoling.app.domain.repository.CourseRepository
import com.echoling.app.domain.repository.LearningProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseDetailUiState(
    val course: Course? = null,
    val progress: LearningProgress? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    private val courseRepository: CourseRepository,
    private val progressRepository: LearningProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseDetailUiState())
    val uiState: StateFlow<CourseDetailUiState> = _uiState.asStateFlow()

    fun loadCourse(courseId: String) {
        viewModelScope.launch {
            _uiState.value = CourseDetailUiState(isLoading = true)

            val course = courseRepository.getCourseById(courseId)
            val progress = progressRepository.getProgressByCourseId(courseId)

            _uiState.value = CourseDetailUiState(
                course = course,
                progress = progress,
                isLoading = false,
                error = if (course == null) "Course not found" else null
            )
        }
    }
}
