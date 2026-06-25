package com.echoling.app.presentation.ui.screens.practice

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoling.app.player.subtitle.Subtitle
import com.echoling.app.player.subtitle.SubtitleMode
import com.echoling.app.presentation.viewmodel.PracticeViewModel

@Composable
fun ListeningPage(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val currentSubtitleIndex by viewModel.currentSubtitleIndex.collectAsState()
    val subtitleMode by viewModel.subtitleMode.collectAsState()
    val isVideoMode by viewModel.isVideoMode.collectAsState()
    val videoPlayerState by viewModel.videoPlayerState.collectAsState()

    var showSubtitles by remember { mutableStateOf(false) }
    // Track revealed words per subtitle index: Map<subtitleIndex, Set<wordIndex>>
    var revealedWordsMap by remember { mutableStateOf<Map<Int, Set<Int>>>(emptyMap()) }
    // Long-press translation dialog state. `null` = dialog closed.
    var wordToTranslate by remember { mutableStateOf<String?>(null) }
    val translationState by viewModel.wordTranslation.collectAsState()
    val lazyListState = rememberLazyListState()

    // Auto-scroll to current subtitle
    LaunchedEffect(currentSubtitleIndex) {
        if (currentSubtitleIndex >= 0 && currentSubtitleIndex < subtitles.size) {
            lazyListState.animateScrollToItem(currentSubtitleIndex)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Video Player (if available)
        if (isVideoMode && videoPlayerState != null) {
            VideoPlayerSection(
                exoPlayer = videoPlayerState!!,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        }

        // Progress bar with play button on the left
        ListeningProgressBar(
            currentPosition = playbackState.currentPositionMs,
            duration = playbackState.durationMs,
            isPlaying = playbackState.isPlaying,
            onPlayPause = {
                if (playbackState.isPlaying) viewModel.pause() else viewModel.play()
            },
            onSeek = { viewModel.seekTo(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Subtitle list
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(subtitles) { index, subtitle ->
                val revealedWords = revealedWordsMap[index] ?: emptySet()
                ListeningSubtitleListItem(
                    subtitle = subtitle,
                    subtitleMode = subtitleMode,
                    isActive = index == currentSubtitleIndex,
                    onClick = { viewModel.playSubtitleOnce(subtitle) },
                    listIndex = index + 1,
                    showSubtitles = showSubtitles,
                    revealedWords = revealedWords,
                    onWordLongPress = { wordIndex ->
                        // Reveal this specific word (only used in hide-mode)
                        revealedWordsMap = revealedWordsMap.toMutableMap().apply {
                            val currentSet = this[index] ?: emptySet()
                            this[index] = currentSet + wordIndex
                        }
                    },
                    onWordTranslate = { word ->
                        wordToTranslate = word
                        viewModel.requestWordTranslation(word)
                    },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // Bottom control buttons: subtitle toggle, play/pause, speed
        ListeningBottomControls(
            showSubtitles = showSubtitles,
            playbackSpeed = playbackState.playbackSpeed,
            isPlaying = playbackState.isPlaying,
            onToggleSubtitles = { showSubtitles = !showSubtitles },
            onPlayPause = {
                if (playbackState.isPlaying) viewModel.pause() else viewModel.play()
            },
            onSpeedChange = { viewModel.setPlaybackSpeed(it) }
        )
    }

    // Long-press → translate dialog
    val pendingWord = wordToTranslate
    if (pendingWord != null) {
        WordSaveDialog(
            initialWord = pendingWord,
            translationState = translationState,
            onDismiss = {
                wordToTranslate = null
                viewModel.clearWordTranslation()
            },
            onRetranslate = { newWord ->
                // Re-runs the local dictionary lookup on the
                // (possibly user-edited) word. The dialog will update
                // with the new translation when state emits Loaded.
                viewModel.requestWordTranslation(newWord)
            },
            onSave = { word, translation, phonetic, pos ->
                viewModel.saveWord(word, translation, "", phonetic, pos)
                wordToTranslate = null
                viewModel.clearWordTranslation()
            },
        )
    }
}

@Composable
private fun ListeningProgressBar(
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember(currentPosition) { mutableFloatStateOf(currentPosition.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Play/Pause button on the left
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(40.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // Slider progress bar
        Column(modifier = Modifier.weight(1f)) {
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
        }
    }
}

@Composable
private fun ListeningBottomControls(
    showSubtitles: Boolean,
    playbackSpeed: Float,
    isPlaying: Boolean,
    onToggleSubtitles: () -> Unit,
    onPlayPause: () -> Unit,
    onSpeedChange: (Float) -> Unit
) {
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var showSpeedMenu by remember { mutableStateOf(false) }

    // Pill-shaped background frame that visually separates the action bar
    // from the subtitle list above it. Slight tonal elevation + surfaceVariant
    // tint give it depth without competing with the colored circle buttons.
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
            // Subtitle toggle
            IconButton(onClick = onToggleSubtitles) {
                Surface(
                    shape = CircleShape,
                    color = if (showSubtitles)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        if (showSubtitles) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        "字幕",
                        modifier = Modifier.size(24.dp),
                        tint = if (showSubtitles)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Play/Pause
            IconButton(onClick = onPlayPause) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
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
                        style = MaterialTheme.typography.labelMedium
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListeningSubtitleListItem(
    subtitle: Subtitle,
    subtitleMode: SubtitleMode,
    isActive: Boolean,
    onClick: () -> Unit,
    listIndex: Int,
    showSubtitles: Boolean,
    revealedWords: Set<Int>,
    onWordLongPress: (Int) -> Unit,
    onWordTranslate: (String) -> Unit,
) {
    val words = subtitle.contentEn.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val cnText = subtitle.contentCn

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // Use pointerInput + detectTapGestures (NOT Modifier.clickable)
            // so the parent only handles taps and never long-press.
            // This is what lets the child words' combinedClickable
            // long-press handlers fire in the showSubtitles branch.
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            },
        shape = RoundedCornerShape(8.dp),
        color = if (isActive)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else
            MaterialTheme.colorScheme.surface,
        tonalElevation = if (isActive) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Active indicator
            if (isActive) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(32.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(2.dp)
                        )
                )
                Spacer(Modifier.width(8.dp))
            }

            Text(
                text = "$listIndex.",
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.width(8.dp))

            if (showSubtitles) {
                // Visible text — long-press any word to translate it.
                // Match the same bodyMedium style and font size as the
                // hidden-mode blocks so the layout doesn't jump when
                // toggling the subtitle visibility.
                ListeningVisibleSubtitleText(
                    subtitle = subtitle,
                    subtitleMode = subtitleMode,
                    onWordLongPress = onWordTranslate,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Show hidden words as covered blocks with long-press to reveal
                Column(modifier = Modifier.weight(1f)) {
                    // English words with wrapping
                    ListeningHiddenWordsFlowRow(
                        words = words,
                        revealedWords = revealedWords,
                        onWordLongPress = onWordLongPress,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Chinese translation (always visible when subtitles hidden)
                    if (cnText.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = cnText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders the subtitle in [SubtitleMode.BILINGUAL] / [SubtitleMode.ENGLISH]
 * / [SubtitleMode.CHINESE] as inline Text composables that wrap
 * naturally. English words are long-pressable to translate; Chinese
 * text and punctuation are plain. Whitespace between tokens is preserved
 * via explicit [Text](" ").
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ListeningVisibleSubtitleText(
    subtitle: Subtitle,
    subtitleMode: SubtitleMode,
    onWordLongPress: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enText = subtitle.contentEn
    // Tokenize into "word" units: each word is a run of [A-Za-z']
    // characters, possibly with leading/trailing punctuation attached
    // (so "Hello," stays one visual unit). Whitespace between words
    // becomes its own token. Example: "Hello, world." →
    // ["Hello,", " ", "world", ".", " "].
    val tokens = remember(enText) {
        enText.split(Regex("(?<=\\s)|(?=\\s)"))
            .filter { it.isNotEmpty() }
    }

    val showEn = subtitleMode != SubtitleMode.CHINESE && enText.isNotEmpty()
    val showCn = subtitleMode != SubtitleMode.ENGLISH && subtitle.contentCn.isNotEmpty()
    val cnText = subtitle.contentCn

    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurface

    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.Top,
    ) {
        if (showEn) {
            for (token in tokens) {
                if (token.isBlank()) {
                    // Preserve the original whitespace (could be multi-space).
                    Text(text = token, style = style, color = color)
                } else {
                    val clean = token.replace(Regex("[^\\w']"), "")
                    if (clean.isEmpty()) {
                        // Punctuation only — no word to translate.
                        Text(text = token, style = style, color = color)
                    } else {
                        // Wrap in a Box with a tiny bit of horizontal
                        // padding so the long-press hit-target is more
                        // forgiving than the bare glyph bounds.
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = { onWordLongPress(clean) },
                                )
                                .padding(horizontal = 2.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = token, style = style, color = color)
                        }
                    }
                }
            }
        }
        if (showEn && showCn) {
            Text(text = "  |  ", style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showCn) {
            Text(
                text = cnText,
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListeningHiddenWordsFlowRow(
    words: List<String>,
    revealedWords: Set<Int>,
    onWordLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Pre-calculate which words fit on each line
    val lines = remember(words) {
        val result = mutableListOf<List<Pair<Int, String>>>()
        var currentLine = mutableListOf<Pair<Int, String>>()
        var currentLineCharCount = 0
        val maxCharsPerLine = 25

        words.forEachIndexed { index, word ->
            val cleanWord = word.replace(Regex("[^\\w']"), "")
            if (cleanWord.isNotEmpty()) {
                if (currentLineCharCount + cleanWord.length > maxCharsPerLine && currentLine.isNotEmpty()) {
                    result.add(currentLine.toList())
                    currentLine = mutableListOf()
                    currentLineCharCount = 0
                }
                currentLine.add(index to cleanWord)
                currentLineCharCount += cleanWord.length + 1
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

                    HiddenWordBlock(
                        word = cleanWord,
                        isRevealed = isRevealed,
                        onLongPress = { onWordLongPress(index) }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HiddenWordBlock(
    word: String,
    isRevealed: Boolean,
    onLongPress: () -> Unit
) {
    if (isRevealed) {
        Text(
            text = word,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    } else {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE0E0E0))
                .combinedClickable(
                    onClick = { },
                    onLongClick = onLongPress
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Transparent
            )
        }
    }
}
