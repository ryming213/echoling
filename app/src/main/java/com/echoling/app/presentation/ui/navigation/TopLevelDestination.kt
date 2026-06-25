package com.echoling.app.presentation.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.ui.graphics.vector.ImageVector
import com.echoling.app.R

/**
 * One row in the bottom navigation bar. Each entry pairs a route (which must
 * exist in [Screen]) with a label, an icon set, and the set of all routes
 * that "are this tab" — used by [MainScaffold] to decide when to render the
 * bar.
 *
 * `Recite` (the "记单词" tab) replaces the old `Learning` / `学习` tab —
 * the icon moved from `Icons.Filled.School` to `Icons.Filled.Style` so the
 * bar reads as "flashcards / cards" rather than "graduation cap" (the new
 * tab is a vocabulary picker, not an academic dashboard).
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Courses(
        route = Screen.Courses.route,
        labelRes = R.string.tab_courses,
        selectedIcon = Icons.Filled.MenuBook,
        unselectedIcon = Icons.Outlined.MenuBook,
    ),
    Vocabulary(
        route = Screen.Vocabulary.route,
        labelRes = R.string.tab_vocabulary,
        selectedIcon = Icons.Filled.Translate,
        unselectedIcon = Icons.Outlined.Translate,
    ),
    Recite(
        route = Screen.Recite.route,
        labelRes = R.string.tab_recite,
        selectedIcon = Icons.Filled.Style,
        unselectedIcon = Icons.Outlined.Style,
    ),
    Me(
        route = Screen.Me.route,
        labelRes = R.string.tab_me,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
    );

    companion object {
        /** All tab routes — used by [MainScaffold] for bar visibility. */
        val tabRoutes: Set<String> = entries.map { it.route }.toSet()

        /** Lookup by current route, or null if route is not a tab. */
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}
