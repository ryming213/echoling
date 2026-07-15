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
import com.echoling.app.domain.model.Sentence
import com.echoling.app.domain.model.Word
import com.echoling.app.domain.usecase.GetCourseDetailUseCase
import com.echoling.app.domain.usecase.GetCourseSentencesUseCase
import com.echoling.app.domain.usecase.LookupWordUseCase
import com.echoling.app.domain.usecase.SaveProgressUseCase
import com.echoling.app.domain.usecase.SaveWordUseCase
import com.echoling.app.domain.usecase.SyncSentencesUseCase
import com.echoling.app.domain.usecase.UpdateSentenceCompletedUseCase
import com.echoling.app.domain.usecase.UpdateSentenceTestedUseCase
import com.echoling.app.player.AudioPlayer
import com.echoling.app.player.PlaybackState
import com.echoling.app.player.subtitle.Subtitle
import com.echoling.app.player.subtitle.SubtitleParserFactory
import com.echoling.app.speech.RecordingResult
import com.echoling.app.speech.RecordingState
import com.echoling.app.speech.VoiceRecorder
import com.echoling.app.speech.WordMatcher
import com.echoling.app.speech.WavRecorder
import com.echoling.app.speech.VoskSpeechRecognizer
import com.echoling.app.speech.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
 * Testing-tab STT state machine.
 * Idle → Listening → Transcribed → (Passed | Failed).
 * User can cancel from Listening back to Idle, or reset from any state.
 */
