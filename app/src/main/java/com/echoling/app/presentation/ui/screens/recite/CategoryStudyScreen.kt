package com.echoling.app.presentation.ui.screens.recite

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echoling.app.domain.model.DictEntry
import com.echoling.app.presentation.ui.components.PageHeader

/**
 * Flashcard study screen for one vocabulary category. UI mirrors the
 * previous LearningScreen word-for-word — the only difference is that
 * the word list comes from
 * [com.echoling.app.domain.usecase.GetDictionaryWordsInCategoryUseCase]
 * for the [categoryId] supplied via the nav route, so the user studies
 * only the words belonging to the selected category (and sees the
 * category-specific translation, not the cross-category merge).
 *
 * The card uses an [AnimatedContent] crossfade + scale transition as
 * the "flip" effect — same UX as the original screen, kept identical
 * so existing users recognize the gesture pattern.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryStudyScreen(
    categoryId: String,
    onNavigateBack: () -> Unit,
    viewModel: CategoryStudyViewModel = hiltViewModel(),
) {
    LaunchedEffect(categoryId) {
        viewModel.load(categoryId)
    }
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.lastSaved) {
        val saved = uiState.lastSaved
        if (saved != null) {
            snackbarHostState.showSnackbar("已加入单词本：$saved")
            viewModel.consumeLastSaved()
        }
    }

    // Surface TTS unavailability as a snackbar. Long-press / repeat
    // tap behavior: each non-null message fires exactly once, then
    // consumeTtsUnavailableMessage clears it so the next tap re-fires.
    LaunchedEffect(uiState.ttsUnavailableMessage) {
        val msg = uiState.ttsUnavailableMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            viewModel.consumeTtsUnavailableMessage()
        }
    }

    Scaffold(
        // No `snackbarHost` slot here — the snackbar is anchored
        // INSIDE StudyBody right above the 认识/不认识 row (see
        // StudyBody). Putting it in the default bottom slot would
        // cover the 上一张/下一张 row when it pops up; placing it
        // inline keeps the confirmation visible without occluding
        // the navigation buttons below.
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
                        text = uiState.categoryName.ifBlank { "记单词" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.resetSession() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重置进度",
                        )
                    }
                },
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.words.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "该词库为空",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    StudyBody(
                        uiState = uiState,
                        snackbarHostState = snackbarHostState,
                        onFlip = viewModel::flipCard,
                        onKnown = viewModel::markKnown,
                        onUnknown = viewModel::markUnknown,
                        onSave = viewModel::saveCurrentToVocabulary,
                        onPrev = viewModel::skipToPrevious,
                        onNext = viewModel::skipToNext,
                        onPronounce = viewModel::pronounceCurrent,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyBody(
    uiState: CategoryStudyUiState,
    snackbarHostState: SnackbarHostState,
    onFlip: () -> Unit,
    onKnown: () -> Unit,
    onUnknown: () -> Unit,
    onSave: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPronounce: () -> Unit,
) {
    val current = uiState.currentWord ?: return
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        ProgressRow(
            currentNumber = uiState.currentIndex + 1,
            total = uiState.totalCount,
            known = uiState.knownCount,
            unknown = uiState.unknownCount,
        )

        // Spacer between the progress row and the card: tightened
        // from 20dp → 12dp so the controls below feel closer to the
        // card (the "buttons move up to hug the card" request).
        Spacer(modifier = Modifier.height(12.dp))

        // The flashcard is a fixed-aspect square, centered in the
        // remaining vertical space — smaller than the old `weight(1f)`
        // full-height card so the word feels like a "flashcard" rather
        // than a full-screen hero. `weight(1f)` on the outer Box keeps
        // the card vertically centered when there's spare height.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            FlashCard(
                entry = current,
                isFlipped = uiState.isFlipped,
                onClick = onFlip,
                onPronounce = onPronounce,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        }

        // Snackbar anchored above the answer buttons. Default M3
        // placement puts it at the very bottom of the Scaffold — there
        // it would overlap the 上一张/下一张 row when "已加入单词本"
        // pops up. Hosting it inline here keeps the confirmation in
        // the user's eye line (right above the button they just
        // tapped) and far from the navigation row at the bottom.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FilledTonalButton(
                onClick = onKnown,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("认识", fontSize = 16.sp)
            }
            Button(
                onClick = onUnknown,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("不认识", fontSize = 16.sp)
            }
        }

        if (uiState.showSaveButton) {
            // "加入单词本" sits tucked under the 认识/不认识 row — same
            // 8dp gap so it reads as part of the "answer" group
            // (visual pairing with the button that revealed it).
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("加入单词本", fontSize = 16.sp)
            }
        }

        // Visual separator between the answer group (认识/不认识 +
        // 加入单词本) and the navigation group (上一张/下一张). The
        // 20dp blank line is wide enough to feel like a deliberate
        // divider between the two control groups.
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onPrev) {
                Icon(Icons.Default.SkipPrevious, contentDescription = null)
                Spacer(modifier = Modifier.padding(end = 4.dp))
                Text("上一张")
            }
            TextButton(onClick = onNext) {
                Text("下一张")
                Spacer(modifier = Modifier.padding(start = 4.dp))
                Icon(Icons.Default.SkipNext, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ProgressRow(
    currentNumber: Int,
    total: Int,
    known: Int,
    unknown: Int,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "已学 $currentNumber / $total",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ScoreBadge(label = "认识", count = known, color = MaterialTheme.colorScheme.primary)
            ScoreBadge(label = "不认识", count = unknown, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ScoreBadge(label: String, count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = "$label $count",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun FlashCard(
    entry: DictEntry,
    isFlipped: Boolean,
    onClick: () -> Unit,
    onPronounce: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Drive a continuous 0..180° rotation off [isFlipped]. We use
    // `graphicsLayer.rotationY` so the rotation is GPU-composited (no
    // layout pass) and `cameraDistance` is bumped up — Compose's
    // default camera is so close that anything beyond ~120° looks
    // like a fisheye; multiplying it by 8 keeps the perspective
    // natural at 180°.
    //
    // At exactly 90° the card is edge-on (invisible). We swap
    // front↔back content at that midpoint so the back face is never
    // rendered mirror-reversed. `[front, back]` is laid out in the
    // same Box stacked with the back pre-rotated 180° so its final
    // position (after the outer rotation) reads correctly.
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "flashcard-rotation",
    )
    val showBack = rotation > 90f

    Card(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (showBack)
                MaterialTheme.colorScheme.secondaryContainer
            else
                MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            // Both faces are mounted in the same Box and inherit the
            // outer Card's `rotationY` (0° → 180°). To stop the front
            // face's text from showing through as a mirror image once
            // the card has flipped, we **counter-rotate** each face:
            //
            //  - [CardFront] is the visible layer during [0°, 90°).
            //    When the outer card passes 90° we hide it via
            //    `alpha = 0` and counter-rotate it 180° so it ends
            //    up orientation-correct if it ever gets composited.
            //  - [CardBack] is pre-rotated 180° (so its text reads
            //    correctly after the outer rotation) and only fades
            //    in once `rotation > 90°` (i.e. once the front is
            //    edge-on and no longer visible).
            //
            // Without the per-face `rotationY`, the front face's word
            // would still be compositing in mirrored form during the
            // second half of the flip — looking like the front's word
            // had "leaked" onto the back.
            CardFront(
                entry = entry,
                onPronounce = onPronounce,
                modifier = Modifier.graphicsLayer {
                    rotationY = if (showBack) 180f else 0f
                    alpha = if (showBack) 0f else 1f
                },
            )
            CardBack(
                entry = entry,
                onPronounce = onPronounce,
                modifier = Modifier.graphicsLayer {
                    rotationY = 180f
                    alpha = if (showBack) 1f else 0f
                },
            )
        }
    }
}

@Composable
private fun CardFront(
    entry: DictEntry,
    onPronounce: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Word + speaker on the same Row. The user asked for the
            // speaker to sit immediately to the right of the word
            // (rather than as a floating top-right corner button) so
            // the pair reads as one unit: "word [🔊]". 44dp box /
            // 26dp icon is slightly larger than the back's 32/20
            // because the front has no other controls nearby — the
            // speaker is the front's only secondary action and
            // deserves to be a clear touch target.
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.word,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onPronounce,
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "朗读单词",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击卡片查看翻译",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun CardBack(
    entry: DictEntry,
    onPronounce: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The back face intentionally omits the word itself — the user
    // already saw it on the front. Re-rendering it here would (a) feel
    // like the front's word "leaked" onto the back, and (b) eat
    // vertical space in the small square card. Layout becomes
    // [phonetic + 🔊] → [pos chip] → [translation], top-down.
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (entry.phonetic.isNotBlank()) {
            // Phonetic + speaker on one row — natural pair, the user
            // reads the IPA and hears the word in one eye-movement.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.phonetic,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onPronounce,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "朗读单词",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else {
            // No phonetic — still show the speaker so the affordance
            // is consistent across cards. Without this fallback the
            // icon would pop in/out as the user flips through words,
            // which feels broken.
            IconButton(
                onClick = onPronounce,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.VolumeUp,
                    contentDescription = "朗读单词",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (entry.pos.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.08f),
            ) {
                Text(
                    text = entry.pos,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = entry.translation,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}
