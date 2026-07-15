package com.echoling.app.presentation.ui.screens.instructions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
 * flows of the app — navigation, course management, practice,
 * flashcards, vocabulary, statistics — so a first-time user can read
 * it once and stop guessing what each button does.
 *
 * The API config section (§12.21 v1) was removed after §12.26 deleted
 * the entire API config feature. Two new behaviors were added since:
 * gesture-driven tab navigation (§12.27) and local on-device
 * pronunciation scoring in the testing tab. Both are documented here.
 *
 * Layout: a single scrollable Column of [InstructionSection] cards.
 * Each section has a bold title and a short bulleted list of steps.
 * The body sits below a left-aligned PageHeader so the screen follows
 * the same visual language as the other sub-pages (see PageHeader
 * §12.18 / §12.21).
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
            ) {
                // Sections are visually separated by a thin
                // HorizontalDivider rather than spacing. The divider's
                // vertical padding gives the eye a breath between
                // sections without resorting to a tinted card background.
                val sections = listOf(
                    "一、主界面与导航" to listOf(
                        "底部 4 个 Tab：首页 / 单词本 / 记单词 / 我的。",
                        "切换 Tab：点底部按钮，或在屏幕上左右滑动（Pager 手势驱动，左滑下一个，右滑上一个）。",
                        "左滑 / 右滑时屏幕会带方向动画：左滑新页从右滑入，右滑新页从左滑入。",
                        "进入子页面（跟读练习 / 素材详情 / 统计 / 导入 / 闪卡）后底部 Tab 自动隐藏，按返回键回到上一级。",
                    ),
                    "二、素材管理" to listOf(
                        "点击首页右下角的「导入素材」按钮开始导入。",
                        "依次选择音频或视频文件（任选其一），再选择字幕文件（可选，支持 SRT / ASS / LRC 格式）。",
                        "填写「素材名字」（例如「摩登家庭第一季」）和「素材标题」（例如「第 1 课」），点击底部「Import Course」完成导入。",
                        "素材名字相同的素材会自动归为一组；首页只显示分组名称，不显示具体素材。",
                        "点击分组进入子页面，可以查看、删除该组下的素材，或点击右下角「导入素材」按钮继续导入同组素材。",
                        "最近学习的素材会出现在首页紫色渐变「继续学习」Hero 卡片中，点圆圈播放按钮可直接进入跟读练习。",
                    ),
                    "三、跟读练习" to listOf(
                        "在素材子页面点击任意素材即可直接进入跟读练习，没有中间页。",
                        "泛听：完整播放音频或视频，字幕与播放进度同步。点击列表中的任意字幕可跳转到对应句子。",
                        "精听：单句播放，支持按句切换、循环播放、调整速度。长按录音按钮进行复述，可回放录音。",
                        "测试：从已学习的句子中随机抽取练习，遮盖英文单词，逐一点击揭示，全部揭示后显示中文翻译。",
                        "测试页的核心交互：按住麦克风复述当前句子，松开后本地 STT 把你的声音转换成文字；点击文字可以手动修改，满意后「提交」——系统把你的文字和原文逐词比对，匹配即通过。",
                        "不通过时会用颜色标出漏词 / 多词 / 错词的位置，可以「重录」再来一次或直接修改文字后再提交。",
                        "本地 STT 把你的声音转换成文字，无需联网、无需任何 API Key。",
                        "长按字幕中的任意英文单词，弹出翻译与「保存到生词本」对话框——查询走本地词典，无需联网。",
                    ),
                    "四、记单词" to listOf(
                        "切换到底部「记单词」标签页，可看到 11 个内置词库（初中 / 高中 / CET-4 / CET-6 / TOEFL等）。",
                        "点击词库卡片进入闪卡学习。卡片正面显示英文单词和相应的英文句子，点击卡片可 3D 翻转到背面查看音标、词性和翻译，再次点击翻回正面。",
                        "单词旁 / 音标旁的喇叭按钮可发音（TTS），支持离线英语引擎，引擎未装时会提示安装。",
                        "「认识」：标记当前单词已掌握，自动 +1 认识数并切换到下一张。",
                        "「不认识」：弹出「加入单词本」按钮，点击后将单词保存到生词本，方便日后复习。",
                        "「上一张」/「下一张」：纯导航按钮，不计入统计，方便回看或跳过。",
                        "顶部右上角的刷新按钮可一键清空当前词库的学习进度（当前位置、认识数、不认识数全部归零）。",
                        "所有学习进度自动保存，下次打开同一词库会从上次位置继续，无需从头开始。",
                        "词库卡片实时显示「已学 N / total 词 · 认识 K · 不认识 U」以及「上次学习于 X 分钟前」。",
                    ),
                    "五、生词本" to listOf(
                        "在跟读练习中长按单词 → 弹出翻译对话框 → 点击「保存」即可加入生词本。",
                        "在「记单词」页面点击「不认识」→「加入单词本」也会保存到生词本。",
                        "切换到底部「生词」标签页，可查看、搜索、删除所有保存过的单词。",
                        "每行单词右侧的喇叭按钮可一键发音。",
                        "点击单词可以切换「已掌握」状态；已掌握的单词会反映到首页统计的「已掌握」数字中。",
                    ),
                    "六、学习统计" to listOf(
                        "点击首页上方的「学习时长」统计卡片可进入统计页面。",
                        "统计页面展示：累计学习时长、已开始学习的素材数、已收集与已掌握的单词数、连续打卡天数。",
                        "柱状图分别展示过去 7 天和过去 30 天的每日学习时长，方便对比短期与长期表现。",
                        "学习时长只在「音频或视频实际播放」时累计——暂停、切到测试题、翻看字幕列表时不会虚增。",
                    ),
                    "七、其他" to listOf(
                        "所有学习进度、单词、统计在本地保存；删除 App 数据或清除存储会一并清空。",
                        "单词翻译、句子翻译、单词发音、复述测试音转文字均为本地离线功能，无需联网、无需任何 API Key。",
                        "如遇问题或希望反馈，可进入「我的」标签页查看联系邮箱。",
                    ),
                )
                sections.forEachIndexed { index, (title, items) ->
                    InstructionSection(title = title, items = items)
                    if (index < sections.lastIndex) {
                        // Thin 1dp rule between sections. Drawn as a
                        // Box + background rather than Material 3's
                        // HorizontalDivider because the latter is a
                        // 1.2.0 token and this project pins
                        // material3 to 1.1.2 (see StatisticsScreen
                        // §12.21/§12.32b notes on the same pin).
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .height(1.dp)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                        )
                    }
                }

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
    // No background fill — sections are visually separated by the
    // HorizontalDivider placed between them in the parent Column, so
    // each section reads as "a block of text with a thin rule under
    // it" rather than "a tinted card". titleSmall + primary keeps the
    // section anchor visually distinct from the body bullets below.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
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
