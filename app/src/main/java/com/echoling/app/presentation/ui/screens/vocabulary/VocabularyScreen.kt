package com.echoling.app.presentation.ui.screens.vocabulary

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echoling.app.domain.model.Word
import com.echoling.app.presentation.ui.components.CompactConfirmDialog
import com.echoling.app.presentation.ui.components.PageHeader
import com.echoling.app.presentation.viewmodel.VocabularyViewModel

/**
 * Vocabulary book — the user's saved words, one per row.
 *
 * §12.22 layout:
 *   Row 1: word (left, weight 1) | phonetic (right of word) | ✓ icon | 🗑 icon
 *   Row 2: Chinese translation (full width)
 *   Row 3 (optional, muted): example sentence, 1 line ellipsized
 *
 * Inter-row spacing is tightened to 2dp (down from 8dp) so a long
 * vocabulary list fits more on screen — the user said "把单词之间的
 * 间隔再缩小一点". Card padding is also reduced (vertical 6dp, was
 * 8dp) to give more vertical density without feeling cramped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: VocabularyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterMenu by remember { mutableStateOf(false) }
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
        // No topBar — title and filter sit in PageHeader (§12.18)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PageHeader(
                onBack = onNavigateBack,
                title = {
                    Text(
                        text = "单词本",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showFilterMenu = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter")
                        }
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("全部") },
                                onClick = {
                                    viewModel.setShowMastered(true)
                                    showFilterMenu = false
                                },
                                leadingIcon = if (uiState.showMastered) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null
                            )
                            DropdownMenuItem(
                                text = { Text("未掌握") },
                                onClick = {
                                    viewModel.setShowMastered(false)
                                    showFilterMenu = false
                                },
                                leadingIcon = if (!uiState.showMastered) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null
                            )
                        }
                    }
                },
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
                            onToggleMastered = { viewModel.toggleMastered(it) },
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
    onToggleMastered: (Word) -> Unit,
    onDelete: (Word) -> Unit,
    onPronounce: (Word) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        // §12.22: tighter vertical spacing — was 8dp, now 2dp. The user
        // wants more words visible per scroll without losing the
        // card-grouping visual cue (the Card's own background color
        // already provides enough separation).
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(words, key = { it.word }) { word ->
            WordRow(
                word = word,
                onToggleMastered = { onToggleMastered(word) },
                onDelete = { onDelete(word) },
                onPronounce = { onPronounce(word) },
            )
        }
    }
}

@Composable
private fun WordRow(
    word: Word,
    onToggleMastered: () -> Unit,
    onDelete: () -> Unit,
    onPronounce: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (word.isMastered)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        ),
    ) {
        // Tighter card padding keeps the row compact while still leaving
        // room for the phonetic chip on the right. Padded directly on the
        // inner Column since Card has no contentPadding slot.
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            // Top row: word (left, takes available space) | phonetic |
            // ✓ | 🗑. The word uses weight(1f) so a long word like
            // "internationalization" doesn't crowd the phonetic.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = word.word,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (word.isMastered) TextDecoration.LineThrough else null,
                    color = if (word.isMastered)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (word.phonetic.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = word.phonetic,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Pronunciation button. Sized to match the existing
                // Check / Delete trailing icons (32dp box, 18dp icon)
                // so the trailing group reads as: phonetic → 🔊 → ✓ → 🗑.
                // Tap speaks the word via TTS. Each trailing icon has
                // its own onClick so they don't interfere with each
                // other or with the row's other gestures.
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
                // Trailing icon buttons. Sized down (32dp instead of
                // 48dp) so the row stays compact — the touch target is
                // still ≥32dp on each axis which is acceptable for
                // secondary actions behind a dedicated word book screen.
                IconButton(
                    onClick = onToggleMastered,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = if (word.isMastered) "Unmark mastered" else "Mark mastered",
                        tint = if (word.isMastered)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            // Translation row. POS prefix chip is rendered inline ahead
            // of the translation text so the part-of-speech reads as a
            // label ("vt. 抛弃") rather than getting concatenated into
            // the translation field — the user sees the part-of-speech
            // as a discrete chip and the translation as the primary
            // payload below the word/phonetic row.
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
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = word.translation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Example sentence — only when the word came from practice
            // and the user actually has an example attached. Kept as a
            // small muted line to provide context without bloating the
            // list.
            if (word.exampleSentence.isNotEmpty()) {
                Text(
                    text = word.exampleSentence,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (showDeleteConfirm) {
        CompactConfirmDialog(
            title = "Delete Word",
            message = "Are you sure you want to delete \"${word.word}\"?",
            confirmText = "Delete",
            dismissText = "Cancel",
            onConfirm = {
                onDelete()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}
