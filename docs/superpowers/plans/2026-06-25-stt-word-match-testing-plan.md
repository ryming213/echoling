# STT 单词对比测试 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把测试 Tab 的"发音评分"换成"按住录音 → STT 转写 → 编辑 → 单词对比通过/不通过"流程，删除所有 DTW 评分代码。

**Architecture:** 系统 `SpeechRecognizer` 边录边转文字，`WordMatcher` 严格顺序匹配。录音不落盘，纯 STT + 文字交互。

**Tech Stack:** Android `SpeechRecognizer`（platform API）、Jetpack Compose、StateFlow、Coroutines、Hilt

---

### Task 1: 删除旧发音评分代码

**Files:**
- Delete: `app/src/main/java/com/echoling/app/speech/PronunciationGrader.kt`
- Delete: `app/src/main/java/com/echoling/app/speech/DtwAligner.kt`
- Delete: `app/src/main/java/com/echoling/app/speech/RmsExtractor.kt`
- Delete: `app/src/main/java/com/echoling/app/speech/WavReader.kt`
- Delete: `app/src/main/java/com/echoling/app/speech/M4aDecoder.kt`
- Delete: `app/src/main/java/com/echoling/app/speech/Resampler.kt`
- Delete: `app/src/main/java/com/echoling/app/speech/TtsReferenceCache.kt`
- Delete: `app/src/main/java/com/echoling/app/domain/model/ScoreResult.kt`
- Delete: `app/src/main/java/com/echoling/app/domain/usecase/UpdateSentenceReadScoreUseCase.kt`
- Delete: `app/src/main/java/com/echoling/app/presentation/ui/screens/practice/components/ScoreCard.kt`
- Modify: `app/src/main/java/com/echoling/app/player/TtsManager.kt` — remove `synthesizeToFile()` and `activeSynthesis` field
- Modify: `app/src/main/java/com/echoling/app/data/local/db/dao/SentenceDao.kt` — remove `updateReadScore()`
- Modify: `app/src/main/java/com/echoling/app/domain/repository/SentenceRepository.kt` — remove `updateReadScore()`
- Modify: `app/src/main/java/com/echoling/app/data/repository/SentenceRepositoryImpl.kt` — remove `updateReadScore()` impl

- [ ] **Step 1: Delete the 10 files**

```bash
cd "c:/Users/MING/myagent/echoling"
rm app/src/main/java/com/echoling/app/speech/PronunciationGrader.kt
rm app/src/main/java/com/echoling/app/speech/DtwAligner.kt
rm app/src/main/java/com/echoling/app/speech/RmsExtractor.kt
rm app/src/main/java/com/echoling/app/speech/WavReader.kt
rm app/src/main/java/com/echoling/app/speech/M4aDecoder.kt
rm app/src/main/java/com/echoling/app/speech/Resampler.kt
rm app/src/main/java/com/echoling/app/speech/TtsReferenceCache.kt
rm app/src/main/java/com/echoling/app/domain/model/ScoreResult.kt
rm app/src/main/java/com/echoling/app/domain/usecase/UpdateSentenceReadScoreUseCase.kt
rm "app/src/main/java/com/echoling/app/presentation/ui/screens/practice/components/ScoreCard.kt"
```

- [ ] **Step 2: Remove `synthesizeToFile()` from TtsManager.kt**

Remove lines 332-378 (the entire `synthesizeToFile` method) and the `activeSynthesis` field at line 92:

```kotlin
// DELETE this field (line 92):
private val activeSynthesis = java.util.concurrent.ConcurrentHashMap<String, (Boolean) -> Unit>()

// DELETE the entire synthesizeToFile method (lines 332-378):
/**
 * Synthesize [text] to [outputFile] (WAV) ... (whole block)
 */
fun synthesizeToFile(text: String, outputFile: java.io.File, onComplete: (Boolean) -> Unit) {
    if (!_isReady.value || tts == null) {
        ...
    }
    ...
}
```

Also remove the `activeSynthesis.remove(utteranceId)?.invoke(true/false)` calls from the `UtteranceProgressListener` in `onEngineSuccess()` (lines 300-321). Replace the UtteranceProgressListener with a diagnostic-only version:

```kotlin
try {
    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            Log.d(TAG, "utterance onStart: $utteranceId")
        }
        override fun onDone(utteranceId: String?) {
            Log.d(TAG, "utterance onDone: $utteranceId")
        }
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            Log.w(TAG, "utterance onError: $utteranceId")
        }
        override fun onError(utteranceId: String?, errorCode: Int) {
            Log.w(TAG, "utterance onError: $utteranceId, code=$errorCode")
        }
    })
} catch (e: Throwable) {
    Log.w(TAG, "setOnUtteranceProgressListener threw — continuing", e)
}
```

- [ ] **Step 3: Remove `updateReadScore` from SentenceDao.kt**

Remove lines 37-42 from `app/src/main/java/com/echoling/app/data/local/db/dao/SentenceDao.kt`:

