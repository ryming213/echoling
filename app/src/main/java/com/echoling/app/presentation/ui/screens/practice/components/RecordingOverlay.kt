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
 *
 * Minimal design (per user spec): just a pulsing red dot, "正在录音…
 * 松开结束" text, and the amplitude bar animation below. No timer, no
 * cancel button — the user simply releases the mic button to stop.
 *
 * The amplitude bars are spread evenly across the full card width via
 * [Arrangement.SpaceEvenly] so the row is wider than the bars
 * themselves would be. Bars are 3dp wide (thinner than the previous
 * 6dp) and use a clean white color on the translucent red background
 * for high contrast.
 *
 * @param elapsedMs kept for API compatibility with the ViewModel's
 *        state; not displayed (the timer was removed per user request)
 * @param amplitudeBars list of 0..1 normalized bar heights; expected
 *        to be 5 entries (matches the ViewModel's [List(5) { 0.4f }])
 * @param modifier modifier for the outer Card
 */
@Composable
fun RecordingOverlay(
    elapsedMs: Long,
    amplitudeBars: List<Float>,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f)
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

/**
 * Five amplitude bars spread evenly across [modifier]'s width.
 * Each bar is 3dp wide with a 1.5dp rounded corner (thinner and
 * more elegant than the previous 6dp/3dp design). Active bars use
 * pure white for high contrast on the translucent red card;
 * placeholder bars (when fewer than 5 values are provided) use
 * 30% opacity white.
 */
@Composable
private fun AmplitudeBars(
    values: List<Float>,
    modifier: Modifier = Modifier,
    barCount: Int = 5,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        val bars = if (values.size >= barCount) values.take(barCount) else values
        // Placeholders for missing values (always rendered first so
        // the actual bars stay at the end of the row).
        repeat(barCount - bars.size) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            )
        }
        bars.forEach { value ->
            val height = (8 + value * 24).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(height)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color.White)
            )
        }
    }
}
