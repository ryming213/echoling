package com.echoling.app.presentation.ui.screens.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echoling.app.presentation.ui.components.PageHeader

/**
 * 权限使用说明 page — required by the 国内应用商店 review (华为 / 小米 / OPPO /
 * vivo / 应用宝) so the user has a clear, in-app explanation of why each
 * sensitive permission is requested, what data is collected, and where that
 * data goes (or doesn't go).
 *
 * As of 2026-07-04 the only requested sensitive permission is
 * `android.permission.RECORD_AUDIO`. INTERNET and ACCESS_NETWORK_STATE were
 * removed when the Vosk STT model was bundled into the APK and the
 * `alphacephei.com` download fallback in `ModelManager` was deleted — see
 * the comment block in AndroidManifest.xml for the full reasoning.
 *
 * Layout: a [PageHeader] at the top + a vertically-scrolling Column of
 * [PermissionCard] / [InfoCard] sections. Each card has a tinted icon and
 * a short body. The page sits in the same visual family as the in-app
 * InstructionsScreen (`使用说明`) since both are explanatory sub-pages, but
 * this page is shorter — only sensitive permissions + privacy disclosure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onNavigateBack: () -> Unit,
) {
    Scaffold {
        padding ->
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
                        text = "权限使用说明",
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
                // Sections below are flat (no Card background) and
                // separated by a thin 1dp divider drawn between them.
                // Same flat treatment as InstructionsScreen §12.21 and
                // MeScreen §12 — see the flat-divider-section-style
                // memory note.
                val sections = listOf<@Composable () -> Unit>(
                    {
                        // Intro — the page exists because the Chinese
                        // app stores (not Google Play) require a
                        // published privacy disclosure inside the
                        // binary as well as in the store listing.
                        InfoCard(
                            icon = Icons.Default.PrivacyTip,
                            title = "隐私声明",
                            body = "听言英语承诺：所有个人数据均在您的设备本地处理与保存，" +
                                    "不会上传至任何服务器，不会用于广告投放，不会与第三方共享。\n\n" +
                                    "下方列出本应用所请求的全部敏感权限，以及每项权限的使用目的与数据流向。",
                        )
                    },
                    {
                        // The one permission the app actually uses.
                        PermissionCard(
                            icon = Icons.Default.Mic,
                            title = "麦克风（RECORD_AUDIO）",
                            status = "可选 · 用户主动触发时申请",
                            purpose = "用于跟读练习的录音与本地语音识别。" +
                                    "仅在跟读测试页面（按住麦克风按钮）时被调用，松开按钮即停止录音。",
                            dataCollected = "用户主动录制的音频，存储在 App 私有目录（filesDir）下的 .wav 文件。",
                            dataUsage = "本地处理：Vosk 离线语音识别引擎将音频转为文字，用于与原字幕逐词比对显示跟读匹配结果。" +
                                    "同时供用户回放自己刚才的录音。",
                            privacy = "音频文件不会上传至任何服务器，App 退出后随系统清理而删除；" +
                                    "应用本身不会保留历史录音。",
                        )
                    },
                    {
                        InfoCard(
                            icon = Icons.Default.Storage,
                            title = "数据存储",
                            body = "应用保存的所有数据（学习进度、收藏的单词、本地词典、跟读对比）均存储在您设备的 " +
                                    "App 私有目录（/data/data/com.echoling.app/）下，" +
                                    "仅本应用可访问，卸载 App 时一并清除。",
                        )
                    },
                    {
                        InfoCard(
                            icon = Icons.Default.CheckCircle,
                            title = "网络权限说明",
                            body = "本应用已移除 INTERNET 与 ACCESS_NETWORK_STATE 权限，" +
                                    "完全不联网：所有功能（包括语音识别、词典查询、跟读对比、TTS 发音）均在设备本地完成。",
                        )
                    },
                )
                sections.forEachIndexed { index, section ->
                    section()
                    if (index < sections.lastIndex) {
                        // Thin 1dp rule between sections. Box +
                        // background instead of M3 HorizontalDivider
                        // because the latter is a 1.2.0 token and this
                        // project pins material3 to 1.1.2 (see
                        // StatisticsScreen §12.21/§12.32b notes on the
                        // same pin).
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
private fun InfoCard(
    icon: ImageVector,
    title: String,
    body: String,
) {
    // No background fill — sections on this page are separated by a
    // thin 1dp divider drawn between them in the parent Column, so
    // each card reads as "icon + title + body" rather than "a tinted
    // box". Same flat treatment as InstructionsScreen §12.21 and
    // MeScreen §12 — see the flat-divider-section-style memory note.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionCard(
    icon: ImageVector,
    title: String,
    status: String,
    purpose: String,
    dataCollected: String,
    dataUsage: String,
    privacy: String,
) {
    // Flat layout (no Card background) to match the rest of the page;
    // see InfoCard comment.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
        // Status pill — muted tone. "可选 · 用户主动触发时申请"
        // signals the runtime consent pattern so the user knows
        // they're not being silently recorded.
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        )

        PermissionField(label = "使用目的", body = purpose)
        PermissionField(label = "收集的数据", body = dataCollected)
        PermissionField(label = "数据用途", body = dataUsage)
        PermissionField(label = "隐私承诺", body = privacy)
    }
}

@Composable
private fun PermissionField(label: String, body: String) {
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
