package com.echoling.app.presentation.ui.screens.practice.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 录音中提示 —— 只渲染「红色圆圈 + 涟漪」(复用 [RedRecordCircle])，
 * 不再带蓝色卡片底色、"请复述你听到的句子" 标题或底部计时器
 * (用户 2026-07-08 第二轮调整：去掉一切外层包裹，只保留圆圈本身)。
 *
 * 历史：
 *  - f1f40b5: Card(errorContainer) + PulsingRedDot + 5 根 AmplitudeBars + 计时/取消按钮
 *  - 字节级状态没了: 青色圆盘 + 左右涟漪
 *  - 第一轮重建（卡片版）: 蓝色渐变卡片 + 请复述 + 红圈 + 涟漪 + 计时
 *  - 第二轮: 去掉卡片，只渲染红圈 + 填色圆盘式涟漪（用户报"只有1层"）
 *  - 2026-07-10 §12.42: 红圈 + 3 道 Stroke 描边涟漪
 *  - 2026-07-22 短暂改为蓝色波形 (用户觉得太丑, 立即回退)
 *  - 当前: 回到红圈 + 3 道 Stroke 描边涟漪, 字号 labelMedium (保留下
 *    wrap 修复, 不回退)
 *
 * API 简化: 移除了 [elapsedMs] / [amplitudeBars] 入参 —— 当前视觉不需要.
 * 调用方 [TestingPage.kt] 也会同步简化调用点.
 */
@Composable
fun RecordingOverlay(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        RedRecordCircle()
    }
}

/**
 * 「红色圆圈 + 3 道向四周扩散的描边涟漪」独立 Composable。
 *
 * 复用场景：
 *  1. [RecordingOverlay]（测试页录音中提示）—— 现在只剩红圈 + 涟漪
 *  2. [SpeakingPage] 的单句录音指示器（不带蓝色卡片）
 *
 * 视觉规格（2026-07-08 第四轮调整——真正的涟漪感）：
 *  - 中心红色圆直径 = `sizeDp * 0.72`（红圈大一点，让"正在录音"装得下）
 *  - 3 道 Stroke 描边涟漪（不是填色圆盘）：
 *      * 只显示**左右两边的"翼"**（用 clipRect 把上下裁掉 + 画完整圆）——
 *        上下不显示，避免挡住红圈上下的视线
 *      * scale 从中心圆外缘的 1.0× 扩到 1.6×（之前 2.4× 拉太开）
 *      * stroke width 3dp，向外扩时略变细（× 0.7）但永远可见
 *      * alpha 用慢淡出 baseAlpha × (1 - 0.6 × progress)：最后到 baseAlpha × 0.4
 *        不再像旧版的 (1 - progress) 那样淡到 0；这样 3 个 ring 永远同时可见
 *  - phase 偏移 0 / 0.33 / 0.66（一个 ripple 周期 = 2000ms）
 *  - baseAlpha 三档 0.55 / 0.40 / 0.28 —— 让内圈更亮、外圈更淡，层次感清晰
 *  - Canvas 尺寸 = sizeDp × 1.6 给最大 ring 留余量
 *      （当前默认 100dp → 画布 160dp；之前 140dp → 画布 224dp，
 *        减了 64dp 的水平带高度，避免挡住下面的话筒）
 *  - (2026-07-22) 文本用 labelMedium (12sp Medium), maxLines=1,
 *    softWrap=false 双保险——上一轮用户报"调整小了动画之后, 正在录音
 *    被换行了", 100dp 红圈在不同 system font (小米 Mi 11 CN MiSans VF)
 *    实测撑到 60+ dp, labelLarge (14sp) 触发 wrap. labelMedium 收敛到
 *    ~48dp, 留 12dp × 2 边距.
 */
