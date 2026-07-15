package com.echoling.app.presentation.ui.screens.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echoling.app.presentation.ui.components.PageHeader
import com.echoling.app.presentation.viewmodel.DailyStatistic
import com.echoling.app.presentation.viewmodel.StatisticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        // No topBar — back + title in PageHeader (§12.18)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PageHeader(
                onBack = onNavigateBack,
                // §12.21: left-align the sub-page title (the brand-
                // bar tabs Courses / Me stay centered by default).
                titleAlignment = Alignment.Start,
                title = {
                    Text(
                        text = "Learning Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        // §12.21: verticalScroll so the user can
                        // see the 7-day chart (and the rest of the
                        // content) on smaller phones where the
                        // 4 stat cards + streak + 30-day heatmap
                        // already exceed the first screen.
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                // Streak card
                StreakCard(streak = uiState.currentStreak)

                // Stats grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Schedule,
                        value = formatDuration(uiState.totalLearnTimeMs),
                        label = "Total Time"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.School,
                        value = uiState.totalCoursesLearned.toString(),
                        label = "Courses"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.MenuBook,
                        value = uiState.totalWordsCollected.toString(),
                        label = "Words"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Star,
                        value = uiState.totalWordsMastered.toString(),
                        label = "Mastered"
                    )
                }

                // (2026-07-04) Re-ordered: weekly 7-day bar chart now
                // sits ABOVE the 30-day heatmap so the user sees the
                // most-recent / most-actionable view first ("how did
                // this week go?"), then the longer context below
                // ("how did the month look?"). Previously the 30-day
                // heatmap was on top per §12.21's "lands within first
                // screen" rationale — that priority no longer wins
                // over the recency-first reading order.
                WeeklyActivityChart(dailyStats = uiState.dailyStats)

                // §12.21: monthly activity (last 30 days) — rendered
                // as a calendar heatmap (GitHub-style). Now below the
                // 7-day bar chart; the heatmap is the longer context
                // view, and the bars give the precise per-day numbers.
                MonthlyActivityChart(monthlyStats = uiState.monthlyStats)
            }
            }
        }
    }
}

@Composable
private fun StreakCard(streak: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (streak > 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "$streak day${if (streak != 1) "s" else ""}",
                    // (2026-07-04) was headlineMedium (28sp) — too
                    // dominant against the 48dp LocalFireDepartment
                    // icon. Dropped one step to headlineSmall (24sp)
                    // so the streak number shares visual weight with
                    // the icon instead of dwarfing it.
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "Current streak",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                // (2026-07-04) was headlineSmall (24sp) — the four
                // stat cards' big numbers dominated the page. Dropped
                // one step to titleLarge (22sp) so each value still
                // reads as the focal point but doesn't overpower the
                // bodySmall (12sp) label underneath.
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeeklyActivityChart(dailyStats: List<DailyStatistic>) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Last 7 Days",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(dailyStats) { stat ->
                    DayStatItem(stat = stat, maxTimeMs = dailyStats.maxOfOrNull { it.learnTimeMs } ?: 1L)
                }
            }
        }
    }
}

/**
 * 30-day activity rendered as a calendar heatmap (GitHub-style).
 *
 * Why heatmap instead of bars:
 * - 30 thin bars (the old design) were 6dp wide — at any normal
 *   screen density that's barely a vertical line, so day-to-day
 *   variation was invisible. A heatmap gives each day a real cell
 *   (~32dp) with both a day-of-month number and a color, which makes
 *   the data scannable at a glance.
 * - 30 days don't divide evenly into 7-day weeks, but the user
 *   thinks in weeks. We pad the first row with empty cells so the
 *   first active day lands in its correct Mon–Sun column, then fill
 *   the rest of the grid. The user sees a proper week layout, with
 *   the 30-day window "snapped" to the calendar.
 * - Color intensity (5 levels) is denser information than bar
 *   height — you can read both "how much" and "which days were
 *   active" from a single glance.
 *
 * Today is highlighted with a 2dp primary border so the user can
 * find the right end of the timeline immediately.
 */
