package com.echoling.app.presentation.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.echoling.app.presentation.ui.screens.course.CourseDetailScreen
import com.echoling.app.presentation.ui.screens.course.CourseListScreen
import com.echoling.app.presentation.ui.screens.home.HomeScreen
import com.echoling.app.presentation.ui.screens.import.ImportScreen
import com.echoling.app.presentation.ui.screens.practice.PracticeScreen
import com.echoling.app.presentation.ui.screens.statistics.StatisticsScreen
import com.echoling.app.presentation.ui.screens.vocabulary.VocabularyScreen

@Composable
fun EchoLingNavGraph(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCourseList = {
                    navController.navigate(Screen.CourseList.route)
                },
                onNavigateToVocabulary = {
                    navController.navigate(Screen.Vocabulary.route)
                },
                onNavigateToPractice = { courseId ->
                    navController.navigate(Screen.Practice.createRoute(courseId))
                },
                onNavigateToStatistics = {
                    navController.navigate(Screen.Statistics.route)
                }
            )
        }

        composable(Screen.CourseList.route) {
            CourseListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCourse = { courseId ->
                    navController.navigate(Screen.CourseDetail.createRoute(courseId))
                },
                onNavigateToImport = {
                    navController.navigate(Screen.Import.route)
                }
            )
        }

        composable(Screen.Import.route) {
            ImportScreen(
                onNavigateBack = { navController.popBackStack() },
                onImportComplete = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.CourseDetail.route,
            arguments = listOf(
                navArgument("courseId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId") ?: return@composable
            CourseDetailScreen(
                courseId = courseId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToPractice = {
                    navController.navigate(Screen.Practice.createRoute(courseId))
                }
            )
        }

        composable(
            route = Screen.Practice.route,
            arguments = listOf(
                navArgument("courseId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val courseId = backStackEntry.arguments?.getString("courseId") ?: return@composable

            PracticeScreen(
                courseId = courseId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Vocabulary.route) {
            VocabularyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Statistics.route) {
            StatisticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
