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
import androidx.compose.material3.Snackbar
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
                        // (2026-07-04) new — speaks the example sentence
                        // for the speaker button on the flashcard front.
                        onPronounceSentence = viewModel::pronounceCurrentSentence,
                    )
                }
            }
        }
    }

    // (2026-07-04) "已加入单词本" confirmation surfaced as a snackbar
    // — earlier today this was an AlertDialog, but the user found
    // a blocking dialog disruptive mid-flip (the user has just
    // tapped "加入单词本" and the card is about to advance; forcing
    // a "确定" tap to dismiss is too much). Back to a transient
    // snackbar, but the snackbar's `containerColor` is themed to
    // `surfaceVariant` (light gray) via the custom `snackbar` slot
    // on `SnackbarHost` below — same muted tone the dialog had, so
    // the visual signal still reads as "different from a default
    // info toast" without the blocking modal interaction.
    //
    // Lifecycle: snackbar appears, M3 auto-dismisses after
    // `SnackbarDuration.Short` (~4s), then we call
    // `consumeLastSaved()` to clear the state field. A second tap
    // on "加入单词本" re-fires it from the new word.
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
    // (2026-07-04) new — plays the example sentence via TTS.
    onPronounceSentence: () -> Unit = {},
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
                // (2026-07-04) new — drives the speaker button on
                // the front face's example-sentence row.
                onPronounceSentence = onPronounceSentence,
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
        //
        // (2026-07-04) Custom `snackbar` slot overrides M3's default
        // container color (which is `inverseSurface` — near-black) with
        // `surfaceVariant` (light purple-tinted gray) per the user's
        // "背景色改为浅灰色" request. The text color switches to
        // `onSurfaceVariant` to keep AA contrast on the lighter
        // background; M3's default `inverseOnSurface` would render the
        // text light on light and be unreadable.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(bottom = 4.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

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
    // (2026-07-04) new — passed through to CardFront for the
    // speaker button on the example-sentence row.
    onPronounceSentence: () -> Unit = {},
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
                // (2026-07-04) new — wires the front's sentence
                // speaker button to the ViewModel's sentence TTS call.
                onPronounceSentence = onPronounceSentence,
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
    // (2026-07-04) new — drives the speaker button on the
    // example-sentence row, sitting to the right of the English
    // sentence. Tapping it speaks the sentence aloud via TTS.
    onPronounceSentence: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Main content (word + example sentences) centered. The
        // hint "点击卡片查看翻译" is rendered as a separate sibling
        // anchored to the bottom of the card — see below.
        //
        // (2026-07-05) Horizontal padding split out from the
        // uniform 20dp so the left-aligned example sentence below
        // can run closer to the card edges. The word + speaker row
        // is centered horizontally so it isn't visually affected
        // by the wider container — only the English sentence and
        // its TTS button (which use fillMaxWidth + Start / the
        // Column's CenterHorizontally) gain the extra room.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 20.dp),
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
                // (2026-07-05) headlineMedium (28sp) → headlineSmall (24sp) per
                // user request — one step down in the M3 headline scale.
                // The word stays the focal point of the front face but
                // no longer overwhelms the example sentence below it
                // (which is bodyMedium / 14sp). The Bold weight is
                // kept so the word still reads as "primary signal"
                // even at the smaller size.
                Text(
                    text = entry.word,
                    style = MaterialTheme.typography.headlineSmall,
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

            // (2026-07-04) English example sentence on the front.
            // Layout split per user's request: the front now holds
            // ONLY English content (word + EN example), the back
            // holds ONLY Chinese / pronunciation content (phonetic +
            // word translation + CN example translation). This keeps
            // each face linguistically pure and prevents long
            // translations from squeezing the front.
            //
            // (2026-07-05) Sentence layout reshaped: the text is
            // left-aligned (fillMaxWidth + TextAlign.Start) so long
            // sentences have a clean left edge as they wrap, and the
            // TTS speaker button now sits BELOW the sentence,
            // centered horizontally (the parent Column already has
            // `horizontalAlignment = CenterHorizontally`, so the 32dp
            // IconButton centers itself without an extra modifier).
            // Previous row-with-button-on-right design had the speaker
            // squeezed against the right edge on long sentences — the
            // vertical stack reads more naturally and keeps the
            // button at a predictable x-coordinate regardless of
            // sentence length.
            //
            //   [ English sentence text,   ]
            //   [ left-aligned, wrapping    ]
            //              [ 🔊 ]
            //
            // Gated on `isNotBlank()` because the source JSON's
            // coverage isn't 100% — short multi-word phrases often
            // lack an example entirely. If the EN sentence is blank
            // we skip both the text AND the speaker button so the
            // card doesn't render a stranded button below an empty
            // block.
            if (entry.exampleSentenceEn.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                // Sentence — `fillMaxWidth` so multi-line wrap has a
                // consistent left edge, `TextAlign.Start` so the text
                // reads from the left rather than centering (which
                // made long sentences look ragged with the previous
                // design).
                Text(
                    text = entry.exampleSentenceEn,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start,
                )
                // Speaker button — directly under the sentence,
                // horizontally centered via the parent Column's
                // `horizontalAlignment = CenterHorizontally`. The
                // 32dp box / 20dp icon match the back face's
                // phonetic/word speakers so the card has a
                // consistent touch-target vocabulary across faces.
                IconButton(
                    onClick = onPronounceSentence,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "朗读句子",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // (2026-07-04) Hint removed from the bottom of the
            // centered Column — see the bottom-anchored sibling below
            // for why.
        }
        // (2026-07-04) Hint is now a sibling of the centered Column,
        // anchored to the card's bottom edge with `Alignment.BottomCenter`.
        //
        // The previous layout put it directly under the Chinese
        // translation with only an 8dp gap, which made the two read
        // as one block ("Chinese sentence [hint]") and confused users
        // into thinking the hint was somehow about the Chinese text.
        //
        // Pinning the hint to the bottom of the card — separated from
        // the centered content by the card's own vertical space —
        // gives it a clear "this is a UI affordance, not part of the
        // vocabulary" identity. The 16dp bottom padding keeps the
        // text from kissing the card's rounded corner.
        Text(
            text = "点击卡片查看翻译",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
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
        // Translation reads as supporting context for the word, not
        // the primary signal — so on the back face's
        // secondaryContainer background we use the muted onSurfaceVariant
        // for the color and Normal for the weight. The M3 theme
        // declares titleMedium as SemiBold (600) — see Type.kt:64-70 —
        // so without the explicit fontWeight override the translation
        // would render heavier than the phonetic above (bodyMedium,
        // Normal/400) and the POS chip below, breaking visual
        // consistency.
        //
        // (2026-07-05) Title size downshifted one step from
        // titleMedium (16sp) → titleSmall (14sp) per user request.
        // The translation is no longer the visual focal point of the
        // back face — the multi-line examples and phonetic row above
        // already carry enough weight, and the previous 16sp read as
        // "loud" on dense cards. The Normal weight override is kept so
        // the text stays visually subordinate to the POS chip's Medium
        // weight, even though they're now the same sp size — the
        // letter-spacing difference (titleSmall = 0.1sp vs
        // POS-labelMedium = 0.5sp) is the cue that signals "supporting
        // text, not a label".
        Text(
            text = entry.translation,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Normal,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        // (2026-07-04) Example sentence translation (CN). Lives on
        // the back face alongside the word translation, so the back
        // is purely the "Chinese / explanation" face:
        //   [phonetic + 🔊] → [pos chip] → [word translation]
        //     → [example sentence translation]
        //
        // The English example sentence (with its speaker button)
        // sits on the FRONT face — splitting the languages per face
        // (English only on front, Chinese only on back) prevents the
        // long-translation overflow problem we had when both
        // languages shared one face.
        //
        // No maxLines / ellipsis here: long Chinese translations
        // are fine because the back face has more vertical room now
        // that it no longer carries the English sentence pair, and
        // an interrupted CN gloss is worse than a slightly tall
        // card. The parent Column doesn't clipToBounds so any
        // extreme overflow stays readable instead of being cut off.
        if (entry.exampleSentenceCn.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = entry.exampleSentenceCn,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
