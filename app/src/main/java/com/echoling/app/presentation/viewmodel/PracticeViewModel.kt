package com.echoling.app.presentation.viewmodel

import android.app.Application
import android.media.MediaPlayer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.domain.model.Word
import com.echoling.app.domain.repository.WordRepository
import com.echoling.app.player.AudioPlayer
import com.echoling.app.player.PlaybackState
import com.echoling.app.player.subtitle.Subtitle
import com.echoling.app.player.subtitle.SubtitleMode
import com.echoling.app.player.subtitle.SubtitleParserFactory
import com.echoling.app.speech.RecordingResult
import com.echoling.app.speech.RecordingState
import com.echoling.app.speech.VoiceRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val application: Application,
    private val audioPlayer: AudioPlayer,
    private val subtitleParserFactory: SubtitleParserFactory,
    private val wordRepository: WordRepository,
    private val voiceRecorder: VoiceRecorder
) : AndroidViewModel(application) {

    val playbackState = audioPlayer.playbackState
    val recordingState = voiceRecorder.recordingState

    private val _subtitleMode = MutableStateFlow(SubtitleMode.BILINGUAL)
    val subtitleMode: StateFlow<SubtitleMode> = _subtitleMode.asStateFlow()

    private val _subtitles = MutableStateFlow<List<Subtitle>>(emptyList())
    val subtitles: StateFlow<List<Subtitle>> = _subtitles.asStateFlow()

    private val _currentSubtitleIndex = MutableStateFlow(-1)
    val currentSubtitleIndex: StateFlow<Int> = _currentSubtitleIndex.asStateFlow()

    private var currentSubtitleObj: Subtitle? = null
    private var positionUpdateJob: Job? = null
    private var currentCourseId: String = ""
    private var currentSentenceId: Int = 0

    // For playing single subtitle once
    private var singleSubtitleIndex: Int = -1
    private var singleSubtitleEndMs: Long = 0L

    // Recording playback
    private var mediaPlayer: MediaPlayer? = null
    private val _recordingPath = MutableStateFlow<String?>(null)
    val recordingPath: StateFlow<String?> = _recordingPath.asStateFlow()

    private val _isPlayingRecording = MutableStateFlow(false)
    val isPlayingRecording: StateFlow<Boolean> = _isPlayingRecording.asStateFlow()

    fun initializePlayer() {
        audioPlayer.initialize()
    }

    fun loadMedia(audioUri: String, subtitleUri: String?, courseId: String = "") {
        currentCourseId = courseId
        audioPlayer.setMediaUri(audioUri)

        subtitleUri?.let { uri ->
            loadSubtitles(uri)
        }

        startPositionUpdates()
    }

    private fun loadSubtitles(uri: String) {
        viewModelScope.launch {
            try {
                val content: String?
                val fileName: String

                android.util.Log.d("PracticeViewModel", "loadSubtitles uri: $uri")

                val parsedUri = android.net.Uri.parse(uri)
                if (parsedUri.scheme == "content") {
                    // Content URI (from document picker)
                    content = application.applicationContext.contentResolver.openInputStream(parsedUri)?.use {
                        it.bufferedReader().readText()
                    }
                    fileName = uri.substringAfterLast("/")
                } else {
                    // File path (from internal storage after import)
                    val file = File(uri)
                    android.util.Log.d("PracticeViewModel", "File exists: ${file.exists()}, isFile: ${file.isFile}")
                    content = if (file.exists()) file.readText() else null
                    fileName = file.name
                }

                android.util.Log.d("PracticeViewModel", "Subtitle content length: ${content?.length}, fileName: $fileName")

                if (content != null && content.isNotEmpty()) {
                    val parsed = subtitleParserFactory.parseSubtitles(content, fileName)
                    android.util.Log.d("PracticeViewModel", "Parsed ${parsed.size} subtitles")
                    _subtitles.value = parsed

                    audioPlayer.setSubtitleProvider { positionMs ->
                        val sub = parsed.find {
                            it.startTimeMs <= positionMs && it.endTimeMs >= positionMs
                        }
                        if (sub != null) {
                            currentSubtitleObj = sub
                            currentSentenceId = sub.index
                            _currentSubtitleIndex.value = parsed.indexOf(sub)
                            sub.contentEn
                        } else {
                            null
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                audioPlayer.updatePosition()

                // Check if single subtitle play reached end
                if (singleSubtitleIndex >= 0) {
                    val currentPos = audioPlayer.getCurrentPosition()
                    if (currentPos >= singleSubtitleEndMs) {
                        // Reached end of this subtitle, pause and stay here
                        audioPlayer.pause()
                        singleSubtitleIndex = -1
                    }
                }
                delay(50)
            }
        }
    }

    // Play a single subtitle once and stop
    fun playSubtitleOnce(subtitle: Subtitle) {
        val index = _subtitles.value.indexOf(subtitle)
        singleSubtitleIndex = index
        singleSubtitleEndMs = subtitle.endTimeMs
        seekToSubtitle(subtitle)
        play()
    }

    fun play() {
        audioPlayer.play()
    }

    fun pause() {
        audioPlayer.pause()
        singleSubtitleIndex = -1
    }

    fun seekTo(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    // Seek to specific subtitle sentence
    fun seekToSubtitle(subtitle: Subtitle) {
        audioPlayer.seekTo(subtitle.startTimeMs)
        currentSubtitleObj = subtitle
        currentSentenceId = subtitle.index
        _currentSubtitleIndex.value = _subtitles.value.indexOf(subtitle)
    }

    fun setPlaybackSpeed(speed: Float) {
        audioPlayer.setPlaybackSpeed(speed)
    }

    fun toggleLooping() {
        audioPlayer.setLooping(!playbackState.value.isLooping)
    }

    fun getCurrentSubtitle(): String? {
        return currentSubtitleObj?.getContent(_subtitleMode.value)
    }

    fun setSubtitleMode(mode: SubtitleMode) {
        _subtitleMode.value = mode
    }

    fun cycleSubtitleMode() {
        _subtitleMode.value = when (_subtitleMode.value) {
            SubtitleMode.BILINGUAL -> SubtitleMode.ENGLISH
            SubtitleMode.ENGLISH -> SubtitleMode.CHINESE
            SubtitleMode.CHINESE -> SubtitleMode.BILINGUAL
        }
    }

    fun seekBackward(ms: Long = 5000) {
        val newPosition = (audioPlayer.getCurrentPosition() - ms).coerceAtLeast(0)
        audioPlayer.seekTo(newPosition)
    }

    fun seekForward(ms: Long = 5000) {
        val newPosition = (audioPlayer.getCurrentPosition() + ms).coerceAtLeast(0)
        audioPlayer.seekTo(newPosition)
    }

    fun saveWord(word: String, translation: String, exampleSentence: String) {
        viewModelScope.launch {
            val existingWord = wordRepository.getWord(word)
            if (existingWord == null) {
                val newWord = Word(
                    word = word,
                    phonetic = "",
                    translation = translation,
                    exampleSentence = exampleSentence,
                    sourceCourseId = currentCourseId,
                    sourceSentenceId = currentSentenceId,
                    collectedAt = System.currentTimeMillis(),
                    nextReviewTime = System.currentTimeMillis() + 86400000
                )
                wordRepository.insertWord(newWord)
            }
        }
    }

    // Voice recording functions for 跟读 (shadowing)
    fun startRecording(): Boolean {
        // Stop any playing recording first
        stopPlayingRecording()
        val path = voiceRecorder.startRecording()
        return path != null
    }

    fun pauseRecording() {
        voiceRecorder.pauseRecording()
    }

    fun resumeRecording() {
        voiceRecorder.resumeRecording()
    }

    fun stopRecording(): RecordingResult? {
        val result = voiceRecorder.stopRecording()
        result?.let {
            _recordingPath.value = it.filePath
        }
        return result
    }

    fun cancelRecording() {
        voiceRecorder.cancelRecording()
    }

    // Playback recording
    fun playRecording() {
        val path = _recordingPath.value ?: return

        stopPlayingRecording()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            setOnCompletionListener {
                _isPlayingRecording.value = false
            }
            start()
        }
        _isPlayingRecording.value = true
    }

    fun stopPlayingRecording() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
            }
            release()
        }
        mediaPlayer = null
        _isPlayingRecording.value = false
    }

    fun isRecordingPlaying(): Boolean = _isPlayingRecording.value

    override fun onCleared() {
        super.onCleared()
        positionUpdateJob?.cancel()
        audioPlayer.release()
        voiceRecorder.release()
        stopPlayingRecording()
    }
}
