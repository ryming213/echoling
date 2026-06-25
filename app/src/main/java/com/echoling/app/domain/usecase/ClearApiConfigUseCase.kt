package com.echoling.app.domain.usecase

import com.echoling.app.domain.repository.ApiConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clear the stored sentence-grading credentials and disable the
 * feature. With the translation API gone, only one card remains, so
 * the use case no longer needs to disambiguate by `ApiKind`.
 */
@Singleton
class ClearApiConfigUseCase @Inject constructor(
    private val repository: ApiConfigRepository,
) {
    suspend operator fun invoke() {
        repository.clear()
    }
}
