package com.echoling.app.domain.model

/**
 * A folder on the Courses tab. [courseName] is the resolved name (the
 * user's input if non-blank, otherwise each lesson's own [Course.title]
 * — see [effectiveCourseName]). The list of [courses] is the lessons
 * belonging to that group, in the order produced by
 * [com.echoling.app.domain.usecase.GetCourseGroupsUseCase].
 */
data class CourseGroup(
    val courseName: String,
    val courses: List<Course>,
) {
    val count: Int get() = courses.size
}
