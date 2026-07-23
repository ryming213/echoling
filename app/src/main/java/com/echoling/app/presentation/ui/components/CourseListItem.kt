package com.echoling.app.presentation.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.echoling.app.R
import com.echoling.app.domain.model.AutoSubtitleStatus
import com.echoling.app.domain.model.Course

/**
 * Single row in the Courses tab's course list. Shows the course title,
 * description, difficulty chip, audio/video/subtitle indicators, and
 * duration. Has a delete affordance and a primary "start" button.
 *
 * Modern card recipe (CLAUDE.md §12.16):
 * - 4dp left accent bar, color picked by difficulty tier
 *   (A* → primary purple, B* → tertiary, C* → secondary, else → primary)
 * - Press animation: elevation 1dp → 8dp, scale 1.0 → 1.02, both eased
 *   with [animateDpAsState] / [animateFloatAsState] (≈150ms feel)
 * - Ripple bounded by the card's rounded clip
 * - Same content layout as before — accent bar + animation are additive
 */
@Composable
fun CourseListItem(
    course: Course,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    onRetryTranscription: () -> Unit = {},
) {
    val hasAudio = course.hasAudioContent()
    val hasVideo = course.hasVideoContent()
    val hasSubtitles = course.subtitleUri != null

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animatedElevation by animateDpAsState(
        targetValue = if (isPressed) 8.dp else 1.dp,
        label = "courseCardElevation",
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 1.02f else 1.0f,
        label = "courseCardScale",
    )

    val accentColor = accentColorFor(course.difficulty)

    // (2026-07-16) Auto-subtitle status chip. Surfaces the
    // background-worker state to the course list so the user knows
    // when a course is still being transcribed and can retry failed
    // jobs without diving into a sub-page.
    val autoStatus = course.autoSubtitleStatus
    val chipData = when (autoStatus) {
        AutoSubtitleStatus.PENDING -> ChipData(
            label = stringResource(R.string.auto_subtitle_chip_pending),
            icon = Icons.Filled.HourglassEmpty,
            color = MaterialTheme.colorScheme.tertiary,
            // (2026-07-18) PENDING chip is now clickable — tapping it
            // kicks off the deferred transcription via the same
            // `onRetryTranscription` callback the FAILED chip uses
            // (which in turn calls `retryAutoSubtitle` on the VM).
            // After the worker enqueues, the DB flips to IN_PROGRESS
            // and this chip is replaced by the top progress line.
            enabled = true,
        )
        AutoSubtitleStatus.FAILED -> ChipData(
            label = stringResource(R.string.auto_subtitle_chip_failed),
            icon = Icons.Filled.Warning,
            color = MaterialTheme.colorScheme.error,
            enabled = true,
        )
        // (2026-07-18) IN_PROGRESS no longer renders a chip — the
        // top progress line + "字幕识别中…" caption below it replaces
        // the funnel chip + "字幕识别中 X%" pair (the X% is dropped;
        // the purple fill bar is now the sole progress indicator).
        AutoSubtitleStatus.IN_PROGRESS,
        AutoSubtitleStatus.READY, null -> null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(animatedScale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                // (2026-07-16) Disable the card while auto-subtitle
                // is still running — the user can't practice the
                // course without subtitles, so tapping it would just
                // land on a "字幕正在识别中" empty state (Task 5.6).
                // FAILED is still clickable so the user can navigate
                // to the sub-page and trigger retry there; READY/null
                // are normal flow.
                enabled = autoStatus != AutoSubtitleStatus.PENDING &&
                        autoStatus != AutoSubtitleStatus.IN_PROGRESS,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
    ) {
        // (2026-07-18) IN_PROGRESS shows a thin progress line at the
        // very top of the card instead of the funnel chip. Layout:
        //   Column
        //     ├── progress line (only when IN_PROGRESS)
        //     └── original Row with accent bar + content + actions
        // The progress line spans the full card width minus a 4dp
        // horizontal inset so it doesn't overlap the 4dp accent bar.
        // Label sits *below* the line (spec: "在长线条的左下角显示").
        Column(modifier = Modifier.fillMaxWidth()) {
            if (autoStatus == AutoSubtitleStatus.IN_PROGRESS) {
                AutoSubtitleProgressLine(
                    progress = course.autoSubtitleProgress,
                    modifier = Modifier.padding(horizontal = 4.dp),
                    labelPosition = ProgressLineLabelPosition.BelowLeft,
                )
            }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor),
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (chipData != null) {
                        AssistChip(
                            // FAILED chip is clickable → retry.
                            // PENDING / IN_PROGRESS chips are inert
                            // (the worker is doing the work; tapping
                            // would just enqueue a duplicate).
                            // `enabled = false` also greys out the
                            // chip so it visually reads as disabled,
                            // not inert-but-clickable.
                            onClick = { if (chipData.enabled) onRetryTranscription() },
                            enabled = chipData.enabled,
                            label = { Text(chipData.label) },
                            leadingIcon = {
                                Icon(
                                    imageVector = chipData.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = chipData.color.copy(alpha = 0.15f),
                                labelColor = chipData.color,
                                leadingIconContentColor = chipData.color,
                            ),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    Text(
                        text = course.title,
                        // titleMedium (16sp) → titleSmall (14sp): kept
                        // in lock-step with ContinueLearningCard and
                        // CourseGroupItem so the courses area type
                        // system stays consistent end-to-end.
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = course.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text(course.difficulty) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                        if (hasAudio) {
                            Icon(
                                imageVector = Icons.Outlined.Headphones,
                                contentDescription = "Has audio",
                                modifier = Modifier.size(16.dp),
                                // §12.32c: small capability icons
                                // (headphones / video / mic) lightened
                                // to violet-400 along with the rest of
                                // the brand purple elements on this
                                // screen.
                                tint = Color(0xFFA78BFA),
                            )
                        }
                        if (hasVideo) {
                            Icon(
                                imageVector = Icons.Outlined.VideoFile,
                                contentDescription = "Has video",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFA78BFA),
                            )
                        }
                        if (hasSubtitles) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "Has subtitles",
                                modifier = Modifier.size(16.dp),
                                tint = Color(0xFFA78BFA),
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = formatDuration(course.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(min = 60.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                FilledIconButton(
                    onClick = onClick,
                    modifier = Modifier.size(48.dp),
                    // (2026-07-04) Reverted the per-lesson "Start"
                    // play button back to M3's default
                    // `primary` (deep violet-600 #7C3AED) container
                    // + `onPrimary` (white) content. The user
                    // preferred the deeper brand purple over the
                    // §12.32c lightened violet-400 (#A78BFA) — the
                    // latter blended with the rest of the screen
                    // and made the CTA read as decorative rather
                    // than actionable.
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            }
        }
    }
}

/**
 * Pick an accent color for the difficulty tier. A* → lighter purple
 * (violet-400), B* → lighter purple (violet-500), C* → secondary
 * (violet-500). Unknown strings fall back to violet-400 so the bar
 * is always visible. The three difficulty tiers stay distinguishable
 * through the three brand-purple shades.
 *
 * Marked @Composable because [MaterialTheme.colorScheme] is only readable
 * inside the composition.
 */
@Composable
private fun accentColorFor(difficulty: String): Color = when {
    difficulty.startsWith("A", ignoreCase = true) ->
        Color(0xFFA78BFA)
    difficulty.startsWith("B", ignoreCase = true) ->
        Color(0xFF8B5CF6)
    difficulty.startsWith("C", ignoreCase = true) ->
        MaterialTheme.colorScheme.secondary
    else -> Color(0xFFA78BFA)
}

private fun formatDuration(ms: Long): String {
    if (ms == 0L) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * (2026-07-16) Per-state chip payload for the auto-subtitle status
 * indicator. Pulled out of the `when` arm so the Compose layer can
 * pass it into the chip without recomputing inside the composable
 * tree.
 */
private data class ChipData(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: androidx.compose.ui.graphics.Color,
    val enabled: Boolean,
)