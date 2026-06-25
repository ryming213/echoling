package com.echoling.app.presentation.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.echoling.app.presentation.ui.screens.api.ApiScreen
import com.echoling.app.presentation.ui.screens.course.CourseDetailScreen
import com.echoling.app.presentation.ui.screens.courses.CoursesScreen
import com.echoling.app.presentation.ui.screens.import.ImportScreen
import com.echoling.app.presentation.ui.screens.instructions.InstructionsScreen
import com.echoling.app.presentation.ui.screens.me.MeScreen
import com.echoling.app.presentation.ui.screens.practice.PracticeScreen
import com.echoling.app.presentation.ui.screens.recite.CategoryStudyScreen
import com.echoling.app.presentation.ui.screens.recite.ReciteScreen
import com.echoling.app.presentation.ui.screens.statistics.StatisticsScreen
import com.echoling.app.presentation.ui.screens.vocabulary.VocabularyScreen

private val slideEnterRight = slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(350)
)
private val slideExitLeft = slideOutHorizontally(
    targetOffsetX = { fullWidth -> -fullWidth },
    animationSpec = tween(350)
)
private val slideEnterLeft = slideInHorizontally(
    initialOffsetX = { fullWidth -> -fullWidth },
    animationSpec = tween(350)
)
private val slideExitRight = slideOutHorizontally(
    targetOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(350)
)

@Composable
fun EchoLingNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Courses.route,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { slideEnterRight },
        exitTransition = { slideExitLeft },
        popEnterTransition = { slideEnterLeft },
        popExitTransition = { slideExitRight },
        modifier = modifier,
    ) {
        // ── Tab roots ────────────────────────────────────────────────────
        composable(Screen.Courses.route) {
            CoursesScreen(
                // §12.21: home page's top-right IconButton is now a
                // Help / "使用说明" button (see CoursesScreen.kt).
                // Statistics is still reachable via the tappable
                // StatsSummaryCard in the body.
                onNavigateToInstructions = {
                    navController.navigate(Screen.Instructions.route)
                },
                onNavigateToStatistics = {
                    navController.navigate(Screen.Statistics.route)
                },
                onNavigateToPractice = { courseId ->
                    navController.navigate(Screen.Practice.createRoute(courseId))
                },
                onNavigateToCategory = { courseName ->
                    // §12.19: the home page now lists *groups* of
                    // courses. Tapping a group navigates to the
                    // category-detail screen with the group name.
                    navController.navigate(Screen.CourseDetail.createRoute(courseName))
                },
                onNavigateToImport = {
                    navController.navigate(Screen.Import.route)
                },
            )
        }

        composable(Screen.Vocabulary.route) {
            VocabularyScreen(onNavigateBack = null)
        }

        // The "记单词" (Recite) tab root — a category picker showing
        // 初中 / 高中 / CET-4 / CET-6 / 托福 as cards. Tapping a card
        // navigates to [Screen.CategoryStudy] for that category.
        composable(Screen.Recite.route) {
            ReciteScreen(
                onNavigateToCategory = { categoryId ->
                    navController.navigate(Screen.CategoryStudy.createRoute(categoryId))
                },
            )
        }

        composable(Screen.Me.route) {
            MeScreen(
                onNavigateToApiConfig = {
                    navController.navigate(Screen.ApiConfig.route)
                },
            )
        }

        // ── Sub-pages (no bottom bar) ───────────────────────────────────
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

        // §12.22: API config sub-page (moved off the bottom bar but
        // still reachable from Me so existing credentials stay editable).
        composable(Screen.ApiConfig.route) {
            ApiScreen(
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