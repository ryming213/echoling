package com.echoling.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.domain.model.Course
import com.echoling.app.domain.repository.CourseRepository
import com.echoling.app.domain.usecase.DeleteCourseUseCase
import com.echoling.app.domain.usecase.GetCourseGroupsUseCase
import com.echoling.app.transcription.AutoTranscriptionScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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

    // (2026-07-18) Single in-flight loader job. Without this, a
    // second `loadGroup(courseName)` call (e.g. screen recomposes
    // with the same key) starts a second concurrent collect on the
    // same flow — both fight to write `_uiState.value` and the room
    // emission handler runs twice. Cancelling the previous job keeps
    // exactly one collector alive.
    private var loadGroupJob: Job? = null

    fun loadGroup(courseName: String) {
        loadGroupJob?.cancel()
        loadGroupJob = viewModelScope.launch {
            _uiState.value = CourseDetailUiState(isLoading = true, courseName = courseName)
            try {
                // (2026-07-18) Was `.first()` — bug: that captured a
                // single snapshot, so any subsequent Room UPDATE to a
                // course in this group (e.g. auto-subtitle worker
                // transitioning PENDING → IN_PROGRESS → READY while
                // the user sat on this screen) did NOT refresh the UI.
                // The user had to navigate to Practice and back so the
                // LaunchedEffect(courseName) re-launched this method.
                // Switch to `collect` so the list stays live.
                getCourseGroups()
                    .map { groups -> groups.firstOrNull { it.courseName == courseName } }
                    // Distinct-by-contents avoids redundant _uiState
                    // writes when an unrelated course in another group
                    // changes (e.g. a delete on a different group).
                    // List<Course> is data-class — equality compares
                    // every field, including autoSubtitleProgress which
                    // ticks 1Hz during transcription. We deliberately
                    // accept those mid-transcription re-renders so the
                    // bar visually moves while the user is on this
                    // screen.
                    .collect { match ->
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
                    }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // (2026-07-18) loadGroupJob?.cancel() above throws
                // CancellationException out of the previous .collect {}.
                // CancellationException extends RuntimeException, so
                // `catch (e: Exception)` happily swallows it — but
                // doing so briefly sets _uiState.error to non-null
                // ("StandaloneCoroutine was cancelled" or similar),
                // which CourseDetailScreen's `uiState.error != null`
                // branch renders as a red centered Text for one frame
                // before the next collect clears it. Three user-visible
                // triggers all hit this race:
                //   1. Tap the PENDING/IN_PROGRESS chip → reload.
                //   2. Delete a course from CourseDetailScreen.
                //   3. Delete a course from CoursesScreen (which
                //      pops back here and re-triggers loadGroup).
                // Re-throw so structured concurrency propagates
                // cancellation cleanly and the UI never flashes red.
                throw e
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
