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
import com.echoling.app.presentation.ui.navigation.SUB_PAGE_NAV_ANIM_MS
import com.echoling.app.presentation.viewmodel.PracticeViewModel
import com.echoling.app.presentation.viewmodel.WordTranslationState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    courseId: String,
    onNavigateBack: () -> Unit,
    viewModel: PracticeViewModel = hiltViewModel()
) {
    val currentPage by viewModel.currentPage.collectAsState()

    // Initialize on first composition
    LaunchedEffect(courseId) {
        // (2026-07-04) Defer ExoPlayer init + course load past the
        // slide-in animation window. The user's reported "轻微卡顿"
        // jumping from the ContinueLearningCard to Practice was a
        // race between the slide animation's per-frame work and the
        // ExoPlayer.Builder().build() / setMediaItem / prepare
        // pipeline — both run on Main and both want the same
        // choreographer slot during the 350ms slide. The same code
        // path is reachable via CourseDetail → Practice, but the user
        // doesn't perceive the jank there because the tap-to-row
        // motion masks the race; the direct-jump path has nothing to
        // hide it. Letting the slide complete first means the user
        // sees the page "settle in" cleanly, then content populates a
        // frame or two later — visually equivalent to a slow network
        // page, much smoother than a janky slide.
        //
        // The PracticeScreen renders fine in its initial state for
        // ~350ms: black-player-area placeholder + empty subtitle list
        // + ListeningPage's empty controls. None of those are bound
        // to the ExoPlayer instance before [loadVideo] runs, so the
        // delay is safe.
        //
        // Sharing SUB_PAGE_NAV_ANIM_MS with [SubPageNavGraph]'s
        // tween() spec means changing the slide duration is a
        // single-site edit; if the slide ever shrinks below ~250ms
        // this delay should be reconsidered.
        delay(SUB_PAGE_NAV_ANIM_MS.toLong())
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
                    // (2026-07-10) §12.40: 48dp → 40dp. The
                    // previous value matched the M3 "default" Row
                    // height, but combined with the TabRow below it
                    // (~48dp), the user's perception was that the
                    // three sub-pages (泛听 / 精听 / 测试) sat "too
                    // far" from the 跟读练习 title. 40dp keeps the
                    // back button tappable (M3 min target is 48dp
                    // for a touch target, but the IconButton's
                    // internal padding already eats that — the Row
                    // itself only needs to fit the title without
                    // clipping the descender).
                    .height(40.dp)
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
                    // visually anchors the row's left edge, so the
                    // title belongs on the left rather than floating
                    // centered. §12.35: the subtitle-mode pill that
                    // used to live on the right edge is gone (the
                    // page only shows English), so the back button is
                    // now the sole horizontal anchor.
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                )
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
                        // (2026-07-10) §12.40: default M3 Tab height
                        // with a leading icon is ~72dp; with just
                        // text it's 48dp. Cap to 40dp so the label
                        // row ("泛听 / 精听 / 测试") becomes a thin
                        // strip, matching the user's "label box
                        // height too big → shrink it" feedback.
                        // heightIn (not height) so the indicator +
                        // padding still render at their natural
                        // minimum if 40dp is too aggressive on
                        // some devices.
                        modifier = Modifier.heightIn(min = 40.dp, max = 40.dp),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                // Tighten the vertical padding so the
                                // icon + text sit centered with the
                                // Row's intrinsic 40dp rather than
                                // spilling into the indicator area
                                // above/below.
                                modifier = Modifier.padding(vertical = 0.dp)
                            ) {
                                Icon(
                                    imageVector = when (page) {
                                        PracticeViewModel.PracticePage.LISTENING -> Icons.Default.Headphones
                                        PracticeViewModel.PracticePage.SPEAKING -> Icons.Default.RecordVoiceOver
                                        PracticeViewModel.PracticePage.TESTING -> Icons.Default.Quiz
                                    },
                                    contentDescription = null,
                                    // (2026-07-10) §12.40: 18dp →
                                    // 16dp. Lighter icon proportional
                                    // to the now-smaller label font
                                    // (13sp). 16dp is still >= M3
                                    // 24dp-touch-target via IconButton
                                    // padding; inside a Tab it's
                                    // purely decorative.
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = when (page) {
                                        PracticeViewModel.PracticePage.LISTENING -> "泛听"
                                        PracticeViewModel.PracticePage.SPEAKING -> "精听"
                                        PracticeViewModel.PracticePage.TESTING -> "测试"
                                    },
                                    // (2026-07-10) §12.40: explicit
                                    // 13sp. M3 `Tab`'s default text
                                    // slot uses `titleSmall` (14sp).
                                    // 13sp is "one tier down" per the
                                    // user spec — closer to M3's
                                    // `labelMedium` (12sp) than the
                                    // default `titleSmall`, while
                                    // still large enough for the 2-char
                                    // CN labels to read at a glance.
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontSize = 13.sp
                                    )
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
        // (2026-07-04) Background switched from default M3
        // `surface` (#FFFBFE — near white) to a clearly-visible
        // light gray (#E0E0E0) per the user's "浅灰色" request.
        //
        // Earlier attempt used `surfaceVariant` (#E7E0EC) but the
        // user reported "跟改之前没有差别" — the two values are
        // only ~6 lightness steps apart and visually
        // indistinguishable. Tried `surfaceContainerHigh` next but
        // that's a M3 1.2.0 token; this project pins material3 to
        // 1.1.2 (verified via `./gradlew :app:dependencies`), so
        // it doesn't exist in the classpath.
        //
        // Hardcoded #E0E0E0 is the simplest fix: clearly readable
        // as "light gray", and the rest of the dialog (violet-400
        // confirm button, dark text on the fields) keeps enough
        // brand purple that it doesn't feel like a foreign screen.
        // Trade-off: dark mode would still render #E0E0E0 (a
        // glaringly bright dialog on a near-black page); the app
        // doesn't appear to ship a dark mode today, so we
        // accept that and revisit if/when dark mode lands.
        containerColor = Color(0xFFE0E0E0),
        title = {
            Text(
                text = "翻译 & 保存",
                // (2026-07-04) 22sp (M3 titleLarge default) → 20sp
                // (earlier this turn) → 18sp now. The user said the
                // title still reads too heavy for the dialog's
                // light-gray surface; 18sp sits between M3
                // titleLarge and titleMedium and feels closer to a
                // dialog-label than a screen-heading.
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp)
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
                shape = RoundedCornerShape(12.dp),
                // §12.32d: "保存" button uses the brand primary
                // #7C3AED (violet-600) for unambiguous purple
                // identification. White text keeps contrast on the
                // darker background. Previously used violet-400
                // (#A78BFA, §12.32c) — too light, user could not
                // recognize as "purple" — reverted to brand primary.
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF7C3AED),
                    contentColor = Color.White,
                ),
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