```kotlin
// DELETE this block:
/**
 * Update the readScore (pronunciation score 0..100) for a sentence.
 */
@Query("UPDATE sentences SET readScore = :score WHERE courseId = :courseId AND sentenceId = :sentenceId")
suspend fun updateReadScore(courseId: String, sentenceId: Int, score: Int)
```

- [ ] **Step 4: Remove `updateReadScore` from SentenceRepository.kt**

Remove the `updateReadScore` declaration from `app/src/main/java/com/echoling/app/domain/repository/SentenceRepository.kt`:

```kotlin
// DELETE this line:
suspend fun updateReadScore(courseId: String, sentenceId: Int, score: Int)
```

- [ ] **Step 5: Remove `updateReadScore` impl from SentenceRepositoryImpl.kt**

Remove lines 43-44 from `app/src/main/java/com/echoling/app/data/repository/SentenceRepositoryImpl.kt`:

```kotlin
// DELETE these lines:
override suspend fun updateReadScore(courseId: String, sentenceId: Int, score: Int) {
    sentenceDao.updateReadScore(courseId, sentenceId, score)
}
```

- [ ] **Step 6: Commit old code deletion**

```bash
cd "c:/Users/MING/myagent/echoling"
git add -A
git commit -m "feat: remove DTW pronunciation scoring (11 files + partial cleanup)

Delete PronunciationGrader, DtwAligner, RmsExtractor, WavReader,
M4aDecoder, Resampler, TtsReferenceCache, ScoreResult/ScoreTier,
UpdateSentenceReadScoreUseCase, ScoreCard. Remove synthesizeToFile
from TtsManager and updateReadScore from SentenceDao+Repository.

Replaced by STT word-match testing flow in subsequent commits.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: 创建 `WordMatcher` + 单元测试

**Files:**
- Create: `app/src/main/java/com/echoling/app/speech/WordMatcher.kt`
- Create: `app/src/test/java/com/echoling/app/speech/WordMatcherTest.kt`

- [ ] **Step 1: Create the unit test file**

```bash
mkdir -p "c:/Users/MING/myagent/echoling/app/src/test/java/com/echoling/app/speech"
```

Write `app/src/test/java/com/echoling/app/speech/WordMatcherTest.kt`:

```kotlin
package com.echoling.app.speech

import org.junit.Assert.*
import org.junit.Test

class WordMatcherTest {

    @Test
    fun `exact match passes`() {
        val result = WordMatcher.match("I love you", "I love you")
        assertTrue(result.passed)
        assertEquals("ok", result.reason)
    }

    @Test
    fun `case insensitive match passes`() {
        val result = WordMatcher.match("I LOVE YOU", "i love you")
        assertTrue(result.passed)
    }

    @Test
    fun `punctuation removed passes`() {
        val result = WordMatcher.match("Hello, world!", "hello world")
        assertTrue(result.passed)
    }

    @Test
    fun `apostrophe preserved in contraction`() {
        val result = WordMatcher.match("don't stop", "don't stop")
        assertTrue(result.passed)
    }

    @Test
    fun `dont vs dont fails due to apostrophe`() {
        val result = WordMatcher.match("don't", "dont")
        assertFalse(result.passed)
        assertEquals("wrong_word", result.reason)
    }

    @Test
    fun `missing word fails`() {
        val result = WordMatcher.match("I love you", "I love")
        assertFalse(result.passed)
        assertEquals("missing_word", result.reason)
    }

    @Test
    fun `extra word fails`() {
        val result = WordMatcher.match("I love you", "I love you too")
        assertFalse(result.passed)
        assertEquals("extra_word", result.reason)
    }

    @Test
    fun `wrong word fails`() {
        val result = WordMatcher.match("I love you", "I love her")
        assertFalse(result.passed)
        assertEquals("wrong_word", result.reason)
    }

    @Test
    fun `empty transcription fails`() {
        val result = WordMatcher.match("I love you", "")
        assertFalse(result.passed)
        assertEquals("empty_transcription", result.reason)
    }

    @Test
    fun `whitespace only transcription fails`() {
        val result = WordMatcher.match("I love you", "   ")
        assertFalse(result.passed)
        assertEquals("empty_transcription", result.reason)
    }

