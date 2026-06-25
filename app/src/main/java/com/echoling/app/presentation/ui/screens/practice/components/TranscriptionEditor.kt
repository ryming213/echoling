package com.echoling.app.presentation.ui.screens.practice.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Editable transcription card shown after STT returns results.
 * User can edit the text, re-record, or submit for comparison.
 */
@Composable
fun TranscriptionEditor(
    initialText: String,
    onTextChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onRerecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(initialText) }
    LaunchedEffect(initialText) { text = initialText }

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "你说的是：",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { onTextChange(it); text = it },
                placeholder = { Text("未识别到语音，请手动输入") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp),
                minLines = 3
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onRerecord) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("重录")
                }
                Button(
                    onClick = { onSubmit(text) },
                    enabled = text.isNotBlank()
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("提交对比")
                }
            }
        }
    }
}
