package com.echoling.app.presentation.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Label position for [AutoSubtitleProgressLine].
 *
 * - [AboveLeft]: label sits *above* the bar, left-aligned. Used when
 *   the bar lives at the **bottom** of a card (ImportScreen's
 *   AutoSubtitleCard) — the label reads as "正在识别中…" caption for
 *   the bar directly beneath it.
 * - [BelowLeft]: label sits *below* the bar, left-aligned. Used when
 *   the bar lives at the **top** of a card (CourseListItem during
 *   IN_PROGRESS) — the label reads as a status caption for the bar
 *   above, like a download-progress badge.
 *
 * The two positions match the user's verbatim spec:
 * - ImportScreen: "在长先线的左上角显示：正在识别中…"
 * - CourseListItem: "在长线条的左下角显示：正在识别中…"
 */
enum class ProgressLineLabelPosition { AboveLeft, BelowLeft }

/**
 * Thin progress bar for the auto-subtitle pipeline. Shared between
 * ImportScreen (bottom of card, label above) and CourseListItem
 * (top of card, label below).
 *
 * Visual:
 *  - 4dp tall, rounded corners (2dp) — same height as M3's
 *    [LinearProgressIndicator] default. Thicker (≥6dp) starts to
 *    "press" into the card; thinner (≤2dp) is invisible on the
 *    Mi 11 CN panel.
 *  - Background = `surfaceVariant` (M3 muted surface, matches the
 *    AutoSubtitleCard's containerColor so the unfilled track blends
 *    with the card).
 *  - Fill = `primary` (#7C3AED brand purple, §11.2). Width is
 *    `progress/100f` of the parent; at 0% the fill Box collapses to
 *    zero width (Compose won't render a 0-width Box).
 *  - Label = `labelSmall` (11sp), `primary` color in AboveLeft mode
 *    (matches the fill — "actively in progress") and `onSurfaceVariant`
 *    in BelowLeft mode (muted caption — the bar itself carries the
 *    active color, no need to repeat). Default text is the
 *    CourseListItem caption "字幕识别中…" — ImportScreen passes
 *    "正在识别中…" explicitly for its above-the-bar caption.
 *
 * No animation: the upstream
 * [com.echoling.app.transcription.AutoTranscriptionWorker] already
 * throttles progress publishes to 1 Hz, so the visual is naturally
 * stepwise without paying for [animateFloatAsState]'s recomposition.
 *
 * @param progress 0..100; values outside the range are clamped.
 * @param labelPosition where to place the caption.
 * @param label caption text. Empty string suppresses rendering of
 *   the Text altogether (callers that want a bare bar).
 */
@Composable
fun AutoSubtitleProgressLine(
    progress: Int,
    modifier: Modifier = Modifier,
    labelPosition: ProgressLineLabelPosition = ProgressLineLabelPosition.BelowLeft,
    label: String = "字幕识别中…",
) {
    // (2026-07-18) Animate the fill width between publishes so the
    // bar appears to move continuously through 5% increments even
    // though the worker only publishes once per second
    // (throttleProgress gates at 1 Hz). The 1200ms tween is slightly
    // longer than the 1 Hz interval so each new target lands mid-
    // animation and the bar never sits still. With Vosk now feeding
    // byte-fraction progress callbacks through the worker during
    // step 2 (30..70% range — the longest step), the bar advances
    // smoothly through every 5% boundary inside that window.
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0, 100) / 100f,
        animationSpec = tween(durationMillis = 1200),
        label = "autoSubtitleProgress",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        if (labelPosition == ProgressLineLabelPosition.AboveLeft && label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
        if (labelPosition == ProgressLineLabelPosition.BelowLeft && label.isNotEmpty()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}