@Composable
fun RedRecordCircle(
    modifier: Modifier = Modifier,
    // (2026-07-18) 第四轮调整前默认 140dp；用户反馈"再小一号"，
    // 降到 100dp 让录制动画不再压住下面的话筒 / 进度条。
    // 涟漪画布 sizeDp * 1.6 = 160dp，给最大 ring 仍留 30dp 余量。
    sizeDp: Dp = 100.dp,
) {
    val transition = rememberInfiniteTransition(label = "ripple")
    val globalProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple-progress",
    )

    // 中心红圆直径
    val centerDiscSize = sizeDp * 0.72f
    // 画布直径 —— 给最大 ring (scale 1.6×) 留余量 + stroke 空间
    val canvasSize = sizeDp * 1.6f
    val rippleColor = Color(0xFFE53935)
    val ringStrokeWidth = 3.dp

    // 3 道涟漪的相位偏移 + baseAlpha
    // 用 list 而不是 3 个 Composable —— Compose 重组粒度更细，性能更好
    val rings = listOf(
        0.0f to 0.55f,
        0.33f to 0.40f,
        0.66f to 0.28f,
    )

    Box(
        modifier = modifier.size(canvasSize),
        contentAlignment = Alignment.Center,
    ) {
        // 3 道描边涟漪：每道从中心圆外缘扩散到画布边缘，描边始终可见
        // 用 clipRect 把画布裁成水平带（只保留中心圆上下半径范围内），
        // 画完整圆但只显示左右两边的"翼"，上下被裁掉
        Canvas(modifier = Modifier.size(canvasSize)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val minRadiusPx = (centerDiscSize / 2f).toPx()
            val maxRadiusPx = (canvasSize / 2f).toPx() - ringStrokeWidth.toPx()
            val strokeBasePx = ringStrokeWidth.toPx()

            // 水平带：高度 = 中心圆直径 + 一点 stroke 余量；上下各裁掉
            val bandHalfHeight = (centerDiscSize / 2f).toPx() + ringStrokeWidth.toPx() * 2f
            val bandTop = cy - bandHalfHeight
            val bandBottom = cy + bandHalfHeight

            // clipRect 是 DrawScope 的扩展，在 lambda 内的 drawCircle/drawArc 都会被裁切
            clipRect(
                left = 0f,
                top = bandTop,
                right = size.width,
                bottom = bandBottom,
            ) {
                rings.forEach { (offset, baseAlpha) ->
                    val progress = (globalProgress + offset) % 1f
                    // 半径从中心圆外缘 → 画布边缘（线性扩张）
                    val radiusPx = minRadiusPx + progress * (maxRadiusPx - minRadiusPx)
                    // alpha 慢淡出：1 - 0.6 × progress，最后保留 40% 可见
                    val alpha = (baseAlpha * (1f - progress * 0.6f)).coerceIn(0f, 1f)
                    // stroke width 略变细但永远清晰
                    val strokeWidthPx = strokeBasePx * (1f - progress * 0.3f)

                    drawCircle(
                        color = rippleColor.copy(alpha = alpha),
                        radius = radiusPx,
                        center = Offset(cx, cy),
                        style = Stroke(width = strokeWidthPx),
                    )
                }
            }
        }

        // 中心红色圆 + "正在录音"
        // 直径 = sizeDp * 0.72 (从 sizeDp/2 放大)：140dp * 0.72 = 100.8dp
        Box(
            modifier = Modifier
                .size(centerDiscSize)
                .clip(CircleShape)
                .background(rippleColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "正在录音",
                // (2026-07-22) §17.X: labelLarge (14sp) → labelMedium
                // (12sp Medium). centerDiscSize = sizeDp * 0.72 = 100dp
                // * 0.72 = 72dp 直径. 4 个中文字符在 labelLarge 下 ≈
                // 56dp 宽, 在不同 system font (e.g. 小米 Mi 11 CN 的
                // MiSans VF) 实测会撑到 60+ dp, 离 72dp 边界只剩 ~6dp,
                // 任何 letter-spacing 或字号继承波动都会让 Text 触发 wrap
                // → "正在录 / 音" 两行. labelMedium (12sp) 收敛到 ~48dp,
                // 留 12dp × 2 边距, 同时 maxLines = 1 + softWrap = false
                // 双保险: 即使将来 sizeDp 继续下调 (e.g. 80dp), 文字也不会
                // 自动换行, 而是整体收缩或截断 (更易被发现 vs 静默换行).
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}