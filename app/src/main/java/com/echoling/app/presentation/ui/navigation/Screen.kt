package com.echoling.app.presentation.ui.navigation

sealed class Screen(val route: String) {
    // Tab roots (bottom bar visible)
    data object Courses    : Screen("courses")
    data object Vocabulary : Screen("vocabulary")
    // Renamed from "Learning" / "学习" to "Recite" / "记单词" — the tab
    // is now a category picker (初中 / 高中 / CET-4 / CET-6 / 托福) and
    // tapping a card navigates to the per-category flashcard study
    // screen at [CategoryStudy]. Keeping the internal route slug
    // "recite" (not "learning") to match the new naming.
    data object Recite     : Screen("recite")
    data object Me         : Screen("me")

    // Sub-pages (bottom bar hidden)
    // The route parameter is `courseName` (the parent group) — the
    // screen shows the lessons in that group; tapping a lesson goes
    // directly to Practice (§12.19). URL-encode the name so Chinese
    // characters, spaces, and slashes survive the round trip.
    data object CourseDetail : Screen("course_detail/{courseName}") {
        fun createRoute(courseName: String): String =
            "course_detail/${courseName.encodeForRoute()}"
    }
    data object Practice : Screen("practice/{courseId}") {
        fun createRoute(courseId: String) = "practice/$courseId"
    }
    data object Statistics : Screen("statistics")
    // §12.22: API config moved off the bottom bar but still reachable
    // from the Me tab as a sub-page, so user-entered translation API
    // credentials are preserved and the screen remains editable.
    data object ApiConfig  : Screen("api_config")
    // §12.19: the import screen accepts an optional `courseName` query
    // param. When the user is on a category-detail sub-page and taps
    // the FAB, the parent group name is pre-filled into the form so
    // they only need to type the per-lesson title. The home page's FAB
    // calls the no-arg overload.
    data object Import : Screen("import?courseName={courseName}") {
        const val ARG_COURSE_NAME = "courseName"
        fun createRoute(courseName: String? = null): String =
            if (courseName.isNullOrBlank()) "import"
            else "import?courseName=${courseName.encodeForRoute()}"
    }
    // §12.21: in-app "使用说明" page. Reached from the home page's
    // top-right Help IconButton.
    data object Instructions : Screen("instructions")

    // Per-category flashcard study sub-page. Tapping a category card
    // on the "记单词" tab navigates here with the category slug (e.g.
    // "junior", "senior", "cet4"). The screen header renders the
    // category's display name (resolved from the manifest), and the
    // flashcard iteration uses the per-category entries list — so
    // studying CET-4 shows the CET-4-specific translation rather than
    // the cross-category merged lookup.
    data object CategoryStudy : Screen("recite/{categoryId}") {
        const val ARG_CATEGORY_ID = "categoryId"
        fun createRoute(categoryId: String): String =
            "recite/${categoryId.encodeForRoute()}"
    }
}

private fun String.encodeForRoute(): String = java.net.URLEncoder.encode(this, "UTF-8")

fun String.decodeFromRoute(): String = java.net.URLDecoder.decode(this, "UTF-8")
