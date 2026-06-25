package com.echoling.app.presentation.ui.screens.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.ExoPlayer
import com.echoling.app.player.subtitle.SubtitleMode
import com.echoling.app.presentation.viewmodel.PracticeViewModel
import com.echoling.app.presentation.viewmodel.WordTranslationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    courseId: String,
    onNavigateBack: () -> Unit,
    viewModel: PracticeViewModel = hiltViewModel()
) {
    val subtitleMode by viewModel.subtitleMode.collectAsState()
    val currentPage by viewModel.currentPage.collectAsState()

    // Initialize on first composition
    LaunchedEffect(courseId) {
        viewModel.initializePlayer()
        viewModel.loadCourse(courseId)
        viewModel.loadSentenceStates(courseId)
    }

    // Reload sentence states when switching to SpeakingPage
    LaunchedEffect(currentPage) {
        if (currentPage == PracticeViewModel.PracticePage.SPEAKING) {
            viewModel.loadSentenceStates(courseId)
        }
    }

    Scaffold(
        // No topBar — back / title / subtitle pill live in a single slim
        // Row right below the status bar (§12.18). This keeps the video
        // player edge-to-edge while still exposing the back action and
        // the subtitle mode toggle right next to the TabRow.
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Compact back-button + title row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "返回",
                    )
                }
                Text(
                    text = "跟读练习",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    // §12.21: left-align the title — the back button
                    // and subtitle-mode pill visually anchor the
                    // row's left/right edges, so the title belongs on
                    // the left rather than floating centered.
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                )
                // Subtitle mode pill
                Surface(
                    onClick = { viewModel.cycleSubtitleMode() },
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(end = 8.dp),
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
            }

            // Tab Row for page switching
            TabRow(
                selectedTabIndex = currentPage.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                PracticeViewModel.PracticePage.entries.forEach { page ->
                    Tab(
                        selected = currentPage == page,
                        onClick = {
                            if (currentPage != page) {
                                viewModel.pause()
                            }
                            viewModel.setCurrentPage(page)
                        },
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when (page) {
                                        PracticeViewModel.PracticePage.LISTENING -> Icons.Default.Headphones
                                        PracticeViewModel.PracticePage.SPEAKING -> Icons.Default.RecordVoiceOver
                                        PracticeViewModel.PracticePage.TESTING -> Icons.Default.Quiz
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = when (page) {
                                        PracticeViewModel.PracticePage.LISTENING -> "泛听"
                                        PracticeViewModel.PracticePage.SPEAKING -> "精听"
                                        PracticeViewModel.PracticePage.TESTING -> "测试"
                                    }
                                )
                            }
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Page content
            Box(modifier = Modifier.weight(1f)) {
                when (currentPage) {
                    PracticeViewModel.PracticePage.LISTENING -> ListeningPage(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    PracticeViewModel.PracticePage.SPEAKING -> SpeakingPage(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                    PracticeViewModel.PracticePage.TESTING -> TestingPage(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Shared Playback Controls Bar (hidden for all pages - each page has its own controls)
            // PlaybackControlsBar is now only used within individual pages if needed
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
            .padding(horizontal = 8.dp, vertical = 4.dp)
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
            modifier = Modifier.fillMaxWidth(),
            enabled = duration > 0
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
            Text(
                text = formatTime(currentPosition),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Loop
                IconButton(
                    onClick = onToggleLoop,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "循环",
                        tint = if (isLooping)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Seek backward
                IconButton(
                    onClick = onSeekBackward,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay10,
                        contentDescription = "后退10秒",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Play/Pause
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(36.dp)
                ) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Seek forward
                IconButton(
                    onClick = onSeekForward,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Forward10,
                        contentDescription = "前进10秒",
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Speed
                Box {
                    TextButton(
                        onClick = { showSpeedMenu = true },
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            style = MaterialTheme.typography.labelSmall,
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

            Text(
                text = formatTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
internal fun VideoPlayerSection(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
internal fun WordSaveDialog(
    initialWord: String,
    translationState: WordTranslationState,
    onDismiss: () -> Unit,
    onSave: (word: String, translation: String, phonetic: String, pos: String) -> Unit,
    onRetranslate: (word: String) -> Unit,
) {
    // Word is editable so the user can correct inflected forms (plurals,
    // past tense, future "will X", etc.) into the dictionary's base
    // form and re-look up. The `remember(initialWord)` key resets the
    // field when a different long-press opens a fresh dialog.
    var currentWord by remember(initialWord) { mutableStateOf(initialWord) }
    // Local editable translation. We seed it from the Loaded result
    // when it arrives, but the user can still tweak it before saving.
    var translation by remember(initialWord) { mutableStateOf("") }
    LaunchedEffect(translationState) {
        if (translationState is WordTranslationState.Loaded) {
            translation = translationState.translation
        }
    }

    val isLoading = translationState is WordTranslationState.Loading
    val errorMessage = (translationState as? WordTranslationState.Failed)?.message
    // Phonetic + POS come from the local-dictionary hit. Practice
    // flow is local-only; phonetic/pos are non-null on `Loaded`.
    val loaded = translationState as? WordTranslationState.Loaded
    val phonetic = loaded?.phonetic.orEmpty()
    val pos = loaded?.pos.orEmpty()
    val canSave = !isLoading &&
        currentWord.isNotBlank() &&
        translation.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "翻译 & 保存",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
            )
        },
        text = {
            Column {
                // Editable word field + "翻译" button. The button
                // re-runs the local dictionary lookup on whatever the
                // user has typed — so they can change "went" → "go",
                // "children" → "child", "will become" → "become", etc.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = currentWord,
                        onValueChange = { currentWord = it },
                        label = { Text("单词") },
                        singleLine = true,
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(
                        onClick = { onRetranslate(currentWord.trim()) },
                        enabled = currentWord.isNotBlank() && !isLoading,
                    ) {
                        Text("翻译")
                    }
                }
                if (phonetic.isNotBlank()) {
                    Text(
                        text = phonetic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = translation,
                    onValueChange = { translation = it },
                    label = { Text(if (pos.isNotBlank()) "$pos 翻译" else "翻译") },
                    placeholder = {
                        Text(
                            if (isLoading) "正在查询本地词典..."
                            else "可编辑后再保存"
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading,
                    trailingIcon = {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                    },
                )
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(currentWord.trim(), translation, phonetic, pos) },
                enabled = canSave,
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