package com.echoling.app.presentation.ui.screens.practice.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Recording overlay shown during STT capture.
 * Displays a pulsing red dot, "正在录音…" text, 5 amplitude bars (random v1),
 * elapsed time, and a cancel button.
 */
@Composable
fun RecordingOverlay(
    elapsedMs: Long,
    amplitudeBars: List<Float>,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulsingRedDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    "正在录音… 松开结束",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            Spacer(Modifier.height(12.dp))
            AmplitudeBars(values = amplitudeBars)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "%.1fs".format(elapsedMs / 1000.0),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                TextButton(onClick = onCancel) {
                    Text("取消", color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
private fun PulsingRedDot() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    Box(
        modifier = Modifier
            .size(12.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.Red)
    )
}

@Composable
private fun AmplitudeBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    barCount: Int = 5
) {
    Row(
        modifier = modifier.height(40.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val bars = if (values.size >= barCount) values.take(barCount) else values
        bars.forEach { value ->
            val height = (8 + value * 24).dp
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(height)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onErrorContainer)
            )
        }
        // Fill remaining if fewer bars than barCount
        repeat(barCount - bars.size) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.3f))
            )
        }
    }
}
