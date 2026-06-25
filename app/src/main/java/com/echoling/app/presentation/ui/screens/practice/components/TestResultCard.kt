package com.echoling.app.presentation.ui.screens.practice.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.echoling.app.presentation.viewmodel.SttTestState

@OptIn(ExperimentalLayoutApi::class)
/**
 * Result card shown after the user submits their transcription.
 * Passed: green card with "下一题" button.
 * Failed: red card with word-by-word comparison chips and "重录" button.
 */
@Composable
fun TestResultCard(
    state: SttTestState,
    originalEn: String,
    onNextItem: () -> Unit,
    onRerecord: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (containerColor, contentColor, title) = when (state) {
        is SttTestState.Passed -> Triple(
            Color(0xFFE8F5E9),
            Color(0xFF1B5E20),
            "✓ 通过！"
        )
        is SttTestState.Failed -> Triple(
            Color(0xFFFFEBEE),
            Color(0xFFB71C1C),
            "✗ 不通过"
        )
        else -> return
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = contentColor)
            Spacer(Modifier.height(8.dp))
            Text(
                "原句：",
                style = MaterialTheme.typography.labelMedium,
                color = contentColor
            )
            Text(originalEn, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            if (state is SttTestState.Failed) {
                Text(
                    "你说：",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
                WordChipsRow(
                    origWords = state.origWords,
                    transWords = state.transWords
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    failureReasonText(state.reason),
                    color = contentColor,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (state is SttTestState.Passed) {
                Text(state.text, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(onClick = onRerecord) {
                    Text("重录")
                }
                if (state is SttTestState.Passed) {
                    Button(onClick = onNextItem) {
                        Text("下一题")
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun WordChipsRow(
    origWords: List<String>,
    transWords: List<String>
) {
    val maxLen = maxOf(origWords.size, transWords.size)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        for (i in 0 until maxLen) {
            val orig = origWords.getOrNull(i)
            val trans = transWords.getOrNull(i)
            val (display, bg) = when {
                trans == null -> "[$orig]" to Color(0xFFFFCDD2)
                orig == null -> "[$trans]" to Color(0xFFFFF9C4)
                orig == trans -> orig to Color(0xFFC8E6C9)
                else -> "$orig/$trans" to Color(0xFFFFCDD2)
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = bg
            ) {
                Text(
                    display,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun failureReasonText(reason: String): String = when (reason) {
    "empty_transcription" -> "未识别到语音，请重录或手动输入"
    "missing_word" -> "缺少单词，请检查是否漏说了"
    "extra_word" -> "多出了单词，请检查是否说多了"
    "wrong_word" -> "单词不匹配，红色标注的为错误单词"
    "no_test_item" -> "当前没有测试句子"
    else -> reason
}
