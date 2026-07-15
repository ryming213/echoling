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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
 * The bar handles the system navigation-bar insets via a separate
 * bottom [Spacer] (see §12.30 in CLAUDE.md) so the surface background
 * extends behind the gesture bar on edge-to-edge devices, but the
 * clickable row stays above it (gesture-bar swipe still works).
 */
@Composable
fun AppBottomBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    // §12.30c: Read the navigation-bar bottom inset. Now that the bar
    // is rendered as a SIBLING of the Scaffold (not inside its
    // `bottomBar` slot — see MainScaffold.kt), `LocalWindowInsets` is
    // NOT overridden by the outer Scaffold's
    // `contentWindowInsets = WindowInsets(0, 0, 0, 0)`, so
    // `WindowInsets.navigationBars` correctly reports the real 16dp
    // nav-bar inset on this Xiaomi Mi 11 CN (1080x2400 at 2.625x
    // density; nav bar = 42px = 16dp).
    //
    // Using `WindowInsets.navigationBars.asPaddingValues()` (instead
    // of the previous `ViewCompat.getRootWindowInsets()` call) means
    // we get the same inset value Compose uses everywhere else, AND
    // we don't need to remember-capture the view.
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val navBarBottom: Dp = navBarPadding.calculateBottomPadding()

    // §12.30c: Use a custom `Layout` to FORCE the bar's height to
    // 60dp + navBarBottom. Reason: the M3 Scaffold's `bottomBar` slot
    // subcompose-measures the bar, and both `Box.height(76.dp)` and
    // `Box.requiredHeight(76.dp)` AND `Surface.height(76.dp)` are
    // silently overridden back to the bar's content intrinsic height
    // (60dp = the Row). A custom `Layout` that explicitly returns
    // `layout(width, 76.dp.toPx())` bypasses whatever clamping the
    // Scaffold does and forces the bar to 76dp.
    //
    // The `background(surface)` paints the full area; the clickable
    // Row sits at the top (60dp) and the bottom 16dp is the nav-bar
    // zone, filled with surface color (the system gesture bar draws
    // on top of this zone, but with the surface color behind it
    // there's no white gap).
    //
    // The bar is rendered as a sibling of the Scaffold (placed with
    // `Modifier.align(Alignment.BottomCenter)` in MainScaffold), NOT
    // as a child of Scaffold's `bottomBar` slot. The `modifier`
    // parameter carries that alignment into the bar.
    val density = LocalDensity.current
    val totalHeightDp = 60.dp + navBarBottom
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
        content = {
            // The clickable Row lives at the top of the bar (60dp).
            // Anything below it (navBarBottom = 16dp) is filled by the
            // Layout's own `background` because the Layout paints its
            // background across its full measured size. Gesture swipes
            // from the bottom of the screen pass through this
            // non-clickable zone to the system.
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // §12.30b: raised from 48dp to 60dp — user reported the
                        // bar's surface area felt too thin between the tab row
                        // and the screen bottom (after §12.30's Spacer fix made
                        // the surface extend to the screen edge, the bar had
                        // zero visual weight between the row and the gesture
                        // bar). 60dp gives the bar a more substantial presence
                        // while still under M3 NavigationBar's 80dp default.
                        // Hit-target contract: 60dp row per tab, clickable.
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
    ) { measurables, constraints ->
        val heightPx = with(density) { totalHeightDp.roundToPx() }
        val placeable = measurables[0].measure(
            constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth, minHeight = 0, maxHeight = heightPx)
        )
        layout(constraints.maxWidth, heightPx) {
            placeable.placeRelative(0, 0)
        }
    }
}

/**
 * One tab cell. Layout (top to bottom):
 *   - 28dp pill area — shows the primaryContainer background when selected,
 *     transparent otherwise (so the row's surface bleeds through).
 *   - 2dp spacer.
 *   - 10sp label.
 *
 * The pill is centered horizontally inside the 28dp slot with 8dp side
 * padding, giving it 56dp width (cell width minus 16dp on a 360dp-wide
 * phone = 90dp-16dp = 74dp, but we cap it via the pill's explicit 56dp
 * width so it looks the same on tablets).
 *
 * §12.30b: The outer [Row] is now 60dp (raised from 48dp) to give the
 * bar more visual weight after the surface-extension fix made the bar
 * paint to the screen edge. Content total height (28dp pill + 2dp gap +
 * ~14dp label) is ~44dp, so a 60dp Row leaves 16dp of dead space.
 * With `Arrangement.Bottom` (preserved from §12.29) that 16dp sits at
 * the TOP of the cell (between the cell's top edge and the pill), and
 * the label hugs the BOTTOM of the cell — which is the gesture-bar
 * edge. This makes the bar look "grounded" against the screen bottom
 * rather than having the labels float mid-cell.
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
            verticalArrangement = Arrangement.Bottom,
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
