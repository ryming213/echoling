package com.echoling.app.presentation.ui.navigation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.echoling.app.presentation.ui.screens.courses.CoursesScreen
import com.echoling.app.presentation.ui.screens.me.MeScreen
import com.echoling.app.presentation.ui.screens.recite.ReciteScreen
import com.echoling.app.presentation.ui.screens.vocabulary.VocabularyScreen

/**
 * Hosts the 4 main tabs in a [HorizontalPager] so that:
 *  1. **Tap a bottom-bar tab** — [MainScaffold] calls
 *     `pagerState.animateScrollToPage(targetIndex)`. The Pager
 *     natively slides the target page in from the **right** when
 *     `target > current` (forward, e.g. 首页→单词本) or from the
 *     **left** when `target < current` (backward, e.g. 我的→记单词).
 *  2. **Swipe the screen** — the Pager handles it natively with the
 *     same right-to-left / left-to-right slide semantics. A left
 *     swipe (finger drags right-to-left) advances to the next tab;
 *     a right swipe (finger drags left-to-right) goes back to the
 *     previous tab.
 *
 * The Pager is the source of truth for the active tab. [MainScaffold]
 * reads `pagerState.currentPage` to drive the [AppBottomBar] highlight
 * (`currentRoute` derivation), so the bottom-bar pill stays in sync
 * with both tap-driven and swipe-driven page changes.
 *
 * Layout-wise the Pager sits **behind** the sub-page NavHost (rendered
 * as a sibling in [MainScaffold]'s Box). Sub-pages can therefore
 * slide on top of it without disturbing the tab-switching animation.
 * The Pager is `userScrollEnabled = false` whenever a sub-page is on
 * top, so a swipe on a sub-page never accidentally fires a tab swap.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TabPagerHost(
    pagerState: PagerState,
    navController: NavHostController,
    userScrollEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = userScrollEnabled,
    ) { page ->
        // The page index is the tab's position in [TopLevelDestination]
        // declaration order: 0=Courses, 1=Vocabulary, 2=Recite, 3=Me.
        when (page) {
            0 -> CoursesScreen(
                // §12.21: home page's top-right IconButton is a
                // Help / "使用说明" button. Statistics is still
                // reachable via the tappable StatsSummaryCard.
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
            1 -> VocabularyScreen(onNavigateBack = null)
            2 -> ReciteScreen(
                onNavigateToCategory = { categoryId ->
                    navController.navigate(Screen.CategoryStudy.createRoute(categoryId))
                },
            )
            3 -> MeScreen(
                // (2026-07-04) wires the "权限使用说明" entry on MeScreen
                // to the new sub-page route. Reaching PermissionsScreen
                // sets `currentBackStackRoute = "permissions"` in
                // MainScaffold, which already hides the bottom bar
                // because of the `isOnSubPage` check.
                onNavigateToPermissions = {
                    navController.navigate(Screen.Permissions.route)
                },
            )
        }
    }
}
