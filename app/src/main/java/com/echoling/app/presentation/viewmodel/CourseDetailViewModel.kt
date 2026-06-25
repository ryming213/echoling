package com.echoling.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.domain.model.Course
import com.echoling.app.domain.usecase.DeleteCourseUseCase
import com.echoling.app.domain.usecase.GetCourseGroupsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * State for the category-detail screen (§12.19). The screen shows the
 * list of lessons inside one courseName "folder". The delete action
 * moved here from the home page so the user can prune individual
 * lessons.
 */
data class CourseDetailUiState(
    val courseName: String = "",
    val courses: List<Course> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    private val getCourseGroups: GetCourseGroupsUseCase,
    // Renamed from `deleteCourse` to avoid a name collision with the
    // public `fun deleteCourse(courseId)` below — Kotlin resolves
    // `deleteCourse(id)` to the function (invocation syntax wins over
    // property access), which would otherwise be infinite recursion.
    private val deleteCourseUseCase: DeleteCourseUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseDetailUiState())
    val uiState: StateFlow<CourseDetailUiState> = _uiState.asStateFlow()

    fun loadGroup(courseName: String) {
        viewModelScope.launch {
            _uiState.value = CourseDetailUiState(isLoading = true, courseName = courseName)
            try {
                // Reuse the same flow that powers the home page so the
                // grouping rules are guaranteed to agree. We snapshot
                // the first emission — the screen will refresh on the
                // next emit (e.g. after a delete) because the screen
                // calls `loadGroup` again on returning from Practice.
                val groups = getCourseGroups().first()
                val match = groups.firstOrNull { it.courseName == courseName }
                if (match == null) {
                    _uiState.value = CourseDetailUiState(
                        courseName = courseName,
                        isLoading = false,
                        error = "Group not found",
                    )
                } else {
                    _uiState.value = CourseDetailUiState(
                        courseName = courseName,
                        courses = match.courses,
                        isLoading = false,
                        error = null,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = CourseDetailUiState(
                    courseName = courseName,
                    isLoading = false,
                    error = e.message ?: "Failed to load group",
                )
            }
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            deleteCourseUseCase(courseId)
            // Re-emit the freshly-filtered list so the row disappears
            // without a screen-level reload.
            val name = _uiState.value.courseName
            if (name.isNotBlank()) loadGroup(name)
        }
    }
}
