package com.echoling.app.presentation.ui.screens.practice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.echoling.app.presentation.ui.screens.practice.components.RecordingOverlay
import com.echoling.app.presentation.ui.screens.practice.components.TestResultCard
import com.echoling.app.presentation.ui.screens.practice.components.TranscriptionEditor
import com.echoling.app.presentation.viewmodel.PracticeViewModel
import com.echoling.app.presentation.viewmodel.SttTestState

@Composable
fun TestingPage(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    val subtitles by viewModel.subtitles.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val sentenceStates by viewModel.sentenceStates.collectAsState()
    val sttTestState by viewModel.sttTestState.collectAsState()
    // (2026-06-28) Per-page recording path. Collects only the
    // test-page path; speaking-page recordings are invisible to
    // this page so the 回放录音 button stays disabled when the
    // user has only a speaking-page recording.
    val recordingPath by viewModel.testRecordingPath.collectAsState()
    val isPlayingRecording by viewModel.isPlayingRecording.collectAsState()
    val modelDownloadState by viewModel.modelDownloadState.collectAsState()

    // RECORD_AUDIO permission for the WavRecorder. The previous version
    // of TestingPage skipped this and called viewModel.startStt() directly
    // — which on a freshly-installed app (no prior SpeakingPage visit)
    // would silently fail at the WavRecorder / MediaRecorder level. Mirrors
    // SpeakingPage.kt:48-60.
    var hasRecordPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) viewModel.startStt()
    }

    val currentTest = testState.testItems.getOrNull(testState.currentTestIndex)
    val currentRealIndex = subtitles.indexOf(currentTest)
    val currentSentenceState = currentRealIndex.takeIf { it >= 0 }?.let {
        sentenceStates[subtitles[it].index]
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Test progress header
        TestingProgressHeader(
            testedCount = testState.testedCount,
            // (2026-06-28) Pass currentTestIndex so the X/Y text
            // reflects the current test position (1-indexed) and
            // visibly changes when the user taps 上一题/下一题.
            // The X is the *current* question number, not the count
            // of completed ones — see TestingProgressHeader body.
            currentTestIndex = testState.currentTestIndex,
            totalCount = if (testState.isActive) testState.testItems.size else subtitles.size,
            isTestActive = testState.isActive,
            onStartTest = { viewModel.startTestMode() },
            // (2026-06-28) "重新测试" button on the top-right. Wires
            // to viewModel.restartTest() which un-marks all test
            // items and re-initializes the test from X=1. Per user
            // spec: "用户也可以再进行新的一轮测试，所有需要增加一个
            // 按钮 重新测试/刷新图标，放在测试页面的右上角".
            onRestartTest = { viewModel.restartTest() }
        )

        if (!testState.isActive || testState.testItems.isEmpty()) {
            // Empty state - prompt to start test
            TestingEmptyState(
                onStartTest = { viewModel.startTestMode() },
                modifier = Modifier.weight(1f)
            )
        } else {
            // Test item card
            currentTest?.let { sub ->
                TestingSubtitleCard(
                    subtitle = sub,
                    revealedWords = testState.revealedWords,
                    isTested = currentSentenceState?.isTested == true,
                    onWordReveal = { viewModel.revealTestWord(it) },
                    // (2026-07-10) §16.X: weight(1f) lets the card share
                    // the vertical space between header and the bottom
                    // control bar instead of pushing the ControlBar off-
                    // screen for long sentences. The inner Column has
                    // verticalScroll(rememberScrollState()) so the words
                    // scroll inside the card when content overflows.
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
            }

            // ─── STT overlay: show based on sttTestState, anchored above
            // the control bar. Only show for the current test item.
            when (val s = sttTestState) {
                is SttTestState.Idle -> { /* nothing */ }
                is SttTestState.Listening -> {
                    RecordingOverlay(
                        // (2026-07-10) §12.42: vertical 8dp → 2dp.
                        // RecordingOverlay is the LAST child before
                        // TestingControlBar (same Column layout), so
                        // its bottom padding IS the gap to the bar.
                        // 8dp left 8dp breathing room + 12dp inside
                        // RecordingOverlay's own padding = 20dp, which
                        // felt detached from the bar. Reducing the
                        // outer to 2dp makes the circle visually
                        // adjacent to the pill, matching the new
                        // layout rule "recording animation belongs
                        // AT the bar, not near it".
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                    )
                }
                is SttTestState.Transcribing -> {
                    // Vosk is processing the recorded WAV file. We can't
                    // show the editor yet (the text isn't ready) and we
                    // can't let the user press the mic again (would
                    // cancel the in-flight transcribe). Show a small
                    // "正在识别" card with a progress indicator. Also
                    // surfaces a first-run "下载模型中" banner if the
                    // model is being downloaded right now.
                    TranscribingCard(
                        downloadState = modelDownloadState,
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
                        // (2026-06-28) Removed the explicit
                        // markSentenceTested() call here — the
                        // ViewModel.submitTranscription() method
                        // already auto-marks the sentence when the
                        // STT result passes (see
                        // PracticeViewModel.submitTranscription,
                        // where it calls
                        // `markSentenceTested(currentTest.index, true)`
                        // before flipping _sttTestState to Passed).
                        // Calling it again from the UI would be
                        // redundant (markSentenceTested is idempotent
                        // — not a bug, just dead code).
                        //
                        // (2026-07-03) Also removed the manual
                        // "mark tested" checkmark button — 下一题
                        // is always free, "tested" is just a display
                        // flag surfaced on the sentence grid. The
                        // checkmark was redundant with the auto-mark
                        // path above and gave the user a way to mark
                        // a sentence tested without actually testing
                        // it, which defeats the practice flow.
                        onNextItem = {
                            // (2026-07-05) Route through
                            // advanceToNextTestItem so the result card
                            // is cleared at the same time the index
                            // advances — see PracticeViewModel for
                            // why the explicit resetStt() used to be
                            // required on this callsite only.
                            viewModel.advanceToNextTestItem()
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
                            // (2026-07-05) Same fix as Passed's
                            // onNextItem — clear STT state so the
                            // Failed card disappears when the user
                            // moves on. Previously the old explicit
                            // nextTestItem() + resetStt() pair had
                            // the same risk of being desynced from
                            // the ControlBar's button.
                            viewModel.advanceToNextTestItem()
                        },
                        onRerecord = { viewModel.resetStt() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            // (2026-07-10) §16.X: Spacer(weight=1f) removed — the card
            // now claims weight(1f) above, so the ControlBar naturally
            // sits at the bottom without needing an explicit Spacer.
            // Test control bar
            TestingControlBar(
                currentIndex = testState.currentTestIndex,
                isSttListening = sttTestState is SttTestState.Listening,
                hasRecording = recordingPath != null,
                isPlayingRecording = isPlayingRecording,
                onPrevious = { viewModel.previousTestItem() },
                // (2026-07-03) Removed onMarkTested entirely. A sentence
                // is now marked as tested only by submitting a
                // transcription that passes the STT matcher — see
                // PracticeViewModel.submitTranscription which calls
                // markSentenceTested() on pass. The checkmark button is
                // gone from the control bar; the 5th slot is now a
                // plain 下一题 button that always advances.
                onNextItem = { viewModel.advanceToNextTestItem() },
                onPlayAudio = {
                    currentTest?.let { viewModel.playSubtitleOnce(it) }
                },
                onPressMic = {
                    // Gate on RECORD_AUDIO: without it, SpeechRecognizer
                    // returns ERROR_INSUFFICIENT_PERMISSIONS (code 9) and
                    // the result is empty ("未识别到语音"). Asking only on
                    // press (not on every render) keeps the prompt
                    // dismissible — same pattern as SpeakingPage.
                    if (hasRecordPermission) viewModel.startStt()
                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                onReleaseMic = { viewModel.stopStt() },
                // (2026-06-28) Pass testRecordingPath.value
                // explicitly so the 回放 button can only ever
                // play test-page recordings. The button is
                // already disabled by hasRecording = (test page
                // path != null), so the click is impossible when
                // there's no test recording — this is the
                // belt-and-suspenders that the correct path
                // is used.
                onPlayRecording = { viewModel.playRecording(viewModel.testRecordingPath.value) },
            )
        }
    }
}

@Composable
private fun TestingProgressHeader(
    testedCount: Int,
    // (2026-06-28) Added currentTestIndex so the X/Y display can
    // show the current test position (1-indexed) — the user's
    // mental model of "X/Y" is "you are on question X of Y", which
    // must increment/decrement in lock-step with the 上一题/下一题
    // buttons. The old display used `testedCount` here, which is
    // a different semantic ("X out of Y are completed") and
    // doesn't change when the user navigates back via 上一题.
    currentTestIndex: Int,
    totalCount: Int,
    isTestActive: Boolean,
    onStartTest: () -> Unit,
    // (2026-06-28) Replaces the old placeholder `onResetTest`. Wired
    // to viewModel.restartTest() which un-marks all test items and
    // re-initializes the test from X=1, so the user can start a new
    // round of testing at any point while a test is active.
    onRestartTest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "测试",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    // (2026-06-28) X is now the *current test
                    // position* (1-indexed: `currentTestIndex + 1`),
                    // not the tested count. The user's mental model
                    // of "X / Y" matches what the 上一题/下一题
                    // buttons do — X must visibly decrement when
                    // tapping 上一题 and increment when tapping
                    // 下一题. testedCount is a different concept
                    // (how many sentences in the pool have been
                    // marked isTested=true) and only changes when
                    // the user marks a sentence as tested; it can
                    // drift up independently of navigation (e.g.
                    // marking the same question twice from the
                    // previous-page card).
                    //
                    // Clamped to totalCount in the (theoretical)
                    // case where currentTestIndex exceeds the test
                    // pool size after a re-init.
                    if (totalCount > 0)
                        "第 ${(currentTestIndex + 1).coerceAtMost(totalCount)} / $totalCount 题"
                    else
                        "点击开始测试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // (2026-06-28) Top-right action: 开始测试 (when not
            // active) or 重新测试 (when active). The two states
            // occupy the same horizontal slot to keep the header's
            // layout stable as the user toggles between "not
            // started" and "in progress".
            if (!isTestActive) {
                Button(
                    onClick = onStartTest,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("开始测试")
                }
            } else {
                // "重新测试" — outlined button with refresh icon.
                // Outlined (not filled) to keep it visually less
                // prominent than the original 开始测试 filled button
                // — a user mid-test shouldn't feel like the app is
                // pushing them to restart. The icon (Refresh) is
                // the standard "restart" affordance; the text label
                // makes the action unambiguous.
                OutlinedButton(
                    onClick = onRestartTest,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("重新测试")
                }
            }
        }

        // Progress bar
        if (totalCount > 0) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = testedCount.toFloat() / totalCount.coerceAtLeast(1),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun TestingEmptyState(
    onStartTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Quiz,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "点击开始测试检验学习效果",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onStartTest,
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("开始测试")
        }
    }
}

@Composable
private fun TestingSubtitleCard(
    subtitle: com.echoling.app.player.subtitle.Subtitle,
    revealedWords: Set<Int>,
    isTested: Boolean,
    onWordReveal: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val words = subtitle.contentEn.split(Regex("\\s+")).filter { it.isNotEmpty() }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTested)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            // (2026-07-10) §16.X: Add verticalScroll so a very long
            // sentence (10+ word lines) doesn't overflow the card.
            // Combined with the outer weight(1f) on TestingSubtitleCard,
            // this keeps the bottom control bar at its natural height —
            // previously a long card pushed the ControlBar off-screen,
            // making the bottom buttons look "compressed".
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "句子 ${subtitle.index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                if (isTested) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "已测试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Test mode: words displayed horizontally, left-aligned, wrap to new lines
            TestingWordsFlowRow(
                words = words,
                revealedWords = revealedWords,
                onWordClick = onWordReveal,
                modifier = Modifier.fillMaxWidth()
            )

            // Show Chinese translation hint when all revealed
            if (revealedWords.size == words.size && subtitle.contentCn.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle.contentCn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TestingWordsFlowRow(
    words: List<String>,
    revealedWords: Set<Int>,
    onWordClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // (2026-07-18) §16.X: 切到 FlowRow, 移除 25 char 启发式.
    //
    // 旧版用 Column{Row{...}} 配合 hardcap `maxCharsPerLine = 25`
    // 估算每行字数后手动切行 + Arrangement.Start + outer Column
    // `CenterHorizontally`: 每行 chip 集合只占卡片宽度 ~67% 就换行
    // (25 chars × ~8dp/char ≈ 200dp, 卡片可显示宽度 ~300dp),
    // 用户看到"右边空着就换行"。
    //
    // FlowRow 让 chip + 空格 Text 自然流到卡片右边界再 wrap, 多余
    // 行由容器宽度决定。新版本每行 chip 仍**视觉上居中**是次要
    // 追求; 主要诉求是"行末不留大片空白"——FlowRow 的天然 wrap
    // 让短行直接到尾部换行, 不再 column-Center 强行平衡。
    //
    // bodyLarge + height(28.dp) 沿用旧版 (允许 chip 比纯文字高
    // 6dp 充当视觉 tap 目标). chip 之间用 Text(" ") bodyLarge
    // 自然间距 ≈ 4.5dp, 接近旧 Spacer(4dp).
    val style = MaterialTheme.typography.bodyLarge
    val revealedColor = MaterialTheme.colorScheme.onSurface

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        words.forEachIndexed { index, word ->
            if (index > 0) {
                Text(" ", style = style, color = revealedColor)
            }
            val isRevealed = revealedWords.contains(index)
            Box(
                modifier = Modifier
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (!isRevealed) {
                            Modifier
                                .background(Color(0xFFE0E0E0))
                                .clickable { onWordClick(index) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = word,
                    style = style,
                    color = if (isRevealed) revealedColor else Color.Transparent,
                )
            }
        }
    }
}

@Composable
private fun TestingControlBar(
    currentIndex: Int,
    isSttListening: Boolean,
    hasRecording: Boolean,
    isPlayingRecording: Boolean,
    onPrevious: () -> Unit,
    // (2026-07-03) Removed the old onMarkTested parameter. A sentence
    // is now marked tested only via submitTranscription() passing the
    // STT matcher — there is no manual "mark complete" path anymore,
    // per user request "勾 去掉". This bar's 5th slot is just 下一题
    // and always advances.
    onNextItem: () -> Unit,
    onPlayAudio: () -> Unit,
    onPressMic: () -> Unit,
    onReleaseMic: () -> Unit,
    onPlayRecording: () -> Unit,
) {
    // Pill-shaped background frame matching the other practice pages'
    // bottom bars. Houses 4 large action buttons (previous / play
    // audio / mic / play recording / 下一题) with consistent
    // visual depth.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Previous
            IconButton(
                onClick = onPrevious,
                enabled = currentIndex > 0
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (currentIndex > 0)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        Icons.Default.SkipPrevious, "上一题",
                        modifier = Modifier.size(26.dp),
                        tint = if (currentIndex > 0)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Play audio (the original sentence's TTS)
            IconButton(onClick = onPlayAudio) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        Icons.Default.VolumeUp, "播放音频",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // 3. Microphone (press and hold to record, release to stop).
            // Restored to the original look per user spec:
            //   - no ripple rings (the in-overlay sound waves now carry
            //     the active-feedback signal)
            //   - no scale-up animation
            //   - button is bigger (72dp) for a more confident tap target
            //   - color flips to red on press
            MicButton(
                isListening = isSttListening,
                onPress = onPressMic,
                onRelease = onReleaseMic,
            )

            // 4. Play back the user's just-recorded audio. Disabled
            // until the user has finished at least one recording in
            // the current STT session; the icon swaps to Stop while
            // audio is playing.
            IconButton(
                onClick = onPlayRecording,
                enabled = hasRecording
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (hasRecording)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        if (isPlayingRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                        if (isPlayingRecording) "停止回放" else "回放录音",
                        modifier = Modifier.size(26.dp),
                        tint = if (hasRecording)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 5. Next — always advances; the mark-tested checkmark
            // branch was removed per user spec (2026-07-03). A
            // sentence is now marked tested solely by
            // submitTranscription() passing the STT matcher.
            IconButton(onClick = onNextItem) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        Icons.Default.SkipNext,
                        "下一题",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * Press-and-hold mic button. While the user holds:
 * - button color flips from primaryContainer to error (red)
 * - NO ripple / scale animation (those have been moved into the
 *   [RecordingOverlay] which now carries the active-feedback signal
 *   on its own, so the bar around the mic stays calm and the
 *   overlay is the focal point)
 *
 * (2026-07-10) §12.42: Sized 54dp + icon 26dp — UNIFIED with the
 * SpeakingPage mic button so the bottom bar's row intrinsic height
 * matches. Previous version was 72dp / 34dp icon which made Testing
 * page's bottom pill visibly TALLER than 精听 / 泛听's same-shape
 * pill. The microphone is no longer the "primary" oversized action
 * anywhere — the RecordingOverlay carries the focal-point signal.
 */
@Composable
private fun MicButton(
    isListening: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    Box(
        modifier = Modifier
            // (2026-07-10) §12.42: 72dp → 54dp to match SpeakingPage's
            // (SpeakingPage.kt:608-615) `Box.size(54.dp)` recording
            // button. The 26dp icon keeps visual weight without
            // inflating the row's intrinsic height — Testing's
            // bottom pill now matches 精听/泛听's.
            .size(54.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = if (isListening) "正在录音，松开结束" else "按住录音",
                    // (2026-07-10) §12.42: 34dp → 26dp to match
                    // SpeakingPage's recording button icon
                    // (SpeakingPage.kt:626).
                    modifier = Modifier.size(26.dp),
                    tint = if (isListening)
                        MaterialTheme.colorScheme.onError
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

/**
 * "正在识别" card shown briefly between releasing the mic and the
 * editor appearing. Vosk runs on the recorded WAV file — usually
 * 100-500ms on a warm device, but can be many seconds on first use
 * while the ~40MB acoustic model is being downloaded.
 *
 * Surfaces download progress inline so the user isn't staring at an
 * unexplained spinner.
 */
@Composable
private fun TranscribingCard(
    downloadState: com.echoling.app.speech.ModelManager.DownloadState,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        modifier = modifier
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                strokeWidth = 3.dp
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "正在识别…",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(4.dp))
            when (val d = downloadState) {
                is com.echoling.app.speech.ModelManager.DownloadState.Deploying -> {
                    // (2026-07-04) First-run: model is being copied
                    // out of APK assets into internal storage
                    // (~68 MB unpacked). Was a network download
                    // before the offline cutover; the user-facing
                    // copy is now "正在准备识别模型" so the message
                    // doesn't claim a network step that no longer
                    // exists.
                    val pct = (d.progress * 100).toInt()
                    Text(
                        "首次使用：正在准备识别模型 ($pct%)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = d.progress,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                is com.echoling.app.speech.ModelManager.DownloadState.Failed -> {
                    Text(
                        "模型加载失败：${d.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {
                    // NotStarted / Ready — model is fine, Vosk is just
                    // processing the audio.
                    Text(
                        "请稍候",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
