package com.echoling.app.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * "Continue learning" hero card — the visual centerpiece of the Courses
 * tab. Sits at the top of the scroll, above [StatsSummaryCard], and shows
 * the course the user most recently started so they can resume in one tap.
 *
 * Visual recipe (see CLAUDE.md §12.16):
 * - Diagonal gradient from [primary] (top-left) to [tertiary] (bottom-right)
 * - Two faint translucent white radial orbs (top-right + bottom-left) for
 *   depth without distracting from the content
 * - 24dp rounded corners, 4dp tonal elevation, 20dp internal padding
 * - "继续学习" eyebrow row (PlayCircle icon + label, white 85%)
 * - Course title (titleMedium 16sp SemiBold — same as [CourseListItem], white)
 * - 56dp circular play button (white background, primary-purple icon) on
 *   the right of the title row — the visual anchor of the hero
 * - Progress bar with white fill + 25% white track, then "62% 完成" label
 *
 * Tapping the card or the play button calls [onClick]. Visual contrast is
 * intentional: the deep-purple gradient hero is the only saturated card
 * on the page; [StatsSummaryCard] below stays in `primaryContainer` so
 * the eye lands here first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinueLearningCard(
    title: String,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    ),
                ),
        ) {
            // Top-right translucent orb — gives the hero a sense of depth
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(160.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                            radius = 260f,
                        ),
                    ),
            )
            // Bottom-left smaller orb — balances the composition
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(110.dp)
                    .offset(x = (-20).dp, y = 30.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent,
                            ),
                            radius = 200f,
                        ),
                    ),
            )

            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White.copy(alpha = 0.85f),
                    )
                    Text(
                        text = "继续学习",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = title,
                        // Match the course-list item title (titleMedium
                        // 16sp SemiBold) so the two surfaces feel like the
                        // same card system rather than two unrelated ones.
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    FilledIconButton(
                        onClick = onClick,
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircle,
                            contentDescription = "Start",
                            modifier = Modifier.size(30.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = progress.coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${(progress * 100).toInt()}% 完成",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}