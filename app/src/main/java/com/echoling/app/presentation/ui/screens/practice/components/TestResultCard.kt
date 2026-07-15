@file:OptIn(ExperimentalLayoutApi::class)

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
                // (2026-06-28) Pass the per-word match flags
                // through. The old call site only sent origWords /
                // transWords, which forced WordChipsRow to compare
                // position-wise and gave the user false-positives
                // like "first word wrong → all subsequent words
                // wrong". Now each chip colors itself from its
                // own flag.
                WordChipsRow(
                    origWords = state.origWords,
                    transWords = state.transWords,
                    origMatched = state.origMatched,
                    transMatched = state.transMatched
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

/**
 * Renders the word-by-word comparison after a failed STT submission.
 *
 * (2026-06-28) Rewritten to use POSITION-INDEPENDENT match flags
 * (origMatched / transMatched) instead of position-by-position
 * equality. Two changes from the previous version:
 *
 *   1. **No more "orig/trans" slash.** When a word is wrong, the
 *      chip shows ONLY the wrong word (e.g. "checks") — the
 *      original ("check") is already displayed in the "原句"
 *      section above, so showing it again in the chip was
 *      redundant. Color alone communicates "wrong".
 *
 *   2. **Two parallel rows, not one interleaved row.** The
 *      previous version iterated `for i in 0..maxLen` and
 *      combined orig[i] with trans[i] at the same position —
 *      which meant an unmatched orig word at position 0 (e.g.
 *      STT dropped "to") would visually "push" the rest of the
 *      alignment off-by-one, even though every subsequent word
 *      was actually correct. The new layout shows the orig row
 *      and the trans row independently, each chip colored by
 *      its own `matched` flag. A wrong word at position 0 no
 *      longer affects how position 3 renders.
 *
 * Color semantics:
 *   - **Green (origMatched / transMatched = true)**: this word
 *     was successfully aligned with a counterpart on the other
 *     side. No problem.
 *   - **Red (origMatched = false)**: orig word that the user
 *     didn't say at all (missing) or said as a different word
 *     (the user's wrong word shows on the trans row instead).
 *   - **Yellow (transMatched = false)**: extra word the user
 *     said that wasn't in the original at all.
 *
 * The colors mirror the convention used elsewhere in the app
 * (e.g. "red = wrong" for sentence completion states).
 */
@Composable
private fun WordChipsRow(
    origWords: List<String>,
    transWords: List<String>,
    origMatched: BooleanArray,
    transMatched: BooleanArray
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Row 1: original sentence. Green if matched, red if not.
        if (origWords.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                origWords.forEachIndexed { i, word ->
                    val matched = origMatched.getOrNull(i) == true
                    WordChip(
                        text = word,
                        bg = if (matched) Color(0xFFC8E6C9) else Color(0xFFFFCDD2)
                    )
                }
            }
        }
        // Row 2: user's transcription. Green if matched, yellow
        // if "extra" (not in the original at all). We deliberately
        // do NOT use red here — the user said an extra word, not
        // a wrong one, so the visual cue should be milder than
        // "you got this wrong".
        if (transWords.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                transWords.forEachIndexed { j, word ->
                    val matched = transMatched.getOrNull(j) == true
                    WordChip(
                        text = word,
                        bg = if (matched) Color(0xFFC8E6C9) else Color(0xFFFFF9C4)
                    )
                }
            }
        }
    }
}

@Composable
private fun WordChip(text: String, bg: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium
        )
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
