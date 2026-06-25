package com.echoling.app.presentation.ui.screens.import

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echoling.app.presentation.ui.components.PageHeader
import com.echoling.app.presentation.viewmodel.ImportState
import com.echoling.app.presentation.viewmodel.ImportViewModel

private val difficultyOptions = listOf("Beginner", "Intermediate", "Advanced")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit,
    /**
     * When the user opens import from a category-detail sub-page
     * (§12.19) this is the parent group name — the form's "课程名字"
     * field is seeded with it so the user only has to type the per-
     * lesson title. Null when launched from the home page.
     */
    prefillCourseName: String? = null,
    viewModel: ImportViewModel = hiltViewModel()
) {
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var courseName by remember(prefillCourseName) {
        mutableStateOf(prefillCourseName.orEmpty())
    }
    var courseTitle by remember { mutableStateOf("") }
    var selectedDifficulty by remember { mutableStateOf("Intermediate") }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var isAudioPickerLoading by remember { mutableStateOf(false) }
    var isVideoPickerLoading by remember { mutableStateOf(false) }
    var isSubtitlePickerLoading by remember { mutableStateOf(false) }

    val importState by viewModel.importState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        audioUri = uri
        isAudioPickerLoading = false
    }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        videoUri = uri
        isVideoPickerLoading = false
    }

    val subtitlePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        subtitleUri = uri
        isSubtitlePickerLoading = false
    }

    LaunchedEffect(importState) {
        if (importState == ImportState.SUCCESS) {
            onImportComplete()
        }
    }

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
                // §12.21: left-align the sub-page title (brand-bar
                // tabs Courses / Me stay centered by default).
                titleAlignment = Alignment.Start,
                title = {
                    Text(
                        text = "Import Course",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
            // Scrollable form area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Header
                Column {
                    // §12.21: shrunk the headline from
                    // `headlineSmall` (24sp) to `titleLarge` (22sp) —
                    // visually similar weight but a bit smaller,
                    // matching the rest of the form's font scale
                    // and avoiding the "page inside a page" feel.
                    Text(
                        text = "Import Your Course",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add audio/video and subtitle files to create a learning course",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Course group name (parent folder, §12.19). Required:
                // blank values fall back to the lesson title at the
                // ViewModel layer, but the button stays disabled until
                // the user fills *something* in (see button enabled=
                // expression at the bottom of the file).
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("Course Name (课程名字)") },
                    placeholder = { Text("e.g. 新概念英语第一册") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = importState != ImportState.IMPORTING,
                    shape = RoundedCornerShape(12.dp)
                )

                // Course title (per-lesson name)
                OutlinedTextField(
                    value = courseTitle,
                    onValueChange = { courseTitle = it },
                    label = { Text("Course Title (课程标题)") },
                    placeholder = { Text("e.g. Lesson 1") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = importState != ImportState.IMPORTING,
                    shape = RoundedCornerShape(12.dp)
                )

                // Audio file selector
                FileSelectorCard(
                    title = "Audio File (Optional)",
                    subtitle = audioUri?.lastPathSegment ?: "Select audio file",
                    icon = Icons.Outlined.AudioFile,
                    isSelected = audioUri != null,
                    isLoading = isAudioPickerLoading,
                    onClick = {
                        if (importState != ImportState.IMPORTING) {
                            isAudioPickerLoading = true
                            audioPickerLauncher.launch(arrayOf("audio/*"))
                        }
                    },
                    enabled = importState != ImportState.IMPORTING
                )

                // Video file selector
                FileSelectorCard(
                    title = "Video File (Optional)",
                    subtitle = videoUri?.lastPathSegment ?: "Select video file",
                    icon = Icons.Outlined.VideoFile,
                    isSelected = videoUri != null,
                    isLoading = isVideoPickerLoading,
                    onClick = {
                        if (importState != ImportState.IMPORTING) {
                            isVideoPickerLoading = true
                            videoPickerLauncher.launch(arrayOf("video/*"))
                        }
                    },
                    enabled = importState != ImportState.IMPORTING
                )

                // Subtitle file selector
                FileSelectorCard(
                    title = "Subtitle File (Optional)",
                    subtitle = subtitleUri?.lastPathSegment ?: "Select subtitle file",
                    icon = Icons.Outlined.Description,
                    isSelected = subtitleUri != null,
                    isLoading = isSubtitlePickerLoading,
                    onClick = {
                        if (importState != ImportState.IMPORTING) {
                            isSubtitlePickerLoading = true
                            subtitlePickerLauncher.launch(arrayOf("text/plain", "application/x-subrip", "*/*"))
                        }
                    },
                    enabled = importState != ImportState.IMPORTING
                )

                // Difficulty selector
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = {
                            if (importState != ImportState.IMPORTING) {
                                dropdownExpanded = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedDifficulty,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Difficulty") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            enabled = importState != ImportState.IMPORTING,
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            difficultyOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        selectedDifficulty = option
                                        dropdownExpanded = false
                                    },
                                    leadingIcon = if (option == selectedDifficulty) {
                                        {
                                            Icon(
                                                Icons.Default.AudioFile,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else null
                                )
                            }
                        }
                    }
                }

                // Error message
                if (importState == ImportState.ERROR) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = errorMessage ?: "Import failed",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Pinned import button at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = {
                        val hasMedia = audioUri != null || videoUri != null
                        if (hasMedia && courseTitle.isNotBlank() && courseName.isNotBlank()) {
                            viewModel.importCourse(
                                courseName = courseName,
                                title = courseTitle,
                                difficulty = selectedDifficulty,
                                audioUri = audioUri,
                                videoUri = videoUri,
                                subtitleUri = subtitleUri
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = (audioUri != null || videoUri != null) &&
                            courseTitle.isNotBlank() &&
                            courseName.isNotBlank() &&
                            importState != ImportState.IMPORTING,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (importState == ImportState.IMPORTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Upload, contentDescription = null)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (importState) {
                            ImportState.IMPORTING -> "Importing..."
                            else -> "Import Course"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSelectorCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    isLoading: Boolean = false,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled && !isLoading,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isLoading) "Opening file picker..." else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
