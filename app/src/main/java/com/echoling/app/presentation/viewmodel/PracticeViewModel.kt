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
import com.echoling.app.domain.model.LearningProgress
import com.echoling.app.domain.model.ScoreResult
import com.echoling.app.domain.model.Sentence
import com.echoling.app.domain.model.Word
import com.echoling.app.domain.usecase.GetCourseDetailUseCase
import com.echoling.app.domain.usecase.GetCourseSentencesUseCase
import com.echoling.app.domain.usecase.LookupWordUseCase
import com.echoling.app.domain.usecase.SaveProgressUseCase
import com.echoling.app.domain.usecase.SaveWordUseCase
import com.echoling.app.domain.usecase.SyncSentencesUseCase
import com.echoling.app.domain.usecase.UpdateSentenceCompletedUseCase
import com.echoling.app.domain.usecase.UpdateSentenceReadScoreUseCase
import com.echoling.app.domain.usecase.UpdateSentenceTestedUseCase
import com.echoling.app.player.AudioPlayer
import com.echoling.app.player.PlaybackState
import com.echoling.app.player.subtitle.Subtitle
import com.echoling.app.player.subtitle.SubtitleMode
import com.echoling.app.player.subtitle.SubtitleParserFactory
import com.echoling.app.speech.RecordingResult
import com.echoling.app.speech.RecordingState
import com.echoling.app.speech.VoiceRecorder
import com.echoling.app.speech.PronunciationGrader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

data class SentenceState(
    val sentenceId: Int,
    val isCompleted: Boolean = false,
    val isTested: Boolean = false
)

data class TestState(
    val isActive: Boolean = false,
    val testItems: List<Subtitle> = emptyList(),
    val currentTestIndex: Int = 0,
    val revealedWords: Set<Int> = emptySet(),
    val testedCount: Int = 0
)

/**
 * One-shot word-translation request. Reset to [Idle] when the dialog
 * closes so a fresh long-press can issue a new request.
 */
sealed class WordTranslationState {
    object Idle : WordTranslationState()
    data class Loading(val word: String) : WordTranslationState()
    /**
     * A translation arrived from the bundled local dictionary. The
     * practice flow no longer falls back to a network translation API
     * — the local dictionary is the sole source. [phonetic] and [pos]
     * are always present (may be empty strings for entries that lack
     * one).
     */
    data class Loaded(
        val word: String,
        val translation: String,
        val phonetic: String,
        val pos: String,
    ) : WordTranslationState()
    data class Failed(val word: String, val message: String) : WordTranslationState()
}

/**
 * Pronunciation-grading state for the test page's "score this sentence" flow.
 * The state machine is:
 *   Idle → Recording → Loading → (Success | Error)
 *   Success / Error → Recording (user retries) or Idle (cancel)
 */
