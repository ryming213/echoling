package com.echoling.app.presentation.ui.screens.vocabulary

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echoling.app.R
import com.echoling.app.domain.model.Word
import com.echoling.app.presentation.ui.components.PageHeader
import com.echoling.app.presentation.viewmodel.VocabularyViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Vocabulary book — the user's saved words, one per row.
 *
 * §12.31 layout (beautified 2026-07-03):
 *   Top row:   word (18sp Bold) | phonetic (14sp) | 🔊
 *   Mid row:   POS chip + translation (bodyMedium primary)
 *   Last row:  example sentence (bodySmall muted, 1 line) — optional
 *   Bottom:    0.5dp HorizontalDivider (omitted on the last row)
 *
 * Interactions:
 *   - Tap row → pronounce via TTS
 *   - Tap 🔊 → pronounce via TTS (same action, visible affordance)
 *   - Swipe left (right→left) → reveal red [删除] background + button.
 *     The row STAYS swiped open (custom SwipeRevealRow with
 *     [Animatable] offset; does NOT auto-dismiss).
 *   - Tap the [删除] button → onDelete(); row disappears.
 *   - Drag the row back to the right past the midpoint → snaps
 *     closed (no commit).
 *
 * Removed in this revision (per user request 2026-07-03):
 *   - The ✓ "Mark mastered" IconButton and its callback chain
 *     (WordRow.onToggleMastered → WordList.onToggleMastered →
 *     VocabularyViewModel.toggleMastered → ToggleWordMasteredUseCase).
 *   - The "全部 / 未掌握" filter dropdown in the page header
 *     (PageHeader actions slot, showFilterMenu state, setShowMastered
 *     method on the ViewModel).
 *   - The domain use case `ToggleWordMasteredUseCase.kt` was deleted
 *     from the codebase (no other callers after the UI was removed).
 *   - The [Word.isMastered] data field is PRESERVED for Room schema
 *     backwards-compat — already-mastered words still display in the
 *     list, they just can't be toggled from this UI.
 */
