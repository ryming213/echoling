package com.echoling.app.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import com.echoling.app.domain.model.AutoSubtitleStatus
import com.echoling.app.domain.model.Course
import com.echoling.app.domain.repository.CourseRepository
import com.echoling.app.domain.usecase.ImportCourseUseCase
import com.echoling.app.transcription.AutoTranscriptionScheduler
import com.echoling.app.transcription.AutoTranscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

enum class ImportState {
    IDLE,
    IMPORTING,
    SUCCESS,
    ERROR
}

/**
 * (2026-07-15) Auto-subtitle UX state. Drives the
 * ImportScreen progress UI when the user taps "立即转字幕".
 *
 * IDLE — no auto-subtitle in progress.
 * EXTRACTING — ffmpeg is running on the media file.
 * TRANSCRIBING — Vosk is decoding the WAV.
 * SYNTHESIZING — building the SRT file.
 * COMPLETED — the .srt is on disk, import is about to land.
 */
enum class AutoTranscriptionPhase { IDLE, EXTRACTING, TRANSCRIBING, SYNTHESIZING, COMPLETED }

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val application: Application,
    private val importCourseUseCase: ImportCourseUseCase,
    private val autoTranscriptionScheduler: AutoTranscriptionScheduler,
    private val courseRepository: CourseRepository,
) : AndroidViewModel(application) {

    private val _importState = MutableStateFlow(ImportState.IDLE)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // (2026-07-15) Auto-subtitle plumbing.
    private val _autoTranscriptionPhase = MutableStateFlow(AutoTranscriptionPhase.IDLE)
    val autoTranscriptionPhase: StateFlow<AutoTranscriptionPhase> = _autoTranscriptionPhase.asStateFlow()

    private val _autoTranscriptionProgress = MutableStateFlow(0)
    val autoTranscriptionProgress: StateFlow<Int> = _autoTranscriptionProgress.asStateFlow()

    fun importCourse(
        courseName: String,
        title: String,
        difficulty: String,
        audioUri: Uri?,
        videoUri: Uri?,
        subtitleUri: Uri?,
        durationMs: Long = 0L
    ) {
        viewModelScope.launch {
            _importState.value = ImportState.IMPORTING
            val course = createCourseEntity(
                courseName = courseName,
                title = title,
                difficulty = difficulty,
                audioUri = audioUri,
                videoUri = videoUri,
                subtitleUri = subtitleUri?.let { uri ->
                    val context = application.applicationContext
                    val extension = getFileExtension(uri)
                    val subtitleFileName = "course_${System.currentTimeMillis()}_subtitle.$extension"
                    copyUriToInternalStorage(context, uri, subtitleFileName)?.absolutePath
                },
                durationMs = durationMs,
                autoSubtitleStatus = null,  // user-provided subtitle
            ) ?: run {
                _importState.value = ImportState.ERROR
                return@launch
            }
            importCourseUseCase(course)
            _importState.value = ImportState.SUCCESS
        }
    }

    /**
     * (2026-07-15) Import a course and run auto-subtitle generation
     * immediately, blocking ImportScreen until the SRT is ready.
     *
     * Flow:
     *   1. Copy audio/video to filesDir (same as importCourse).
     *   2. Insert CourseEntity with subtitleUri = null,
     *      autoSubtitleStatus = IN_PROGRESS.
     *   3. Enqueue the worker with REPLACE policy.
     *   4. Observe WorkInfo until SUCCEEDED or FAILED; update
     *      _autoTranscriptionProgress every 1 Hz.
     *   5. On SUCCEEDED, set _importState = SUCCESS so ImportScreen
     *      navigates away.
     */
    fun importCourseWithImmediateTranscription(
        courseName: String,
        title: String,
        difficulty: String,
        audioUri: Uri?,
        videoUri: Uri?,
        durationMs: Long = 0L,
    ) {
        viewModelScope.launch {
            _importState.value = ImportState.IMPORTING
            val course = createCourseEntity(
                courseName = courseName,
                title = title,
                difficulty = difficulty,
                audioUri = audioUri,
                videoUri = videoUri,
                subtitleUri = null,
                durationMs = durationMs,
                autoSubtitleStatus = AutoSubtitleStatus.IN_PROGRESS.dbValue,
            ) ?: run {
                _importState.value = ImportState.ERROR
                return@launch
            }
            importCourseUseCase(course)
            val mediaPath = course.audioUri ?: course.videoUri ?: return@launch

            _autoTranscriptionPhase.value = AutoTranscriptionPhase.EXTRACTING
            autoTranscriptionScheduler.enqueue(course.courseId, mediaPath)

            // Observe WorkInfo; cancel observation when the work
            // reaches a terminal state.
            val workInfoFlow = autoTranscriptionScheduler.observeWorkInfo(course.courseId)
            try {
                workInfoFlow.collect { infos ->
                    val info = infos.firstOrNull() ?: return@collect
                    _autoTranscriptionProgress.value = info.progress.getInt(AutoTranscriptionWorker.KEY_PROGRESS, 0)
                    when (info.state) {
                        WorkInfo.State.RUNNING -> {
                            val progress = info.progress.getInt(AutoTranscriptionWorker.KEY_PROGRESS, 0)
                            _autoTranscriptionPhase.value = when {
                                progress < 30 -> AutoTranscriptionPhase.EXTRACTING
                                progress < 70 -> AutoTranscriptionPhase.TRANSCRIBING
                                else -> AutoTranscriptionPhase.SYNTHESIZING
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            _autoTranscriptionPhase.value = AutoTranscriptionPhase.COMPLETED
                            _importState.value = ImportState.SUCCESS
                            return@collect
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            _errorMessage.value = "字幕识别失败"
                            _importState.value = ImportState.ERROR
                            return@collect
                        }
                        else -> { /* ENQUEUED — wait */ }
                    }
                }
            } catch (e: CancellationException) {
                throw e  // re-throw structured-concurrency cancellation
            }
        }
    }

    /**
     * (2026-07-15) Import a course and enqueue the worker for
     * background transcription. Returns immediately; the user lands
     * in the course list / detail with the status chip showing
     * "字幕识别中".
     */
    fun importCourseWithDeferredTranscription(
        courseName: String,
        title: String,
        difficulty: String,
        audioUri: Uri?,
        videoUri: Uri?,
        durationMs: Long = 0L,
    ) {
        viewModelScope.launch {
            _importState.value = ImportState.IMPORTING
            val course = createCourseEntity(
                courseName = courseName,
                title = title,
                difficulty = difficulty,
                audioUri = audioUri,
                videoUri = videoUri,
                subtitleUri = null,
                durationMs = durationMs,
                autoSubtitleStatus = AutoSubtitleStatus.PENDING.dbValue,
            ) ?: run {
                _importState.value = ImportState.ERROR
                return@launch
            }
            importCourseUseCase(course)
            val mediaPath = course.audioUri ?: course.videoUri ?: return@launch
            autoTranscriptionScheduler.enqueue(course.courseId, mediaPath)
            _importState.value = ImportState.SUCCESS
        }
    }

    /**
     * (2026-07-15) Public for ImportScreen's onDispose.
     * Reset the 2 auto-subtitle state flows when leaving the import screen.
     */
    fun resetAutoTranscriptionState() {
        _autoTranscriptionPhase.value = AutoTranscriptionPhase.IDLE
        _autoTranscriptionProgress.value = 0
    }

    /**
     * Extracts the shared copy / build logic that the three import
     * methods (regular, immediate, deferred) all need. Returns
     * null if the input is invalid (no media, exception).
     */
    private suspend fun createCourseEntity(
        courseName: String,
        title: String,
        difficulty: String,
        audioUri: Uri?,
        videoUri: Uri?,
        subtitleUri: String?,
        durationMs: Long,
        autoSubtitleStatus: String?,
    ): Course? {
        try {
            val context = application.applicationContext
            if (audioUri == null && videoUri == null) {
                _errorMessage.value = "请选择音频或视频文件"
                return null
            }
            val resolvedCourseName = courseName.trim().ifBlank { title.trim() }

            var audioFile: File? = null
            if (audioUri != null) {
                val extension = getMediaExtension(audioUri, "mp3")
                val audioFileName = "course_${System.currentTimeMillis()}_audio.$extension"
                audioFile = copyUriToInternalStorage(context, audioUri, audioFileName)
            }
            var videoFile: File? = null
            if (videoUri != null) {
                val extension = getMediaExtension(videoUri, "mp4")
                val videoFileName = "course_${System.currentTimeMillis()}_video.$extension"
                videoFile = copyUriToInternalStorage(context, videoUri, videoFileName)
            }
            val sourceUri = audioUri ?: videoUri
            val duration = durationMs.takeIf { it > 0 }
                ?: if (sourceUri != null) getMediaDuration(context, sourceUri) else 0L

            return Course(
                courseId = "course_${System.currentTimeMillis()}",
                courseName = resolvedCourseName,
                title = title,
                description = "Imported course: $title",
                difficulty = difficulty,
                audioUri = audioFile?.absolutePath,
                videoUri = videoFile?.absolutePath,
                subtitleUri = subtitleUri,
                durationMs = duration,
                totalSentences = 0,
                thumbnailUri = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                autoSubtitleStatus = AutoSubtitleStatus.fromDbString(autoSubtitleStatus),
                autoSubtitleErrorMessage = null,
                autoSubtitleProgress = 0,
            )
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Unknown error occurred"
            return null
        }
    }

    private fun copyUriToInternalStorage(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val outputDir = File(context.filesDir, "courses")
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            val outputFile = File(outputDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            outputFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getMediaDuration(context: Context, uri: Uri): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    private fun getFileExtension(uri: Uri): String {
        val mimeType = application.contentResolver.getType(uri)
        return when {
            mimeType?.contains("srt") == true -> "srt"
            mimeType?.contains("ass") == true -> "ass"
            mimeType?.contains("ssa") == true -> "ssa"
            else -> uri.lastPathSegment?.substringAfterLast('.', "srt") ?: "srt"
        }
    }

    private fun getMediaExtension(uri: Uri, defaultExt: String): String {
        val mimeType = application.contentResolver.getType(uri)
        return when {
            mimeType?.contains("mp3") == true -> "mp3"
            mimeType?.contains("mpeg") == true -> "mp3"
            mimeType?.contains("mp4") == true -> "mp4"
            mimeType?.contains("mkv") == true -> "mkv"
            mimeType?.contains("webm") == true -> "webm"
            mimeType?.contains("avi") == true -> "avi"
            mimeType?.contains("m4a") == true -> "m4a"
            mimeType?.contains("wav") == true -> "wav"
            mimeType?.contains("flac") == true -> "flac"
            else -> uri.lastPathSegment?.substringAfterLast('.', defaultExt) ?: defaultExt
        }
    }

    fun resetState() {
        _importState.value = ImportState.IDLE
        _errorMessage.value = null
    }
}