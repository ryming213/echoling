package com.echoling.app.presentation.ui.screens.practice.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.echoling.app.domain.model.ScoreResult

/**
 * Displays pronunciation grading result with replay/regrade/next actions.
 *
 * Stub — implementation pending.
 */
@Composable
fun ScoreCard(
    result: ScoreResult,
    onReplayRecording: () -> Unit,
    onRegrade: () -> Unit,
    onNextItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "评分: ${result.total}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = onReplayRecording) {
                    Text("回放")
                }
                Button(onClick = onRegrade) {
                    Text("重新评分")
                }
                OutlinedButton(onClick = onNextItem) {
                    Text("下一题")
                }
            }
        }
    }
}