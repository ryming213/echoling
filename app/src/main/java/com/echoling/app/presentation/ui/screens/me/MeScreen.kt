package com.echoling.app.presentation.ui.screens.me

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echoling.app.R
import com.echoling.app.presentation.ui.components.PageHeader

/**
 * "Me" tab — app info, contact, and version. Body ported from the previous
 * AboutScreen with the back arrow removed and the version read from
 * PackageManager instead of a hardcoded string.
 *
 * Layout: the top of the page uses a Material 3 [CenterAlignedTopAppBar]
 * with a two-line [Column] title that mirrors the [CoursesScreen] header
 * exactly — same fontSize / fontWeight / letterSpacing / fontStyle for
 * both the main title (`听言英语`) and the English subtitle. This keeps
 * the four-tab app visually consistent: switching from "首页" to "我的"
 * should feel like the same brand bar, just with a different title.
 *
 * The [TopAppBar] natively handles the status-bar inset (no
 * [statusBarsPadding] needed on the body) and is always visible — it
 * does not scroll with the page, which was the original §12.11
 * requirement ("听言英语 始终可见").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeScreen(
    // (2026-07-04) New — wires the "权限使用说明" entry card below the
    // ContactCard to a sub-page navigation. TabPagerHost owns the
    // navController and injects the lambda. Defaulted to a no-op so
    // future preview surfaces (composable previews, tests) still
    // compile without a NavController.
    onNavigateToPermissions: () -> Unit = {},
) {
    val context = LocalContext.current
    val version = remember {
        resolveVersion(context.packageManager, context.packageName)
    }

    Scaffold(
        // §12.30: only consume the top status-bar inset, not the bottom
        // navigation-bar inset. The outer MainScaffold already accounts
        // for the bottom tab bar, so a second nav-bar subtraction here
        // would leave a 24dp page-colored strip between the tab bar
        // and the last list item. See CLAUDE.md §12.30.
        contentWindowInsets = WindowInsets.statusBars,
        // No topBar — the brand title sits in the PageHeader at the top of
        // the body, right below the status bar (§12.18). Same two-line
        // layout as CoursesScreen's header.
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PageHeader(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.me_title),
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp,
                            ),
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(R.string.me_subtitle),
                            style = TextStyle(
                                fontSize = 11.sp,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    // Use a dedicated copy of the app icon (ic_me_page_icon.png)
                    // instead of referencing R.drawable.ic_app_icon directly.
                    // Sharing the launcher asset between the system launcher and
                    // an in-page tile forces Android to scale the same bitmap to
                    // wildly different target sizes, which can crop or distort
                    // the rounded corners that the asset was designed around.
                    // A separate copy keeps each drawable's density bucket
                    // independent. `Image` (unlike `Icon`) does not apply a
                    // default tint, so we don't need to set anything to keep
                    // the bitmap's original colors.
                painter = painterResource(id = R.drawable.ic_me_page_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Text(
                    text = "Version $version",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Sections below are flat (no Card background) and
            // separated by a thin 1dp divider drawn between them.
            // Same flat treatment as InstructionsScreen §12.21 — see
            // InfoCard comment for the rationale.
            //
            // (2026-07-07) ContactCard reordered to the bottom so the
            // disclosure-style "Permission Usage" sits closer to the
            // user-facing product features above, and the call-to-
            // action "Contact Us" lives at the end of the page where
            // users reach it after they've read about the app.
            val sections = listOf<@Composable () -> Unit>(
                {
                    InfoCard(
                        icon = Icons.Default.Info,
                        title = "App Introduction",
                        body = "听言英语是一款专注于英语听力与口语练习的应用。" +
                                "通过跟读训练、智能断句、逐句播放等功能，帮助用户提升英语听说能力。",
                    )
                },
                {
                    InfoCard(
                        icon = Icons.Default.Star,
                        title = "Features",
                        items = listOf(
                            "跟读练习 - 智能录音对比",
                            "逐句播放 - 精准控制进度",
                            "英文字幕 - 纯英文显示",
                            "断点续学 - 自动记忆进度",
                            "本地词典 - 长按取词离线查",
                            "单词收藏 - 一键保存复习",
                            "声音识别 - 自动识别复述的句子",
                            // (2026-07-18) 导入音视频时可一键生成 SRT
                            // 字幕, 详见 InstructionsScreen「三、自动
                            // 生成字幕」。
                            "字幕生成 - 自动识别音视频",
                        ),
                    )
                },
                {
                    // §12.24: flashcard feature documentation. Dedicated
                    // InfoCard (not merged into the Features list above)
                    // so the two feature groups stay visually separate —
                    // the icons differ (School vs Star) and users can
                    // scan them as "Practice features" → "Vocabulary
                    // features".
                    InfoCard(
                        icon = Icons.Default.School,
                        title = "记单词 · Flashcards",
                        items = listOf(
                            "十一大词库 - 小学 / 初中 / 高中 / CET-4 / CET-6 / CET-8 / 考研 / 托福 / GRE / IELTS / BEC",
                            "方形卡片 - 点击 3D 翻牌查看翻译",
                            "进度记忆 - 自动保存当前位置和已学数",
                            "加入单词本 - 不认识的词一键收藏复习",
                            "进度可视化 - 首页查看每词库已学进度",
                        ),
                    )
                },
                {
                    // (2026-07-04) Permissions disclosure card. 国内
                    // 应用商店 review requires an in-app, user-
                    // reachable page that lists each sensitive
                    // permission and explains why the app needs it +
                    // where the data goes. The card below jumps to
                    // PermissionsScreen which covers RECORD_AUDIO (the
                    // only current sensitive permission) and asserts
                    // that INTERNET is not used at all.
                    PermissionsCard(onClick = onNavigateToPermissions)
                },
                {
                    ContactCard(context = context)
                },
            )
            sections.forEachIndexed { index, section ->
                section()
                if (index < sections.lastIndex) {
                    // Thin 1dp rule between sections. Box + background
                    // instead of M3 HorizontalDivider because the
                    // latter is a 1.2.0 token and this project pins
                    // material3 to 1.1.2 (see InstructionsScreen
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

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "© 2026 听言英语\nAll rights reserved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String? = null,
    items: List<String> = emptyList(),
) {
    // No background fill — sections on this page are separated by a
    // thin 1dp divider drawn between them in the parent Column, so
    // each card reads as "icon + title + body" rather than "a tinted
    // box". Same flat treatment as InstructionsScreen §12.21.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (body != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (items.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { item ->
                FeatureItem(text = item)
            }
        }
    }
}

@Composable
private fun FeatureItem(text: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.padding(horizontal = 8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionsCard(onClick: () -> Unit) {
    // Flat layout (no Card background) to match the rest of the page;
    // see InfoCard comment.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                text = "Permission Usage",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "查看应用所使用的全部敏感权限、收集的数据与隐私承诺。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        // TextButton (not OutlinedButton) so the affordance is a
        // "查看 →" link, matching the lower visual weight of a
        // disclosure-page entry rather than a primary action like
        // "Contact Us" below.
        TextButton(onClick = onClick) {
            Text("查看权限使用说明 →")
        }
    }
}

@Composable
private fun ContactCard(context: android.content.Context) {
    // Flat layout (no Card background) to match the rest of the page;
    // see InfoCard comment.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                text = "Contact Us",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "If you have any questions or suggestions, please contact us by email or WeChat:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:ryming213@sina.com")
                    putExtra(Intent.EXTRA_SUBJECT, "Feedback for 听言英语")
                }
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Email, contentDescription = null)
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Text("ryming213@sina.com")
        }
        Spacer(modifier = Modifier.height(8.dp))
        // (2026-07-18) WeChat contact. No universal intent can open
        // the WeChat "add contact" flow from a third-party app, so the
        // pattern is to copy the ID to clipboard + Toast confirmation.
        // User pastes the ID into WeChat's 搜索 → 添加朋友 to find the
        // contact. Button stays full-width and uses the same outlined
        // style as the email button so the two reads as "two equally
        // valid ways to reach us".
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("WeChat ID", WECHAT_ID))
                Toast.makeText(context, "微信号已复制到剪贴板", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Chat, contentDescription = null)
            Spacer(modifier = Modifier.padding(horizontal = 8.dp))
            Text("WeChat: $WECHAT_ID")
        }
    }
}

private const val WECHAT_ID = "ryming213"

private fun resolveVersion(pm: PackageManager, packageName: String): String {
    return try {
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(packageName, 0)
        // 用户 2026-07-08 反馈：Me 页只显示 versionName（如 "1.0"），
        // 不再拼接 versionCode（如 "(1)"）。versionCode 仍由 build.gradle.kts
        // 管理，用于商店上传递增；UI 上让版本号更干净。
        info.versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
}