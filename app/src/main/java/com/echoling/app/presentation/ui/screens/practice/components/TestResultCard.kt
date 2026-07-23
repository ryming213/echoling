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
                // (2026-07-23) §17.X: 用户反馈"你说：标签下面有两句,
                // 只保留你说的那一句". 旧 WordChipsRow 把 origWords +
                // transWords 各渲染一行 (orig 行在 顶部"原句："已经有
                // 完整原文文字, 重复). 现在只渲染 transWords 单行 —
                // 用户说的词逐个 chip, 命中绿/多余黄. 顶部"原句："已
                // 经是完整文本, 不需要再叠一遍 orig chips.
                UserWordsChipsRow(
                    transWords = state.transWords,
                    transMatched = state.transMatched
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    failureReasonText(state.reason),
                    color = contentColor,
                    style = MaterialTheme.typography.bodySmall
                )
            } else if (state is SttTestState.Passed) {
                // (2026-07-23) §17.X: 用户反馈"通过时只显示录音文字,
                // 没有'你说：'标签". Failed 分支有"原句："/"你说："两个
                // label, Passed 分支只有原句 + 一段裸文字, 不对称. 改成
                // 两个 label 都打, 跟 Failed 一致; 通过时不需要逐词对
                // 比 chips, 直接一段文字 (state.text 已经是 Vosk 识别出
                // 来的、与原句一致的整段) 即可.
                Text(
                    "你说：",
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
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
 * Renders the user's transcribed words as a single row of chips
 * under the "你说：" label after a failed STT submission.
 *
 * History:
 *   - (2026-06-28) Originally `WordChipsRow` with two parallel
 *     rows (orig + trans) using POSITION-INDEPENDENT match flags.
 *     The orig row helped users see which orig word each trans
 *     word aligned to.
 *   - (2026-07-23) §17.X: User said "你说：标签下面有两句, 只保留
 *     你说的那一句". The orig row is now redundant — the "原句："
 *     plain-text section at the top already shows the full original
 *     sentence. Duplicating it as chips under "你说：" was visual
 *     noise. Renamed to `UserWordsChipsRow`, dropped the orig params.
 *
 * Color semantics for trans row:
 *   - **Green (transMatched = true)**: this word aligned with a
 *     counterpart in the original.
 *   - **Yellow (transMatched = false)**: extra word the user said
 *     that wasn't in the original at all. Deliberately NOT red —
 *     saying an extra word is a milder error than saying a wrong
 *     word.
 *
 * Missing-orig-word cases (STT dropped "to") are visible in the
 * top "原句：" plain-text — the user can compare by eye. We don't
 * render an orig chip row here.
 *
 * Legacy reference below — kept here for git-blame archaeology
 * only. The (2026-06-28) flags are still relevant for understanding
 * why the per-chip color is the sole signal (no slash separators,
 * no orig/trans interleaving).
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
private fun UserWordsChipsRow(
    transWords: List<String>,
    transMatched: BooleanArray
) {
    // (2026-07-23) §17.X: 用户反馈"你说：标签下面有两句". 旧
    // WordChipsRow 把 origWords + transWords 各渲染一行, 但 orig 行
    // 已经在顶部"原句："的纯文本里显示完整原句了, 重复. 现在只渲染
    // 用户说的 transWords 单行 —— 命中绿/多余黄 (yellow 不是 red:
    // 多说一个词比说错一个词的视觉强度应该更弱).
    if (transWords.isEmpty()) return
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
