package com.echoling.app.presentation.ui.screens.practice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoling.app.player.subtitle.Subtitle
import com.echoling.app.presentation.viewmodel.PracticeViewModel
import com.echoling.app.presentation.viewmodel.SentenceState
import com.echoling.app.speech.RecordingState

@Composable
fun SpeakingPage(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val currentSubtitleIndex by viewModel.currentSubtitleIndex.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val isPlayingRecording by viewModel.isPlayingRecording.collectAsState()
    val sentenceStates by viewModel.sentenceStates.collectAsState()
    val recordingPath by viewModel.recordingPath.collectAsState()
    val isVideoMode by viewModel.isVideoMode.collectAsState()
    val videoPlayerState by viewModel.videoPlayerState.collectAsState()

    var revealedWords by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var showWordDialog by remember { mutableStateOf(false) }
    var selectedWord by remember { mutableStateOf("") }
    var hasRecordPermission by remember { mutableStateOf(false) }
    var showSentenceMenu by remember { mutableStateOf(false) }

    val translationState by viewModel.wordTranslation.collectAsState()
    val currentSubtitle = subtitles.getOrNull(currentSubtitleIndex)
    val exampleSentence = currentSubtitle?.contentEn.orEmpty()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) viewModel.startRecording()
    }

    val currentSub = subtitles.getOrNull(currentSubtitleIndex)
    val currentSentenceState = currentSub?.let { sentenceStates[it.index] }

    Column(modifier = modifier.fillMaxSize()) {
        // Video Player
        if (isVideoMode && videoPlayerState != null) {
            VideoPlayerSection(
                exoPlayer = videoPlayerState!!,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        }

        // Sentence selector dropdown
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedButton(
                onClick = { showSentenceMenu = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${currentSubtitleIndex + 1} / ${subtitles.size}")
                    Icon(Icons.Default.ArrowDropDown, null)
                }
            }
            DropdownMenu(
                expanded = showSentenceMenu,
                onDismissRequest = { showSentenceMenu = false },
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                // Display sentences in a grid of 5 columns
                val columns = 5
                val rows = (subtitles.size + columns - 1) / columns
                for (row in 0 until rows) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        for (col in 0 until columns) {
                            val index = row * columns + col
                            if (index < subtitles.size) {
                                val state = sentenceStates[subtitles[index].index]
                                val isCompleted = state?.isCompleted == true
                                val isSelected = index == currentSubtitleIndex

                                Surface(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable {
                                            viewModel.skipToSubtitle(index)
                                            revealedWords = emptySet()
                                            showSentenceMenu = false
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        isCompleted -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                        else -> Color.Transparent
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                isCompleted -> Color(0xFF4CAF50) // Green for completed
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        if (isCompleted) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                "已完成",
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .align(Alignment.TopEnd)
                                                    .padding(2.dp),
                                                tint = Color(0xFF4CAF50)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Current sentence card
        if (currentSub != null) {
            SpeakingSubtitleCard(
                subtitle = currentSub,
                displayIndex = currentSubtitleIndex,
                isCompleted = currentSentenceState?.isCompleted == true,
                revealedWords = revealedWords,
                onWordReveal = { wordIdx ->
                    revealedWords = if (revealedWords.contains(wordIdx)) {
                        revealedWords - wordIdx
                    } else {
                        revealedWords + wordIdx
                    }
                },
                onWordLongClick = { word ->
                    selectedWord = word
                    showWordDialog = true
                    viewModel.requestWordTranslation(word)
                },
                onMarkCompleted = { completed ->
                    viewModel.markSentenceCompleted(currentSub.index, completed)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        Spacer(Modifier.weight(1f))

        // Recording playback card (if recording exists)
        recordingPath?.let {
            RecordingPlaybackCard(
                isPlaying = isPlayingRecording,
                onPlayRecording = { viewModel.playRecording() },
                onStopPlaying = { viewModel.stopPlayingRecording() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        // Bottom controls: Prev, Play, Record, Playback, Next
        SpeakingControlBar(
            recordingState = recordingState,
            hasRecordPermission = hasRecordPermission,
            permissionLauncher = permissionLauncher,
            onSkipPrevious = {
                revealedWords = emptySet()
                viewModel.skipToPreviousSubtitle()
            },
            onSkipNext = {
                revealedWords = emptySet()
                viewModel.skipToNextSubtitle()
            },
            onPlayPause = {
                if (playbackState.isPlaying) {
                    viewModel.pause()
                } else {
                    currentSub?.let { viewModel.playSubtitleOnce(it) }
                }
            },
            viewModel = viewModel
        )
    }

    if (showWordDialog) {
        WordSaveDialog(
            initialWord = selectedWord,
            translationState = translationState,
            onDismiss = {
                showWordDialog = false
                viewModel.clearWordTranslation()
            },
            onRetranslate = { newWord ->
                // Re-runs the local dictionary lookup on the
                // (possibly user-edited) word. The dialog will update
                // with the new translation when state emits Loaded.
                viewModel.requestWordTranslation(newWord)
            },
            onSave = { word, translation, phonetic, pos ->
                viewModel.saveWord(word, translation, exampleSentence, phonetic, pos)
                showWordDialog = false
                viewModel.clearWordTranslation()
            }
        )
    }
}

@Composable
private fun SpeakingSubtitleCard(
    subtitle: Subtitle,
    displayIndex: Int,
    isCompleted: Boolean,
    revealedWords: Set<Int>,
    onWordReveal: (Int) -> Unit,
    onWordLongClick: (String) -> Unit,
    onMarkCompleted: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val words = subtitle.contentEn.split(Regex("\\s+")).filter { it.isNotEmpty() }
    // Calculate max word length for uniform block width
    val maxWordLength = words.maxOfOrNull { it.replace(Regex("[^\\w']"), "").length } ?: 0

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "句子 ${displayIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!isCompleted) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                "未完成",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Completion toggle
                IconButton(onClick = { onMarkCompleted(!isCompleted) }) {
                    Icon(
                        imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isCompleted) "取消完成" else "标记完成",
                        tint = if (isCompleted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Words row - wrap words into multiple lines
            SpeakingWordsFlowRow(
                words = words,
                maxWordLength = maxWordLength,
                revealedWords = revealedWords,
                onWordClick = onWordReveal,
                onWordLongClick = onWordLongClick,
                modifier = Modifier.fillMaxWidth()
            )

            // Chinese translation
            if (subtitle.contentCn.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = subtitle.contentCn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeakingWordsFlowRow(
    words: List<String>,
    maxWordLength: Int,
    revealedWords: Set<Int>,
    onWordClick: (Int) -> Unit,
    onWordLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Pre-calculate which words fit on each line
    val lines = remember(words, maxWordLength) {
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
                        // Visible word — tap is a no-op, long-press opens
                        // the translation dialog. Padded Box for a more
                        // forgiving long-press hit target.
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = { onWordLongClick(cleanWord) },
                                )
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = cleanWord,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    } else {
                        // Hidden word block — invisible text determines natural size, avoiding descender clipping
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFE0E0E0))
                                .clickable { onWordClick(index) }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cleanWord,
                                style = MaterialTheme.typography.bodyMedium,
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
private fun SpeakingControlBar(
    recordingState: RecordingState,
    hasRecordPermission: Boolean,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onPlayPause: () -> Unit,
    viewModel: PracticeViewModel
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            if (hasRecordPermission) {
                viewModel.startRecording()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        } else {
            if (recordingState == RecordingState.RECORDING) {
                viewModel.stopRecording()
            }
        }
    }

    val isPlayingRecording by viewModel.isPlayingRecording.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()

    // Pill-shaped background frame matching ListeningPage's bottom bar style.
    // Gives the 5-button action bar a clear visual anchor at the bottom of
    // the practice screen without overpowering the recording button.
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
            // Previous sentence
            IconButton(onClick = onSkipPrevious) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(
                        Icons.Default.SkipPrevious, "上一句",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Play current sentence
            IconButton(onClick = onPlayPause) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Icon(
                        if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "播放",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // Recording button (press and hold)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { }
                    )
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (recordingState == RecordingState.RECORDING) Color.Red
                            else MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        if (recordingState == RecordingState.RECORDING) Icons.Default.Stop else Icons.Default.Mic,
                        "录音",
                        modifier = Modifier.size(26.dp),
                        tint = if (recordingState == RecordingState.RECORDING) Color.White
                               else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Play recording
            IconButton(
                onClick = {
                    if (isPlayingRecording) viewModel.stopPlayingRecording()
                    else viewModel.playRecording()
                }
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isPlayingRecording) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        if (isPlayingRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                        if (isPlayingRecording) "停止" else "播放录音",
                        modifier = Modifier.size(26.dp),
                        tint = if (isPlayingRecording) Color.White
                               else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Next sentence
            IconButton(onClick = onSkipNext) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(
                        Icons.Default.SkipNext, "下一句",
                        modifier = Modifier.size(26.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingPlaybackCard(
    isPlaying: Boolean,
    onPlayRecording: () -> Unit,
    onStopPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "我的录音",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    if (isPlaying) "播放中..." else "点击播放",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            IconButton(onClick = if (isPlaying) onStopPlaying else onPlayRecording) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "停止" else "播放",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
