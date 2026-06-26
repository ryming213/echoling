package com.echoling.app.presentation.ui.screens.practice.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Post-recording panel shown after the user releases the mic.
 *
 * Per user spec, the panel is intentionally minimal: the recognized
 * text is hidden by default and only revealed when the user clicks
 * the "显示文字" button at the bottom-right. The recognized text,
 * once shown, is itself clickable to submit for matching. The
 * "手动修改" button at the center-bottom switches the content area
 * to an editable OutlinedTextField for manual entry.
 *
 * Three buttons at the bottom in a fixed layout (always the same
 * labels, matching the user's spec for left/center/right positions):
 * - **重录** (bottom-left): re-record (calls [onRerecord])
 * - **手动修改** (center-bottom): enter edit mode to manually type
 * - **显示文字** (bottom-right): show the recognized text
 *
 * Display states inside the card:
 * - **Idle**: placeholder text, no recognized text shown
 * - **Showing**: recognized text rendered inside a clickable
 *   Surface — clicking the text submits it
 * - **Editing**: OutlinedTextField + 确认提交 button below
 *
 * @param initialText the STT-recognized text (may be blank)
 * @param onTextChange kept for API compatibility — not used internally
 * @param onSubmit called with the final text to submit for matching
 * @param onRerecord called when the user taps 重录
 */
@Composable
fun TranscriptionEditor(
    initialText: String,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRerecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showText by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    var editedText by remember { mutableStateOf(initialText) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "录音完成",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))

            when {
                isEditing -> {
                    // Editing mode: editable text field + 确认提交 below
                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        placeholder = { Text("输入你复述的句子") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 96.dp),
                        minLines = 3
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { onSubmit(editedText) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = editedText.isNotBlank()
                    ) {
                        Text("确认提交")
                    }
                }
                showText -> {
                    // Showing mode: recognized text inside a clickable Surface.
                    // Clicking the text submits the original STT result.
                    val display = initialText.ifBlank { "（未识别到语音）" }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSubmit(initialText) }
                    ) {
                        Text(
                            display,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点击文字提交，或点击「手动修改」",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    // Idle mode: no text shown yet, just a hint
                    Text(
                        "点击右下角「显示文字」查看识别结果",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Fixed 3-button row: 重录 (left) / 手动修改 (center) / 显示文字 (right)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onRerecord) {
                    Text("重录")
                }
                OutlinedButton(
                    onClick = {
                        if (!isEditing) {
                            editedText = initialText
                            isEditing = true
                        }
                    },
                    enabled = !isEditing
                ) {
                    Text("手动修改")
                }
                Button(
                    onClick = { showText = true },
                    enabled = !showText
                ) {
                    Text("显示文字")
                }
            }
        }
    }
}
