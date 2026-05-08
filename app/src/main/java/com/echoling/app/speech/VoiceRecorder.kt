package com.echoling.app.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED
}

data class RecordingResult(
    val filePath: String,
    val durationMs: Long
)

@Singleton
class VoiceRecorder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFilePath: String? = null
    private var recordingStartTime: Long = 0L

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

    fun startRecording(): String? {
        val outputDir = File(context.cacheDir, "recordings")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val outputFile = File(outputDir, "recording_${System.currentTimeMillis()}.m4a")
        currentFilePath = outputFile.absolutePath

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(currentFilePath)

            try {
                prepare()
                start()
                recordingStartTime = System.currentTimeMillis()
                _recordingState.value = RecordingState.RECORDING
            } catch (e: Exception) {
                e.printStackTrace()
                release()
                mediaRecorder = null
                return null
            }
        }

        return currentFilePath
    }

    fun pauseRecording() {
        if (_recordingState.value == RecordingState.RECORDING && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.pause()
            _recordingState.value = RecordingState.PAUSED
        }
    }

    fun resumeRecording() {
        if (_recordingState.value == RecordingState.PAUSED && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.resume()
            _recordingState.value = RecordingState.RECORDING
        }
    }

    fun stopRecording(): RecordingResult? {
        val filePath = currentFilePath ?: return null
        val duration = System.currentTimeMillis() - recordingStartTime

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mediaRecorder = null
        _recordingState.value = RecordingState.STOPPED
        _amplitude.value = 0

        return RecordingResult(filePath, duration)
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mediaRecorder = null
        currentFilePath?.let { File(it).delete() }
        currentFilePath = null
        _recordingState.value = RecordingState.IDLE
        _amplitude.value = 0
    }

    fun getMaxAmplitude(): Int {
        return try {
            if (_recordingState.value == RecordingState.RECORDING) {
                val amp = mediaRecorder?.maxAmplitude ?: 0
                _amplitude.value = amp
                amp
            } else {
                0
            }
        } catch (e: Exception) {
            0
        }
    }

    fun release() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null
        _recordingState.value = RecordingState.IDLE
    }
}
