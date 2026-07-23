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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.echoling.app.player.subtitle.Subtitle
import com.echoling.app.presentation.ui.screens.practice.components.RedRecordCircle
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
    // (2026-06-28) Per-page recording path. Collects only the
    // speaking-page path; test-page recordings are invisible to
    // this page so the "我的录音" playback card won't show a
    // test-page recording here.
    val recordingPath by viewModel.speakingRecordingPath.collectAsState()
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
                // (2026-07-04) Bottom padding dropped from 8dp → 0dp
                // so the [SpeakingSubtitleCard] below sits flush
                // against the OutlinedButton. Top padding kept at
                // 8dp to keep the dropdown away from the video
                // player frame above. Combined with the card's
                // own internal 16dp padding, the first row
                // ("句子 N" + completion check) now sits 16dp
                // below the button's bottom edge (was 24dp).
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 0.dp)
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
                // (2026-07-04) Removed the `widthIn(max = 300.dp)`
                // cap that fit the previous 5-column grid. With
                // 8 × 36dp cells + 7 × 4dp gaps = 316dp the row
                // already fits inside the full-width anchor (≈
                // screen-width minus 32dp Box padding) on any
                // modern phone. Forcing 300dp here would clip the
                // rightmost column on phones narrower than 350dp.
            ) {
                // (2026-07-04) Layout reshaped per user request:
                //   - columns 5 → 8 (more sentence numbers per row)
                //   - cell size 48dp → 36dp (so 8 cells × 36dp +
                //     7 × 4dp gaps = 316dp fits within the menu)
                //   - completed style: previously "faded lavender
                //     secondaryContainer + green text + corner check
                //     badge"; now "solid green + white text, no
                //     badge". The solid green + white is the
                //     strongest readability signal on its own — the
                //     check badge cluttered the smaller 36dp cells
                //     and made completed numbers look noisier than
                //     the rest. Selected (current subtitle) styling
                //     unchanged: primaryContainer + onPrimaryContainer
                //     — keeping primary purple for selection so
                //     "completed" (green) and "selected"
                //     (purple) read as two independent states.
                val columns = 8
                val cellSize = 36.dp
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
                                        .size(cellSize)
                                        .clickable {
                                            viewModel.skipToSubtitle(index)
                                            revealedWords = emptySet()
                                            showSentenceMenu = false
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.primaryContainer
                                        isCompleted -> Color(0xFF4CAF50)
                                        else -> Color.Transparent
                                    }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = when {
                                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                                isCompleted -> Color.White
                                                else -> MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ─── Middle area (subtitle card + RedRecordCircle anchor + playback) ───
        // (2026-07-10) §12.39: Wrap middle area in a single weight(1f)
        // Column so the bottom SpeakingControlBar always sticks to the
        // bottom of the screen — even when subtitles haven't loaded yet
        // (i.e. `currentSub == null`, the SpeakingSubtitleCard call below
        // is skipped). Previously the layout collapsed mid-screen when
        // subtitles were empty because nothing in the middle claimed
        // weight; the control bar floated up. Now the middle Column
        // always claims weight(1f), pinning the control bar at the
        // bottom regardless of subtitle availability.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Current sentence card — skipped when subtitles haven't
            // loaded (first launch before any course; or course with no
            // subtitles). The surrounding Column still keeps its slot.
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
                    // (2026-07-10) §12.39: weight(1f) lets the card
                    // share the vertical space inside the middle Column
                    // with the RedRecordCircle anchor below, instead
                    // of pushing the ControlBar off-screen for long
                    // sentences. The inner Column has
                    // verticalScroll(rememberScrollState()) so words
                    // scroll inside the card when content overflows.
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                )
            }

            // RedRecordCircle anchor — (2026-07-10) §12.42 changed
            // from heightIn(min = 48.dp) + Alignment.Center to
            // heightIn(min = 48.dp, max = 100.dp) + Alignment.BottomCenter
            // (2026-07-18: 140 → 100dp 与 RedRecordCircle 默认值同步),
            // and the previously-following RecordingPlaybackCard was
            // removed (see below). Combined effect: when recording,
            // the 100dp circle is gravity-aligned to the BOTTOM of
            // this slot, sitting visually adjacent to the
            // SpeakingControlBar below; when not recording the slot
            // falls back to 48dp minimum (no empty 100dp hole). The
            // Removed-by-§12.42 RecordingPlaybackCard used to eat
            // ~80dp of vertical space + a duplicate play button; its
            // deletion makes the Speaking 5-button bar's 4th "Play
            // recording" the SOLE playback affordance.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // (2026-07-18) max 140 → 100 与
                    // RecordingOverlay.RedRecordCircle 的新默认值
                    // (140 → 100dp) 同步；slot 跟着圆圈缩小, 避免留
                    // 40dp 空隙把控制条往下挤。
                    .heightIn(min = 48.dp, max = 100.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (recordingState == RecordingState.RECORDING) {
                    RedRecordCircle()
                }
            }

            // (2026-07-10) §12.42: RecordingPlaybackCard was REMOVED.
            // The user's "我的录音点击播放的窗口" referred to this
            // card's wide surface + play/stop button row that
            // appeared between the subtitle card and the bottom
            // control bar after a recording was completed. Function
            // duplicated by the 4th button on SpeakingControlBar
            // ("播放录音" → "停止"); keeping both added ~80dp of
            // vertical space + a second tap target for the same
            // action. The 4th bar button is kept enabled as long as
            // speakingRecordingPath != null, so the user can still
            // replay the recording after recording stops.
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
    // (2026-07-18) 移除 maxWordLength 局部变量 + 参数:FlowRow 自然按内容宽度排,
    // 旧版"统一 chip 宽度"目标在 chip 实际宽度跟着内容走时并未生效.

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        // (2026-07-23) §17.X: completed 句子不再用浅紫 primaryContainer
        // 背景 — 用户反馈"已完成时卡片背景色看起来怪", 与普通句子同色
        // (`surface`). 完成状态由右上角的 ✔ 圆圈 + 句子编号旁的紫色文字
        // 一起表达, 不需要再叠背景色; 否则浅紫把整张卡片"染"得像
        // 选中态, 跟正常的 primary 选中态 (dropdown cell 紫色) 也
        // 视觉冲突.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        // (2026-07-10) §12.41: Outer Column is **non-scrollable** so the
        // header row (句子 N · 未完成 · 标记完成) stays pinned at the
        // top of the card. The previous design wrapped EVERYTHING
        // including the header in a single verticalScroll(…) — that
        // meant a long sentence scrolled the header off the visible
        // area, so mid-recording the user couldn't tell which
        // sentence they were on or whether it was already marked
        // completed. New design: header outside scroll, only the
        // words + translation inside scroll.
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ─── Header row (always visible, pinned) ───────────────
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

            // ─── Scrollable area: words + translation ──────────────
            // (2026-07-10) §12.41: verticalScroll only on this inner
            // column, NOT the outer one. The outer Card's content
            // height is now bounded (header takes ~36dp), so the
            // inner scrollable column fills the remaining vertical
            // space inside the weight(1f)-sized card without ever
            // pushing the bottom control bar off-screen.
            //
            // weight(1f) on the inner Column is REQUIRED —
            // `verticalScroll` only kicks in when the container has
            // bounded height; without weight(1f) it would inherit
            // wrap-content and grow with content (no scroll trigger).
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Words row - wrap words into multiple lines
                SpeakingWordsFlowRow(
                    words = words,
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
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SpeakingWordsFlowRow(
    words: List<String>,
    revealedWords: Set<Int>,
    onWordClick: (Int) -> Unit,
    onWordLongClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // (2026-07-18) §16.X: 切到 FlowRow, 移除 25 char 启发式 + 移除
    // maxWordLength 入参。
    //
    // 旧版用 Column{Row{...}} 配合 hardcap `maxCharsPerLine = 25`
    // (≈ ~200dp / 行) 估算每行字数后手动切行 + Arrangement.Start
    // 左对齐: 卡片可显示宽度 ~300dp 时, 每行 chip 集合只占 ~67%
    // 就换行, 用户看到"右边空着就换行"。maxWordLength 本是给"统一
    // chip 宽度"用, 但 revealed/hidden 两个分支用不同字体, chip
    // 实际宽度跟着内容走, maxWordLength 没起作用。
    //
    // FlowRow 让 chip + 空格 Text 自然流到卡片右边界再 wrap,
    // 与 ListeningPage 同算法, 覆盖 / 显示两状态不再错位。chip
    // 之间用 Text(" ") bodyMedium 替代 Spacer(4dp), 自然间距
    // ≈ 3.6dp 接近 visible 路径。
    val style = MaterialTheme.typography.bodyMedium
    val revealedColor = MaterialTheme.colorScheme.onSurface

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.Start,
        // (2026-07-18) §16.X fix2: 上下行恢复 4dp 间隙, 同 ListeningPage.
        // 见该文件 ListeningHiddenWordsFlowRow 的 §16.X fix2 注释.
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        words.forEachIndexed { index, word ->
            if (index > 0) {
                Text(" ", style = style, color = revealedColor)
            }
            val isRevealed = revealedWords.contains(index)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (isRevealed) {
                            // revealed: combinedClickable, 长按翻译;
                            // onClick 留空 (与旧版一致, 点击不做).
                            Modifier.combinedClickable(
                                onClick = { },
                                onLongClick = { onWordLongClick(word) },
                            )
                        } else {
                            // hidden: 浅灰底 + 点击触发 reveal.
                            Modifier
                                .background(Color(0xFFE0E0E0))
                                .clickable { onWordClick(index) }
                        }
                    )
                    .padding(horizontal = 2.dp, vertical = 2.dp),
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
            // (2026-06-28) Reads the latest speakingRecordingPath
            // from the StateFlow at click time and passes it
            // explicitly to playRecording. The button is only
            // enabled when speakingRecordingPath != null, but
            // capturing the value at click time is more robust
            // than relying on a closure-captured `recordingPath`
            // variable (which could in theory be stale if the
            // StateFlow re-emitted between render and click).
            IconButton(
                onClick = {
                    if (isPlayingRecording) {
                        viewModel.stopPlayingRecording()
                    } else {
                        viewModel.playRecording(viewModel.speakingRecordingPath.value)
                    }
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
