package com.echoling.app.presentation.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.echoling.app.presentation.ui.screens.course.CourseDetailScreen
import com.echoling.app.presentation.ui.screens.import.ImportScreen
import com.echoling.app.presentation.ui.screens.instructions.InstructionsScreen
import com.echoling.app.presentation.ui.screens.permissions.PermissionsScreen
import com.echoling.app.presentation.ui.screens.practice.PracticeScreen
import com.echoling.app.presentation.ui.screens.recite.CategoryStudyScreen
import com.echoling.app.presentation.ui.screens.statistics.StatisticsScreen

/**
 * Internal start-destination route for the sub-page NavHost. The
 * composable for this route is a transparent [Box] — when the user is
 * on a tab (no sub-page pushed), the NavHost is parked here and the
 * [TabPagerHost] behind it is fully visible.
 *
 * When a sub-page is pushed, the NavHost transitions:
 *   - **Forward (push)**: `tab_root` slides out to the left
 *     (`slideExitLeft`), the new sub-page slides in from the right
 *     (`slideEnterRight`). The Pager underneath is gradually covered.
 *   - **Backward (pop)**: sub-page slides out to the right
 *     (`slideExitRight`), `tab_root` slides in from the left
 *     (`slideEnterLeft`). The Pager underneath is gradually revealed.
 *
 * The Pager itself does not move during a sub-page push/pop, only the
 * NavHost layer above it. This is the standard iOS-style overlay
 * pattern.
 */
private const val TAB_ROOT_ROUTE = "tab_root"

/** The dummy route used as the sub-page NavHost start destination. */
val TAB_ROOT: String = TAB_ROOT_ROUTE

// (2026-07-04) Made public instead of private so PracticeScreen can
// reference the same value to defer its heavy first-frame work past the
// slide-in animation window (see PracticeScreen.kt:LaunchedEffect).
// Without this shared constant, the screen-level delay would have to
// hard-code 350 and silently drift if the slide ever changes.
const val SUB_PAGE_NAV_ANIM_MS: Int = 350

private val slideEnterRight = slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(SUB_PAGE_NAV_ANIM_MS)
)
private val slideExitLeft = slideOutHorizontally(
    targetOffsetX = { fullWidth -> -fullWidth },
    animationSpec = tween(SUB_PAGE_NAV_ANIM_MS)
)
private val slideEnterLeft = slideInHorizontally(
    initialOffsetX = { fullWidth -> -fullWidth },
    animationSpec = tween(SUB_PAGE_NAV_ANIM_MS)
)
private val slideExitRight = slideOutHorizontally(
    targetOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(SUB_PAGE_NAV_ANIM_MS)
)

/**
 * NavHost for sub-pages only. The 4 main tabs are hosted in a
 * separate [TabPagerHost] (HorizontalPager) in [MainScaffold].
 *
 * The 4 tab routes (Courses / Vocabulary / Recite / Me) used to live
 * here too (in the original NavGraph), but were extracted to the
 * Pager in 2026-06-28 so the tab switch could be direction-aware
 * (forward tabs slide in from the right; backward tabs slide in from
 * the left) and gesture-driven (swipe left/right to change tab).
 *
 * The sub-page transitions remain the standard slide-left / slide-right
 * pattern. Sub-pages are full-screen, so the bottom bar is hidden
 * while one is on top.
 */
@Composable
fun SubPageNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TAB_ROOT_ROUTE,
        enterTransition = { slideEnterRight },
        exitTransition = { slideExitLeft },
        popEnterTransition = { slideEnterLeft },
        popExitTransition = { slideExitRight },
        modifier = modifier,
    ) {
        // Transparent placeholder. The Pager is visible underneath.
        composable(TAB_ROOT_ROUTE) {
            Box(modifier = Modifier.fillMaxSize())
        }

        composable(
            route = Screen.Import.route,
            arguments = listOf(
                navArgument(Screen.Import.ARG_COURSE_NAME) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val raw = backStackEntry.arguments?.getString(Screen.Import.ARG_COURSE_NAME)
            // The query arg was URL-encoded by `Screen.Import.createRoute`
            // when navigating from the category-detail FAB, so decode it
            // back to the original (Chinese) name. The home page passes
            // null and we leave it null.
            val prefillCourseName = raw?.takeIf { it.isNotEmpty() }?.decodeFromRoute()
            ImportScreen(
                onNavigateBack = { navController.popBackStack() },
                onImportComplete = { navController.popBackStack() },
                prefillCourseName = prefillCourseName,
            )
        }

        composable(
            route = Screen.CourseDetail.route,
            arguments = listOf(
                navArgument("courseName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val rawCourseName = backStackEntry.arguments?.getString("courseName")
                ?: return@composable
            val courseName = rawCourseName.decodeFromRoute()
            CourseDetailScreen(
                courseName = courseName,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPractice = { courseId ->
                    navController.navigate(Screen.Practice.createRoute(courseId))
                },
                // §12.19: category-detail has its own Import FAB; we
                // pass the current group name so the form pre-fills it.
                onNavigateToImport = {
                    navController.navigate(Screen.Import.createRoute(courseName))
                },
            )
        }

        composable(
            route = Screen.Practice.route,
            arguments = listOf(
                navArgument("courseId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId") ?: return@composable
            PracticeScreen(
                courseId = courseId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // §12.21: in-app "使用说明" page.
        composable(Screen.Instructions.route) {
            InstructionsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // (2026-07-04) in-app "权限使用说明" page. Required by 国内应用商店
        // review. Reached from MeScreen's "权限使用说明" entry.
        composable(Screen.Permissions.route) {
            PermissionsScreen(
                onNavigateBack = { navController.popBackStack() },
            )
        }

        // Per-category flashcard study sub-page reached from the
        // "记单词" tab. The `categoryId` route arg is the slug from
        // vocab_manifest.json (e.g. "junior", "senior", "cet4", "cet6",
        // "toefl") — URL-encoded in [Screen.CategoryStudy.createRoute]
        // and decoded here so Chinese-only slugs (none today, but the
        // helper exists) survive the round trip.
        composable(
            route = Screen.CategoryStudy.route,
            arguments = listOf(
                navArgument(Screen.CategoryStudy.ARG_CATEGORY_ID) {
                    type = NavType.StringType
                },
            ),
        ) { backStackEntry ->
            val raw = backStackEntry.arguments
                ?.getString(Screen.CategoryStudy.ARG_CATEGORY_ID)
                ?: return@composable
            val categoryId = raw.decodeFromRoute()
            CategoryStudyScreen(
                categoryId = categoryId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
