package com.echoling.app.presentation.ui.screens.practice

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoling.app.player.subtitle.Subtitle
import com.echoling.app.presentation.viewmodel.PracticeViewModel

@Composable
fun ListeningPage(
    viewModel: PracticeViewModel,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsState()
    val subtitles by viewModel.subtitles.collectAsState()
    val currentSubtitleIndex by viewModel.currentSubtitleIndex.collectAsState()
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

@Composable
private fun ListeningSubtitleListItem(
    subtitle: Subtitle,
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

    // (2026-07-18) §16.X fix9: rememberUpdatedState 让 playback tick
    // 期间每次重组件的 onClick 新闭包不再让 gesture detector 重启.
    // 详见 ListeningHiddenWordsFlowRow 的同号注释.
    val currentOnClick by rememberUpdatedState(onClick)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // §12.34: use pointerInput + detectTapGestures (NOT
            // Modifier.clickable) so the parent only handles taps and
            // never long-press. This is what lets the child words'
            // combinedClickable long-press handlers fire in the
            // showSubtitles branch. The tap handler is what fires for
            // taps that land on whitespace / Chinese / the index
            // number / the active indicator — taps on word Boxes are
            // handled by the words themselves (see below).
            // §16.X fix9: keys = Unit (永远稳定), callback 走
            // currentOnClick (永远拿到最新闭包但不重启 detector).
            .pointerInput(Unit) {
                detectTapGestures(onTap = { currentOnClick() })
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
                // Visible text — long-press any word to translate it,
                // tap any word to play this sentence.
                // §12.35: always renders ENGLISH only (no Chinese /
                // bilingual branches — the page only displays English).
                ListeningVisibleSubtitleText(
                    subtitle = subtitle,
                    onWordLongPress = onWordTranslate,
                    onSentenceClick = onClick,
                    modifier = Modifier.weight(1f),
                )
            } else {
                // Show hidden words as covered blocks with long-press to reveal
                Column(modifier = Modifier.weight(1f)) {
                    // English words with wrapping — tap a block to play
                    // this sentence, long-press to reveal the word.
                    ListeningHiddenWordsFlowRow(
                        words = words,
                        revealedWords = revealedWords,
                        onWordLongPress = onWordLongPress,
                        onSentenceClick = onClick,
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
 * Renders the subtitle as inline Text composables that wrap
 * naturally. §12.35: the practice page only displays English now
 * (the subtitle-mode pill is gone), so this function is ENGLISH-only
 * — no bilingual / Chinese branches.
 *
 * English words are tappable (play this sentence) and long-pressable
 * (translate it). Whitespace between tokens is preserved via explicit
 * [Text](" ").
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ListeningVisibleSubtitleText(
    subtitle: Subtitle,
    onWordLongPress: (String) -> Unit,
    onSentenceClick: () -> Unit,
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

    val showEn = enText.isNotEmpty()

    val style = MaterialTheme.typography.bodyMedium
    val color = MaterialTheme.colorScheme.onSurface

    // (2026-07-18) §16.X fix9: rememberUpdatedState 让 playback tick
    // 期间每次重组件的 onSentenceClick / onWordLongPress 新闭包不再让
    // gesture detector 重启. keys 仅留 stable 的 clean (per-token String).
    // 详见 ListeningHiddenWordsFlowRow 的同号注释.
    val currentOnSentenceClick by rememberUpdatedState(onSentenceClick)
    val currentOnWordLongPress by rememberUpdatedState(onWordLongPress)

    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        // (2026-07-18) §16.X fix5: 显示态 FlowRow 加 2dp 行间隙, 与
        // 覆盖态对齐.
        //
        // 用户 2026-07-18 (第二轮): "我的要求是: 句子在覆盖和显示的时候,
        // 句子的长度和上下之间的间隔都要保持一致".
        //
        // 当前状态:
        //  chip per-row 几何 — 隐藏与显示对齐 (fix3: vertical 2dp → 0dp,
        //    horizontal=2dp, 同)
        //  chip 之间的横向间隔 — 隐藏与显示对齐 (FlowRow + Text(" ")
        //    自然间距, 同)
        //  行间净间隙 — 隐藏态 spacedBy(2.dp), 显示态 Arrangement.Top
        //    (0dp), 这一项不一致.
        //
        // 显示态加 spacedBy(2.dp) 后, 多行句子的两态行节距完全一致
        // = text_height + 2dp × (n-1) dp.
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
                        // §12.34: each word is a tap+long-press
                        // Box. Tap plays the whole sentence (the
                        // parent's onClick); long-press translates
                        // this word. Using pointerInput +
                        // detectTapGestures (NOT combinedClickable)
                        // so the press does NOT show the M3 ripple —
                        // words are inline text, not buttons, and the
                        // ripple on a press in the middle of a flowing
                        // sentence reads as visual noise. The hit
                        // target is unchanged; only the visual
                        // feedback is suppressed. Whitespace /
                        // Chinese / index / active indicator taps
                        // still fall through to the parent Surface's
                        // detectTapGestures handler and play the
                        // sentence the same way.
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                // §16.X fix9: keys = Unit (永远稳定),
                                // 回调走 currentOnSentenceClick /
                                // currentOnWordLongPress (永远拿到最新闭
                                // 包但不重启 detector).
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { currentOnSentenceClick() },
                                        onLongPress = { currentOnWordLongPress(clean) },
                                    )
                                },
                            // (2026-07-18) §16.X fix8: 去掉 padding(horizontal=2.dp).
                            //
                            // 上一步 (§16.X fix7) 把覆盖态改成"灰块宽度 = 该词
                            // 自然宽", 显示态却仍保留 padding(2dp) → 每词多
                            // 4dp → 10 词句长 40dp 偏移, 用户报"句子长度还会变".
                            //
                            // 拿掉 padding 后, Box wrap-to-content (Text 自
                            // 然宽), 与覆盖态灰块尺寸一一对应.
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = token, style = style, color = color)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ListeningHiddenWordsFlowRow(
    words: List<String>,
    revealedWords: Set<Int>,
    onWordLongPress: (Int) -> Unit,
    onSentenceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // (2026-07-18) §16.X fix7: 根本性重构 — 隐藏态改成"灰块等于
    // 词的天然宽度",不再用"透明文字包进灰底 box" 这条路。
    //
    // 旧方案 (`Box(透明文字, padding 2dp) + 灰底`):
    //  chip Box 宽度 = 文字宽度 + 4dp (horizontal padding ×2)
    //  句子总宽度 = Σ (词宽 + 4dp) + Σ 空格 Text ≈ "词自然宽"
    //    × 1.05 倍, 比显示态纯文字稍宽. 累加 5+ 词后视觉
    //    偏移明显, 用户报"单词之间的间隔太大了" (本节反馈).
    //
    // 新方案 (`Box(灰底, 尺寸=文字自然宽 × 行高)`):
    //  chip Box 宽度 = 该词 Text 在 measurer 上量出的精确宽度,
    //    高度 = 全行最长高度 (各词 max ascender+descender).
    //  灰块精确等于"假如那个词是文字时会占的空间". 没 padding,
    //    没 chip 撑开。
    //  行内空白仍是 Text(" ") bodyMedium, 与显示态共用同一布局。
    //
    // 用户的核心提议 (本节):
    //  "单词隐藏时时没有单词的, 只出现了灰色的块,
    //   根据单词长短的不一样, 这个灰色的块也是长短不一样"
    //
    // 实现方式 — TextMeasurer + 缓存: 首次渲染时用 remember 量
    // 出每个词的 (px.width, px.height), 缓存到 wordSizes.
    // 切换 reveal/hide 不重算 (style + words 未变). density 变化
    // (旋转屏幕) 让 remember 自动 invalidate.
    val style = MaterialTheme.typography.bodyMedium
    val revealedColor = MaterialTheme.colorScheme.onSurface
    val grayBlock = Color(0xFFE0E0E0)

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // 量每个词的自然像素宽 + 自然像素高. height 取整行 max 以容纳
    // descender. cached on words + style (cache hit on every reveal
    // toggle).
    val (wordWidthsDp, rowHeightDp) = remember(words, style) {
        val sizes = words.map { word -> measurer.measure(word, style).size }
        val widthsDp = sizes.map { with(density) { it.width.toDp() } }
        val heightDp = with(density) {
            sizes.maxOf { it.height }.toDp()
        }
        widthsDp to heightDp
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // (2026-07-18) §16.X fix9: rememberUpdatedState 让 playback
        // tick (每 ~50ms) 期间每次重组件的 onSentenceClick /
        // onWordLongPress 新闭包不再让 gesture detector 重启.
        //
        // 现象: 长按 500ms 需要到达, 但每次 emit playbackState 都触发
        // ListeningSubtitleListItem 重组, itemsIndexed 的 lambda 创建
        // 新 `() -> viewModel.playSubtitleOnce(subtitle)` 闭包, 旧
        // pointerInput(index, onSentenceClick) 因 key 变化重启 gesture
        // detector — detectTapGestures 永远等不到 500ms 触发 onLongPress.
        // (clickable / onClick 在毫秒级完成, 一般能抢在下一次 tick 前完事,
        // 所以单击仍工作.)
        //
        // 修法: keys 仅保留 stable 的 index (per-word, in forEachIndexed
        // 永远稳定), 回调走 State-backed delegate, 永远拿到最新闭包但
        // 不再因闭包变化重启 detector. 播放中长按 500ms 即可触发.
        val currentOnSentenceClickHidden by rememberUpdatedState(onSentenceClick)
        val currentOnWordLongPressHidden by rememberUpdatedState(onWordLongPress)

        words.forEachIndexed { index, word ->
            if (index > 0) {
                // 词间空格, 仅作空格 Token — 与 [ListeningVisibleSubtitleText]
                // 完全一致。
                Text(" ", style = style, color = revealedColor)
            }
            val isRevealed = revealedWords.contains(index)
            if (isRevealed) {
                // 显示态: 纯文字, 与显示路径共用 Text 自然尺寸.
                // 父 [Surface] 的 detectTapGestures 兜底 onTap (播句),
                // 这里再叠一个 pointerInput 拿 long-press (翻译).
                // §16.X fix9: keys = index (stable per-word).
                Box(
                    modifier = Modifier
                        .pointerInput(index) {
                            detectTapGestures(
                                onTap = { currentOnSentenceClickHidden() },
                                onLongPress = { currentOnWordLongPressHidden(index) },
                            )
                        },
                ) {
                    Text(text = word, style = style, color = revealedColor)
                }
            } else {
                // 隐藏态: 灰块, Box 尺寸 = 该词自然宽 × 行高. 灰底
                // 直接 size, 无 padding. tap/long-press 行为同 revealed,
                // 只是 Box 自身成为命中目标.
                // §16.X fix9: keys = index (stable per-word).
                Box(
                    modifier = Modifier
                        .size(width = wordWidthsDp[index], height = rowHeightDp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(grayBlock)
                        .pointerInput(index) {
                            detectTapGestures(
                                onTap = { currentOnSentenceClickHidden() },
                                onLongPress = { currentOnWordLongPressHidden(index) },
                            )
                        },
                )
            }
        }
    }
}

@Composable
private fun HiddenWordBlock(
    word: String,
    isRevealed: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    // (2026-07-18) 间距对齐显示路径:padding 6dp → 2dp。
    //
    // 整句覆盖路径 (this fn) 的 chip-to-chip 间距原本是
    //   Box(6dp) + Spacer(4dp) + Box(6dp) = 16dp
    // 显示路径 [ListeningVisibleSubtitleText] 的 chip-to-chip 间距是
    //   Box(2dp) + Text(" ")自然宽度(~3.6dp) + Box(2dp) = ~7.6dp
    // 两条路径差 8.4dp, 用户切到整句覆盖时整行单词瞬间向右撑开 8dp。
    // 改为 2dp × 2dp + 4dp Spacer = 8dp, 与显示路径 ~7.6dp 肉眼无差。
    // 两个分支(revealed / hidden)保持相同 padding, 保证用户长按揭示
    // 单个单词时该 chip 不会跳宽。
    if (isRevealed) {
        // §12.34: even when revealed, tap on a word plays the
        // sentence. §12.34a: pointerInput + detectTapGestures
        // (NOT combinedClickable) so the press does NOT show the
        // M3 ripple on the word block.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .pointerInput(onClick, onLongPress) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongPress() },
                    )
                }
                .padding(horizontal = 2.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = word,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    } else {
        // §12.34a: pointerInput + detectTapGestures — no M3 ripple
        // when the user taps a hidden word block.
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE0E0E0))
                .pointerInput(onClick, onLongPress) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongPress() },
                    )
                }
                .padding(horizontal = 2.dp, vertical = 2.dp),
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
