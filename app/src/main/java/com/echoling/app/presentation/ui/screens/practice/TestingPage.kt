package com.echoling.app.presentation.ui.screens.practice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.echoling.app.R
import com.echoling.app.presentation.ui.screens.practice.components.ScoreCard
import com.echoling.app.presentation.viewmodel.GradeState
import com.echoling.app.presentation.viewmodel.PracticeViewModel

@Composable
fun TestingPage(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    val subtitles by viewModel.subtitles.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val sentenceStates by viewModel.sentenceStates.collectAsState()
    val gradeState by viewModel.gradeState.collectAsState()
    val recordingPath by viewModel.recordingPath.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val currentTest = testState.testItems.getOrNull(testState.currentTestIndex)
    val currentRealIndex = subtitles.indexOf(currentTest)
    val currentSentenceState = currentRealIndex.takeIf { it >= 0 }?.let {
        sentenceStates[subtitles[it].index]
    }

    // Permission launcher — only start recording if granted.
    // Denied: stay in Idle so the user can retry; the next time they
    // tap the mic we re-launch.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startRecordingForGrading()
        } else {
            // snackbar handled below via gradeState.Error if VM surfaces one;
            // otherwise user just sees no recording start. They can tap again.
        }
    }

    // Surface Error state to the user, then reset to Idle so the
    // snackbar doesn't re-show on recomposition.
    LaunchedEffect(gradeState) {
        val state = gradeState
        if (state is GradeState.Error) {
            snackbarHostState.showSnackbar(
                message = state.message,
                duration = SnackbarDuration.Short,
            )
            viewModel.cancelGrading()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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

                // ─── ScoreCard overlay: show after a successful grade,
                // anchored above the control bar. Only show for the
                // sentence that was actually graded (don't follow if
                // user already advanced to the next item).
                val successState = (gradeState as? GradeState.Success)
                if (successState != null &&
                    currentTest != null &&
                    successState.sentenceId == currentTest.index &&
                    recordingPath != null
                ) {
                    ScoreCard(
                        result = successState.result,
                        onReplayRecording = { viewModel.playRecording() },
                        onRegrade = { viewModel.startRecordingForGrading() },
                        onNextItem = {
                            viewModel.markSentenceTested(currentTest.index, true)
                            viewModel.nextTestItem()
                            viewModel.cancelGrading()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(Modifier.weight(1f))

                // Test control bar
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
            }
        }

        // Snackbar host — floats above the control bar
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        )
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
    gradeState: GradeState,
    onPrevious: () -> Unit,
    onMarkTested: () -> Unit,
    onPlayAudio: () -> Unit,
    onStartGrading: () -> Unit,
    onStopAndGrade: () -> Unit,
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

            // 3. Microphone (grade this sentence) - center
            val isRecording = gradeState is GradeState.Recording
            val isLoading = gradeState is GradeState.Loading
            IconButton(
                onClick = if (isRecording) onStopAndGrade else onStartGrading,
                enabled = !isLoading,
            ) {
                Surface(
                    shape = CircleShape,
                    color = when {
                        isRecording -> MaterialTheme.colorScheme.error
                        isLoading -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    modifier = Modifier.size(54.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(12.dp)
                                .size(30.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            if (isRecording) "停止并评分" else stringResource(R.string.grade_btn_start),
                            modifier = Modifier.size(26.dp),
                            tint = if (isRecording)
                                MaterialTheme.colorScheme.onError
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

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