sealed class GradeState {
    data object Idle : GradeState()
    data object Recording : GradeState()  // user is currently recording their voice
    data object Loading : GradeState()    // recording stopped, grading in progress
    data class Success(
        val result: ScoreResult,
        val sentenceId: Int,
        val recordingPath: String,
    ) : GradeState()
    data class Error(val message: String) : GradeState()
}

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val application: Application,
    private val getCourseDetailUseCase: GetCourseDetailUseCase,
    private val getCourseSentencesUseCase: GetCourseSentencesUseCase,
    private val saveProgressUseCase: SaveProgressUseCase,
    private val updateSentenceCompletedUseCase: UpdateSentenceCompletedUseCase,
    private val updateSentenceTestedUseCase: UpdateSentenceTestedUseCase,
    private val syncSentencesUseCase: SyncSentencesUseCase,
    private val saveWordUseCase: SaveWordUseCase,
    private val audioPlayer: AudioPlayer,
    private val subtitleParserFactory: SubtitleParserFactory,
    private val voiceRecorder: VoiceRecorder,
    private val lookupWordUseCase: LookupWordUseCase,
    private val pronunciationGrader: PronunciationGrader,
    private val updateSentenceReadScoreUseCase: UpdateSentenceReadScoreUseCase,
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

    // §12.21: learn-time accounting.
    //
    // Old design (pre-§12.21) used a single `sessionStartTimeMs` wall-
    // clock and recorded the full elapsed time on every save. Combined
    // with the 10s periodic save, that meant the user got ~10s of
    // "learning time" credited for every 10s they had the Practice
    // screen open — even while paused, in a test mode dialog, or just
    // looking at the subtitle list. Result: 5 minutes of actual
    // listening inflated to 30+ minutes of credited time.
    //
    // New design: only count time when `playbackState.isPlaying` is
    // true. The position loop ticks every 50ms; on each tick we add
    // the delta-since-last-tick to [accumulatedPlayMs] only if the
    // player is currently producing audio. The save body reads the
    // counter via getAndSet(0) so each window is consumed exactly
    // once even if a periodic save and a pause-save race.
    private val accumulatedPlayMs = AtomicLong(0L)
    private val lastTickMs = AtomicLong(0L)

    // Wall-clock of the last periodic save. Used by
    // startPositionUpdates() to throttle the auto-save to once per
    // [PERIODIC_SAVE_INTERVAL_MS] so a crash mid-session loses at
    // most that much progress.
    private var lastPeriodicSaveTimeMs: Long = 0L

    private companion object {
        /** Auto-save cadence while Practice is open. */
        const val PERIODIC_SAVE_INTERVAL_MS: Long = 10_000L
    }

    // For playing single subtitle once
    private var singleSubtitleIndex: Int = -1
    private var singleSubtitleEndMs: Long = 0L
    private var isSinglePlayMode: Boolean = false
    private var skipTargetListIndex: Int = -1
    private var skipTargetTimeoutMs: Long = 0L

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

    // Page switching state
    enum class PracticePage { LISTENING, SPEAKING, TESTING }
    private val _currentPage = MutableStateFlow(PracticePage.LISTENING)
    val currentPage: StateFlow<PracticePage> = _currentPage.asStateFlow()

    // Sentence completion/test states (in-memory cache)
    private val _sentenceStates = MutableStateFlow<Map<Int, SentenceState>>(emptyMap())
    val sentenceStates: StateFlow<Map<Int, SentenceState>> = _sentenceStates.asStateFlow()

    // Test mode state
    private val _testState = MutableStateFlow(TestState())
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    // Long-press word translation state — driven by the long-press handler
    // on ListeningPage / SpeakingPage. [WordTranslationState.Loading] is
    // emitted immediately so the dialog can show a spinner, then either
    // [Loaded] or [Failed] once the network call completes.
    private val _wordTranslation = MutableStateFlow<WordTranslationState>(WordTranslationState.Idle)
    val wordTranslation: StateFlow<WordTranslationState> = _wordTranslation.asStateFlow()

    private var wordTranslationJob: Job? = null

    // ─── Pronunciation grading state (跟读评分) ────────────────────
    // Phases: Idle → Recording → Loading → (Success | Error).
    // The UI owns permission — we only switch state in response to
    // startRecordingForGrading() / stopAndGrade() / cancelGrading().
    private val _gradeState = MutableStateFlow<GradeState>(GradeState.Idle)
    val gradeState: StateFlow<GradeState> = _gradeState.asStateFlow()

    private var gradeJob: Job? = null

    fun setCurrentPage(page: PracticePage) {
        _currentPage.value = page
    }

    fun loadSentenceStates(courseId: String) {
        if (courseId.isBlank()) return
        viewModelScope.launch {
            val sentences = getCourseSentencesUseCase.getByCourseIdSync(courseId)
            val states = sentences.associate { it.sentenceId to SentenceState(
                sentenceId = it.sentenceId,
                isCompleted = it.isCompleted,
                isTested = it.isTested
            ) }
            _sentenceStates.value = states
        }
    }

    fun markSentenceCompleted(sentenceId: Int, completed: Boolean) {
        viewModelScope.launch {
            updateSentenceCompletedUseCase(currentCourseId, sentenceId, completed)
            val current = _sentenceStates.value.toMutableMap()
            current[sentenceId] = current[sentenceId]?.copy(isCompleted = completed)
                ?: SentenceState(sentenceId, isCompleted = completed)
            _sentenceStates.value = current
        }
    }

    fun markSentenceTested(sentenceId: Int, tested: Boolean) {
        viewModelScope.launch {
            updateSentenceTestedUseCase(currentCourseId, sentenceId, tested)
            val current = _sentenceStates.value.toMutableMap()
            current[sentenceId] = current[sentenceId]?.copy(isTested = tested)
                ?: SentenceState(sentenceId, isTested = tested)
            _sentenceStates.value = current
            // Sync testedCount in test state
            if (tested) {
                val ts = _testState.value
                _testState.value = ts.copy(testedCount = ts.testedCount + 1)
            }
        }
    }

    fun startTestMode() {
        val allSubtitles = _subtitles.value.filter { it.index >= 0 }
        val shuffled = allSubtitles.shuffled()
        val testCount = maxOf(1, shuffled.size / 3)
        val testItems = shuffled.take(testCount)
        _testState.value = TestState(
            isActive = true,
            testItems = testItems,
            currentTestIndex = 0,
            revealedWords = emptySet(),
            testedCount = _sentenceStates.value.values.count { it.isTested }
        )
    }

    fun nextTestItem() {
        val current = _testState.value
        if (current.currentTestIndex < current.testItems.size - 1) {
            _testState.value = current.copy(
                currentTestIndex = current.currentTestIndex + 1,
                revealedWords = emptySet()
            )
        }
    }

    fun previousTestItem() {
        val current = _testState.value
        if (current.currentTestIndex > 0) {
            _testState.value = current.copy(
                currentTestIndex = current.currentTestIndex - 1,
                revealedWords = emptySet()
            )
        }
    }

    fun revealTestWord(wordIndex: Int) {
        val current = _testState.value
        _testState.value = current.copy(revealedWords = current.revealedWords + wordIndex)
    }

    fun initializePlayer() {
        audioPlayer.initialize()
    }

    fun loadCourse(courseId: String) {
        if (courseId.isBlank()) return
        currentCourseId = courseId
        // §12.21: counter starts at 0 — we only count time when
        // audio is actually playing, not when the user has the screen
        // open. Don't reset accumulatedPlayMs here in case the user
        // navigates away and comes back: the periodic save (10s) and
        // pause() / onCleared() saves already consume (getAndSet(0))
        // the counter, so a re-entry of loadCourse inherits a clean
        // 0 from those.
        lastTickMs.set(0L)

        viewModelScope.launch {
            try {
                val detail = getCourseDetailUseCase(courseId)
                val course = detail.course
                if (course == null) {
                    android.util.Log.e("PracticeViewModel", "Course not found: $courseId")
                    return@launch
                }

                val savedProgress = detail.progress
                android.util.Log.d("PracticeViewModel", "Loaded saved progress: ${savedProgress?.currentPositionMs}ms")

                if (course.hasVideoContent() && course.videoUri.isNullOrBlank().not()) {
                    loadVideo(course.videoUri!!, course.subtitleUri, courseId)
                } else if (course.hasAudioContent() && course.audioUri.isNullOrBlank().not()) {
                    loadMedia(course.audioUri!!, course.subtitleUri, courseId)
                } else {
                    android.util.Log.e("PracticeViewModel", "No media available for course: $courseId")
                }

                if (savedProgress != null && savedProgress.currentPositionMs > 0) {
                    android.util.Log.d("PracticeViewModel", "Restoring position: ${savedProgress.currentPositionMs}ms")
                    delay(1000)
                    seekTo(savedProgress.currentPositionMs)
                    delay(200)
                    seekTo(savedProgress.currentPositionMs)
                }

                // §12.20: eager save so the Continue Learning card on
                // home picks up this course even if the user kills the
                // app / process dies before pause() or onCleared()
                // get a chance to fire. sessionStartTimeMs was set at
                // the top of loadCourse(), so this initial save writes
                // a 0ms session window — fine, the row exists.
                saveProgress()
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

    private fun syncSentencesToDb(subtitles: List<Subtitle>) {
        if (currentCourseId.isBlank()) return
        viewModelScope.launch {
            val sentences = subtitles.map { sub ->
                com.echoling.app.domain.model.Sentence(
                    courseId = currentCourseId,
                    sentenceId = sub.index,
                    contentEn = sub.contentEn,
                    contentCn = sub.contentCn,
                    startTimeMs = sub.startTimeMs,
                    endTimeMs = sub.endTimeMs
                )
            }
            syncSentencesUseCase(sentences)
        }
    }

    private fun loadSubtitles(uri: String) {
        viewModelScope.launch {
            try {
                val content: String?
                val fileName: String

                android.util.Log.d("PracticeViewModel", "loadSubtitles uri: $uri")

                val parsedUri = android.net.Uri.parse(uri)
                if (parsedUri.scheme == "content") {
                    content = application.applicationContext.contentResolver.openInputStream(parsedUri)?.use {
                        it.bufferedReader().readText()
                    }
                    fileName = uri.substringAfterLast("/")
                } else {
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

                    // Sync sentences to database so completion/test status persists
                    syncSentencesToDb(parsed)

                    audioPlayer.setSubtitleProvider { positionMs ->
                        val sub = parsed.find { sub ->
                            positionMs >= sub.startTimeMs && positionMs <= sub.endTimeMs
                        } ?: parsed.find { sub ->
                            positionMs >= sub.startTimeMs - 100 && positionMs <= sub.endTimeMs + 100
                        }
                        if (sub != null) {
                            val listIndex = parsed.indexOf(sub)
                            android.util.Log.d("PracticeViewModel", "subtitleProvider: pos=$positionMs, matched index=${sub.index}, listIndex=$listIndex, isSinglePlayMode=$isSinglePlayMode, singleSubtitleIndex=$singleSubtitleIndex, skipTargetListIndex=$skipTargetListIndex")
                            currentSubtitleObj = sub
                            currentSentenceId = sub.index
                            if (isSinglePlayMode && listIndex > singleSubtitleIndex) {
                                val currentSub = parsed.getOrNull(singleSubtitleIndex)
                                if (currentSub != null) {
                                    android.util.Log.d("PracticeViewModel", "single play mode: staying on subtitle $singleSubtitleIndex")
                                    return@setSubtitleProvider currentSub.contentEn
                                }
                            }
                            // When skipTargetListIndex is set, only update index when we reach the target
                            if (skipTargetListIndex >= 0) {
                                if (listIndex == skipTargetListIndex) {
                                    _currentSubtitleIndex.value = listIndex
                                    skipTargetListIndex = -1 // Target reached, clear guard
                                    skipTargetTimeoutMs = 0L
                                    android.util.Log.d("PracticeViewModel", "skip target reached: listIndex=$listIndex")
                                } else if (System.currentTimeMillis() > skipTargetTimeoutMs) {
                                    // Timeout: seek took too long, accept whatever we matched
                                    _currentSubtitleIndex.value = listIndex
                                    skipTargetListIndex = -1
                                    skipTargetTimeoutMs = 0L
                                    android.util.Log.d("PracticeViewModel", "skip target timeout: falling back to listIndex=$listIndex")
                                }
                                // else: still seeking, don't update index
                            } else if (!isSinglePlayMode) {
                                _currentSubtitleIndex.value = listIndex
                            }
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
        // §12.20: anchor the periodic-save clock to "now" so the
        // first auto-save fires ~PERIODIC_SAVE_INTERVAL_MS after the
        // player starts, not whenever the previous run of this loop
        // happened to last save. Also reset here (not in the eager
        // loadCourse() save) so a fresh media load starts the
        // cadence over.
        lastPeriodicSaveTimeMs = System.currentTimeMillis()
        // §12.21: reset the per-tick wall-clock so the very first
        // tick after media starts doesn't credit the time between
        // loadCourse() and now as "playing" (it wasn't — the player
        // is paused until the user hits play).
        lastTickMs.set(0L)
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                if (_isVideoMode.value) {
                    videoPlayer?.let { player ->
                        val position = player.currentPosition
                        val duration = player.duration.coerceAtLeast(0)
                        audioPlayer.updatePositionFromExternal(position, duration, player.isPlaying)
                        if (singleSubtitleIndex >= 0 && position >= singleSubtitleEndMs - 500) {
                            pauseVideo()
                            android.util.Log.d("PracticeViewModel", "Video single play ended: position=$position, endMs=$singleSubtitleEndMs")
                            isSinglePlayMode = false
                        }
                    }
                } else {
                    audioPlayer.updatePosition()
                    if (singleSubtitleIndex >= 0) {
                        val currentPos = audioPlayer.getCurrentPosition()
                        if (currentPos >= singleSubtitleEndMs - 500) {
                            audioPlayer.pause()
                            android.util.Log.d("PracticeViewModel", "Single play ended: currentPos=$currentPos, endMs=$singleSubtitleEndMs")
                            singleSubtitleIndex = -1
                            isSinglePlayMode = false
                        }
                    }
                }

                val now = System.currentTimeMillis()

                // §12.21: accumulate playing time. Only add the tick
                // delta when the player is actively producing audio.
                // The 5000ms ceiling guards against pathological
                // deltas (process sleep, debugger break) that would
                // otherwise credit huge chunks of wall-clock time.
                val isPlaying = playbackState.value.isPlaying
                val prevTick = lastTickMs.get()
                if (isPlaying && prevTick > 0L) {
                    val delta = now - prevTick
                    if (delta in 1L..5_000L) {
                        accumulatedPlayMs.addAndGet(delta)
                    }
                }
                lastTickMs.set(now)

                // §12.20: periodic auto-save. Throttled by
                // PERIODIC_SAVE_INTERVAL_MS so a crash mid-session
                // loses at most that much progress. saveProgress()
                // consumes accumulatedPlayMs atomically (getAndSet(0))
                // so periodic vs pause-saves never double-count.
                if (now - lastPeriodicSaveTimeMs >= PERIODIC_SAVE_INTERVAL_MS) {
                    lastPeriodicSaveTimeMs = now
                    saveProgress()
                }
                delay(50)
            }
        }
    }

    fun playSubtitleOnce(subtitle: Subtitle) {
        val index = _subtitles.value.indexOf(subtitle)
        android.util.Log.d("PracticeViewModel", "playSubtitleOnce: subtitle.index=${subtitle.index}, listIndex=$index, total=${_subtitles.value.size}")
        singleSubtitleIndex = index
        singleSubtitleEndMs = subtitle.endTimeMs
        isSinglePlayMode = true
        skipTargetListIndex = -1
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
        skipTargetListIndex = -1
        saveProgress()
    }

    fun seekTo(positionMs: Long) {
        if (_isVideoMode.value) {
            seekVideoTo(positionMs)
        } else {
            audioPlayer.seekTo(positionMs)
        }
    }

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

    fun skipToSubtitle(index: Int? = null) {
        isSinglePlayMode = false
        singleSubtitleIndex = -1
        val targetIndex = index ?: _currentSubtitleIndex.value
        if (targetIndex >= 0 && targetIndex < _subtitles.value.size) {
            val targetSubtitle = _subtitles.value[targetIndex]
            currentSubtitleObj = targetSubtitle
            currentSentenceId = targetSubtitle.index
            _currentSubtitleIndex.value = targetIndex
            skipTargetListIndex = targetIndex
            skipTargetTimeoutMs = System.currentTimeMillis() + 3000
            seekTo(targetSubtitle.startTimeMs)
        }
    }

    fun skipToPreviousSubtitle() {
        isSinglePlayMode = false
        singleSubtitleIndex = -1
        val currentIndex = _currentSubtitleIndex.value
        val newIndex = if (currentIndex > 0) currentIndex - 1 else 0
        if (_subtitles.value.isNotEmpty()) {
            val targetSubtitle = _subtitles.value[newIndex]
            android.util.Log.d("PracticeViewModel", "skipToPreviousSubtitle: currentIndex=$currentIndex, newIndex=$newIndex, targetSubtitleIndex=${targetSubtitle.index}")
            currentSubtitleObj = targetSubtitle
            currentSentenceId = targetSubtitle.index
            _currentSubtitleIndex.value = newIndex
            skipTargetListIndex = newIndex
            skipTargetTimeoutMs = System.currentTimeMillis() + 3000
            seekTo(targetSubtitle.startTimeMs)
        }
    }

    fun skipToNextSubtitle() {
        isSinglePlayMode = false
        singleSubtitleIndex = -1
        val currentIndex = _currentSubtitleIndex.value
        val newIndex = if (currentIndex < _subtitles.value.size - 1) currentIndex + 1 else _subtitles.value.size - 1
        if (_subtitles.value.isNotEmpty()) {
            val targetSubtitle = _subtitles.value[newIndex.coerceAtLeast(0)]
            android.util.Log.d("PracticeViewModel", "skipToNextSubtitle: currentIndex=$currentIndex, newIndex=$newIndex, targetSubtitleIndex=${targetSubtitle.index}")
            currentSubtitleObj = targetSubtitle
            currentSentenceId = targetSubtitle.index
            _currentSubtitleIndex.value = newIndex
            skipTargetListIndex = newIndex
            skipTargetTimeoutMs = System.currentTimeMillis() + 3000
            seekTo(targetSubtitle.startTimeMs)
        }
    }

    fun saveWord(
        word: String,
        translation: String,
        exampleSentence: String,
        phonetic: String = "",
        pos: String = "",
    ) {
        viewModelScope.launch {
            val newWord = Word(
                word = word,
                phonetic = phonetic,
                pos = pos,
                translation = translation,
                exampleSentence = exampleSentence,
                sourceCourseId = currentCourseId,
                sourceSentenceId = currentSentenceId,
                collectedAt = System.currentTimeMillis(),
                nextReviewTime = System.currentTimeMillis() + 86400000
            )
            saveWordUseCase(newWord)
        }
    }

    /**
     * Look up a single word in the bundled local dictionary
     * (CET-6 / Gaokao list). The result is published to
     * [wordTranslation] as a [WordTranslationState] flow. If a previous
     * request is still in flight it is cancelled — only the latest
     * long-press wins.
     *
     * The practice flow is local-only: there is no network translation
     * fallback. If the word is not in the bundled list, we surface a
     * [WordTranslationState.Failed] with a "未在本地词典中找到本词"
     * message — the user can still type a translation manually in the
     * dialog and save.
     */
    fun requestWordTranslation(word: String) {
        val clean = word.trim()
        if (clean.isEmpty()) return
        wordTranslationJob?.cancel()
        _wordTranslation.value = WordTranslationState.Loading(clean)
        wordTranslationJob = viewModelScope.launch {
            try {
                val local = lookupWordUseCase(clean)
                if (local != null) {
                    _wordTranslation.value = WordTranslationState.Loaded(
                        word = clean,
                        translation = local.translation,
                        phonetic = local.phonetic,
                        pos = local.pos,
                    )
                } else {
                    _wordTranslation.value = WordTranslationState.Failed(
                        clean,
                        "未在本地词典中找到本词，请手动输入翻译",
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("PracticeViewModel", "Local dictionary exception", e)
                _wordTranslation.value = WordTranslationState.Failed(
                    clean,
                    "翻译异常：${e.message ?: e.javaClass.simpleName}",
                )
            }
        }
    }

    /** Reset the translation state — call when the dialog dismisses. */
    fun clearWordTranslation() {
        wordTranslationJob?.cancel()
        _wordTranslation.value = WordTranslationState.Idle
    }

    // Voice recording functions for 跟读 (shadowing)
    fun startRecording(): Boolean {
        stopPlayingRecording()
        return try {
            voiceRecorder.startRecording()
            true
        } catch (e: Exception) {
            false
        }
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

    // ─── Pronunciation grading (跟读评分) ────────────────────────────

    /**
     * Start recording the user's voice for pronunciation scoring.
     * Safe to call repeatedly — if a previous recording is in progress it
     * will be cancelled first.
     *
     * UI flow: the caller is expected to have already verified / requested
     * RECORD_AUDIO permission. We don't check permission here because the
     * caller (Composable) holds the launcher.
     */
    fun startRecordingForGrading() {
        stopPlayingRecording()
        cancelGradingInternal()  // reset any in-flight grading state
        val started = startRecording()
        if (started) {
            _gradeState.value = GradeState.Recording
        } else {
            _gradeState.value = GradeState.Error("录音启动失败")
        }
    }

    /**
     * Stop the current recording and trigger pronunciation grading.
     * No-op if not currently recording.
     *
     * **Defensive**: the entire grading coroutine is wrapped in a
     * try/catch + CoroutineExceptionHandler. PronunciationGrader
     * returns Result.failure for known error paths (TTS failed, decode
     * failed, etc.), but if anything inside the grading pipeline
     * throws an unexpected exception (e.g. an uncaught NPE in a
     * third-party TTS engine), we convert it to GradeState.Error
     * instead of letting it bubble to Thread.UncaughtExceptionHandler
     * and crash the process.
     */
    fun stopAndGrade() {
        val recordingState = _gradeState.value
        if (recordingState != GradeState.Recording) {
            android.util.Log.w("PracticeViewModel", "stopAndGrade called but not recording (state=$recordingState)")
            return
        }
        val result = stopRecording()
        if (result == null || result.filePath.isBlank()) {
            _gradeState.value = GradeState.Error("录音失败")
            return
        }
        val sentence = currentGradingSentence()
        if (sentence == null) {
            _gradeState.value = GradeState.Error("找不到当前测试句子")
            return
        }
        _gradeState.value = GradeState.Loading
        gradeJob?.cancel()
        val safeExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
            android.util.Log.e("PracticeViewModel", "Unhandled exception during grading — converting to Error state", e)
            _gradeState.value = GradeState.Error("评分异常：${e.javaClass.simpleName} - ${e.message ?: "(no message)"}")
        }
        gradeJob = viewModelScope.launch(safeExceptionHandler) {
            try {
                pronunciationGrader.grade(sentence, result.filePath)
                    .onSuccess { scoreResult ->
                        _gradeState.value = GradeState.Success(
                            result = scoreResult,
                            sentenceId = sentence.sentenceId,
                            recordingPath = result.filePath,
                        )
                        // Persist the score to DB
                        runCatching {
                            updateSentenceReadScoreUseCase(
                                courseId = currentCourseId,
                                sentenceId = sentence.sentenceId,
                                score = scoreResult.total,
                            )
                        }.onFailure {
                            android.util.Log.w("PracticeViewModel", "Failed to persist readScore", it)
                        }
                    }
                    .onFailure { e ->
                        _gradeState.value = GradeState.Error("评分失败：${e.javaClass.simpleName} - ${e.message ?: e.javaClass.simpleName}")
                    }
            } catch (e: Throwable) {
                // Last-ditch catch: any exception that escaped
                // PronunciationGrader.grade() (which is supposed to
                // return Result.failure, never throw) becomes an Error
                // state instead of crashing the process.
                android.util.Log.e("PracticeViewModel", "Caught exception escaping PronunciationGrader.grade()", e)
                _gradeState.value = GradeState.Error("评分异常：${e.javaClass.simpleName} - ${e.message ?: "(no message)"}")
            }
        }
    }

    /**
     * Cancel any in-flight grading and reset to Idle. Safe to call at any
     * time. If the user was mid-recording, cancels the recording too.
     */
    fun cancelGrading() {
        cancelGradingInternal()
    }

    private fun cancelGradingInternal() {
        gradeJob?.cancel()
        gradeJob = null
        if (_gradeState.value is GradeState.Recording) {
            cancelRecording()
        }
        stopPlayingRecording()
        _gradeState.value = GradeState.Idle
    }

    /**
     * Helper: get the Sentence domain object for the current test item.
     * The test item is a Subtitle (player layer); we need a Sentence to
     * pass to PronunciationGrader. We have startTimeMs / endTimeMs / index
     * / contentEn on the Subtitle, so we can construct a minimal Sentence
     * here. We use currentCourseId for the courseId.
     */
    private fun currentGradingSentence(): Sentence? {
        val testState = _testState.value
        val sub = testState.testItems.getOrNull(testState.currentTestIndex) ?: return null
        return Sentence(
            courseId = currentCourseId,
            sentenceId = sub.index,
            contentEn = sub.contentEn,
            contentCn = sub.contentCn,
            startTimeMs = sub.startTimeMs,
            endTimeMs = sub.endTimeMs,
        )
    }

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

    fun saveProgress() {
        viewModelScope.launch {
            try {
                val currentPos = if (_isVideoMode.value) getVideoCurrentPosition() else audioPlayer.getCurrentPosition()
                val duration = if (_isVideoMode.value) getVideoDuration() else audioPlayer.getDuration()

                // §12.21: read-and-reset the playing-time counter
                // atomically. Even if a periodic save (from the
                // position loop) and a pause-save (from the user
                // hitting back / home button) fire at the same time,
                // getAndSet(0) hands the full accumulated window to
                // exactly one of them — no double-count, no mutex
                // needed. (The old design needed a Mutex + wall-clock
                // anchor reset; the atomic counter replaces both.)
                val sessionTimeMs = accumulatedPlayMs.getAndSet(0L)

                val existingProgress = saveProgressUseCase.getProgressByCourseId(currentCourseId)
                val newProgress = LearningProgress(
                    courseId = currentCourseId,
                    currentPositionMs = currentPos,
                    currentSentenceId = currentSentenceId,
                    learnedSentences = existingProgress?.learnedSentences ?: 0,
                    totalLearnTimeMs = (existingProgress?.totalLearnTimeMs ?: 0) + sessionTimeMs,
                    lastLearnTime = System.currentTimeMillis(),
                    finishRate = if (duration > 0) currentPos.toFloat() / duration.toFloat() else 0f
                )
                saveProgressUseCase(newProgress)
                // Reset the per-tick anchor so the next session's
                // first tick doesn't credit any pre-save time.
                lastTickMs.set(0L)
                android.util.Log.d("PracticeViewModel", "Progress saved: courseId=$currentCourseId, pos=$currentPos, duration=$duration, sessionTime=$sessionTimeMs")
            } catch (e: Exception) {
                android.util.Log.e("PracticeViewModel", "Error saving progress: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        positionUpdateJob?.cancel()
        audioPlayer.release()
        videoPlayer?.release()
        voiceRecorder.release()
        stopPlayingRecording()
        saveProgress()
    }
}
