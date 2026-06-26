package com.echoling.app.presentation.ui.screens.practice

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
    val sttAmplitudeBars by viewModel.sttAmplitudeBars.collectAsState()

    val currentTest = testState.testItems.getOrNull(testState.currentTestIndex)
    val currentRealIndex = subtitles.indexOf(currentTest)
    val currentSentenceState = currentRealIndex.takeIf { it >= 0 }?.let {
        sentenceStates[subtitles[it].index]
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Test progress header
        TestingProgressHeader(
            testedCount = testState.testedCount,
            totalCount = if (testState.isActive) testState.testItems.size else subtitles.size,
            isTestActive = testState.isActive,
            onStartTest = { viewModel.startTestMode() },
            onResetTest = { /* Could add reset functionality */ }
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            // ─── STT overlay: show based on sttTestState, anchored above
            // the control bar. Only show for the current test item.
            when (val s = sttTestState) {
                is SttTestState.Idle -> { /* nothing */ }
                is SttTestState.Listening -> {
                    RecordingOverlay(
                        elapsedMs = s.elapsedMs,
                        amplitudeBars = sttAmplitudeBars,
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

            Spacer(Modifier.weight(1f))

            // Test control bar
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
        }
    }
}

@Composable
private fun TestingProgressHeader(
    testedCount: Int,
    totalCount: Int,
    isTestActive: Boolean,
    onStartTest: () -> Unit,
    onResetTest: () -> Unit
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
                    if (totalCount > 0) "$testedCount / $totalCount 已完成" else "点击开始测试",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
            modifier = Modifier.padding(16.dp),
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

@Composable
private fun TestingWordsFlowRow(
    words: List<String>,
    revealedWords: Set<Int>,
    onWordClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Pre-calculate which words fit on each line
    val lines = remember(words) {
        val result = mutableListOf<List<Pair<Int, String>>>()
        var currentLine = mutableListOf<Pair<Int, String>>()
        var currentLineCharCount = 0
        val maxCharsPerLine = 25 // Approximate characters per line

        words.forEachIndexed { index, word ->
            val cleanWord = word.replace(Regex("[^\\w']"), "")
            if (cleanWord.isNotEmpty()) {
                if (currentLineCharCount + cleanWord.length > maxCharsPerLine && currentLine.isNotEmpty()) {
                    result.add(currentLine.toList())
                    currentLine = mutableListOf()
                    currentLineCharCount = 0
                }
                currentLine.add(index to cleanWord)
                currentLineCharCount += cleanWord.length + 1 // +1 for space
            }
        }
        if (currentLine.isNotEmpty()) {
            result.add(currentLine.toList())
        }
        result
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start
    ) {
        lines.forEach { lineWords ->
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                lineWords.forEach { (index, cleanWord) ->
                    val isRevealed = revealedWords.contains(index)

                    if (isRevealed) {
                        Text(
                            text = cleanWord,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    } else {
                        // Hidden word block with fixed height matching word length width
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE0E0E0))
                                .clickable { onWordClick(index) }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cleanWord,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.Transparent
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

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
) {
    // Pill-shaped background frame matching the other practice pages'
    // bottom bars. Houses 4 large action buttons (previous / play
    // audio / mic-grade / mark tested) with consistent visual depth.
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

            // 2. Play audio
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

            // 3. Microphone (press and hold to record, release to stop)
            // Visual feedback: button scales up + 3 concentric ripple rings expand
            // outward (like a speaker broadcasting) when held. The pointerInput
            // stays on the OUTER box so the press area is the full ripple zone,
            // not just the inner button.
            ListeningMicButton(
                isListening = isSttListening,
                onPress = onPressMic,
                onRelease = onReleaseMic,
            )

            // 4. Mark tested / Next
            IconButton(onClick = onMarkTested) {
                Surface(
                    shape = CircleShape,
                    color = if (isTested)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        if (isTested) Icons.Default.SkipNext else Icons.Default.Check,
                        if (isTested) "下一题" else "标记完成",
                        modifier = Modifier.size(26.dp),
                        tint = if (isTested)
                            MaterialTheme.colorScheme.onSecondaryContainer
                        else
                            MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

/**
 * Press-and-hold mic button. When [isListening] is true:
 * - 3 concentric ripple rings expand outward from the button (staggered 600ms)
 * - The button itself scales up 1.15x
 * - The button turns red (error color)
 *
 * The pointerInput sits on the outer 140dp Box so the press target
 * stays generous even though the button visually appears at 54dp.
 */
@Composable
private fun ListeningMicButton(
    isListening: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(140.dp)
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
        // Ripple rings — only when listening. No parent .clip() so
        // they can extend beyond the 140dp box bounds.
        if (isListening) {
            repeat(3) { index ->
                MicRippleRing(delayMs = index * 600L)
            }
        }
        // The actual mic button (scales up when listening)
        Surface(
            shape = CircleShape,
            color = if (isListening) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(54.dp)
                .scale(if (isListening) 1.15f else 1f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = if (isListening) "正在录音，松开结束" else "按住录音",
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

@Composable
private fun MicRippleRing(delayMs: Long) {
    val transition = rememberInfiniteTransition(label = "ripple_$delayMs")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, delayMillis = delayMs.toInt()),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple_scale_$delayMs"
    )
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, delayMillis = delayMs.toInt()),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple_alpha_$delayMs"
    )
    Box(
        modifier = Modifier
            .size(54.dp)
            .scale(scale)
            .alpha(alpha)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
    )
}
