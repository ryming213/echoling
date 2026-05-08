package com.echoling.app.presentation.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.domain.model.Course
import com.echoling.app.domain.repository.CourseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val application: Application,
    private val courseRepository: CourseRepository
) : AndroidViewModel(application) {

    private val _importState = MutableStateFlow(ImportState.IDLE)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun importCourse(
        title: String,
        audioUri: Uri,
        subtitleUri: Uri?,
        durationMs: Long = 0L
    ) {
        viewModelScope.launch {
            _importState.value = ImportState.IMPORTING

            try {
                val context = application.applicationContext

                // Copy audio file to app's internal storage
                val audioFileName = "course_${System.currentTimeMillis()}_audio.mp3"
                val audioFile = copyUriToInternalStorage(context, audioUri, audioFileName)

                if (audioFile == null) {
                    _errorMessage.value = "Failed to import audio file"
                    _importState.value = ImportState.ERROR
                    return@launch
                }

                // Copy subtitle file if provided
                var subtitleFile: File? = null
                if (subtitleUri != null) {
                    val extension = getFileExtension(subtitleUri)
                    val subtitleFileName = "course_${System.currentTimeMillis()}_subtitle.$extension"
                    subtitleFile = copyUriToInternalStorage(context, subtitleUri, subtitleFileName)
                }

                // Create course entity
                val course = Course(
                    courseId = "course_${System.currentTimeMillis()}",
                    title = title,
                    description = "Imported course: $title",
                    difficulty = "Intermediate",
                    audioUri = audioFile.absolutePath,
                    subtitleUri = subtitleFile?.absolutePath,
                    durationMs = durationMs,
                    totalSentences = 0,
                    thumbnailUri = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                courseRepository.insertCourse(course)

                _importState.value = ImportState.SUCCESS

            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Unknown error occurred"
                _importState.value = ImportState.ERROR
            }
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

    private fun getFileExtension(uri: Uri): String {
        val mimeType = application.contentResolver.getType(uri)
        return when {
            mimeType?.contains("srt") == true -> "srt"
            mimeType?.contains("ass") == true -> "ass"
            mimeType?.contains("ssa") == true -> "ssa"
            else -> uri.lastPathSegment?.substringAfterLast('.', "srt") ?: "srt"
        }
    }

    fun resetState() {
        _importState.value = ImportState.IDLE
        _errorMessage.value = null
    }
}
