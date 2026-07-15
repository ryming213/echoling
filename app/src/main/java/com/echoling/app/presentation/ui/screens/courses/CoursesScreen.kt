package com.echoling.app.presentation.ui.screens.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echoling.app.R
import com.echoling.app.domain.model.CourseGroup
import com.echoling.app.domain.model.effectiveCourseName
import com.echoling.app.presentation.ui.components.ContinueLearningCard
import com.echoling.app.presentation.ui.components.CourseGroupItem
import com.echoling.app.presentation.ui.components.PageHeader
import com.echoling.app.presentation.ui.components.StatsSummaryCard
import com.echoling.app.presentation.viewmodel.CoursesViewModel

/**
 * Root of the Courses tab. Single LazyColumn that scrolls:
 *   1. StatsSummaryCard (study time + words, tappable → statistics)
 *   2. ContinueLearningCard (only if there's a course in progress)
 *   3. Section header
 *   4. List of [CourseGroupItem] — each one is a "folder" of lessons
 *      sharing a courseName; tapping it opens the category detail
 *      page (§12.19) which lists the individual lessons.
 *
 * An Extended FAB anchored bottom-end opens the Import flow. Delete
 * moved off this screen — it now lives on the inner CourseListItem
 * inside the category detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoursesScreen(
    onNavigateToInstructions: () -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToPractice: (courseId: String) -> Unit,
    onNavigateToCategory: (courseName: String) -> Unit,
    onNavigateToImport: () -> Unit,
    viewModel: CoursesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        // §12.30: only consume the top status-bar inset, not the bottom
        // navigation-bar inset. The outer MainScaffold already accounts
        // for the bottom tab bar (it lives in MainScaffold's bottomBar
        // slot and gets folded into innerPadding), so if THIS inner
        // Scaffold ALSO subtracts the nav bar inset, the LazyColumn
        // body ends 24dp shorter than the Pager that hosts it — leaving
        // a 24dp page-colored strip between the tab bar and the last
        // list item. See CLAUDE.md §12.30 for the full chain.
        contentWindowInsets = WindowInsets.statusBars,
        // No topBar — content starts right below the status bar via
        // Scaffold's `padding` parameter (which still includes the status
        // bar inset). The PageHeader at the top of the body holds the
        // brand title (see §12.18).
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToImport,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("导入素材") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PageHeader(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.courses_title),
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(R.string.courses_subtitle),
                            style = androidx.compose.ui.text.TextStyle(
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
                actions = {
                    // §12.21: home page's stats IconButton has been
                    // replaced with a Help / "使用说明" IconButton that
                    // opens the new InstructionsScreen. The user can
                    // still reach the statistics page via the
                    // tappable StatsSummaryCard on the home body.
                    IconButton(onClick = onNavigateToInstructions) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "使用说明")
                    }
                },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    // Only show the empty state once we KNOW there's no
                    // data. Before the first Room emission, `groups` is
                    // the default empty list — flashing "暂无练习" for
                    // that brief window looks like the title + empty
                    // state appear first and the real list arrives a
                    // moment later, which is exactly what we want to
                    // avoid.
                    uiState.hasLoadedOnce && uiState.groups.isEmpty() -> {
                        EmptyCourses(onNavigateToImport = onNavigateToImport)
                    }
                    else -> {
                        CoursesList(
                            totalLearnTimeMs = uiState.totalLearnTimeMs,
                            totalWordsCollected = uiState.totalWordsCollected,
                            // §12.21: prefix the lesson title with the
                            // parent group name so the user knows
                            // "which course is this lesson from?" at
                            // a glance — e.g. "新概念英语第一册 ·
                            // Lesson 1" instead of just "Lesson 1".
                            continueTitle = uiState.continueLearning?.let { item ->
                                val group = item.course.effectiveCourseName
                                val lesson = item.course.title
                                if (group.isNotBlank() && group != lesson) "$group · $lesson"
                                else lesson
                            },
                            continueProgress = uiState.continueLearning?.progress?.finishRate ?: 0f,
                            onContinueClick = {
                                uiState.continueLearning?.let {
                                    onNavigateToPractice(it.course.courseId)
                                }
                            },
                            onStatsClick = onNavigateToStatistics,
                            groups = uiState.groups,
                            onGroupClick = onNavigateToCategory,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoursesList(
    totalLearnTimeMs: Long,
    totalWordsCollected: Int,
    continueTitle: String?,
    continueProgress: Float,
    onContinueClick: () -> Unit,
    onStatsClick: () -> Unit,
    groups: List<CourseGroup>,
    onGroupClick: (courseName: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Stats summary on top — gives the user an at-a-glance "you've
        // studied X for Y" before the "pick up where you left off" CTA.
        item("stats") {
            StatsSummaryCard(
                totalLearnTimeMs = totalLearnTimeMs,
                totalWordsCollected = totalWordsCollected,
                onClick = onStatsClick,
            )
        }
        // Continue-learning card below the stats — the deep-purple gradient
        // hero is the visual anchor and the primary CTA on the page.
        if (continueTitle != null) {
            item("continue") {
                ContinueLearningCard(
                    title = continueTitle,
                    progress = continueProgress,
                    onClick = onContinueClick,
                )
            }
        }
        item("section-header") {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "我的练习",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Group list — each row is a folder of lessons sharing a
        // courseName. Tapping a row opens the category detail screen.
        items(groups, key = { it.courseName }) { group ->
            CourseGroupItem(
                courseName = group.courseName,
                count = group.count,
                onClick = { onGroupClick(group.courseName) },
            )
        }
        item("fab-clear") {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun EmptyCourses(onNavigateToImport: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Headphones,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "暂无练习",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "导入素材开始学习",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onNavigateToImport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.height(8.dp))
                Text("导入素材")
            }
        }
    }
}
