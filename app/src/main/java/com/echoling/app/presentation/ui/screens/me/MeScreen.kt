package com.echoling.app.presentation.ui.screens.me

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    // §12.22: API config moved off the bottom bar — surfaced here as a
    // tappable card so users can still edit credentials for any future
    // feature that needs them (e.g. grading API).
    onNavigateToApiConfig: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val version = remember {
        resolveVersion(context.packageManager, context.packageName)
    }

    Scaffold(
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
                text = "Echo Ling",
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

            InfoCard(
                icon = Icons.Default.Info,
                title = "App Introduction",
                body = "听言英语是一款专注于英语听力与口语练习的应用。" +
                        "通过跟读训练、智能断句、逐句播放等功能，帮助用户提升英语听说能力。",
            )

            Spacer(modifier = Modifier.height(16.dp))

            InfoCard(
                icon = Icons.Default.Star,
                title = "Features",
                items = listOf(
                    "跟读练习 - 智能录音对比",
                    "逐句播放 - 精准控制进度",
                    "英文字幕 - 纯英文显示",
                    "单词收藏 - 一键保存复习",
                    "断点续学 - 自动记忆进度",
                    "本地词典 - 长按取词离线查",
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // §12.24: flashcard feature documentation. Dedicated InfoCard
            // (not merged into the Features list above) so the two
            // feature groups stay visually separate — the icons differ
            // (School vs Star) and users can scan them as "Practice
            // features" → "Vocabulary features".
            InfoCard(
                icon = Icons.Default.School,
                title = "记单词 · Flashcards",
                items = listOf(
                    "五大词库 - 初中 / 高中 / CET-4 / CET-6 / TOEFL",
                    "方形卡片 - 点击 3D 翻牌查看翻译",
                    "进度记忆 - 自动保存当前位置和已学数",
                    "加入单词本 - 不认识的词一键收藏复习",
                    "进度可视化 - 首页查看每词库已学进度",
                    "双 schema 支持 - 嵌套 / 扁平 JSON 自动识别",
                ),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // §12.22: API config moved off the bottom bar; tap here to
            // open the same screen the old API tab used to show.
            if (onNavigateToApiConfig != null) {
                ApiConfigLinkCard(onClick = onNavigateToApiConfig)
                Spacer(modifier = Modifier.height(16.dp))
            }

            ContactCard(context = context)

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "© 2024 听言英语\nAll rights reserved.",
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            androidx.compose.foundation.layout.Row(
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
private fun ContactCard(context: android.content.Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            androidx.compose.foundation.layout.Row(
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
                text = "If you have any questions or suggestions, please contact us:",
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
        }
    }
}

private fun resolveVersion(pm: PackageManager, packageName: String): String {
    return try {
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(packageName, 0)
        val name = info.versionName ?: "?"
        val code = info.longVersionCode
        "$name ($code)"
    } catch (e: Exception) {
        "?"
    }
}

/**
 * §12.22: tile linking to the API config sub-page. Renders as a
 * settings-style row — left-aligned icon + label, right-aligned
 * chevron — that the user reads as "open settings". The card surface
 * uses the same surfaceVariant container as the other info cards so
 * the visual rhythm of the Me page is preserved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiConfigLinkCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "API 配置",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "句子评分等第三方服务",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}