@Composable
fun VocabularyScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: VocabularyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface TTS unavailability as a snackbar. Long duration so the
    // user has time to read the install instructions.
    LaunchedEffect(uiState.ttsUnavailableMessage) {
        val msg = uiState.ttsUnavailableMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            viewModel.consumeTtsUnavailableMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        // §12.30: only consume the top status-bar inset, not the bottom
        // navigation-bar inset. The outer MainScaffold already accounts
        // for the bottom tab bar, so a second nav-bar subtraction here
        // would leave a 24dp page-colored strip between the tab bar
        // and the last list item. See CLAUDE.md §12.30.
        contentWindowInsets = WindowInsets.statusBars,
        // No topBar — title sits in PageHeader (§12.18, §12.31)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PageHeader(
                onBack = onNavigateBack,
                title = {
                    // Two-line brand bar matching the home page (Courses)
                    // and Me tabs — Chinese title with 3sp letter-spacing
                    // so the three characters don't visually crowd, plus
                    // an italic English subtitle below in onSurfaceVariant.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.vocabulary_title),
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(R.string.vocabulary_subtitle),
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                // §12.31: filter dropdown removed (the only path into
                // `setShowMastered` was this menu; with ✓ gone there's
                // no new way to mark words as mastered, so the filter
                // has no purpose). PageHeader has no actions slot now.
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.words.isEmpty() -> {
                        EmptyVocabulary()
                    }
                    else -> {
                        WordList(
                            words = uiState.words,
                            onDelete = { viewModel.deleteWord(it) },
                            onPronounce = { viewModel.pronounce(it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyVocabulary() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "单词本还是空的",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "在跟读练习中长按单词，或在学习页点击「不认识 → 加入单词本」来保存",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
        }
    }
}

@Composable
private fun WordList(
    words: List<Word>,
    onDelete: (Word) -> Unit,
    onPronounce: (Word) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // 4dp top + 0 bottom: the first row's own 12dp vertical
        // padding already gives breathing room from the PageHeader;
        // 0 bottom so the last row sits flush with the bottom
        // edge (the divider is omitted on the last row anyway).
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        itemsIndexed(words, key = { _, w -> w.word }) { index, word ->
            WordRow(
                word = word,
                isLast = index == words.lastIndex,
                onDelete = { onDelete(word) },
                onPronounce = { onPronounce(word) },
            )
        }
    }
}

@Composable
private fun WordRow(
    word: Word,
    isLast: Boolean,
    onDelete: () -> Unit,
    onPronounce: () -> Unit,
) {
    SwipeRevealRow(onDelete = onDelete) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                // Whole row is tappable. The inner IconButton still
                // receives its own click — M3's IconButton routes the
                // click before the parent's clickable in the gesture
                // path, so tapping 🔊 speaks without also triggering
                // a duplicate TTS call from the parent.
                .clickable(onClick = onPronounce)
                // §12.31b: 12 → 8dp vertical padding — tighten the
                // row height so more words fit per scroll, matching
                // the smaller 14sp word + 12sp translation fonts.
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Top row: word (weight=1) | 4dp | phonetic | 8dp | 🔊
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = word.word,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (word.phonetic.isNotEmpty()) {
                    // §12.31b: 8 → 4dp — pull the phonetic flush
                    // against the word so they read as a single
                    // "abandon /əˈbændən/" unit. The 8dp gap before
                    // 🔊 stays to keep the action visually separate
                    // from the read-only data.
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = word.phonetic,
                        style = TextStyle(
                            fontSize = 12.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onPronounce,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "朗读单词",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // Translation row. POS prefix chip rendered inline ahead
            // of the translation text so the part-of-speech reads as
            // a label ("vt. 抛弃") rather than getting concatenated.
            Spacer(modifier = Modifier.height(2.dp))
            if (word.pos.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = word.pos,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                    Text(
                        text = word.translation,
                        // §12.31b: bodyMedium (14sp) → bodySmall
                        // (12sp) — match the phonetic size for a
                        // uniform secondary-text scale, and make the
                        // row more compact.
                        // §12.31c: color = primary (purple) → #666666
                        // medium gray. The purple used to compete with
                        // the word text for attention; a softer gray
                        // keeps the Chinese reading as the "payload"
                        // without pulling focus from the 14sp Bold
                        // English word.
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF666666),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = word.translation,
                    // §12.31b: bodyMedium (14sp) → bodySmall (12sp)
                    // §12.31c: medium gray instead of primary purple
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Example sentence — only when the word came from practice
            // and the user actually has an example attached. Shown in
            // full (no maxLines / no ellipsis) so the user can see the
            // whole context for the word — practice sentences are the
            // whole point of saving a word with a source link, and a
            // 1-line ellipsized version was hiding the meaningful half.
            if (word.exampleSentence.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = word.exampleSentence,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // §12.31: thin gray divider between rows. Omitted on the last
    // row so the list visually "ends" cleanly without a dangling
    // line at the bottom of the scrollable area.
    if (!isLast) {
        Divider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
        )
    }
}

/**
 * Custom swipe-to-reveal-a-button row.
 *
 * Why not M3 `SwipeToDismissBox`? That component auto-dismisses on
 * release (or snaps back if `confirmValueChange` returns false) —
 * neither is what we want. We want the row to STAY swiped open
 * showing the [删除] button until the user either taps the button
 * (commits the delete) or drags the row back to the right past the
 * midpoint (cancels).
 *
 * Implementation: track the foreground offset with an [Animatable]
 * driven by `Modifier.draggable`. On drag end, spring to either the
 * fully-revealed position (-maxReveal) or back to 0 depending on
 * which side of the midpoint we ended on. The red background and
 * [删除] button live behind the foreground at the right edge; they
 * become visible as the foreground slides left.
 */
@Composable
private fun SwipeRevealRow(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val maxRevealPx = with(density) { 96.dp.toPx() }  // width of the [删除] area
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }

    val draggableState = rememberDraggableState { delta ->
        scope.launch {
            val target = (offsetX.value + delta).coerceIn(-maxRevealPx, 0f)
            offsetX.snapTo(target)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Red full-width background. Always present so the row
        // doesn't "collapse" when the foreground slides left. The
        // [删除] button is anchored to the right edge with 16dp
        // padding so it lines up with the row's right margin.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.CenterEnd,
        ) {
            TextButton(
                onClick = onDelete,
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "删除",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        // Foreground: the actual row content. Slides left as the
        // user drags right→left. Background color = page background
        // so the red area is hidden by the row when offset = 0.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        scope.launch {
                            // Snap rule: if past halfway, fully open;
                            // otherwise snap back closed. The `velocity`
                            // sign determines flick direction (a quick
                            // left flick from -10px opens; a quick right
                            // flick from -80px closes).
                            val shouldOpen = offsetX.value < -maxRevealPx / 2 ||
                                velocity < -800f
                            val shouldClose = offsetX.value > -maxRevealPx / 2 ||
                                velocity > 800f
                            val target = when {
                                shouldOpen && !shouldClose -> -maxRevealPx
                                else -> 0f
                            }
                            offsetX.animateTo(
                                targetValue = target,
                                animationSpec = spring(
                                    dampingRatio = 0.85f,
                                    stiffness = 1200f,
                                ),
                                initialVelocity = velocity,
                            )
                        }
                    },
                ),
        ) {
            content()
        }
    }
}
