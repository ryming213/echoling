package com.echoling.app.data.local

import com.echoling.app.domain.model.Course
import com.echoling.app.domain.repository.CourseRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseSeeder @Inject constructor(
    private val courseRepository: CourseRepository
) {
    suspend fun seedIfEmpty() {
        // Demo courses removed - user imports their own courses
    }
}