    @Test
    fun `numbers preserved`() {
        val result = WordMatcher.match("Lesson 1 is done", "lesson 1 is done")
        assertTrue(result.passed)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd "c:/Users/MING/myagent/echoling"
./gradlew testDebugUnitTest --tests "com.echoling.app.speech.WordMatcherTest"
```

Expected: FAIL (WordMatcher class not found)

- [ ] **Step 3: Implement WordMatcher**

Write `app/src/main/java/com/echoling/app/speech/WordMatcher.kt`:

```kotlin
package com.echoling.app.speech

/**
 * Strict sequential word matcher for STT transcription comparison.
 *
 * Normalizes both strings (lowercase, strip punctuation except apostrophes,
 * split on whitespace) then compares position-by-position. Same length and
 * all words match → passed. Any difference → failed with reason.
 *
 * No stemming, no synonym matching, no fuzzy matching — the user can edit
 * the transcription if STT misrecognized a word.
 */
object WordMatcher {
    data class MatchResult(
        val passed: Boolean,
        val origWords: List<String>,
        val transWords: List<String>,
        val reason: String  // "ok" | "empty_transcription" | "missing_word" | "extra_word" | "wrong_word"
    )

    private val NORMALIZE_REGEX = Regex("[^a-z0-9\\s']")

    fun match(original: String, transcribed: String): MatchResult {
        val orig = normalize(original)
        val trans = normalize(transcribed)
        if (trans.isEmpty()) return MatchResult(false, orig, trans, "empty_transcription")
        if (orig.size != trans.size) {
            val reason = if (trans.size > orig.size) "extra_word" else "missing_word"
            return MatchResult(false, orig, trans, reason)
        }
        for (i in orig.indices) {
            if (orig[i] != trans[i]) return MatchResult(false, orig, trans, "wrong_word")
        }
        return MatchResult(true, orig, trans, "ok")
    }

    private fun normalize(s: String): List<String> =
        s.lowercase()
            .replace(NORMALIZE_REGEX, " ")
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd "c:/Users/MING/myagent/echoling"
./gradlew testDebugUnitTest --tests "com.echoling.app.speech.WordMatcherTest"
```

Expected: 11/11 PASS

- [ ] **Step 5: Commit**

```bash
cd "c:/Users/MING/myagent/echoling"
git add app/src/main/java/com/echoling/app/speech/WordMatcher.kt app/src/test/java/com/echoling/app/speech/WordMatcherTest.kt
git commit -m "feat: add WordMatcher for strict sequential word comparison

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: 创建 `SttRecognizer`

**Files:**
- Create: `app/src/main/java/com/echoling/app/speech/SttRecognizer.kt`

- [ ] **Step 1: Implement SttRecognizer**

Write `app/src/main/java/com/echoling/app/speech/SttRecognizer.kt`:

```kotlin
package com.echoling.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * System SpeechRecognizer wrapper for the testing tab's STT flow.
 *
 * Wraps Android's [SpeechRecognizer] with a [SharedFlow] of [SttEvent] so
 * callers don't need to manage [RecognitionListener] lifecycle. Uses
 * [MutableSharedFlow] with extraBufferCapacity=4 so events survive a brief
 * collector start race.
 *
 * Not thread-safe for concurrent start() calls — callers must serialize
 * (the ViewModel does this via state checks).
 */
@Singleton
class SttRecognizer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed class SttEvent {
        data class PartialResults(val text: String) : SttEvent()
        data class Results(val text: String) : SttEvent()
        data class Error(val code: Int, val message: String) : SttEvent()
    }

    private val _events = MutableSharedFlow<SttEvent>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SttEvent> = _events.asSharedFlow()

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(language: String = "en-US") {
        if (recognizer != null) stop()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "onReadyForSpeech")
                }
                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "onBeginningOfSpeech")
                }
                override fun onRmsChanged(rmsdB: Float) {
                    // v2: emit real amplitude for waveform animation
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    Log.d(TAG, "onEndOfSpeech")
                }
                override fun onError(error: Int) {
                    Log.w(TAG, "onError: $error")
                    _events.tryEmit(SttEvent.Error(error, "SpeechRecognizer error code: $error"))
                }
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    Log.d(TAG, "onResults: '$text'")
                    _events.tryEmit(SttEvent.Results(text))
                }
                override fun onPartialResults(partial: Bundle?) {
                    // v1: ignore partial results
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(intent)
        }
    }

    fun stop() {
        try {
            recognizer?.stopListening()
        } catch (e: Throwable) {
            Log.w(TAG, "stopListening threw", e)
        }
        try {
            recognizer?.destroy()
        } catch (e: Throwable) {
            Log.w(TAG, "destroy threw", e)
        }
        recognizer = null
    }

    private companion object {
        const val TAG = "SttRecognizer"
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd "c:/Users/MING/myagent/echoling"
git add app/src/main/java/com/echoling/app/speech/SttRecognizer.kt
git commit -m "feat: add SttRecognizer wrapping system SpeechRecognizer

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: 更新 `PracticeViewModel` — 删旧状态 + 加 `SttTestState` + 新方法

**Files:**
- Modify: `app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt`

- [ ] **Step 1: Remove old grading imports and injection**

Remove these imports from the top of the file:

```kotlin
// DELETE:
import com.echoling.app.domain.model.ScoreResult
import com.echoling.app.speech.PronunciationGrader
```

Remove these constructor parameters (lines 115-116):

```kotlin
// DELETE:
private val pronunciationGrader: PronunciationGrader,
private val updateSentenceReadScoreUseCase: UpdateSentenceReadScoreUseCase,
```

Also remove the trailing comma from the `lookupWordUseCase` line (line 114) so it compiles:

```kotlin
// BEFORE (line 114):
private val lookupWordUseCase: LookupWordUseCase,
// AFTER:
private val lookupWordUseCase: LookupWordUseCase
```

Add the new injection:

```kotlin
// ADD after the VoiceRecorder injection (line 113):
private val sttRecognizer: SttRecognizer,
```

Add the new import:

```kotlin
import com.echoling.app.speech.SttRecognizer
import com.echoling.app.speech.WordMatcher
```

- [ ] **Step 2: Delete old GradeState sealed class and grading fields**

Remove lines 89-99 (the entire `GradeState` sealed class):

```kotlin
// DELETE the entire sealed class:
sealed class GradeState {
    data object Idle : GradeState()
    data object Recording : GradeState()
    data object Loading : GradeState()
    data class Success(
        val result: ScoreResult,
        val sentenceId: Int,
        val recordingPath: String,
    ) : GradeState()
    data class Error(val message: String) : GradeState()
}
```

Remove lines 215-218 (the grading fields):

```kotlin
// DELETE:
private val _gradeState = MutableStateFlow<GradeState>(GradeState.Idle)
val gradeState: StateFlow<GradeState> = _gradeState.asStateFlow()
private var gradeJob: Job? = null
```

- [ ] **Step 3: Add SttTestState sealed class (after the existing GradeState was deleted, before the class body)**

Insert the new `SttTestState` sealed class at the file level (after `WordTranslationState` at line ~81):

```kotlin
/**
 * Testing-tab STT state machine.
 * Idle → Listening → Transcribed → (Passed | Failed).
 * User can cancel from Listening back to Idle, or reset from any state.
 */
sealed class SttTestState {
    data object Idle : SttTestState()
    data class Listening(val elapsedMs: Long = 0L) : SttTestState()
    data class Transcribed(val text: String) : SttTestState()
    data class Passed(val text: String) : SttTestState()
    data class Failed(
        val transcribed: String,
        val original: String,
        val origWords: List<String>,
        val transWords: List<String>,
        val reason: String
    ) : SttTestState()
}
```

- [ ] **Step 4: Add STT state fields**

Add after the `_gradeState` deletion spot (around line 215):

```kotlin
// ─── STT word-match testing state (跟读测试) ──────────────────────
// Phases: Idle → Listening → Transcribed → (Passed | Failed).
private val _sttTestState = MutableStateFlow<SttTestState>(SttTestState.Idle)
val sttTestState: StateFlow<SttTestState> = _sttTestState.asStateFlow()

// 5 random amplitude bars for v1 recording overlay animation.
private val _sttAmplitudeBars = MutableStateFlow(List(5) { 0.4f })
val sttAmplitudeBars: StateFlow<List<Float>> = _sttAmplitudeBars.asStateFlow()

private var sttElapsedJob: Job? = null
private var sttEventCollectionJob: Job? = null
```

- [ ] **Step 5: Delete old grading methods**

Remove these methods: `startRecordingForGrading()` (lines 868-877), `stopAndGrade()` (lines 892-946), `cancelGrading()` (lines 952-953), `cancelGradingInternal()` (lines 956-964), `currentGradingSentence()` (lines 973-984).

- [ ] **Step 6: Add new STT methods**

Insert after the existing `cancelRecording()` method (around line 855):

```kotlin
// ─── STT word-match testing ────────────────────────────────────

/** Start STT. Called by UI onPress of mic button. */
fun startStt() {
    if (_sttTestState.value is SttTestState.Listening) return
    if (!sttRecognizer.isAvailable()) {
        _sttTestState.value = SttTestState.Transcribed("")
        _sttAmplitudeBars.value = List(5) { 0.4f }
        return
    }
    _sttTestState.value = SttTestState.Listening(0L)
    sttEventCollectionJob = viewModelScope.launch {
        sttRecognizer.events.collect { event ->
            when (event) {
                is SttRecognizer.SttEvent.Results -> onSttResults(event.text)
                is SttRecognizer.SttEvent.Error -> onSttResults("")
                is SttRecognizer.SttEvent.PartialResults -> { /* v1 ignore */ }
            }
        }
    }
    sttRecognizer.start(language = "en-US")
    startSttTimers()
}

/** Stop STT. Called by UI onRelease of mic button. */
fun stopStt() {
    if (_sttTestState.value !is SttTestState.Listening) return
    sttRecognizer.stop()
    stopSttTimers()
}

private fun onSttResults(text: String) {
    stopSttTimers()
    _sttTestState.value = SttTestState.Transcribed(text)
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

private fun stopSttTimers() {
    sttElapsedJob?.cancel()
    sttElapsedJob = null
    sttEventCollectionJob?.cancel()
    sttEventCollectionJob = null
}

/** Cancel STT (user taps "取消" in overlay). */
fun cancelStt() {
    if (_sttTestState.value is SttTestState.Listening) {
        sttRecognizer.stop()
    }
    stopSttTimers()
    _sttTestState.value = SttTestState.Idle
    _sttAmplitudeBars.value = List(5) { 0.4f }
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
            reason = result.reason
        )
    }
}

/** Reset to Idle for next attempt. */
fun resetStt() {
    _sttTestState.value = SttTestState.Idle
    _sttAmplitudeBars.value = List(5) { 0.4f }
}
```

- [ ] **Step 7: Update `onCleared`**

Add `sttRecognizer.stop()` and STT job cancellation to the existing `onCleared()`:

```kotlin
override fun onCleared() {
    super.onCleared()
    positionUpdateJob?.cancel()
    audioPlayer.release()
    videoPlayer?.release()
    voiceRecorder.release()
    stopPlayingRecording()
    sttRecognizer.stop()           // ADD
    sttElapsedJob?.cancel()        // ADD
    sttEventCollectionJob?.cancel() // ADD
    saveProgress()
}
```

- [ ] **Step 8: Commit**

```bash
cd "c:/Users/MING/myagent/echoling"
git add app/src/main/java/com/echoling/app/presentation/viewmodel/PracticeViewModel.kt
git commit -m "feat: replace GradeState with SttTestState in PracticeViewModel

Delete GradeState sealed class, all grading methods, and
PronunciationGrader injection. Add SttTestState, SttRecognizer
injection, and 6 new methods: startStt/stopStt/cancelStt/
submitTranscription/resetStt/onSttResults.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: 创建 `RecordingOverlay` UI 组件

**Files:**
- Create: `app/src/main/java/com/echoling/app/presentation/ui/screens/practice/components/RecordingOverlay.kt`

- [ ] **Step 1: Implement RecordingOverlay**

Write the file:

```kotlin
package com.echoling.app.presentation.ui.screens.practice.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Recording overlay shown during STT capture.
 * Displays a pulsing red dot, "正在录音…" text, 5 amplitude bars (random v1),
 * elapsed time, and a cancel button.
 */
@Composable
fun RecordingOverlay(
    elapsedMs: Long,
    amplitudeBars: List<Float>,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingRedDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    "正在录音… 松开结束",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(Modifier.height(12.dp))
            AmplitudeBars(values = amplitudeBars)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "%.1fs".format(elapsedMs / 1000.0),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                TextButton(onClick = onCancel) {
                    Text("取消", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun PulsingRedDot() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.Red)
    )
}

@Composable
private fun AmplitudeBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    barCount: Int = 5
) {
    Row(
        modifier = modifier.height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val bars = if (values.size >= barCount) values.take(barCount) else values
        bars.forEach { value ->
            val height = (8 + value * 24).dp
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onErrorContainer)
            )
        }
        // Fill remaining if fewer bars than barCount
        repeat(barCount - bars.size) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.3f))
            )
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd "c:/Users/MING/myagent/echoling"
git add app/src/main/java/com/echoling/app/presentation/ui/screens/practice/components/RecordingOverlay.kt
git commit -m "feat: add RecordingOverlay with pulsing dot and amplitude bars

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: 创建 `TranscriptionEditor` UI 组件

**Files:**
- Create: `app/src/main/java/com/echoling/app/presentation/ui/screens/practice/components/TranscriptionEditor.kt`

- [ ] **Step 1: Implement TranscriptionEditor**

Write the file:

```kotlin
package com.echoling.app.presentation.ui.screens.practice.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Editable transcription card shown after STT returns results.
 * User can edit the text, re-record, or submit for comparison.
 */
@Composable
fun TranscriptionEditor(
    initialText: String,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRerecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(initialText) }
    LaunchedEffect(initialText) { text = initialText }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "你说的是：",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { onTextChange(it); text = it },
                placeholder = { Text("未识别到语音，请手动输入") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                minLines = 3
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onRerecord) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("重录")
                }
                Button(
                    onClick = { onSubmit(text) },
                    enabled = text.isNotBlank()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("提交对比")
                }
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
cd "c:/Users/MING/myagent/echoling"
git add app/src/main/java/com/echoling/app/presentation/ui/screens/practice/components/TranscriptionEditor.kt
git commit -m "feat: add TranscriptionEditor with editable text and submit/rerecord

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: 创建 `TestResultCard` UI 组件

**Files:**
- Create: `app/src/main/java/com/echoling/app/presentation/ui/screens/practice/components/TestResultCard.kt`

- [ ] **Step 1: Implement TestResultCard**

Write the file:

```kotlin
package com.echoling.app.presentation.ui.screens.practice.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.echoling.app.presentation.viewmodel.SttTestState

/**
 * Result card shown after the user submits their transcription.
 * Passed: green card with "下一题" button.
 * Failed: red card with word-by-word comparison chips and "重录" button.
 */
@Composable
fun TestResultCard(
    state: SttTestState,
    originalEn: String,
    onNextItem: () -> Unit,
    onRerecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor, title) = when (state) {
        is SttTestState.Passed -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF1B5E20),
            "✓ 通过！"
        )
        is SttTestState.Failed -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFB71C1C),
            "✗ 不通过"
        )
        else -> return
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = contentColor)
            Spacer(Modifier.height(8.dp))
            Text(
                "原句：",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
            Text(originalEn, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            if (state is SttTestState.Failed) {
                Text(
                    "你说：",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
                WordChipsRow(
                    origWords = state.origWords,
                    transWords = state.transWords
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    failureReasonText(state.reason),
                    color = contentColor,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (state is SttTestState.Passed) {
                Text(state.text, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onRerecord) {
                    Text("重录")
                }
                if (state is SttTestState.Passed) {
                    Button(onClick = onNextItem) {
                        Text("下一题")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun WordChipsRow(
    origWords: List<String>,
    transWords: List<String>
) {
    val maxLen = maxOf(origWords.size, transWords.size)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (i in 0 until maxLen) {
            val orig = origWords.getOrNull(i)
            val trans = transWords.getOrNull(i)
            val (display, bg) = when {
                trans == null -> "[$orig]" to Color(0xFFFFCDD2)
                orig == null -> "[$trans]" to Color(0xFFFFF9C4)
                orig == trans -> orig to Color(0xFFC8E6C9)
                else -> "$orig/$trans" to Color(0xFFFFCDD2)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = bg
            ) {
                Text(
                    display,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun failureReasonText(reason: String): String = when (reason) {
    "empty_transcription" -> "未识别到语音，请重录或手动输入"
    "missing_word" -> "缺少单词，请检查是否漏说了"
    "extra_word" -> "多出了单词，请检查是否说多了"
    "wrong_word" -> "单词不匹配，红色标注的为错误单词"
    "no_test_item" -> "当前没有测试句子"
    else -> reason
}
```

- [ ] **Step 2: Commit**

```bash
cd "c:/Users/MING/myagent/echoling"
git add app/src/main/java/com/echoling/app/presentation/ui/screens/practice/components/TestResultCard.kt
git commit -m "feat: add TestResultCard with word-level comparison chips

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: 修改 `TestingPage` — 替换评分 UI 为 STT 交互

**Files:**
- Modify: `app/src/main/java/com/echoling/app/presentation/ui/screens/practice/TestingPage.kt`

- [ ] **Step 1: Remove old imports and state bindings**

Remove these imports (lines 20-25):

```kotlin
// DELETE:
import com.echoling.app.R
import com.echoling.app.presentation.ui.screens.practice.components.ScoreCard
import com.echoling.app.presentation.viewmodel.GradeState
```

Add these imports:

```kotlin
// ADD:
import com.echoling.app.presentation.viewmodel.SttTestState
import com.echoling.app.presentation.ui.screens.practice.components.RecordingOverlay
import com.echoling.app.presentation.ui.screens.practice.components.TranscriptionEditor
import com.echoling.app.presentation.ui.screens.practice.components.TestResultCard
```

- [ ] **Step 2: Replace state collections in TestingPage composable**

Replace lines 31-36 (the stateFlow collections):

```kotlin
// DELETE:
val gradeState by viewModel.gradeState.collectAsState()
val recordingPath by viewModel.recordingPath.collectAsState()

// ADD:
val sttTestState by viewModel.sttTestState.collectAsState()
val sttAmplitudeBars by viewModel.sttAmplitudeBars.collectAsState()
```

- [ ] **Step 3: Remove permissionLauncher and LaunchedEffect**

Delete the permissionLauncher block (lines 47-56) and the LaunchedEffect(gradeState) block (lines 60-69):

```kotlin
// DELETE:
val permissionLauncher = rememberLauncherForActivityResult(...)
// DELETE:
LaunchedEffect(gradeState) { ... }
```

- [ ] **Step 4: Replace the ScoreCard overlay with STT state overlay**

Replace lines 106-126 (the ScoreCard display block) with the STT overlay:

```kotlin
// ─── STT overlay: show based on sttTestState, anchored above the
// control bar. Only show for the current test item.
when (val s = sttTestState) {
    is SttTestState.Idle -> { /* nothing */ }
    is SttTestState.Listening -> {
        RecordingOverlay(
            elapsedMs = s.elapsedMs,
            amplitudeBars = sttAmplitudeBars,
            onCancel = { viewModel.cancelStt() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    is SttTestState.Transcribed -> {
        TranscriptionEditor(
            initialText = s.text,
            onTextChange = { /* editor manages internally */ },
            onSubmit = { text -> viewModel.submitTranscription(text) },
            onRerecord = { viewModel.resetStt() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    is SttTestState.Passed -> {
        TestResultCard(
            state = s,
            originalEn = currentTest?.contentEn.orEmpty(),
            onNextItem = {
                currentTest?.let {
                    viewModel.markSentenceTested(it.index, true)
                    viewModel.nextTestItem()
                    viewModel.resetStt()
                }
            },
            onRerecord = { viewModel.resetStt() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
    is SttTestState.Failed -> {
        TestResultCard(
            state = s,
            originalEn = s.original,
            onNextItem = {
                currentTest?.let {
                    viewModel.nextTestItem()
                    viewModel.resetStt()
                }
            },
            onRerecord = { viewModel.resetStt() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
```

- [ ] **Step 5: Modify TestingControlBar call**

Replace the `gradeState` parameter and `onStartGrading`/`onStopAndGrade` callbacks with the new STT parameters:

```kotlin
// BEFORE:
TestingControlBar(
    currentIndex = testState.currentTestIndex,
    totalCount = testState.testItems.size,
    isTested = currentSentenceState?.isTested == true,
    gradeState = gradeState,
    onPrevious = { viewModel.previousTestItem() },
    onMarkTested = {
        currentTest?.let {
            viewModel.markSentenceTested(it.index, true)
            viewModel.nextTestItem()
        }
    },
    onPlayAudio = {
        currentTest?.let { viewModel.playSubtitleOnce(it) }
    },
    onStartGrading = {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    },
    onStopAndGrade = { viewModel.stopAndGrade() },
)

// AFTER:
TestingControlBar(
    currentIndex = testState.currentTestIndex,
    totalCount = testState.testItems.size,
    isTested = currentSentenceState?.isTested == true,
    isSttListening = sttTestState is SttTestState.Listening,
    onPrevious = { viewModel.previousTestItem() },
    onMarkTested = {
        currentTest?.let {
            viewModel.markSentenceTested(it.index, true)
            viewModel.nextTestItem()
        }
    },
    onPlayAudio = {
        currentTest?.let { viewModel.playSubtitleOnce(it) }
    },
    onPressMic = { viewModel.startStt() },
    onReleaseMic = { viewModel.stopStt() },
)
```

- [ ] **Step 6: Modify TestingControlBar composable**

Replace the existing `TestingControlBar` function signature and the mic button. The function signature changes from:

```kotlin
@Composable
private fun TestingControlBar(
    currentIndex: Int,
    totalCount: Int,
    isTested: Boolean,
    gradeState: GradeState,
    onPrevious: () -> Unit,
    onMarkTested: () -> Unit,
    onPlayAudio: () -> Unit,
    onStartGrading: () -> Unit,
    onStopAndGrade: () -> Unit,
)
```

To:

```kotlin
@Composable
private fun TestingControlBar(
    currentIndex: Int,
    totalCount: Int,
    isTested: Boolean,
    isSttListening: Boolean,
    onPrevious: () -> Unit,
    onMarkTested: () -> Unit,
    onPlayAudio: () -> Unit,
    onPressMic: () -> Unit,
    onReleaseMic: () -> Unit,
)
```

Replace the microphone button (lines 483-519) with a long-press version:

```kotlin
// 3. Microphone (press and hold to record, release to stop)
Box(
    modifier = Modifier
        .size(54.dp)
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onPressMic()
                    tryAwaitRelease()
                    onReleaseMic()
                }
            )
        }
) {
    Surface(
        shape = CircleShape,
        color = if (isSttListening) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            Icons.Default.Mic,
            contentDescription = if (isSttListening) "正在录音，松开结束" else "按住录音",
            modifier = Modifier
                .size(26.dp)
                .align(Alignment.Center),
            tint = if (isSttListening)
                MaterialTheme.colorScheme.onError
            else
                MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
```

Also add these new imports at the top of the file:

```kotlin
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
```

And remove unused imports:

```kotlin
// DELETE (no longer needed):
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.echoling.app.R
import com.echoling.app.presentation.viewmodel.GradeState
```

- [ ] **Step 7: Remove the SnackbarHost**

Remove the SnackbarHost at the bottom of TestingPage (lines 154-159):

```kotlin
// DELETE:
SnackbarHost(
    hostState = snackbarHostState,
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 96.dp),
)
```

Also remove the `snackbarHostState` variable declaration (line 36):

```kotlin
// DELETE:
val snackbarHostState = remember { SnackbarHostState() }
```

- [ ] **Step 8: Commit**

```bash
cd "c:/Users/MING/myagent/echoling"
git add app/src/main/java/com/echoling/app/presentation/ui/screens/practice/TestingPage.kt
git commit -m "feat: replace grading UI with STT word-match flow in TestingPage

Replace ScoreCard + permissionLauncher + GradeState with
RecordingOverlay + TranscriptionEditor + TestResultCard driven by
SttTestState. Mic button now uses press-and-hold gesture.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: 更新 `AndroidManifest.xml` — 加 RecognitionService query

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add RecognitionService intent query**

Add inside the existing `<queries>` block in `app/src/main/AndroidManifest.xml`:

```xml
<intent>
    <action android:name="android.speech.RecognitionService" />
</intent>
```

The full `<queries>` block should become:

```xml
<queries>
    <intent>
        <action android:name="android.intent.action.TTS_SERVICE" />
    </intent>
    <intent>
        <action android:name="android.speech.RecognitionService" />
    </intent>
</queries>
```

- [ ] **Step 2: Commit**

```bash
cd "c:/Users/MING/myagent/echoling"
git add app/src/main/AndroidManifest.xml
git commit -m "feat: add RecognitionService query for Android 11+ STT visibility

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: 编译验证 + 回归测试

- [ ] **Step 1: Assemble debug**

```bash
cd "c:/Users/MING/myagent/echoling"
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run unit tests**

```bash
cd "c:/Users/MING/myagent/echoling"
./gradlew testDebugUnitTest
```

Expected: All tests pass, including WordMatcherTest (11/11).

- [ ] **Step 3: Verify no references to deleted code**

```bash
cd "c:/Users/MING/myagent/echoling"
grep -r "PronunciationGrader\|GradeState\|ScoreCard\|ScoreResult\|ScoreTier\|UpdateSentenceReadScoreUseCase\|synthesizeToFile\|updateReadScore" app/src/main/ --include="*.kt" | grep -v "readScore" | grep -v "SentenceEntity"
```

Expected: No output (all references removed). `readScore` in SentenceEntity/Sentence model is intentionally kept.

- [ ] **Step 4: Commit verification**

```bash
cd "c:/Users/MING/myagent/echoling"
git add -A
git commit -m "chore: BUILD SUCCESSFUL — STT word-match testing fully wired

All 11 old scoring files removed. 4 new files added (WordMatcher,
SttRecognizer, RecordingOverlay, TranscriptionEditor, TestResultCard).
TestingPage wired with press-and-hold mic + state overlay.
Manifest updated with RecognitionService query.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## File Summary

### Created (5)

| Path | Purpose |
|---|---|
| `speech/WordMatcher.kt` | Pure function: strict sequential word match |
| `speech/SttRecognizer.kt` | `@Singleton` wrapping system SpeechRecognizer |
| `ui/screens/practice/components/RecordingOverlay.kt` | Pulsing dot + amplitude bars + timer |
| `ui/screens/practice/components/TranscriptionEditor.kt` | Editable text + submit/rerecord buttons |
| `ui/screens/practice/components/TestResultCard.kt` | Pass/Fail card with word chip comparison |

### Test Created (1)

| Path | Purpose |
|---|---|
| `test/.../speech/WordMatcherTest.kt` | 11 tests for WordMatcher |

### Deleted (10)

| Path | Reason |
|---|---|
| `speech/PronunciationGrader.kt` | Replaced by STT flow |
| `speech/DtwAligner.kt` | Replaced by STT flow |
| `speech/RmsExtractor.kt` | Replaced by STT flow |
| `speech/WavReader.kt` | Replaced by STT flow |
| `speech/M4aDecoder.kt` | Replaced by STT flow |
| `speech/Resampler.kt` | Replaced by STT flow |
| `speech/TtsReferenceCache.kt` | Replaced by STT flow |
| `domain/model/ScoreResult.kt` | Replaced by SttTestState |
| `domain/usecase/UpdateSentenceReadScoreUseCase.kt` | Replaced by STT flow |
| `ui/screens/practice/components/ScoreCard.kt` | Replaced by TestResultCard |

### Modified (6)

| Path | Change |
|---|---|
| `viewmodel/PracticeViewModel.kt` | Delete GradeState + grading methods; add SttTestState + 6 STT methods |
| `ui/screens/practice/TestingPage.kt` | Replace ScoreCard + permissionLauncher with STT overlay |
| `player/TtsManager.kt` | Remove `synthesizeToFile()` + `activeSynthesis` field |
| `data/local/db/dao/SentenceDao.kt` | Remove `updateReadScore()` |
| `domain/repository/SentenceRepository.kt` | Remove `updateReadScore()` declaration |
| `data/repository/SentenceRepositoryImpl.kt` | Remove `updateReadScore()` impl |
| `AndroidManifest.xml` | Add `RecognitionService` intent query |

---

## Verification Checklist

- [ ] `./gradlew assembleDebug` → BUILD SUCCESSFUL
- [ ] `./gradlew testDebugUnitTest` → all tests pass
- [ ] `grep -r "PronunciationGrader\|GradeState\|ScoreCard\|ScoreResult" app/src/main/` → no output
- [ ] Manual: 长按 mic → 说 "I love you" → 松手 → 看到 Transcribed 文字
- [ ] Manual: 编辑文字 → 提交 → 看到 TestResultCard
- [ ] Manual: 通过后点"下一题" → 切到下一题
- [ ] Manual: 不通过 → 能看到红色错词 chip
- [ ] Manual: STT 不可用 → 直接进空编辑框，可手动输入