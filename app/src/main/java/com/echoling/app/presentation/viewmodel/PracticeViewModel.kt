package com.echoling.app.presentation.viewmodel

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.echoling.app.domain.model.Course
import com.echoling.app.domain.model.Word
import com.echoling.app.domain.repository.CourseRepository
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
    private val courseRepository: CourseRepository,
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

    // Video player for video playback
    private var videoPlayer: ExoPlayer? = null
    private val _videoPlayer = MutableStateFlow<ExoPlayer?>(null)
    val videoPlayerState: StateFlow<ExoPlayer?> = _videoPlayer.asStateFlow()

    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()

    fun initializePlayer() {
        audioPlayer.initialize()
    }

    fun loadCourse(courseId: String) {
        if (courseId.isBlank()) return
        currentCourseId = courseId

        viewModelScope.launch {
            try {
                val course = courseRepository.getCourseById(courseId)
                if (course == null) {
                    android.util.Log.e("PracticeViewModel", "Course not found: $courseId")
                    return@launch
                }

                if (course.hasVideo() && course.videoUri.isNullOrBlank().not()) {
                    loadVideo(course.videoUri!!, course.subtitleUri, courseId)
                } else if (course.hasAudio() && course.audioUri.isNullOrBlank().not()) {
                    loadMedia(course.audioUri!!, course.subtitleUri, courseId)
                } else {
                    android.util.Log.e("PracticeViewModel", "No media available for course: $courseId")
                }
            } catch (e: Exception) {
                android.util.Log.e("PracticeViewModel", "Error loading course: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun loadMedia(audioUri: String, subtitleUri: String?, courseId: String = "") {
        if (audioUri.isBlank()) return
        currentCourseId = courseId
        _isVideoMode.value = false

        try {
            audioPlayer.setMediaUri(audioUri)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        subtitleUri?.let { uri ->
            if (uri.isNotBlank()) {
                loadSubtitles(uri)
            }
        }

        startPositionUpdates()
    }

    fun loadVideo(videoUri: String, subtitleUri: String?, courseId: String = "") {
        if (videoUri.isBlank()) return
        currentCourseId = courseId
        _isVideoMode.value = true

        try {
            // Initialize video player
            if (videoPlayer == null) {
                videoPlayer = ExoPlayer.Builder(application).build().apply {
                    addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            // Sync with audio player state if needed
                        }

                        override fun onPlaybackStateChanged(state: Int) {
                            if (state == Player.STATE_READY) {
                                // Video is ready
                            }
                        }
                    })
                }
                _videoPlayer.value = videoPlayer
            }

            videoPlayer?.apply {
                val mediaItem = MediaItem.fromUri(Uri.parse(videoUri))
                setMediaItem(mediaItem)
                prepare()
            }
            audioPlayer.setVideoPlayer(videoPlayer)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        subtitleUri?.let { uri ->
            if (uri.isNotBlank()) {
                loadSubtitles(uri)
            }
        }

        startPositionUpdates()
    }

    fun playVideo() {
        videoPlayer?.play()
        // Update audio player state to reflect playing
        audioPlayer.play()
    }

    fun pauseVideo() {
        videoPlayer?.pause()
        audioPlayer.pause()
        singleSubtitleIndex = -1
    }

    fun seekVideoTo(positionMs: Long) {
        videoPlayer?.seekTo(positionMs)
        audioPlayer.seekTo(positionMs)
    }

    fun getVideoCurrentPosition(): Long = videoPlayer?.currentPosition ?: 0L

    fun getVideoDuration(): Long = videoPlayer?.duration?.coerceAtLeast(0) ?: 0L

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
                        // Find exact matching subtitle first, then try tolerance
                        val sub = parsed.find { sub ->
                            positionMs >= sub.startTimeMs && positionMs <= sub.endTimeMs
                        } ?: parsed.find { sub ->
                            // Small tolerance: within 100ms before start or after end
                            positionMs >= sub.startTimeMs - 100 && positionMs <= sub.endTimeMs + 100
                        }
                        if (sub != null) {
                            android.util.Log.d("PracticeViewModel", "subtitleProvider: pos=$positionMs, matched index=${sub.index}, listIndex=${parsed.indexOf(sub)}")
                            currentSubtitleObj = sub
                            currentSentenceId = sub.index
                            _currentSubtitleIndex.value = parsed.indexOf(sub)
                            sub.contentEn
                        } else {
                            android.util.Log.d("PracticeViewModel", "subtitleProvider: pos=$positionMs, no match found")
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
                if (_isVideoMode.value) {
                    videoPlayer?.let { player ->
                        val position = player.currentPosition
                        val duration = player.duration.coerceAtLeast(0)
                        // Update playback state from video player
                        audioPlayer.updatePositionFromExternal(position, duration, player.isPlaying)
                        // Check if single subtitle play reached end
                        if (singleSubtitleIndex >= 0 && position >= singleSubtitleEndMs) {
                            pauseVideo()
                        }
                    }
                } else {
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
                }
                delay(50)
            }
        }
    }

    // Play a single subtitle once and stop
    fun playSubtitleOnce(subtitle: Subtitle) {
        val index = _subtitles.value.indexOf(subtitle)
        android.util.Log.d("PracticeViewModel", "playSubtitleOnce: subtitle.index=${subtitle.index}, listIndex=$index, total=${_subtitles.value.size}")
        singleSubtitleIndex = index
        singleSubtitleEndMs = subtitle.endTimeMs
        seekToSubtitle(subtitle)
        if (_isVideoMode.value) {
            playVideo()
        } else {
            play()
        }
    }

    fun play() {
        if (_isVideoMode.value) {
            playVideo()
        } else {
            audioPlayer.play()
        }
    }

    fun pause() {
        if (_isVideoMode.value) {
            pauseVideo()
        } else {
            audioPlayer.pause()
        }
        singleSubtitleIndex = -1
    }

    fun seekTo(positionMs: Long) {
        if (_isVideoMode.value) {
            seekVideoTo(positionMs)
        } else {
            audioPlayer.seekTo(positionMs)
        }
    }

    // Seek to specific subtitle sentence
    fun seekToSubtitle(subtitle: Subtitle) {
        android.util.Log.d("PracticeViewModel", "seekToSubtitle: index=${subtitle.index}, startTime=${subtitle.startTimeMs}")
        if (_isVideoMode.value) {
            seekVideoTo(subtitle.startTimeMs)
        } else {
            audioPlayer.seekTo(subtitle.startTimeMs)
        }
        currentSubtitleObj = subtitle
        currentSentenceId = subtitle.index
        _currentSubtitleIndex.value = _subtitles.value.indexOf(subtitle)
    }

    fun setPlaybackSpeed(speed: Float) {
        audioPlayer.setPlaybackSpeed(speed)
        videoPlayer?.setPlaybackSpeed(speed)
    }

    fun toggleLooping() {
        val newLooping = !playbackState.value.isLooping
        audioPlayer.setLooping(newLooping)
        videoPlayer?.repeatMode = if (newLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
        val currentPos = if (_isVideoMode.value) getVideoCurrentPosition() else audioPlayer.getCurrentPosition()
        val newPosition = (currentPos - ms).coerceAtLeast(0)
        seekTo(newPosition)
    }

    fun seekForward(ms: Long = 5000) {
        val currentPos = if (_isVideoMode.value) getVideoCurrentPosition() else audioPlayer.getCurrentPosition()
        val newPosition = (currentPos + ms).coerceAtLeast(0)
        seekTo(newPosition)
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
        videoPlayer?.release()
        voiceRecorder.release()
        stopPlayingRecording()
    }
}