@Composable
private fun MonthlyActivityChart(monthlyStats: List<DailyStatistic>) {
    val totalMs = monthlyStats.sumOf { it.learnTimeMs }
    val activeDays = monthlyStats.count { it.learnTimeMs > 0 }
    val maxTimeMs = monthlyStats.maxOfOrNull { it.learnTimeMs } ?: 1L

    // Compute the 5-week × 7-day grid (35 cells, 4..5 of which may
    // be empty padding on the first or last row depending on where
    // "30 days back" lands in its week). [remember] keyed on the
    // stats identity so it doesn't recompute on every recomposition.
    val grid = remember(monthlyStats) { buildMonthlyHeatGrid(monthlyStats) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Last 30 Days",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "$activeDays 天 · ${formatDuration(totalMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Day-of-week header row — Chinese "一二三四五六日" so the
            // first column is Monday, matching the calendar
            // convention in the app's primary locale.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { d ->
                    Text(
                        text = d,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))

            // 5 weeks of 7 days. Each cell is `weight(1f)` so they
            // divide the row width evenly, and `aspectRatio(1f)` so
            // they stay square. The 4dp spacedBy matches the header
            // row's spacing so the columns line up vertically.
            grid.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    row.forEach { cell ->
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f)) {
                            if (cell != null) {
                                MonthlyHeatCell(
                                    dayOfMonth = cell.dayOfMonth,
                                    learnTimeMs = cell.learnTimeMs,
                                    maxTimeMs = maxTimeMs,
                                    isToday = cell.isToday,
                                )
                            }
                            // Empty cells (padding at the start of
                            // row 1, or the end of the last row)
                            // render as nothing — the parent Box
                            // takes the same space so the grid stays
                            // aligned.
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Color legend — explains the 5-level intensity scale
            // (None / Low / Medium / High / Max) so the user knows
            // that darker = more study time.
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "少",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { alpha ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 1.5.dp)
                            .size(12.dp)
                            .background(
                                color = if (alpha == 0f) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                                },
                                shape = RoundedCornerShape(3.dp),
                            ),
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "多",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Single cell of the 30-day heatmap. Cell color encodes the
 * learn-time intensity; the day-of-month number sits centered inside
 * so the user can read both at once.
 *
 * Today is rendered specially so the user can spot it at a glance:
 * the rest of the grid is intensity-based, but today gets a fixed
 * filled primary background, a larger bold white number, and a small
 * "今天" label below the number. This makes "where am I on this
 * timeline?" answerable in one glance — the user does not have to
 * compare each cell's day number against today's date in their head,
 * and it stays unambiguous even when a day number (e.g. "1") appears
 * more than once across a month boundary.
 */
@Composable
private fun MonthlyHeatCell(
    dayOfMonth: Int,
    learnTimeMs: Long,
    maxTimeMs: Long,
    isToday: Boolean,
) {
    val ratio = if (learnTimeMs > 0 && maxTimeMs > 0) {
        (learnTimeMs.toFloat() / maxTimeMs).coerceIn(0f, 1f)
    } else 0f

    // Today uses a soft primaryContainer fill + larger bold dark-violet
    // number, regardless of learn time. This makes today easy to find
    // and removes the need to interpret color intensity on that cell,
    // while keeping visual weight in the same family as the rest of
    // the grid (violet-200 instead of full primary).
    // Other cells: gradient intensity by learn-time ratio.
    val fillColor = if (isToday) {
        MaterialTheme.colorScheme.primaryContainer
    } else when {
        ratio == 0f -> MaterialTheme.colorScheme.surfaceVariant
        ratio < 0.25f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        ratio < 0.5f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        ratio < 0.75f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.primary
    }

    // Today is dark-violet-on-light-violet (primaryContainer / onPrimaryContainer
    // pair from §11.2 brand palette). For other cells, white text
    // on the two darkest intensity levels for contrast.
    val numberColor = if (isToday) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else when {
        ratio >= 0.5f -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Bump today's number up to titleSmall (14sp) Bold; regular
    // cells use labelMedium (12sp) — both larger than the previous
    // labelSmall (11sp) so the day numbers are easy to read in a
    // ~43dp cell.
    val numberStyle = if (isToday) {
        MaterialTheme.typography.titleSmall
    } else {
        MaterialTheme.typography.labelMedium
    }
    val numberFontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(6.dp))
            .background(fillColor),
        contentAlignment = Alignment.Center,
    ) {
        if (isToday) {
            // Today: number + "今天" label, both centered. The label
            // is the unambiguous "you are here" marker — even if the
            // user can read the day number, the text spells it out
            // so there is zero ambiguity.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = dayOfMonth.toString(),
                    style = numberStyle,
                    color = numberColor,
                    fontWeight = numberFontWeight,
                )
                Text(
                    text = "今天",
                    style = MaterialTheme.typography.labelSmall,
                    color = numberColor.copy(alpha = 0.85f),
                )
            }
        } else {
            Text(
                text = dayOfMonth.toString(),
                style = numberStyle,
                color = numberColor,
                fontWeight = numberFontWeight,
            )
        }
    }
}

