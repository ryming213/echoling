package com.echoling.app.presentation.ui.screens.instructions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echoling.app.presentation.ui.components.PageHeader

/**
 * In-app 使用说明 page (§12.21). Reached from the home page's top-right
 * Help IconButton. Drafted as a one-page reference covering the main
 * flows of the app — course management, practice, flashcards,
 * vocabulary, statistics, API config — so a first-time user can read
 * it once and stop guessing what each button does.
 *
 * Layout: a single scrollable Column of [InstructionSection] cards.
 * Each section has a bold title and a short bulleted list of steps.
 * The body sits below a left-aligned PageHeader so the screen follows
 * the same visual language as the other sub-pages (see PageHeader
 * §12.18 / §12.21). The 「记单词」section (§12.24) was added after
 * the initial §12.21 ship to document the flashcard feature
 * introduced in the same session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstructionsScreen(
    onNavigateBack: () -> Unit,
) {
    Scaffold(
        // No topBar — PageHeader at the top of the body handles the
        // back + title.
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PageHeader(
                onBack = onNavigateBack,
                titleAlignment = Alignment.Start,
                title = {
                    Text(
                        text = "使用说明",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InstructionSection(
                    title = "一、课程管理",
                    items = listOf(
                        "点击首页右下角的“导入课程”按钮开始导入。",
                        "依次选择音频或视频文件（任选其一），再选择字幕文件（可选，支持 SRT / ASS / LRC 格式）。",
                        "填写“课程名字”（例如“新概念英语第一册”）和“课程标题”（例如“第 1 课”），点击底部“Import Course”完成导入。",
                        "课程名字相同的课程会自动归为一组；首页只显示分组名称，不显示具体课程。",
                        "点击分组进入子页面，可以查看、删除该组下的课程，或点击右下角“导入课程”按钮继续导入同组课程。",
                    ),
                )

                InstructionSection(
                    title = "二、跟读练习",
                    items = listOf(
                        "在课程子页面点击任意课程即可直接进入跟读练习，没有中间页。",
                        "顶部“跟读练习”右侧的“双语 / 英语 / 中文”按钮可切换字幕显示模式。",
                        "泛听：完整播放音频或视频，字幕与播放进度同步。点击列表中的任意字幕可跳转到对应句子。",
                        "精听：单句播放，支持按句切换、循环播放、调整速度。长按录音按钮进行复述，可回放录音。",
                        "测试：从已学习的句子中随机抽取练习，遮盖英文单词，逐一点击揭示，全部揭示后显示中文翻译。",
                        "长按字幕中的任意英文单词，可弹出翻译与“保存到生词本”对话框。",
                    ),
                )

                InstructionSection(
                    title = "三、记单词",
                    items = listOf(
                        "切换到底部「记单词」标签页，可看到 5 个内置词库（初中 / 高中 / CET-4 / CET-6 / TOEFL）。",
                        "点击词库卡片进入闪卡学习。卡片正面显示英文单词，点击卡片可 3D 翻转到背面查看音标、词性和翻译，再次点击翻回正面。",
                        "「认识」：标记当前单词已掌握，自动 +1 认识数并切换到下一张。",
                        "「不认识」：弹出「加入单词本」按钮，点击后将单词保存到生词本，方便日后复习。",
                        "「上一张」/「下一张」：纯导航按钮，不计入统计，方便回看或跳过。",
                        "顶部右上角的刷新按钮可一键清空当前词库的学习进度（当前位置、认识数、不认识数全部归零）。",
                        "所有学习进度（当前位置、认识数、不认识数）自动保存，下次打开同一词库会从上次位置继续，无需从头开始。",
                        "词库卡片会实时显示「已学 N / total 词 · 认识 K · 不认识 U」以及「上次学习于 X 分钟前」。",
                        "「已加入单词本」提示会出现在卡片与「认识/不认识」按钮之间，不会遮挡「上一张 / 下一张」导航按钮。",
                    ),
                )

                InstructionSection(
                    title = "四、生词本",
                    items = listOf(
                        "在跟读练习中长按单词 → 弹出翻译对话框 → 点击“保存”即可加入生词本。",
                        "在「记单词」页面点击「不认识」→「加入单词本」也会保存到生词本。",
                        "切换到底部“生词”标签页，可查看、搜索、删除所有保存过的单词。",
                        "点击单词可以切换“已掌握”状态；已掌握的单词会反映到首页统计的“已掌握”数字中。",
                    ),
                )

                InstructionSection(
                    title = "五、学习统计",
                    items = listOf(
                        "点击首页上方的“学习时长”统计卡片可进入统计页面。",
                        "统计页面展示：累计学习时长、已开始学习的课程数、已收集与已掌握的单词数、连续打卡天数。",
                        "柱状图分别展示过去 7 天和过去 30 天的每日学习时长，方便对比短期与长期表现。",
                        "学习时长只在“音频或视频实际播放”时累计——暂停、切到测试题、翻看字幕列表时不会虚增。",
                    ),
                )

                InstructionSection(
                    title = "六、API 配置",
                    items = listOf(
                        "切换到底部“我的”标签页，点击“API 配置”进入。",
                        "可填入未来语音评分服务所需的 App ID 与 App Key；评分功能尚未启用，设置项仅为预留。",
                        "跟读练习中的长按取词功能已改为本地词典查询，无需任何 API 密钥。",
                    ),
                )

                InstructionSection(
                    title = "七、其他",
                    items = listOf(
                        "所有学习进度、单词、统计在本地保存；删除 App 数据或清除存储会一并清空。",
                        "如遇问题或希望反馈，可进入“我的”标签页查看联系信息。",
                    ),
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun InstructionSection(
    title: String,
    items: List<String>,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            items.forEach { line ->
                Text(
                    text = "• $line",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
