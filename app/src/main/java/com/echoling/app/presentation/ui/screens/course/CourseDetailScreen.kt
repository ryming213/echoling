package com.echoling.app.presentation.ui.screens.course

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echoling.app.presentation.ui.components.CompactConfirmDialog
import com.echoling.app.presentation.ui.components.CourseListItem
import com.echoling.app.presentation.ui.components.PageHeader
import com.echoling.app.presentation.viewmodel.CourseDetailViewModel

/**
 * Category-detail screen — repurposed from the old "Start Learning"
 * single-course detail page in §12.19. The route parameter is now the
 * `courseName` group (the user-facing folder name), and the body shows
 * the list of lessons in that group via [CourseListItem]. Tapping a
 * lesson goes **directly to Practice** (no second detail page in
 * between). Long-tap / delete-icon removes the lesson; when the last
 * lesson in a group is deleted, the empty-state hint tells the user
 * the group will disappear on back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    courseName: String,
    onNavigateBack: () -> Unit,
    onNavigateToPractice: (courseId: String) -> Unit,
    /**
     * Navigates to the Import screen with the current group name
     * pre-filled into the form (see §12.19). The user only needs to
     * type the per-lesson title.
     */
    onNavigateToImport: () -> Unit,
    viewModel: CourseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var courseToDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(courseName) {
        viewModel.loadGroup(courseName)
    }

    Scaffold(
        // No topBar — back button + group-name title in PageHeader.
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
                onBack = onNavigateBack,
                title = {
                    Text(
                        text = uiState.courseName.ifBlank { "练习" },
                        // titleMedium (16sp) → titleSmall (14sp): keeps
                        // parity with ContinueLearningCard / CourseGroupItem
                        // / CourseListItem so the courses area uses a
                        // consistent title size top-to-bottom.
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    uiState.error != null -> {
                        Text(
                            text = uiState.error ?: "Unknown error",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    uiState.courses.isEmpty() -> {
                        // Group exists but has no lessons (e.g. the
                        // user just deleted the last one). Show a soft
                        // hint instead of an error — the home page
                        // will drop this group on the next emission.
                        Text(
                            text = "暂无练习",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(uiState.courses, key = { it.courseId }) { course ->
                                CourseListItem(
                                    course = course,
                                    onClick = { onNavigateToPractice(course.courseId) },
                                    onDeleteClick = { courseToDelete = course.courseId },
                                )
                            }
                            // Reserve space at the bottom so the last
                            // CourseListItem doesn't sit underneath the
                            // Extended FAB.
                            item("fab-clear") {
                                Spacer(modifier = Modifier.height(80.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    courseToDelete?.let { id ->
        CompactConfirmDialog(
            title = "删除练习",
            message = "确定要删除这个练习吗？",
            confirmText = "删除",
            dismissText = "取消",
            onConfirm = {
                viewModel.deleteCourse(id)
                courseToDelete = null
            },
            onDismiss = { courseToDelete = null },
        )
    }
}
