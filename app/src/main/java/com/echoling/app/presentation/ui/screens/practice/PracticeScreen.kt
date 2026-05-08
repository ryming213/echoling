package com.echoling.app.presentation.ui.screens.practice

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.Font
import com.echoling.app.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echoling.app.player.subtitle.Subtitle
import com.echoling.app.player.subtitle.SubtitleMode
import com.echoling.app.presentation.viewmodel.PracticeViewModel
import com.echoling.app.speech.RecordingState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PracticeScreen(
    courseId: String,
    onNavigateBack: () -> Unit,
    audioUri: String? = null,
    subtitleUri: String? = null,
    viewModel: PracticeViewModel = hiltViewModel()
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val subtitleMode by viewModel.subtitleMode.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val currentSubtitleIndex by viewModel.currentSubtitleIndex.collectAsState()
    val recordingPath by viewModel.recordingPath.collectAsState()
    val isPlayingRecording by viewModel.isPlayingRecording.collectAsState()
    var currentSubtitle by remember { mutableStateOf<String?>(null) }
    var showWordDialog by remember { mutableStateOf(false) }
    var selectedWord by remember { mutableStateOf("") }
    var showRecordingUI by remember { mutableStateOf(true) }
    var hasRecordPermission by remember { mutableStateOf(false) }

    // Track revealed words per subtitle index
    var revealedWords by remember { mutableStateOf<Map<Int, Set<Int>>>(emptyMap()) }
    // Track if all are currently revealed
    var allRevealed by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()

    // Auto-scroll to current subtitle when it changes
    LaunchedEffect(currentSubtitleIndex) {
        if (currentSubtitleIndex >= 0 && subtitles.isNotEmpty()) {
            val index = currentSubtitleIndex.coerceIn(0, subtitles.size - 1)
            lazyListState.animateScrollToItem(index, scrollOffset = -80)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasRecordPermission = isGranted
        if (isGranted) {
            viewModel.startRecording()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initializePlayer()
        if (!audioUri.isNullOrBlank()) {
            viewModel.loadMedia(audioUri, subtitleUri, courseId)
        }
    }

    LaunchedEffect(playbackState.currentPositionMs, subtitleMode) {
        currentSubtitle = viewModel.getCurrentSubtitle()
    }

    // Background gradient colors
    val surfaceGradient = listOf(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "跟读练习",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // Subtitle mode pill
                    Surface(
                        onClick = { viewModel.cycleSubtitleMode() },
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = when (subtitleMode) {
                                    SubtitleMode.BILINGUAL -> "双语"
                                    SubtitleMode.ENGLISH -> "英语"
                                    SubtitleMode.CHINESE -> "中文"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = { showRecordingUI = !showRecordingUI }) {
                        Icon(
                            imageVector = Icons.Outlined.RecordVoiceOver,
                            contentDescription = "跟读开关"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Compact Playback Controls
            PlaybackControlsBar(
                isPlaying = playbackState.isPlaying,
                isLooping = playbackState.isLooping,
                playbackSpeed = playbackState.playbackSpeed,
                currentPosition = playbackState.currentPositionMs,
                duration = playbackState.durationMs,
                onPlayPause = {
                    if (playbackState.isPlaying) viewModel.pause() else viewModel.play()
                },
                onSeekBackward = { viewModel.seekBackward() },
                onSeekForward = { viewModel.seekForward() },
                onToggleLoop = { viewModel.toggleLooping() },
                onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                onSeek = { viewModel.seekTo(it) }
            )

            // Recording Section
            if (showRecordingUI) {
                RecordingSection(
                    recordingState = recordingState,
                    recordingPath = recordingPath,
                    isPlayingRecording = isPlayingRecording,
                    onStartRecording = {
                        if (hasRecordPermission) {
                            viewModel.startRecording()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopRecording = { viewModel.stopRecording() },
                    onPlayRecording = { viewModel.playRecording() },
                    onStopPlayingRecording = { viewModel.stopPlayingRecording() }
                )
            }

            // Subtitle List Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "字幕列表",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${subtitles.size}句",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            // Subtitle List
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(subtitles) { index, subtitle ->
                    SubtitleCard(
                        subtitle = subtitle,
                        subtitleMode = subtitleMode,
                        isActive = index == currentSubtitleIndex,
                        isAllRevealed = allRevealed,
                        revealedWords = revealedWords[index] ?: emptySet(),
                        onClick = {
                            viewModel.playSubtitleOnce(subtitle)
                        },
                        onWordReveal = { wordIdx ->
                            if (!allRevealed) {
                                val current = revealedWords[index] ?: emptySet()
                                revealedWords = revealedWords + (index to if (current.contains(wordIdx)) {
                                    current - wordIdx
                                } else {
                                    current + wordIdx
                                })
                            }
                        },
                        onWordLongClick = { word ->
                            selectedWord = word
                            showWordDialog = true
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }

            // Bottom Toggle Button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = if (allRevealed)
                    MaterialTheme.colorScheme.surfaceVariant
                else
                    MaterialTheme.colorScheme.primary
            ) {
                Button(
                    onClick = {
                        allRevealed = !allRevealed
                        if (allRevealed) {
                            revealedWords = emptyMap()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent,
                        contentColor = if (allRevealed)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = if (allRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (allRevealed) "全部隐藏" else "全部显示",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Word Save Dialog
    if (showWordDialog) {
        WordSaveDialog(
            word = selectedWord,
            onDismiss = { showWordDialog = false },
            onSave = { translation ->
                viewModel.saveWord(selectedWord, translation, currentSubtitle ?: "")
                showWordDialog = false
            }
        )
    }
}

@Composable
private fun SubtitleCard(
    subtitle: Subtitle,
    subtitleMode: SubtitleMode,
    isActive: Boolean,
    isAllRevealed: Boolean,
    revealedWords: Set<Int>,
    onClick: () -> Unit,
    onWordReveal: (Int) -> Unit,
    onWordLongClick: (String) -> Unit
) {
    val cardElevation by animateFloatAsState(
        targetValue = if (isActive) 6f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "card_elevation"
    )

    val content = subtitle.getContent(subtitleMode)
    val words = content.split(Regex("\\s+")).filter { it.isNotEmpty() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(cardElevation.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Active indicator bar
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Sentence number badge
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "第${subtitle.index}句",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Words with cover effect
                if (isAllRevealed) {
                    // All words revealed - show as elegant chips
                    WordsRevealedRow(
                        words = words,
                        onWordLongClick = onWordLongClick
                    )
                } else {
                    // Show with cover effect
                    WordsCoveredRow(
                        words = words,
                        revealedWords = revealedWords,
                        onWordReveal = onWordReveal,
                        onWordLongClick = onWordLongClick
                    )
                }
            }
        }
    }
}

@Composable
private fun WordsRevealedRow(
    words: List<String>,
    onWordLongClick: (String) -> Unit
) {
    // Use a simple wrapping approach - build rows that fit content
    WrapWordsRow(
        words = words.map { it.replace(Regex("[^\\w']"), "") }.filter { it.isNotEmpty() },
        isRevealed = true,
        onWordClick = { },
        onWordLongClick = onWordLongClick
    )
}

@Composable
private fun WordsCoveredRow(
    words: List<String>,
    revealedWords: Set<Int>,
    onWordReveal: (Int) -> Unit,
    onWordLongClick: (String) -> Unit
) {
    // Map words to reveal state
    WrapWordsRow(
        words = words.map { it.replace(Regex("[^\\w']"), "") }.filter { it.isNotEmpty() },
        isRevealed = false,
        revealedIndices = revealedWords,
        onWordClick = onWordReveal,
        onWordLongClick = onWordLongClick
    )
}

@Composable
private fun WrapWordsRow(
    words: List<String>,
    isRevealed: Boolean,
    revealedIndices: Set<Int> = emptySet(),
    onWordClick: (Int) -> Unit,
    onWordLongClick: (String) -> Unit
) {
    // Pre-calculate which words are revealed
    val wordStates = words.mapIndexed { index, word ->
        val cleanWord = word.replace(Regex("[^\\w']"), "")
        if (cleanWord.isEmpty()) null else {
            val revealed = isRevealed || revealedIndices.contains(index)
            Triple(cleanWord, revealed, index)
        }
    }.filterNotNull()

    // Use BoxWithConstraints to get available width
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        val maxWidth = maxWidth.value.toInt()

        // Build rows of word states (as data, not UI)
        val rows = mutableListOf<List<Triple<String, Boolean, Int>>>()
        var currentRow = mutableListOf<Triple<String, Boolean, Int>>()
        var currentRowWidth = 0
        val spacing = 2 // dp between words
        val availableWidth = maxWidth - 8 // account for row padding

        wordStates.forEach { (word, revealed, idx) ->
            // Estimate width more conservatively
            val estimatedWidth = word.length * 7 + 20

            // If single word is wider than available, it goes on its own line
            if (estimatedWidth > availableWidth) {
                if (currentRow.isNotEmpty()) {
                    rows.add(currentRow.toList())
                    currentRow.clear()
                    currentRowWidth = 0
                }
                rows.add(listOf(Triple(word, revealed, idx)))
            } else if (currentRowWidth + estimatedWidth > availableWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow.toList())
                currentRow.clear()
                currentRowWidth = 0
                currentRow.add(Triple(word, revealed, idx))
                currentRowWidth = estimatedWidth + spacing
            } else {
                currentRow.add(Triple(word, revealed, idx))
                currentRowWidth += estimatedWidth + spacing
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow.toList())
        }

        // Now render the rows as UI
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            rows.forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { (word, revealed, idx) ->
                        WordChipItem(
                            word = word,
                            isRevealed = revealed,
                            onClick = { onWordClick(idx) },
                            onLongClick = { onWordLongClick(word) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordChipItem(
    word: String,
    isRevealed: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val wordStyle = TextStyle(
        fontFamily = FontFamily(Font(R.font.aptos, FontWeight.Medium)),
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )

    if (isRevealed) {
        // No background when revealed
        Text(
            text = word,
            style = wordStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = 2.dp, vertical = 2.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        )
    } else {
        // Gray background box when hidden, same size as word
        Surface(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            shape = RoundedCornerShape(2.dp),
            color = Color(0xFFE0E0E0)
        ) {
            Text(
                text = word,
                style = wordStyle,
                color = Color.Transparent,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun PlaybackControlsBar(
    isPlaying: Boolean,
    isLooping: Boolean,
    playbackSpeed: Float,
    currentPosition: Long,
    duration: Long,
    onPlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleLoop: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSeek: (Long) -> Unit
) {
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var showSpeedMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Progress bar
        var sliderPosition by remember(currentPosition) { mutableFloatStateOf(currentPosition.toFloat()) }
        var isDragging by remember { mutableStateOf(false) }

        Slider(
            value = if (duration > 0) sliderPosition else 0f,
            onValueChange = { newValue ->
                isDragging = true
                sliderPosition = newValue
            },
            onValueChangeFinished = {
                isDragging = false
                onSeek(sliderPosition.toLong())
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        )

        LaunchedEffect(currentPosition) {
            if (!isDragging) {
                sliderPosition = currentPosition.toFloat()
            }
        }

        // Time and controls row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Loop
                IconButton(
                    onClick = onToggleLoop,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "循环",
                        tint = if (isLooping)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Seek backward
                IconButton(
                    onClick = onSeekBackward,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "后退10秒",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Play/Pause
                FilledIconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }

                // Seek forward
                IconButton(
                    onClick = onSeekForward,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "前进10秒",
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Speed
                Box {
                    TextButton(
                        onClick = { showSpeedMenu = true },
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        speedOptions.forEach { speed ->
                            DropdownMenuItem(
                                text = { Text("${speed}x") },
                                onClick = {
                                    onSpeedChange(speed)
                                    showSpeedMenu = false
                                },
                                leadingIcon = if (speed == playbackSpeed) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Duration
            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecordingSection(
    recordingState: RecordingState,
    recordingPath: String?,
    isPlayingRecording: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayRecording: () -> Unit,
    onStopPlayingRecording: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Record button
            val buttonScale by animateFloatAsState(
                targetValue = if (recordingState == RecordingState.RECORDING) 1.1f else 1f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "record_btn_scale"
            )

            IconButton(
                onClick = {
                    if (recordingState == RecordingState.RECORDING) {
                        onStopRecording()
                    } else {
                        onStartRecording()
                    }
                },
                modifier = Modifier
                    .size(56.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (recordingState == RecordingState.RECORDING)
                        Color.Red
                    else
                        MaterialTheme.colorScheme.primary
                ) {
                    Box(
                        modifier = Modifier.size(56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (recordingState == RecordingState.RECORDING)
                                Icons.Default.Stop
                            else
                                Icons.Default.Mic,
                            contentDescription = "录音",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Status text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (recordingState) {
                        RecordingState.IDLE -> "跟读练习"
                        RecordingState.RECORDING -> "录音中..."
                        RecordingState.STOPPED -> "录音已保存"
                        else -> ""
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = if (recordingState == RecordingState.RECORDING)
                        Color.Red
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                if (recordingState == RecordingState.IDLE) {
                    Text(
                        text = "点击录制你的声音",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Play recording button
            if (recordingPath != null && recordingState != RecordingState.RECORDING) {
                IconButton(
                    onClick = if (isPlayingRecording) onStopPlayingRecording else onPlayRecording,
                    modifier = Modifier.size(48.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (isPlayingRecording)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = if (isPlayingRecording) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlayingRecording) "停止" else "播放录音",
                            modifier = Modifier.size(24.dp),
                            tint = if (isPlayingRecording)
                                Color.White
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WordSaveDialog(
    word: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var translation by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "保存单词",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = word,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                OutlinedTextField(
                    value = translation,
                    onValueChange = { translation = it },
                    label = { Text("翻译") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(translation) },
                enabled = translation.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
