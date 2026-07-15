package com.echoling.app.presentation.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.echoling.app.presentation.ui.screens.splash.SplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the cold-start splash is held before the app shell takes over. */
private const val SPLASH_DURATION_MS = 1_000L

/**
 * Top-level app shell. Holds three independent state machines and wires
 * them together so that the user gets a unified, gesture-driven tab
 * experience:
 *
 *  1. **[rememberPagerState]** — the active tab. The Pager natively
 *     animates page changes in the correct direction (forward tab
 *     slides in from the right, backward tab slides in from the left)
 *     and supports swipe gestures. The state lives here so it
 *     survives a sub-page push/pop (the Pager is rendered behind the
 *     sub-page NavHost and is not recreated).
 *  2. **[rememberNavController]** — the sub-page back stack. Used only
 *     for screens that are NOT one of the 4 bottom-bar tabs
 *     (Practice, CourseDetail, Import, Statistics, Instructions,
 *     CategoryStudy). The 4 tab routes are NOT in this NavHost.
 *  3. **[AppBottomBar]** — rendered as a sibling of the Scaffold in a
 *     `Column`, not via the Scaffold's `bottomBar` slot. See
 *     "Why Column + sibling bar" below.
 *
 * # Layout
 *
 * ```
 * Column (fillMaxSize)
 *   ├─ Scaffold (weight=1, no bottomBar)   ← content area
 *   │   └─ Box (padding = innerPadding)
 *   │      ├─ TabPagerHost                  ← 4 pages, behind everything
 *   │      └─ SubPageNavGraph               ← overlaid when isOnSubPage
 *   └─ AppBottomBar (fixed height, only when !onSubPage)
 * ```
 *
 * The sub-page NavHost is overlaid on top of the Pager, so pushing a
 * sub-page slides it over the Pager (iOS-style). The Pager itself
 * stays in place underneath; the user perceives a clean slide-in
 * for the sub-page even though the underlying Pager doesn't move.
 *
 * # Why Column + sibling bar (§12.32b)
 *
 * §12.32a originally used `Box { Scaffold + AppBottomBar(align=BottomCenter) }`.
 * That worked for the bar height (60dp + nav bar inset = 76dp), but had
 * a side effect: the inner `Scaffold` (in CoursesScreen.kt) places its
 * `floatingActionButton` (the "导入素材" FAB) at the bottom-end of its
 * own content area, which is the **full screen** in the Box approach.
 * The FAB ended up at y ≈ screenHeight - 16dp - FAB_height, which is
 * **inside the bar's 76dp area**. Since the bar is drawn on top of
 * the Scaffold in the Box, the bar's surface hid the FAB.
 *
 * Fix: use a `Column` instead of a `Box`. The Scaffold takes
 * `weight(1f)` (remaining space after the bar), and the bar is a
 * fixed-height child AFTER the Scaffold in the Column. The FAB
 * inside the inner Scaffold is now positioned within the
 * Scaffold's area, which is **above the bar** — the FAB is visible.
 *
 * Edge-to-edge still works: the Column fills the screen, the bar
 * sits at the bottom of the Column (= bottom of the screen), and
 * the bar's `background(MaterialTheme.colorScheme.surface)` paints
 * the bottom 76dp. The system gesture handle is drawn on top of
 * the bottom 16dp of the bar's surface (with `isNavigationBarContrastEnforced
 * = false` set in MainActivity so no scrim is applied).
 *
 * # Why not the Scaffold's `bottomBar` slot (§12.32a)
 *
 * We initially used `Scaffold(bottomBar = AppBottomBar(...))` and
 * tried every reasonable way to make the bar measure to 76dp
 * (60dp Row + 16dp nav-bar inset): `Box.height(76.dp)`,
 * `Box.requiredHeight(76.dp)`, `Surface.height(76.dp)`, and a custom
 * `Layout { measurables -> layout(w, 76.dp.toPx()) }` that
 * explicitly returned 200px from its measure block. All of them
 * measured correctly (logcat confirmed `layout(1080, 200)`) but
 * the Scaffold still placed the bar at 158px (60dp). The M3 1.1.2
 * Scaffold's `bottomBar` slot uses SubcomposeLayout with `looseConstraints`
 * and reads `bottomBarPlaceables.maxByOrNull { it.height }?.height`
 * — but empirically on this device the rendered height is clamped
 * to the bar's content intrinsic size, ignoring the placeable's
 * own measured height. See §12.32a in CLAUDE.md for the full chain.
 *
 * # Why `contentWindowInsets = WindowInsets(0)`
 *
 * Every page-level screen wraps its content in another `Scaffold`
 * (CoursesScreen, MeScreen, ImportScreen, etc.) and those inner
 * Scaffolds default to `contentWindowInsets = WindowInsets.systemBars`.
 * If THIS outer Scaffold also consumes systemBars, the status-bar
 * height is added to the content's top via `padding(innerPadding)`
 * AND then the inner Scaffold adds it AGAIN, producing `PageHeader`
 * at `2 × status_bar_height` from the top of the window. Setting
 * `contentWindowInsets = WindowInsets(0)` here means the outer's
 * `innerPadding` is empty. The inner Scaffolds are then free to add
 * the status-bar inset exactly once.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScaffold(
    navController: NavHostController = rememberNavController(),
) {
    // 2026-07-03: switch to `rememberSaveable` so the "splash already
    // shown" flag survives configuration changes (rotation, theme
    // change, font scale). Without this, every config change destroys
    // and recreates `MainScaffold`, re-initializing `showSplash = true`
    // and re-firing the 1-second `LaunchedEffect` — the user reported
    // the splash image re-appearing on every portrait↔landscape
    // rotation. With `rememberSaveable` the Boolean is written to the
    // activity's saved Bundle the moment the delay completes, so on
    // recreation the restored value is `false` and the Compose splash
    // is skipped. The manifest-level `Theme.EchoLing.Splash` window
    // background is a separate (sub-100ms) flash and is also
    // suppressed via the `savedInstanceState != null` check in
    // `MainActivity.onCreate`.
    var showSplash by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen(modifier = Modifier.fillMaxSize())
        return
    }

    // ── State machines ──────────────────────────────────────────────────
    val pagerState = rememberPagerState(pageCount = { 4 })
    val backStackEntry by navController.currentBackStackEntryAsState()
    val coroutineScope = rememberCoroutineScope()

    // The NavHost's start destination is the transparent `tab_root`
    // placeholder. When the user is on a tab (no sub-page pushed),
    // backStackEntry is either null or at `tab_root`. When the user
    // is on a real sub-page, backStackEntry is at a sub-page route.
    val currentBackStackRoute = backStackEntry?.destination?.route
    val isOnSubPage = currentBackStackRoute != null && currentBackStackRoute != TAB_ROOT
    val showBottomBar = !isOnSubPage

    // `currentRoute` is the source of truth for [AppBottomBar] highlight.
    //   - On a tab: derived from `pagerState.currentPage` (the Pager
    //     knows which tab is active regardless of whether the user
    //     got there by tap or swipe).
    //   - On a sub-page: derived from the navController. The bottom
    //     bar is hidden anyway in this case, but the value is kept
    //     consistent for any other consumers.
    val currentRoute: String? = if (isOnSubPage) {
        currentBackStackRoute
    } else {
        TopLevelDestination.entries.getOrNull(pagerState.currentPage)?.route
    }

    // §12.32b: use a Column instead of a Box. The Scaffold takes
    // `weight(1f)` (remaining space after the bar), and the bar is a
    // fixed-height child AFTER the Scaffold. This way the inner
    // Scaffold's FAB (in CoursesScreen.kt) is positioned within the
    // Scaffold's area, ABOVE the bar — the FAB is no longer hidden
    // behind the bar.
    Column(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.weight(1f),
            // No bottomBar — the AppBottomBar is rendered as a
            // sibling in the outer Column (see below). §12.32a
            // explained why the bottomBar slot clamps the bar to
            // 60dp; the Column approach is the actual fix.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                // Pager (background). `userScrollEnabled = false` whenever a
                // sub-page is on top so a swipe on the sub-page never
                // accidentally fires a tab swap.
                TabPagerHost(
                    pagerState = pagerState,
                    navController = navController,
                    userScrollEnabled = !isOnSubPage,
                )

                // Sub-page NavHost (overlay). ALWAYS mounted so the
                // navController's graph is set the moment the user first
                // taps a sub-page link from a tab. If we only mount on
                // isOnSubPage, the first navController.navigate(...) from
                // inside a tab screen (e.g. tap a course in CoursesScreen)
                // would fire before SubPageNavGraph has run setGraph(),
                // and NavController would throw
                // `IllegalArgumentException: Navigation graph has not been
                // set for NavController`. The visible "transparent
                // placeholder" path stays identical — the Pager shows
                // through the tab_root Box — but the NavController is
                // always warm.
                SubPageNavGraph(
                    navController = navController,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // §12.32b: AppBottomBar as a sibling AFTER the Scaffold in the
        // outer Column. The bar takes its natural measured height
        // (60dp + navBarBottom = 76dp on this device). The bar's
        // surface color extends to the screen bottom (no white gap),
        // and the inner Scaffold's FAB is no longer hidden behind it.
        if (showBottomBar && currentRoute != null) {
            AppBottomBar(
                currentRoute = currentRoute,
                onNavigate = { dest ->
                    // Tap a tab → drive the Pager. Do NOT navigate
                    // in the NavController — the Pager IS the tab
                    // navigator now. `animateScrollToPage` slides
                    // the new page in from the right (target >
                    // current) or from the left (target < current).
                    val targetIndex = TopLevelDestination.entries
                        .indexOf(dest)
                        .coerceAtLeast(0)
                    if (targetIndex != pagerState.currentPage) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(targetIndex)
                        }
                    }
                },
            )
        }
    }
}
