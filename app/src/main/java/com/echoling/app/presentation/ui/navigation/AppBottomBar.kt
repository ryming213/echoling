package com.echoling.app.presentation.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Custom compact bottom navigation bar. The M3 [androidx.compose.material3.NavigationBar]
 * hardcodes its item height at 80dp and its indicator pill at 64×32dp, so there
 * is no public API to shrink the pill's vertical extent. This composable
 * reproduces the same visual contract (four equal-weight tabs, pill behind
 * the selected icon, primaryContainer highlight, ripple feedback) while
 * letting us choose a smaller bar height and a thinner pill.
 *
 * Sizing: bar is 60dp tall (vs M3's 80dp), the pill is 56×28dp with a 14dp
 * corner radius (vs M3's 64×32 with 16dp). Combined with the smaller icon
 * and label in [BottomTabItem] this gives the bar a noticeably lighter
 * footprint, which is the right call for a four-tab app on a phone — the
 * M3 default is designed for five-tab layouts and looks oversized here.
 *
 * Hit-target contract: the entire 60dp row per tab is clickable (so the
 * 48dp minimum tap target is satisfied by the row's full width × 60dp
 * height), but the pill is purely visual and does not enlarge the hit area.
 *
 * The bar consumes the system navigation-bar insets via [windowInsetsPadding]
 * so the background extends behind the gesture bar on edge-to-edge devices,
 * but the clickable row stays above it (gesture-bar swipe still works).
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            ) {
                TopLevelDestination.entries.forEach { dest ->
                    val selected = dest.route == currentRoute
                    BottomTabItem(
                        destination = dest,
                        selected = selected,
                        onClick = { onNavigate(dest) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/**
 * One tab cell. Layout (top to bottom):
 *   - 28dp pill area — shows the primaryContainer background when selected,
 *     transparent otherwise (so the row's surface bleeds through).
 *   - 4dp spacer.
 *   - 10sp label.
 *
 * The pill is centered horizontally inside the 28dp slot with 8dp side
 * padding, giving it 56dp width (cell width minus 16dp on a 360dp-wide
 * phone = 90dp-16dp = 74dp, but we cap it via the pill's explicit 56dp
 * width so it looks the same on tablets).
 */
@Composable
private fun BottomTabItem(
    destination: TopLevelDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val label = stringResource(destination.labelRes)
    val iconColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    val textColor = iconColor
    val pillColor = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null, // ripple drawn manually below for finer control
            ) { onClick() }
            .indication(
                interactionSource = interactionSource,
                indication = rememberRipple(bounded = false, radius = 48.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            // Pill slot: 28dp tall, centers a 56×28dp rounded rect.
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) pillColor else androidx.compose.ui.graphics.Color.Transparent)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp),
                )
            }
            // 2dp breathing room between pill and label.
            Box(modifier = Modifier.size(height = 2.dp, width = 1.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
