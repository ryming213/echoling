package com.echoling.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.domain.model.Course
import com.echoling.app.domain.repository.CourseRepository
import com.echoling.app.domain.usecase.DeleteCourseUseCase
import com.echoling.app.domain.usecase.GetCourseGroupsUseCase
import com.echoling.app.transcription.AutoTranscriptionScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
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
    // (2026-07-16) Auto-subtitle retry wiring (spec §5.5).
    // Descriptive names (`courseRepository` / `autoTranscriptionScheduler`)
    // to avoid the §12.14 property-vs-function name collision trap.
    private val courseRepository: CourseRepository,
    private val autoTranscriptionScheduler: AutoTranscriptionScheduler,
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

    /**
     * (2026-07-16) Re-enqueue a failed auto-subtitle job. Triggered
     * by the FAILED status chip on a [CourseListItem] in
     * CourseDetailScreen.
     *
     * Steps:
     *  1. Fetch the course (must exist) to learn its media path.
     *  2. Delete any stale `.srt` file left behind by the failed
     *     run so the worker starts from a clean slate.
     *  3. Reset the row's auto-subtitle columns to PENDING / 0 / null
     *     via [CourseRepository.markTranscriptionStarted] (single SQL
     *     UPDATE — no need to read + write the full entity).
     *  4. Enqueue the worker again. The scheduler uses REPLACE on the
     *     `auto-subtitle-<courseId>` unique name, so any leftover
     *     FAILED WorkInfo is cleared and a fresh RUNNING job starts.
     */
    fun retryAutoSubtitle(courseId: String) {
        viewModelScope.launch {
            val course = courseRepository.getCourseById(courseId) ?: return@launch
            val mediaPath = course.audioUri ?: course.videoUri ?: return@launch
            // Step 2: stale SRT. We don't fail the retry if this
            // throws — the worker will overwrite it on completion.
            runCatching {
                // Explicit null-check on subtitleUri: for FAILED
                // courses the field is usually null (no SRT was
                // written), and `File(null)` would NPE. The wrapping
                // runCatching would swallow it but using NPE as
                // control-flow is brittle.
                course.subtitleUri?.let { File(it).takeIf { f -> f.exists() }?.delete() }
            }
            // Step 3 + 4: reset columns then enqueue.
            courseRepository.markTranscriptionStarted(courseId)
            autoTranscriptionScheduler.enqueue(courseId, mediaPath)
            // Refresh the list so the chip flips from FAILED → PENDING
            // without waiting for the next emission.
            val name = _uiState.value.courseName
            if (name.isNotBlank()) loadGroup(name)
        }
    }
}