/**
 * Convert the 30-entry [monthlyStats] (index 0 = today) into a
 * 5-week × 7-day grid of cells. The first row is padded with `null`
 * entries so the first active day lands in the correct Mon–Sun
 * column based on its actual day-of-week. The last row may also be
 * padded if the 30-day window doesn't reach the end of a week.
 *
 * Day-of-month numbers are computed from the index (0 = today, i =
 * today minus i days) so the cell text reads naturally ("22" for
 * "30 days ago" if today is the 21st).
 */
private data class HeatCell(
    val dayOfMonth: Int,
    val learnTimeMs: Long,
    val isToday: Boolean,
)

private fun buildMonthlyHeatGrid(
    monthlyStats: List<DailyStatistic>,
    todayMillis: Long = System.currentTimeMillis(),
): List<List<HeatCell?>> {
    if (monthlyStats.isEmpty()) return emptyList()

    // Day-of-week of "30 days ago" (the last item in the stats list).
    // We convert Calendar.SUNDAY=1..SATURDAY=7 to Mon=0..Sun=6.
    val firstStatCal = java.util.Calendar.getInstance().apply {
        timeInMillis = todayMillis
        add(java.util.Calendar.DAY_OF_YEAR, -(monthlyStats.size - 1))
    }
    val calDow = firstStatCal.get(java.util.Calendar.DAY_OF_WEEK)
    val firstCol = ((calDow - java.util.Calendar.MONDAY + 7) % 7)

    // Build the cells list, then chunk into rows of 7.
    val cells = mutableListOf<HeatCell?>()
    repeat(firstCol) { cells.add(null) }

    // Iterate from oldest (index = size-1) to newest (index = 0) so
    // the cells land in chronological order. The stats list from
    // the ViewModel is indexed 0=today, ..., 29=29-days-ago — if we
    // iterated it in natural order, today would land in the column
    // of "29 days ago" instead of its actual day-of-week column,
    // making "21" appear in the Saturday slot when today is actually
    // a Sunday. Reversing the iteration puts the oldest day in the
    // first available cell (after the leading padding) and today at
    // the bottom-right of the grid — the "end of the timeline"
    // position, which matches the calendar's left-to-right,
    // top-to-bottom reading order.
    monthlyStats.indices.reversed().forEach { index ->
        val stat = monthlyStats[index]
        val cellCal = java.util.Calendar.getInstance().apply {
            timeInMillis = todayMillis
            add(java.util.Calendar.DAY_OF_YEAR, -index)
        }
        cells.add(
            HeatCell(
                dayOfMonth = cellCal.get(java.util.Calendar.DAY_OF_MONTH),
                learnTimeMs = stat.learnTimeMs,
                isToday = index == 0,
            )
        )
    }

    // Pad the tail so the grid is exactly 5 rows × 7 cols.
    while (cells.size < 35) { cells.add(null) }

    return cells.chunked(7)
}

@Composable
private fun DayStatItem(stat: DailyStatistic, maxTimeMs: Long) {
    val maxHeight = 80.dp
    val barHeight = if (stat.learnTimeMs > 0) {
        val ratio = (stat.learnTimeMs.toFloat() / maxTimeMs.coerceAtLeast(1)).coerceIn(0.1f, 1f)
        maxHeight * ratio
    } else {
        4.dp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stat.date,
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(maxHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight),
                shape = MaterialTheme.shapes.small,
                color = if (stat.learnTimeMs > 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ) {}
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatShortDuration(stat.learnTimeMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms == 0L) return "0m"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatShortDuration(ms: Long): String {
    val totalMinutes = ms / 60000
    return if (totalMinutes >= 60) "${totalMinutes / 60}h" else "${totalMinutes}m"
}