sealed class SttTestState {
    data object Idle : SttTestState()
    data class Listening(val elapsedMs: Long = 0L) : SttTestState()
    /** Vosk is transcribing the recorded WAV file. Shown briefly
     *  after the user releases the mic, before the editor appears. */
    data class Transcribing(val recordingPath: String) : SttTestState()
    data class Transcribed(val text: String) : SttTestState()
    data class Passed(val text: String) : SttTestState()
    data class Failed(
        val transcribed: String,
        val original: String,
        val origWords: List<String>,
        val transWords: List<String>,
        // (2026-06-28) Per-word match flags from WordMatcher.
        // Index-aligned with origWords / transWords. Drives the
        // per-chip color in TestResultCard.WordChipsRow — the
        // previous version did strict position-by-position
        // comparison, which caused a single wrong word to mark
        // all subsequent (correct) words as wrong too. With
        // these flags the UI can mark each word independently.
        val origMatched: BooleanArray = BooleanArray(0),
        val transMatched: BooleanArray = BooleanArray(0),
        val reason: String
    ) : SttTestState()
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
    private val wavRecorder: WavRecorder,
    private val voskRecognizer: VoskSpeechRecognizer,
    private val modelManager: ModelManager,
) : AndroidViewModel(application) {

    val playbackState = audioPlayer.playbackState
    val recordingState = voiceRecorder.recordingState

    /** Vosk model download state — surfaced to TestingPage so the user
     *  sees a "downloading model" message on first use. */
    val modelDownloadState = modelManager.downloadState
    val wavRecordingState = wavRecorder.recordingState
    val wavAmplitude = wavRecorder.amplitude

    private val _subtitles = MutableStateFlow<List<Subtitle>>(emptyList())
    val subtitles: StateFlow<List<Subtitle>> = _subtitles.asStateFlow()

    // Page switching state. Declared before [_subtitleIndexByPage] /
    // [currentSubtitleIndex] so the derived StateFlow below can
    // reference [_currentPage] (forward references aren't allowed in
    // class bodies).
    enum class PracticePage { LISTENING, SPEAKING, TESTING }
    private val _currentPage = MutableStateFlow(PracticePage.LISTENING)
    val currentPage: StateFlow<PracticePage> = _currentPage.asStateFlow()

    // Per-page subtitle index. v2: the three practice pages (泛听 / 精听 /
    // 测试) each track their own position in the subtitle list, so
    // switching tabs no longer makes one page jump to another page's
    // last position.
    //
    // TESTING actually uses [_testState]'s `currentTestIndex` for its
    // own progress (it has a separate test-item list, see
    // [startTestMode]). We still include TESTING in the map for
    // symmetry — its entry just stays at -1 since TESTING never
    // updates it.
    //
    // Initial value -1 means "no subtitle focused yet" (before any
    // playback or skip action).
    private val _subtitleIndexByPage = MutableStateFlow<Map<PracticePage, Int>>(
        mapOf(
            PracticePage.LISTENING to -1,
            PracticePage.SPEAKING to -1,
            PracticePage.TESTING to -1,
        )
    )
    val subtitleIndexByPage: StateFlow<Map<PracticePage, Int>> = _subtitleIndexByPage.asStateFlow()

    /**
     * UI-bound: the current page's subtitle index.
     *
     * The UI doesn't need to know there's a per-page map — it just
     * reads the right value for the page it's on. Switching pages
     * (via [setCurrentPage]) automatically updates this derived flow
     * because [combine] reacts to [_currentPage] changes.
     */
    val currentSubtitleIndex: StateFlow<Int> = combine(
        _currentPage, _subtitleIndexByPage
    ) { page, map -> map[page] ?: -1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, -1)

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

        /**
         * Minimum press-and-hold duration to start STT, in milliseconds.
         * Below this, we treat the press as accidental and return to Idle
         * without invoking the recognizer — Android's SpeechRecognizer needs
         * ~300ms to initialize, and stopping it before that fires
         * ERROR_NO_MATCH which the user perceives as a broken button.
         */
        const val STT_MIN_HOLD_MS: Long = 300L
    }

    // For playing single subtitle once
    private var singleSubtitleIndex: Int = -1
    private var singleSubtitleEndMs: Long = 0L
    private var isSinglePlayMode: Boolean = false
    private var skipTargetListIndex: Int = -1
    private var skipTargetTimeoutMs: Long = 0L

    // Recording playback
    private var mediaPlayer: MediaPlayer? = null
    // (2026-06-28) Per-page recording paths. Previously a single
    // _recordingPath was shared between SpeakingPage (via
    // stopRecording → VoiceRecorder) and TestingPage (via
    // stopStt → WavRecorder for Vosk). The shared flow meant the
    // recording made on one page leaked into the other page's
    // "播放录音" / 回放录音 button — e.g. recording on the test
    // page, switching to the speaking page, the speaking page
    // would show "我的录音 / 点击播放" with the test page's audio.
    // Each page now owns its own path; [playRecording] takes the
    // path as a parameter so the caller is explicit about which
    // recording to play.
    private val _speakingRecordingPath = MutableStateFlow<String?>(null)
    val speakingRecordingPath: StateFlow<String?> = _speakingRecordingPath.asStateFlow()

    private val _testRecordingPath = MutableStateFlow<String?>(null)
    val testRecordingPath: StateFlow<String?> = _testRecordingPath.asStateFlow()

    private val _isPlayingRecording = MutableStateFlow(false)
    val isPlayingRecording: StateFlow<Boolean> = _isPlayingRecording.asStateFlow()

    // Video player for video playback
    private var videoPlayer: ExoPlayer? = null
    private val _videoPlayer = MutableStateFlow<ExoPlayer?>(null)
    val videoPlayerState: StateFlow<ExoPlayer?> = _videoPlayer.asStateFlow()

    private val _isVideoMode = MutableStateFlow(false)
    val isVideoMode: StateFlow<Boolean> = _isVideoMode.asStateFlow()

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

    // ─── STT word-match testing state (跟读测试) ──────────────────────
    // Phases: Idle → Listening → Transcribed → (Passed | Failed).
    private val _sttTestState = MutableStateFlow<SttTestState>(SttTestState.Idle)
    val sttTestState: StateFlow<SttTestState> = _sttTestState.asStateFlow()

    // 5 random amplitude bars for v1 recording overlay animation.
    private val _sttAmplitudeBars = MutableStateFlow(List(5) { 0.4f })
    val sttAmplitudeBars: StateFlow<List<Float>> = _sttAmplitudeBars.asStateFlow()

    private var sttElapsedJob: Job? = null
    private var sttEventCollectionJob: Job? = null
    private var sttWaitJob: Job? = null
    private var sttPressStartTimeMs: Long = 0L

    /**
     * Slot for the latest STT result text. The Vosk transcribe
     * coroutine writes here when it completes; it does NOT change
     * the state machine directly. [stopStt] is the only place that
     * reads this and performs the Transcribed(...) transition.
     *
     * v2: the recognizer is Vosk (offline, file-based), not the
     * system SpeechRecognizer. The "early error races the user's
     * release" failure mode of v1 is gone because Vosk runs after
     * the user releases, not in parallel with the recording.
     *
     * null = result not yet arrived (Vosk still running).
     * ""   = Vosk returned empty / errored, treat as no recognition.
     * "…"  = Vosk returned the recognized text.
     */
    private var sttResult: String? = null

    fun setCurrentPage(page: PracticePage) {
        if (_currentPage.value != page) {
            // Tab-switch bug fix (2026-06-27): reset audioPlayer state
            // and clear per-tab playback context so each tab has
            // independent playback state. Without this reset, the
            // 50ms position-update loop keeps ticking against the
            // previous tab's frozen audioPlayer position; the
            // subtitleProvider callback (see loadSubtitles) then
            // finds the matching subtitle at that stale position
            // and writes its index into _subtitleIndexByPage[page]
            // — the NEW tab's slot — leaking the previous tab's
            // context into the new tab's UI. Symptom: 泛听 played
            // sentence 5 → switch to 精听 → 精听 highlights
            // sentence 5 as currently playing. The isPlaying guard
            // inside the subtitleProvider is the belt-and-suspenders
            // backup: even if a future refactor removes the
            // seekTo(0) here, paused ticks won't leak.
            resetPlaybackForTabSwitch()
        }
        _currentPage.value = page
    }

    /**
     * Update the current page's subtitle index. Used by the playback
     * auto-advance and by [skipToSubtitle] / [skipToPreviousSubtitle] /
     * [skipToNextSubtitle]. Page-aware: only the active page's slot
     * changes, so switching tabs preserves each page's position.
     */
    private fun setCurrentSubtitleIndex(newIndex: Int) {
        val page = _currentPage.value
        _subtitleIndexByPage.update { current ->
            current.toMutableMap().apply { this[page] = newIndex }
        }
    }

    /**
     * Reset playback state when switching between tabs.
     *
     * Critical: the audioPlayer is a @Singleton, so its position is
     * "sticky" across tabs. After pause(), the 50ms position-update
     * loop (startPositionUpdates, line 649) still calls
     * audioPlayer.updatePosition() with the frozen position from
     * the previous tab. The subtitleProvider callback (see
     * loadSubtitles) then finds the matching subtitle at that
     * stale position and would write its index into
     * _subtitleIndexByPage[page] — the NEW tab's slot — leaking
     * the previous tab's context.
     *
     * Belt-and-suspenders with the `playbackState.value.isPlaying`
     * guard inside the subtitleProvider: this method resets
     * audioPlayer to a clean state (seekTo(0) etc.) so the next
     * tick doesn't see a stale position; the isPlaying guard
     * catches any future refactor that forgets to seekTo(0).
     *
     * Order matters: save progress BEFORE seekTo(0) so the user's
     * last position from the previous tab is persisted (Continue
     * Learning card on home still picks it up).
     */
    private fun resetPlaybackForTabSwitch() {
        // 1) Persist the previous tab's position before we lose it.
        saveProgress()
        // 2) Pause audio/video.
        if (_isVideoMode.value) {
            pauseVideo()
        } else {
            audioPlayer.pause()
        }
        // 3) Clear single-play / skip state.
        singleSubtitleIndex = -1
        singleSubtitleEndMs = 0L
        isSinglePlayMode = false
        skipTargetListIndex = -1
        skipTargetTimeoutMs = 0L
        // 4) Clear "current subtitle" so the new tab doesn't show
        //    the previous tab's subtitle as its context.
        currentSubtitleObj = null
        currentSentenceId = 0
        // 5) Seek to 0 so the position-update loop's next tick
        //    doesn't re-trigger the bug with the previous tab's
        //    position. (subtitleProvider's isPlaying guard also
        //    blocks auto-advance while paused — this seekTo(0) is
        //    belt-and-suspenders.)
        if (_isVideoMode.value) {
            seekVideoTo(0L)
        } else {
            audioPlayer.seekTo(0L)
        }
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
        // (2026-07-04) Bug 2 fix: do the in-memory state update
        // SYNCHRONOUSLY, before the DB write. The previous ordering
        // put the StateFlow write inside the coroutine, after
        // `updateSentenceCompletedUseCase(...)` (which suspends for
        // the Room write). That meant the dropdown's cell color
        // didn't update until the DB write finished — usually
        // <10ms but observable to the user as "I tapped the check,
        // opened the dropdown, and the cell isn't green yet". Worse,
        // if the user clicked "下一句" before the DB write finished,
        // Compose batched the sentenceStates update with the
        // currentSubtitleIndex update from the next-button click,
        // making it LOOK like the green only appeared after clicking
        // next — but it was actually just a delayed snapshot apply.
        //
        // Doing the StateFlow write synchronously ensures the change
        // is visible in the next Compose frame, regardless of when
        // the DB write lands. The DB write still happens on
        // viewModelScope as before; if it fails, the in-memory state
        // is already correct for the current session, and the next
        // loadSentenceStates() will reconcile from the DB.
        val current = _sentenceStates.value.toMutableMap()
        current[sentenceId] = current[sentenceId]?.copy(isCompleted = completed)
            ?: SentenceState(sentenceId, isCompleted = completed)
        _sentenceStates.value = current
        viewModelScope.launch {
            updateSentenceCompletedUseCase(currentCourseId, sentenceId, completed)
        }
    }

    fun markSentenceTested(sentenceId: Int, tested: Boolean) {
        // (2026-07-04) Same Bug 2 fix as [markSentenceCompleted]:
        // hoist the StateFlow writes out of the coroutine so the UI
        // reflects the change in the next Compose frame instead of
        // after the DB write completes. Read the prior isTested
        // BEFORE the synchronous write so the increment-testCount
        // logic still detects "newly tested".
        val wasTested = _sentenceStates.value[sentenceId]?.isTested == true
        val current = _sentenceStates.value.toMutableMap()
        current[sentenceId] = current[sentenceId]?.copy(isTested = tested)
            ?: SentenceState(sentenceId, isTested = tested)
        _sentenceStates.value = current
        // Sync testedCount in test state. (2026-06-28) Only
        // increment when this call actually newly-tests a
        // sentence that is in the current test items. Prevents:
        //  - Double-tap "标记完成" on the same test item
        //    incrementing testedCount twice (e.g., 5 → 6 even
        //    though we only have 5 test items → "6 / 5 已完成"
        //    display bug).
        //  - Marking a sentence NOT in the current test items
        //    inflating testedCount beyond testItems.size
        //    (e.g., from a future feature that lets users
        //    mark sentences tested outside test mode).
        if (tested && !wasTested) {
            val ts = _testState.value
            if (ts.testItems.any { it.index == sentenceId }) {
                _testState.value = ts.copy(testedCount = ts.testedCount + 1)
            }
        }
        viewModelScope.launch {
            updateSentenceTestedUseCase(currentCourseId, sentenceId, tested)
        }
    }

    fun startTestMode() {
        val allSubtitles = _subtitles.value.filter { it.index >= 0 }
        // (2026-06-28) Per user spec: test items = a random 1/3 of
        // sentences in the current course, with a per-sentence
        // minimum of 3 words.
        //   "从当前所学素材中所有句子中随机收取三分之一的句子
        //    用于测试，要求每个所选的句子长度必须满足至少三个单词"
        //
        // The 3-word minimum is a sanity check on the test sample:
        // - Sentences shorter than 3 words are usually
        //   interjections / fragments ("Yes.", "Oh, no.", "Sure.")
        //   that don't exercise comprehension meaningfully.
        // - They also visually look broken in the test page's
        //   word-hide UI (fewer than 3 boxes to reveal — the user
        //   can guess the sentence from 1-2 revealed words).
        //
        // We filter BEFORE the random pick so the 1/3 sample isn't
        // biased: a 30-sentence course with 5 short interjections
        // would otherwise have ~17% of its pool be untestable;
        // filtering first gives us a clean 1/3 sample of the
        // testable subset.
        //
        // Word count uses the same whitespace-split heuristic as
        // [TestingWordsFlowRow] (each visible word block = one
        // non-blank token) so the filter matches what the user
        // actually sees on screen.
        val eligibleSentences = allSubtitles.filter { sub ->
            val wordCount = sub.contentEn
                .split(Regex("\\s+"))
                .count { it.isNotBlank() }
            wordCount >= 3
        }
        // (2026-06-28) Edge case: if NO sentence in the course has
        // 3+ words (very rare — could happen on a single-line
        // import or a course with only short phrases), fall back
        // to all sentences so the test mode can still produce at
        // least 1 test item instead of leaving the user staring at
        // "X / 0 已完成".
        val pool = if (eligibleSentences.isNotEmpty()) eligibleSentences else allSubtitles
        val shuffled = pool.shuffled()
        // maxOf(1, ...) preserves the old behavior of always giving
        // the user at least 1 test item (e.g., a 2-sentence course
        // → size/3 = 0 → would be 0 test items without this guard).
        val testCount = maxOf(1, shuffled.size / 3)
        val testItems = shuffled.take(testCount)
        // (2026-06-28) Only count test items that are already marked
        // tested in the DB. Previously this counted all sentences
        // in the course, which could exceed testItems.size. The
        // more-specific count guards against the display ever
        // showing "X / Y 已完成" with X > Y (the user-reported
        // testedCount > totalCount bug).
        val testedSoFar = testItems.count { item ->
            _sentenceStates.value[item.index]?.isTested == true
        }
        _testState.value = TestState(
            isActive = true,
            testItems = testItems,
            currentTestIndex = 0,
            revealedWords = emptySet(),
            testedCount = testedSoFar
        )
        // (2026-06-28) Make sure no leftover STT / recording state
        // bleeds into the new session — a previous mid-test STT
        // attempt (Listening / Transcribing / Transcribed / etc.)
        // would otherwise show up on the freshly-started session's
        // first sentence and confuse the user.
        resetStt()
        // (2026-06-28) Stop any in-progress audio playback from the
        // previous test attempt — typically the user is starting a
        // fresh round, audio from the old session shouldn't continue
        // playing into the new one.
        if (_isVideoMode.value) pauseVideo() else audioPlayer.pause()
    }

    /**
     * (2026-06-28) "重新测试" button action — user wants to start a
     * brand-new test round from X=1. Per user spec:
     *   "用户也可以再进行新的一轮测试，所有需要增加一个按钮
     *    重新测试/刷新图标，放在测试页面的右上角"
     *
     * Steps:
     *   1. Un-mark all current test items in _sentenceStates so
     *      the counter starts fresh at 0. (Otherwise re-using
     *      pre-tested sentences wouldn't increment testedCount —
     *      [markSentenceTested] is idempotent for already-tested
     *      items, and testedCount would never reach testItems.size
     *      → "round complete" state never triggers.)
     *   2. Persist the un-marking to DB so a process kill doesn't
     *      leave the DB out of sync with what the UI shows.
     *   3. Reset STT state (any leftover Listening/Transcribed UI
     *      from the old round should be cleared).
     *   4. Delegate to [startTestMode] to re-initialize the test
     *      state with X=1.
     *
     * Order matters: un-mark in _sentenceStates FIRST so
     * [startTestMode]'s `testedSoFar` computation reads the cleared
     * state and starts at 0. If we called startTestMode first,
     * `testedSoFar` would be Y (all tested), and un-marking later
     * would only lower the in-memory state — the counter would stay
     * at Y.
     */
    fun restartTest() {
        val current = _testState.value
        if (current.isActive && current.testItems.isNotEmpty()) {
            val newStates = _sentenceStates.value.toMutableMap()
            current.testItems.forEach { item ->
                val existing = newStates[item.index]
                    ?: SentenceState(item.index)
                newStates[item.index] = existing.copy(isTested = false)
            }
            _sentenceStates.value = newStates
            // Persist un-marking to DB (async). Same viewModelScope
            // channel as markSentenceTested uses, so the two don't
            // race: this launches AFTER the synchronous state flip
            // above, and any subsequent markSentenceTested call from
            // the UI fires its own launch.
            val courseId = currentCourseId
            if (courseId.isNotBlank()) {
                viewModelScope.launch {
                    current.testItems.forEach { item ->
                        updateSentenceTestedUseCase(courseId, item.index, false)
                    }
                }
            }
        }
        // Cancel any in-progress STT (recording / transcribing /
        // transcribed) and audio from the old round.
        cancelStt()
        if (_isVideoMode.value) pauseVideo() else audioPlayer.pause()
        // Re-initialize test state (X=1, testedCount=0).
        startTestMode()
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

    /**
     * Advance to the next test item AND clear any STT result state.
     *
     * (2026-07-05) Bug fix: previously the ControlBar's 下一题
     * button only called [nextTestItem], leaving [_sttTestState]
     * at `Passed` / `Failed` so the result card from the just-
     * finished sentence stayed on screen while the new sentence's
     * subtitle card appeared above it. Users saw what looked like
     * "next sentence already tested + showing the passed card"
     * because both cards rendered simultaneously.
     *
     * The TestResultCard's own 下一题 button already called
     * `nextTestItem(); resetStt()` to avoid this — the ControlBar's
     * 下一题 just didn't. Routing both callsites through this
     * helper keeps them in sync.
     *
     * Also called from the Failed card's 下一题 for the same
     * reason.
     */
    fun advanceToNextTestItem() {
        nextTestItem()
        resetStt()
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
        // In video mode, audioPlayer is a UI-state holder only — the actual
        // playback engine is videoPlayer (which carries the audio track of the
        // MKV itself). Calling audioPlayer.play() here is a no-op for the
        // empty audioPlayer ExoPlayer but used to cause two ExoPlayers to
        // race for audio focus. The position-update loop mirrors videoPlayer
        // state into audioPlayer.playbackState via updatePositionFromExternal.
        videoPlayer?.play()
    }

    fun pauseVideo() {
        videoPlayer?.pause()
        singleSubtitleIndex = -1
    }

    fun seekVideoTo(positionMs: Long) {
        // Seek only the actual playback engine. audioPlayer's UI state is
        // updated on the next 50ms tick via updatePositionFromExternal.
        videoPlayer?.seekTo(positionMs)
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

                    // (2026-07-10) §12.39: Auto-land on first sentence when a
                    // page's subtitle-index slot is still at -1 (initial
                    // value before any playback or skip). Before this, the
                    // dropdown button rendered "0 / N" because
                    // currentSubtitleIndex started at -1 and never moved
                    // (no playback happens on first entry to SpeakingPage).
                    //
                    // Only initialize slots that are still at -1 — if the user
                    // already navigated to sentence 5 via skipToSubtitle, we
                    // must not reset their position.
                    _subtitleIndexByPage.update { current ->
                        current.mapValues { (_, idx) -> if (idx == -1) 0 else idx }
                    }

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
                                    setCurrentSubtitleIndex(listIndex)
                                    skipTargetListIndex = -1 // Target reached, clear guard
                                    skipTargetTimeoutMs = 0L
                                    android.util.Log.d("PracticeViewModel", "skip target reached: listIndex=$listIndex")
                                } else if (System.currentTimeMillis() > skipTargetTimeoutMs) {
                                    // (2026-07-04) Bug 1 fix: previous behavior
                                    // called setCurrentSubtitleIndex(listIndex)
                                    // here, which forced the UI back to
                                    // whatever subtitle the audio happened
                                    // to be at — usually the OLD one if
                                    // seek hadn't completed (paused state,
                                    // or audio position reading returning
                                    // stale exoPlayer.currentPosition while
                                    // ExoPlayer's seek was still in
                                    // flight). Symptom: click "下一句"
                                    // → UI shows new sentence → ~1 second
                                    // later UI jumps back to previous.
                                    //
                                    // New behavior: clear the skipTarget
                                    // guard so the next tick can run its
                                    // natural isPlaying branch, but do
                                    // NOT overwrite the user's explicit
                                    // navigation. If audio truly never
                                    // reached the target, the playing
                                    // branch will eventually align UI to
                                    // the audio's actual position — that
                                    // path is honest because it reflects
                                    // what the user is hearing. The 3-second
                                    // timeout here was conflating "seek
                                    // never finished" with "user changed
                                    // their mind", which is wrong.
                                    skipTargetListIndex = -1
                                    skipTargetTimeoutMs = 0L
                                    android.util.Log.d("PracticeViewModel", "skip target timeout: clearing guard without reverting UI (audio still at listIndex=$listIndex)")
                                }
                                // else: still seeking, don't update index
                            } else if (!isSinglePlayMode && playbackState.value.isPlaying) {
                                // Tab-switch bug fix (2026-06-27): when
                                // paused, don't auto-advance
                                // _subtitleIndexByPage. The audioPlayer
                                // position freezes on pause but the
                                // 50ms tick keeps firing — without this
                                // guard the residual ticks would write
                                // the previous tab's subtitle into the
                                // new tab's per-page slot. User actions
                                // (skipTo* / seekToSubtitle) write to
                                // setCurrentSubtitleIndex directly and
                                // bypass this guard, so explicit
                                // navigation still works.
                                setCurrentSubtitleIndex(listIndex)
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
                        val threshold = singleSubtitleEndMs - 500
                        // §12.36: diagnostic log on every gate check
                        // while single-play is active. If the
                        // "continues past subtitle end" symptom
                        // recurs, this line is what we need in
                        // logcat — it shows the exact
                        // currentPos / endMs / singleSubtitleIndex
                        // at every 50ms tick. Filter logcat with
                        // `tag:PracticeViewModel` to capture.
                        android.util.Log.d("PracticeViewModel", "single-play gate: currentPos=$currentPos, endMs=$singleSubtitleEndMs, threshold=$threshold, singleSubtitleIndex=$singleSubtitleIndex, isPlaying=${playbackState.value.isPlaying}")
                        if (currentPos >= threshold) {
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
        // §12.36: set the single-play fields BEFORE starting playback
        // and bypass play()'s clear-state contract. The previous
        // design (see git history for §12.34) called play() between
        // two field-set blocks: play() would wipe singleSubtitleIndex /
        // singleSubtitleEndMs / isSinglePlayMode to (-1, 0L, false),
        // and the second block would re-arm them. Same-thread analysis
        // showed the gap between play() returning and the re-arm was
        // microseconds — well within a single statement's window — so
        // the position-update loop could not fire during it. The
        // residual "continues past subtitle end" failure that survived
        // §12.34 (user reported 2026-07-04: "多数情况下会自动暂停,有时候会
        // 出现继续往后播放") suggested the race window was real on some
        // devices — possibly under thread contention that let the
        // coroutine scheduler advance past one statement boundary. To
        // eliminate the race class entirely we now set the fields
        // FIRST and call audioPlayer.play() directly, sidestepping
        // play()'s clear-state contract (which is meant for the
        // continuous-mode play button, not for entering single-play).
        singleSubtitleIndex = index
        singleSubtitleEndMs = subtitle.endTimeMs
        isSinglePlayMode = true
        skipTargetListIndex = -1
        seekToSubtitle(subtitle)
        if (_isVideoMode.value) {
            playVideo()
        } else {
            // Bypass play() — its contract is "exit single-play mode"
            // for the play button, the opposite of what we want here.
            audioPlayer.play()
        }
    }

    fun play() {
        if (_isVideoMode.value) {
            playVideo()
        } else {
            audioPlayer.play()
        }
        // §12.34: the play button (ListeningPage's progress-bar /
        // bottom-controls play) is the ONLY way to resume continuous
        // auto-advance from a single-play state. Tapping a sentence
        // row calls playSubtitleOnce() (which re-arms the flag after
        // this call returns); tapping prev/next also clears the flag
        // in its own skip flow. So the play button is the canonical
        // "exit single-play" gesture.
        isSinglePlayMode = false
        singleSubtitleIndex = -1
        singleSubtitleEndMs = 0L
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
        setCurrentSubtitleIndex(_subtitles.value.indexOf(subtitle))
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
        // (2026-07-05) Bug fix: see skipToPreviousSubtitle /
        // skipToNextSubtitle — re-arm single-play for the target
        // sentence. Without this, jumping to a sentence via the
        // dropdown menu would also fall into continuous-play and
        // run through every following subtitle.
        val targetIndex = index ?: currentSubtitleIndex.value
        if (targetIndex >= 0 && targetIndex < _subtitles.value.size) {
            val targetSubtitle = _subtitles.value[targetIndex]
            currentSubtitleObj = targetSubtitle
            currentSentenceId = targetSubtitle.index
            setCurrentSubtitleIndex(targetIndex)
            singleSubtitleIndex = targetIndex
            singleSubtitleEndMs = targetSubtitle.endTimeMs
            isSinglePlayMode = true
            skipTargetListIndex = targetIndex
            skipTargetTimeoutMs = System.currentTimeMillis() + 3000
            seekTo(targetSubtitle.startTimeMs)
        }
    }

    fun skipToPreviousSubtitle() {
        // (2026-07-05) Bug fix: the skip-* paths used to clear
        // single-play state and never re-arm it for the new
        // sentence, so after a skip the player fell into normal
        // continuous-play mode and ran through every following
        // sentence. Now re-arm single-play for the target
        // sentence so playback stops at its endTimeMs.
        val currentIndex = currentSubtitleIndex.value
        val newIndex = if (currentIndex > 0) currentIndex - 1 else 0
        if (_subtitles.value.isNotEmpty()) {
            val targetSubtitle = _subtitles.value[newIndex]
            android.util.Log.d("PracticeViewModel", "skipToPreviousSubtitle: currentIndex=$currentIndex, newIndex=$newIndex, targetSubtitleIndex=${targetSubtitle.index}")
            currentSubtitleObj = targetSubtitle
            currentSentenceId = targetSubtitle.index
            setCurrentSubtitleIndex(newIndex)
            singleSubtitleIndex = newIndex
            singleSubtitleEndMs = targetSubtitle.endTimeMs
            isSinglePlayMode = true
            skipTargetListIndex = newIndex
            skipTargetTimeoutMs = System.currentTimeMillis() + 3000
            seekTo(targetSubtitle.startTimeMs)
        }
    }

    fun skipToNextSubtitle() {
        // (2026-07-05) Bug fix: see skipToPreviousSubtitle —
        // re-arm single-play for the target sentence so playback
        // stops at its endTimeMs instead of rolling into the next
        // subtitle. Without this, clicking "下一句" during
        // playback of A would jump to B then keep going through
        // C, D, E…
        val currentIndex = currentSubtitleIndex.value
        val newIndex = if (currentIndex < _subtitles.value.size - 1) currentIndex + 1 else _subtitles.value.size - 1
        if (_subtitles.value.isNotEmpty()) {
            val targetSubtitle = _subtitles.value[newIndex.coerceAtLeast(0)]
            android.util.Log.d("PracticeViewModel", "skipToNextSubtitle: currentIndex=$currentIndex, newIndex=$newIndex, targetSubtitleIndex=${targetSubtitle.index}")
            currentSubtitleObj = targetSubtitle
            currentSentenceId = targetSubtitle.index
            setCurrentSubtitleIndex(newIndex)
            singleSubtitleIndex = newIndex
            singleSubtitleEndMs = targetSubtitle.endTimeMs
            isSinglePlayMode = true
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
            // (2026-06-28) Write to the speaking-page path. Does
            // NOT touch _testRecordingPath — recordings made in
            // the speaking page should not appear in the test
            // page's 回放 button, and vice versa.
            _speakingRecordingPath.value = it.filePath
        }
        return result
    }

    fun cancelRecording() {
        voiceRecorder.cancelRecording()
    }

    // ─── STT word-match testing ────────────────────────────────────
    //
    // v2 flow (Vosk-based, replaces the system SpeechRecognizer that
    // the previous version used). Why the change:
    //   - System SpeechRecognizer + MediaRecorder cannot share the mic
    //     (Android audio HAL allows only one consumer per stream), so
    //     any attempt to give the user both a recording file AND a
    //     transcription failed with one side getting silence.
    //   - Vosk is offline, accepts a WAV file, and gives us text from
    //     the exact audio we recorded. So we record with WavRecorder
    //     (AudioRecord → 16 kHz mono 16-bit PCM WAV file) and then
    //     run Vosk on that file after the user releases the mic.
    //   - The recording file is preserved for playback (sets
    //     _recordingPath so the existing 回放 button works), AND Vosk
    //     gives us text → "显示文字" button works too.
    //
    // State machine:
    //   Idle → (press) → Listening → (release) → Transcribing(path)
    //   → (vosk done) → Transcribed(text)
    //   → (submit) → Passed / Failed
    //   → (重录) → Idle

    /**
     * Start recording via WavRecorder. Kicks off the Vosk model
     * download in the background (no-op if already present) so the
     * first transcribe is fast on subsequent uses.
     */
    fun startStt() {
        if (_sttTestState.value is SttTestState.Listening) return
        sttPressStartTimeMs = System.currentTimeMillis()
        sttWaitJob?.cancel()
        sttWaitJob = null
        sttResult = null
        cancelSttEventCollection()
        _sttTestState.value = SttTestState.Listening(0L)

        // Start the recording. WavRecorder writes a 16 kHz mono WAV
        // file under cacheDir/recordings/recording_<ts>.wav.
        try {
            val path = wavRecorder.start()
            android.util.Log.d("SttTest", "startStt: wavRecorder started at $path")
        } catch (e: Throwable) {
            android.util.Log.e("SttTest", "startStt: wavRecorder.start failed", e)
            // Couldn't start recording — bounce back to Idle.
            _sttTestState.value = SttTestState.Idle
            return
        }

        // Kick off model download in background. If the model is
        // already present this short-circuits to Ready in a few ms.
        // If the user is on a slow connection on first use, the
        // download may still be in flight when they release the mic
        // — in that case stopStt() will await the download before
        // transcribing.
        if (!modelManager.isModelReady()) {
            viewModelScope.launch {
                modelManager.ensureModelReady()
            }
        }

        startSttTimers()
    }

    /**
     * Stop recording. Called by UI onRelease of mic button.
     *
     * Flow:
     *   1. If the user released too quickly, treat as accidental and
     *      bounce back to Idle (matches the previous v1 behavior so
     *      "I brushed the mic" doesn't pollute the editor).
     *   2. Otherwise, stop the WavRecorder → we have a .wav file.
     *      Publish its path to _recordingPath so the 回放 button works.
     *   3. Transition to Transcribing(path) and run Vosk in a
     *      coroutine. Vosk may block briefly on first use while
     *      ModelManager downloads the model. On any failure we fall
     *      back to Transcribed("") — the user can still type manually.
     */
    fun stopStt() {
        if (_sttTestState.value !is SttTestState.Listening) return
        val holdMs = System.currentTimeMillis() - sttPressStartTimeMs
        if (holdMs < STT_MIN_HOLD_MS) {
            // Accidental press — discard the recording, return to Idle.
            try { wavRecorder.cancel() } catch (_: Throwable) {}
            sttWaitJob?.cancel()
            sttWaitJob = null
            stopSttElapsedTimer()
            _sttTestState.value = SttTestState.Idle
            _sttAmplitudeBars.value = List(5) { 0.4f }
            sttResult = null
            return
        }
        // Stop the recorder and capture the file path.
        val rec = try {
            wavRecorder.stop()
        } catch (e: Throwable) {
            android.util.Log.e("SttTest", "stopStt: wavRecorder.stop failed", e)
            null
        }
        stopSttElapsedTimer()
        val path = rec?.filePath
        if (path == null) {
            // Recorder didn't yield a file — treat as no input.
            _sttTestState.value = SttTestState.Idle
            return
        }
        // Publish the file for playback (回放 button reads this).
        // (2026-06-28) Write to the test-page path. Does NOT touch
        // _speakingRecordingPath — STT recordings made in the test
        // page should not appear in the speaking page's "我的录音"
        // playback card, and vice versa.
        _testRecordingPath.value = path

        // Show a "正在识别" intermediate state, then run Vosk.
        _sttTestState.value = SttTestState.Transcribing(path)
        sttWaitJob?.cancel()
        sttWaitJob = viewModelScope.launch {
            // Ensure model is bundled and ready before transcribing.
            // The Vosk model is shipped inside the APK at
            // `assets/models/vosk-model-small-en-us-0.15/` and is
            // unpacked to internal storage on first use (~1s). After
            // that it short-circuits in milliseconds — there is no
            // network download.
            //
            // (2026-07-10) Constrained grammar was tried here (passing
            // currentTest.contentEn as the only allowed phrase) but
            // it made WER strictly worse — Vosk's grammar mode is
            // exact-phrase match, so any filler word or single-token
            // deviation yields the empty string. Open-vocabulary
            // returns the close-but-wrong text and lets WordMatcher
            // give partial credit. See VoskSpeechRecognizer.transcribeFile
            // for the longer writeup.
            val modelReady = modelManager.ensureModelReady()
            if (modelReady.isFailure) {
                android.util.Log.e("SttTest", "stopStt: model not ready, manual entry", modelReady.exceptionOrNull())
                _sttTestState.value = SttTestState.Transcribed("")
                return@launch
            }
            // (2026-07-10) Use n-best transcription. Vosk's small
            // model (WER ~10%) often returns a Top-1 that's "close
            // but wrong" when the speech is ambiguous; the Top-3
            // contains the correct phrase frequently. We ask Vosk
            // for up to 3 alternatives and let WordMatcher vote
            // across them, picking the first Pass or the candidate
            // with the most orig-words matched. See
            // [VoskSpeechRecognizer.transcribeFileAlternatives] and
            // §12.38 for the full design rationale.
            val candidates = try {
                voskRecognizer.transcribeFileAlternatives(path, maxAlternatives = 3).getOrElse {
                    android.util.Log.e("SttTest", "stopStt: transcribeFileAlternatives failed", it)
                    listOf("[识别失败: ${it.javaClass.simpleName}]")
                }
            } catch (e: Throwable) {
                android.util.Log.e("SttTest", "stopStt: transcribeFileAlternatives threw", e)
                listOf("[识别失败: ${e.javaClass.simpleName}]")
            }
            android.util.Log.d("SttTest", "stopStt: vosk returned ${candidates.size} candidate(s): $candidates")
            val pickedText = pickBestCandidate(candidates)
            android.util.Log.d("SttTest", "stopStt: picked='$pickedText'")
            _sttTestState.value = SttTestState.Transcribed(pickedText)
        }
    }

    /**
     * (2026-07-10) Pick the best candidate from Vosk's n-best list.
     *
     * Strategy (in order):
     *  1. **First PASS wins.** If any candidate passes WordMatcher
     *     (all orig words matched), use it immediately. This is the
     *     "happy path" — Top-3 contains the correct phrase and we
     *     stop looking.
     *  2. **Most orig words matched.** If nothing passes, pick the
     *     candidate with the highest count of matched orig words
     *     (`origMatched.count { it }`). This favors the candidate
     *     that "got the most right" rather than the one that's just
     *     closest in length.
     *  3. **Closest length to orig.** Tiebreak in (2) by the absolute
     *     length difference between `transWords.size` and `origWords.size`
     *     — a candidate with the same word count as the expected
     *     sentence is more likely to be "the right number of words
     *     but wrong words" (still actionable feedback) than one that
     *     dropped half the words.
     *
     * The picked **text** (not the WordMatcher result) is returned
     * so the user sees the candidate Vosk actually recognized, with
     * whatever errors it contains — they can then edit it in
     * TranscriptionEditor before submission. The actual Pass/Fail
     * verdict is still computed against the picked text in
     * [submitTranscription].
     *
     * Visible for testing — kept `internal` rather than `private`
     * so [WordMatcher.bestOf] can be exercised in isolation. The
     * companion tests live in [com.echoling.app.speech.WordMatcherTest].
     */
    internal fun pickBestCandidate(candidates: List<String>): String {
        if (candidates.isEmpty()) return ""
        // Skip error placeholders — they're not real candidates and
        // shouldn't influence vote. If every candidate is an error,
        // return the first one (it'll render as "未识别" anyway).
        val realCandidates = candidates.filter {
            !it.startsWith("[识别失败") && it.isNotBlank()
        }
        if (realCandidates.isEmpty()) return candidates.first()
        if (realCandidates.size == 1) return realCandidates.first()
        val currentTest = _testState.value.testItems.getOrNull(_testState.value.currentTestIndex)
            ?: return realCandidates.first()
        return WordMatcher.bestOf(currentTest.contentEn, realCandidates)
    }

    /**
     * Suspend until [sttResult] is filled by the event collection job,
     * or up to 2s. Returns the result (or "" on timeout). Reads and
     * clears [sttResult] as a side-effect.
     *
     * Safe to call only from [stopStt], which has already signalled
     * end-of-input via [SttRecognizer.stop]. Both the wait loop and
     * the event collector run on the main thread (default for
     * viewModelScope.launch), so the read/write to [sttResult] is
     * serialized — no extra synchronization needed.
     */
    private suspend fun waitForSttResult(): String {
        val timeoutMs = 2000L
        val start = System.currentTimeMillis()
        while (sttResult == null && System.currentTimeMillis() - start < timeoutMs) {
            delay(50)
        }
        val result = sttResult ?: ""
        sttResult = null
        return result
    }

    private fun startSttTimers() {
        val startTime = System.currentTimeMillis()
        sttElapsedJob = viewModelScope.launch {
            while (isActive && _sttTestState.value is SttTestState.Listening) {
                val elapsed = System.currentTimeMillis() - startTime
                (_sttTestState.value as? SttTestState.Listening)?.let {
                    _sttTestState.value = it.copy(elapsedMs = elapsed)
                }
                _sttAmplitudeBars.value = List(5) { (Math.random().toFloat() * 0.7f + 0.3f) }
                delay(100)
            }
        }
    }

    /**
     * Cancel only the elapsed-time ticker. The event collection job
     * stays alive — we still need to receive the final onResults/
     * onError from SpeechRecognizer after stopListening() is called.
     * The event collection job is cancelled in onSttResults() (when
     * we get the result) or cancelStt() (when the user aborts).
     */
    private fun stopSttElapsedTimer() {
        sttElapsedJob?.cancel()
        sttElapsedJob = null
    }

    private fun cancelSttEventCollection() {
        sttEventCollectionJob?.cancel()
        sttEventCollectionJob = null
    }

    /**
     * Cancel STT (user taps "取消" in overlay, or backs out of the page).
     * v2: stop the WavRecorder if it's running, and discard any
     * in-flight transcribe coroutine. Doesn't touch the model cache.
     */
    fun cancelStt() {
        try { wavRecorder.cancel() } catch (_: Throwable) {}
        sttWaitJob?.cancel()
        sttWaitJob = null
        stopSttElapsedTimer()
        cancelSttEventCollection()
        _sttTestState.value = SttTestState.Idle
        _sttAmplitudeBars.value = List(5) { 0.4f }
        sttResult = null
    }

    /** User submitted the transcription for matching. */
    fun submitTranscription(text: String) {
        val currentTest = _testState.value.testItems.getOrNull(_testState.value.currentTestIndex)
            ?: run {
                _sttTestState.value = SttTestState.Failed(
                    transcribed = text,
                    original = "",
                    origWords = emptyList(),
                    transWords = emptyList(),
                    // (2026-06-28) Empty match arrays — there's no
                    // orig sentence to align against in this branch.
                    origMatched = BooleanArray(0),
                    transMatched = BooleanArray(0),
                    reason = "no_test_item"
                )
                return
            }
        val result = WordMatcher.match(currentTest.contentEn, text)
        if (result.passed) {
            _sttTestState.value = SttTestState.Passed(text)
            markSentenceTested(currentTest.index, true)
        } else {
            _sttTestState.value = SttTestState.Failed(
                transcribed = text,
                original = currentTest.contentEn,
                origWords = result.origWords,
                transWords = result.transWords,
                // (2026-06-28) Pass the per-word match flags
                // through so WordChipsRow can color each chip
                // independently. See WordMatcher for the
                // position-independent alignment logic.
                origMatched = result.origMatched,
                transMatched = result.transMatched,
                reason = result.reason
            )
        }
    }

    /** Reset to Idle for next attempt. */
    fun resetStt() {
        _sttTestState.value = SttTestState.Idle
        _sttAmplitudeBars.value = List(5) { 0.4f }
    }

    /**
     * (2026-06-28) Plays a recording by file path. The caller
     * supplies the path explicitly instead of the ViewModel
     * reading from a shared field — this is what enforces the
     * per-page isolation (SpeakingPage passes
     * [speakingRecordingPath], TestingPage passes
     * [testRecordingPath], so a speaking-page recording can never
     * be played from the test page's "回放录音" button and vice
     * versa).
     *
     * @param path the file path to play. Null / blank → no-op,
     *        matches the old behavior so the UI can call this
     *        unconditionally from any "播放录音" button.
     */
    fun playRecording(path: String?) {
        val p = path ?: return

        stopPlayingRecording()

        mediaPlayer = MediaPlayer().apply {
            setDataSource(p)
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
        wavRecorder.release()
        voskRecognizer.shutdown()
        stopPlayingRecording()
        sttElapsedJob?.cancel()
        sttEventCollectionJob?.cancel()
        sttWaitJob?.cancel()
        saveProgress()
    }
}
