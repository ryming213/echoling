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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .scale(animatedScale)
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = rememberRipple(),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
    ) {
        // IntrinsicSize.Min lets the accent bar fillMaxHeight() to the
        // card's natural height without forcing a fixed dp value.
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
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.titleMedium,
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
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (hasVideo) {
                            Icon(
                                imageVector = Icons.Outlined.VideoFile,
                                contentDescription = "Has video",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (hasSubtitles) {
                            Icon(
                                imageVector = Icons.Outlined.Mic,
                                contentDescription = "Has subtitles",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
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

/**
 * Pick an accent color for the difficulty tier. A* → primary purple,
 * B* → tertiary deeper purple, C* → secondary. Unknown strings fall back
 * to primary so the bar is always visible.
 *
 * Marked @Composable because [MaterialTheme.colorScheme] is only readable
 * inside the composition.
 */
@Composable
private fun accentColorFor(difficulty: String): Color = when {
    difficulty.startsWith("A", ignoreCase = true) ->
        MaterialTheme.colorScheme.primary
    difficulty.startsWith("B", ignoreCase = true) ->
        MaterialTheme.colorScheme.tertiary
    difficulty.startsWith("C", ignoreCase = true) ->
        MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

private fun formatDuration(ms: Long): String {
    if (ms == 0L) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}