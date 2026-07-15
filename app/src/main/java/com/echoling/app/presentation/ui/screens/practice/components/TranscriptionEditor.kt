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
 * Recognized text is displayed immediately on entry — no "reveal"
 * step. The displayed text is **clickable to enter edit mode** for
 * manual correction; submitting happens via the bottom-right
 * 提交 button.
 *
 * (2026-06-28) UI flow was simplified per user feedback:
 *   - **Old**: click text → submit immediately; click 手动修改 →
 *     enter edit mode; then click 提交 to submit.
 *   - **New**: click text → enter edit mode (OutlinedTextField);
 *     type / leave as-is; click 提交 to submit. The 手动修改
 *     button was redundant with the text-click affordance and
 *     has been removed.
 *
 * The 提交 button stays because edit mode needs a way to commit
 * the final text. Re-record via 重录 on the left.
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

            if (isEditing) {
                // Editing mode: editable text field. User types /
                // pastes / leaves as-is, then taps 提交 below.
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    placeholder = { Text("输入你复述的句子") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp),
                    minLines = 3
                )
            } else {
                // Showing mode (default): recognized text inside a
                // clickable Surface. Clicking the text now enters
                // edit mode (not submits directly). The hint text
                // was reworded accordingly.
                val display = initialText.ifBlank { "（未识别到语音）" }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Seed editedText with the current
                            // recognized text so the OutlinedTextField
                            // opens with the STT result pre-filled.
                            editedText = initialText
                            isEditing = true
                        }
                ) {
                    Text(
                        display,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "点击文字修改",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            // Bottom row: 重录 on the left, 提交 on the right. The
            // 手动修改 button was removed (text click now enters
            // edit mode directly). 提交 is the only way to commit.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onRerecord) {
                    Text("重录")
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = {
                        onSubmit(if (isEditing) editedText else initialText)
                    },
                    enabled = (if (isEditing) editedText else initialText).isNotBlank(),
                ) {
                    Text("提交")
                }
            }
        }
    }
}