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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echoling.app.R
import com.echoling.app.presentation.ui.components.PageHeader
import com.echoling.app.presentation.viewmodel.AutoTranscriptionPhase
import com.echoling.app.presentation.viewmodel.ImportState
import com.echoling.app.presentation.viewmodel.ImportViewModel

// (2026-06-28) Difficulty values kept in English internally so:
//   1. Existing Room rows persist with the original English strings
//      (Beginner/Intermediate/Advanced) and display identically in
//      [CourseListItem]'s difficulty chip (line ~147: `Text(course.difficulty)`)
//   2. [CourseListItem]'s accent-bar tier detection
//      (`accentColorFor(difficulty).startsWith("A"/"B"/"C")`) keeps
//      matching existing rows
//   3. iOS-side Course.difficulty stays a freeform String — no
//      schema-level coupling either way
// The dropdown UI now shows the Chinese `displayName` and writes
// the English `value` to the DB.
private data class DifficultyOption(val value: String, val displayName: String)
private val difficultyOptions = listOf(
    DifficultyOption("Beginner", "初级"),
    DifficultyOption("Intermediate", "中级"),
    DifficultyOption("Advanced", "高级"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onNavigateBack: () -> Unit,
    onImportComplete: () -> Unit,
    /**
     * When the user opens import from a category-detail sub-page
     * (§12.19) this is the parent group name — the form's "素材名字"
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
    var selectedDifficulty by remember { mutableStateOf("Intermediate") }  // English value, displayed via displayName lookup
    var dropdownExpanded by remember { mutableStateOf(false) }
    var isAudioPickerLoading by remember { mutableStateOf(false) }
    var isVideoPickerLoading by remember { mutableStateOf(false) }
    var isSubtitlePickerLoading by remember { mutableStateOf(false) }

    val importState by viewModel.importState.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // (2026-07-16) Auto-subtitle plumbing. The card is shown only
    // when the user has selected audio/video (so there's something
    // to transcribe) but no subtitle file yet. Once they pick a
    // subtitle, the manual flow wins and the card disappears.
    val autoPhase by viewModel.autoTranscriptionPhase.collectAsState()
    val autoProgress by viewModel.autoTranscriptionProgress.collectAsState()

    val canShowAutoSubtitleCard =
        (audioUri != null || videoUri != null) && subtitleUri == null

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
                        text = "导入素材",
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
                    // (2026-07-04) Shrunk further: §12.21 took it
                    // from headlineSmall 24sp → titleLarge 22sp. The
                    // user said it still felt like "page inside a
                    // page" compared with the rest of the form, so
                    // dropped another notch to titleMedium 16sp —
                    // reads as a section label rather than a page
                    // heading. Description below stays bodyMedium
                    // (14sp), keeping clear hierarchy headline →
                    // helper text.
                    Text(
                        text = "导入你的素材",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "添加音频、视频和字幕文件，创建学习素材",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // (2026-07-04) Removed the 8dp Spacer that used to live
                // here. Combined with Arrangement.spacedBy(20.dp) the
                // header→form gap was 28dp, which the user said read
                // as "detached" — the description and the first OTF
                // felt like separate groups. The Arrangement's 20dp is
                // already plenty for visual separation; dropping the
                // extra Spacer pulls the form up and lets the
                // description and 素材分组名 row read as one block.

                // Course group name (parent folder, §12.19). Required:
                // blank values fall back to the lesson title at the
                // ViewModel layer, but the button stays disabled until
                // the user fills *something* in (see button enabled=
                // expression at the bottom of the file).
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("素材分组名（父目录）") },
                    placeholder = { Text("例如：摩登家庭第一季") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = importState != ImportState.IMPORTING,
                    shape = RoundedCornerShape(12.dp)
                )

                // Course title (per-lesson name)
                OutlinedTextField(
                    value = courseTitle,
                    onValueChange = { courseTitle = it },
                    label = { Text("素材标题") },
                    placeholder = { Text("例如：第 1 课") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = importState != ImportState.IMPORTING,
                    shape = RoundedCornerShape(12.dp)
                )

                // Audio file selector
                FileSelectorCard(
                    title = "音频文件（可选）",
                    subtitle = audioUri?.lastPathSegment ?: "选择音频文件",
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
                    title = "视频文件（可选）",
                    subtitle = videoUri?.lastPathSegment ?: "选择视频文件",
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
                    title = "字幕文件（可选）",
                    subtitle = subtitleUri?.lastPathSegment ?: "选择字幕文件",
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

                // (2026-07-16) Auto-subtitle card. Only visible when
                // a media file is selected but no subtitle file yet —
                // the user gets a single CTA surface for "立即转字幕"
                // (block until SRT is ready) vs "稍后转字幕" (enqueue
                // and let the chip on the course list track progress).
                if (canShowAutoSubtitleCard) {
                    AutoSubtitleCard(
                        phase = autoPhase,
                        progress = autoProgress,
                        onImmediate = {
                            if (courseTitle.isNotBlank() && courseName.isNotBlank()) {
                                viewModel.importCourseWithImmediateTranscription(
                                    courseName = courseName,
                                    title = courseTitle,
                                    difficulty = selectedDifficulty,
                                    audioUri = audioUri,
                                    videoUri = videoUri,
                                )
                            }
                        },
                        onDeferred = {
                            if (courseTitle.isNotBlank() && courseName.isNotBlank()) {
                                viewModel.importCourseWithDeferredTranscription(
                                    courseName = courseName,
                                    title = courseTitle,
                                    difficulty = selectedDifficulty,
                                    audioUri = audioUri,
                                    videoUri = videoUri,
                                )
                            }
                        },
                    )
                }

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
                            // Display Chinese label, store English value.
                            // Lookup via `firstOrNull { it.value == selectedDifficulty }`
                            // — the option list is tiny (3 entries) so linear scan is fine.
                            value = difficultyOptions
                                .firstOrNull { it.value == selectedDifficulty }
                                ?.displayName ?: selectedDifficulty,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("难度") },
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
                                    text = { Text(option.displayName) },
                                    onClick = {
                                        selectedDifficulty = option.value
                                        dropdownExpanded = false
                                    },
                                    leadingIcon = if (option.value == selectedDifficulty) {
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
                                text = errorMessage ?: "导入失败",
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
                            ImportState.IMPORTING -> "导入中..."
                            else -> "导入素材"
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
                    text = if (isLoading) "正在打开文件选择器..." else subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = "已选择",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * (2026-07-16) Auto-subtitle entry card. Shows on ImportScreen when a
 * media file is selected but no subtitle file is — gives the user a
 * single surface with two paths:
 *
 *  - IDLE: two side-by-side buttons (immediate vs deferred).
 *  - EXTRACTING / TRANSCRIBING / SYNTHESIZING: progress + percent.
 *  - COMPLETED: confirmation checkmark + brief message; the screen
 *    then transitions out via ImportState.SUCCESS.
 *
 * Body text is the same across phases; only the action area changes.
 * This keeps the card height stable and avoids layout shift during
 * the 4-step pipeline.
 */
@Composable
private fun AutoSubtitleCard(
    phase: AutoTranscriptionPhase,
    progress: Int,
    onImmediate: () -> Unit,
    onDeferred: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            // Header: icon + title row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.auto_subtitle_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.auto_subtitle_card_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            when (phase) {
                AutoTranscriptionPhase.IDLE -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = onDeferred,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.auto_subtitle_btn_deferred))
                        }
                        Button(
                            onClick = onImmediate,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Text(stringResource(R.string.auto_subtitle_btn_immediate))
                        }
                    }
                }
                AutoTranscriptionPhase.EXTRACTING,
                AutoTranscriptionPhase.TRANSCRIBING,
                AutoTranscriptionPhase.SYNTHESIZING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(
                                R.string.auto_subtitle_progress_format,
                                progress,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                AutoTranscriptionPhase.COMPLETED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "字幕已生成",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
