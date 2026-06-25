package com.echoling.app.presentation.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.echoling.app.presentation.ui.screens.splash.SplashScreen
import kotlinx.coroutines.delay

/** How long the cold-start splash is held before the app shell takes over. */
private const val SPLASH_DURATION_MS = 1_000L

/**
 * Top-level app shell: wraps the single [EchoLingNavGraph] in a [Scaffold]
 * whose [Scaffold.bottomBar] is the [AppBottomBar]. The bar is hidden on
 * any route that is not a [TopLevelDestination] (sub-pages like Practice,
 * CourseDetail, Statistics, Import get the full screen).
 *
 * On first composition we paint a [SplashScreen] for [SPLASH_DURATION_MS]
 * so the user always sees a deliberate hero image at launch instead of
 * an instantaneous flicker between the system launch theme and the
 * Compose content. Once the delay elapses the splash is replaced by the
 * real nav graph in the same recomposition pass — no animation, no
 * pop-in.
 */
@Composable
fun MainScaffold(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Courses.route,
) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        showSplash = false
    }

    if (showSplash) {
        SplashScreen(modifier = Modifier.fillMaxSize())
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in TopLevelDestination.tabRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { dest ->
                        navController.navigate(dest.route) {
                            // Pop back to the start destination so the back
                            // stack doesn't grow on every tab tap.
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same tab root.
                            launchSingleTop = true
                            // Restore state when re-selecting a previously
                            // selected tab (scroll position etc.).
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        EchoLingNavGraph(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}