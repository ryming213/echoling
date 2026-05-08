package com.echoling.app.presentation.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object CourseList : Screen("course_list")
    data object CourseDetail : Screen("course_detail/{courseId}") {
        fun createRoute(courseId: String) = "course_detail/$courseId"
    }
    data object Practice : Screen("practice/{courseId}/{audioUri}/{videoUri}/{subtitleUri}") {
        fun createRoute(courseId: String, audioUri: String?, videoUri: String?, subtitleUri: String?) =
            "practice/$courseId/${audioUri.orEmpty().ifBlank { "null" }.encodeForRoute()}/${videoUri.orEmpty().ifBlank { "null" }.encodeForRoute()}/${subtitleUri.orEmpty().ifBlank { "null" }.encodeForRoute()}"
    }
    data object Vocabulary : Screen("vocabulary")
    data object Settings : Screen("settings")
    data object Statistics : Screen("statistics")
    data object Import : Screen("import")
}

private fun String.encodeForRoute(): String = java.net.URLEncoder.encode(this, "UTF-8")

fun String.decodeFromRoute(): String = java.net.URLDecoder.decode(this, "UTF-8")
