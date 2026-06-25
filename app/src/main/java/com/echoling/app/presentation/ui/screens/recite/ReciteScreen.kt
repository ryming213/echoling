package com.echoling.app.presentation.ui.screens.recite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echoling.app.data.local.db.entity.ReciteProgressEntity
import com.echoling.app.domain.model.DictCategory
import com.echoling.app.presentation.ui.components.PageHeader

/**
 * "记单词" tab landing — renders the five bundled vocabulary
 * categories as tappable cards. Each card shows the category name,
 * description, and entry count. Tapping a card navigates to the
 * per-category flashcard study screen.
 *
 * Cards are sorted in manifest-declaration order
 * (初中 → 高中 → CET-4 → CET-6 → TOEFL) so the picker shows the
 * natural difficulty progression. The icons follow the same sequence
 * (junior → senior → intermediate → advanced → academic) — picked to
 * hint at the level without literal mapping that could feel
 * preachy (e.g. we don't use a "hard" icon for TOEFL, just "global"
 * to signal "broader scope").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReciteScreen(
    onNavigateToCategory: (categoryId: String) -> Unit,
    viewModel: ReciteViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        // No topBar — the "记单词" title sits in PageHeader below (§12.18)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PageHeader(
                title = {
                    Text(
                        text = "记单词",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.categories.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "未找到词库，请检查 app/src/main/assets/vocab_manifest.json",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(uiState.categories, key = { it.id }) { category ->
                            CategoryCard(
                                category = category,
                                progress = uiState.progressByCategory[category.id],
                                onClick = { onNavigateToCategory(category.id) },
                            )
                        }
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryCard(
    category: DictCategory,
    progress: ReciteProgressEntity?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading icon — picks one of 5 brand-coherent Material icons
            // based on the category id. The icon tint uses primary so
            // the card reads as "tappable action" rather than "static
            // info panel".
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForCategory(category.id),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    ) {
                        Text(
                            text = "${category.size} 词",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }
                }
                if (category.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = category.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Per-card study sub-label — replaced the static
                // description's "X 词" reading with a real progress
                // line so the user can see the system IS tracking
                // their sessions. Without this the picker always
                // showed "未开始学习" / 0 / 0 which felt broken.
                val studied = (progress?.knownCount ?: 0) + (progress?.unknownCount ?: 0)
                if (studied > 0 || progress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = progressSubline(progress, category.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Render the small primary-tinted line under each category card.
 *
 *  - No progress row → "未开始学习"
 *  - Otherwise → "已学 N / total 词 · 认识 K · 不认识 U" with an
 *    optional " · 上次学习于 X 分钟前" suffix when the timestamp
 *    is recent enough to be meaningful (>= 1 min ago).
 *
 * Kept in one place so the format is consistent across the 5 cards
 * and easy to tweak later (e.g. switch to "X% 已完成" once the
 * dataset is large enough that the raw count looks noisy).
 */
private fun progressSubline(
    progress: ReciteProgressEntity?,
    total: Int,
): String {
    if (progress == null) return "未开始学习"
    val studied = progress.knownCount + progress.unknownCount
    if (studied == 0) return "未开始学习"
    val base = "已学 $studied / $total 词 · 认识 ${progress.knownCount} · 不认识 ${progress.unknownCount}"
    val last = relativeTimeAgo(progress.lastStudiedAt)
    return if (last.isNotEmpty()) "$base · $last" else base
}

/**
 * Compact relative-time string for the "last studied" suffix.
 * Returns empty string when the timestamp is missing or in the
 * future (clock skew after restoring a backup) — the caller then
 * omits the suffix.
 */
private fun relativeTimeAgo(epochMs: Long): String {
    if (epochMs <= 0L) return ""
    val deltaMin = (System.currentTimeMillis() - epochMs) / 60_000L
    return when {
        deltaMin < 0L -> ""                                // clock skew
        deltaMin < 1L -> "刚刚学习"
        deltaMin < 60L -> "上次学习于 $deltaMin 分钟前"
        deltaMin < 24 * 60L -> "上次学习于 ${deltaMin / 60} 小时前"
        else -> "上次学习于 ${deltaMin / (24 * 60)} 天前"
    }
}

private fun iconForCategory(id: String): ImageVector = when (id) {
    "junior" -> Icons.Default.School
    "senior" -> Icons.Default.MenuBook
    "cet4" -> Icons.Default.MenuBook
    "cet6" -> Icons.Default.AutoStories
    "toefl" -> Icons.Default.Public
    else -> Icons.Default.MenuBook
}
