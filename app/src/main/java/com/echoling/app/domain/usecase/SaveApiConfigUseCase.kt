package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.ApiConfig
import com.echoling.app.domain.repository.ApiConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

sealed class SaveApiConfigResult {
    object Success : SaveApiConfigResult()
    object EmptyFields : SaveApiConfigResult()
}

/**
 * Persist an [ApiConfig]. Returns [SaveApiConfigResult.EmptyFields]
 * when App ID or App Key is blank — the UI surfaces this as a
 * validation error so the user can't save a half-filled card.
 */
@Singleton
class SaveApiConfigUseCase @Inject constructor(
    private val repository: ApiConfigRepository,
) {
    suspend operator fun invoke(config: ApiConfig): SaveApiConfigResult {
        if (config.appId.isBlank() || config.appKey.isBlank()) {
            return SaveApiConfigResult.EmptyFields
        }
        repository.save(config)
        return SaveApiConfigResult.Success
    }
}
