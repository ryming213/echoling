package com.echoling.app.domain.usecase

import com.echoling.app.domain.model.ApiConfigs
import com.echoling.app.domain.repository.ApiConfigRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Reactive read of the stored grading API configuration. */
@Singleton
class GetApiConfigsUseCase @Inject constructor(
    private val repository: ApiConfigRepository,
) {
    operator fun invoke(): kotlinx.coroutines.flow.Flow<ApiConfigs> =
        repository.observeAll()

    suspend fun once(): ApiConfigs = repository.getAll()
}
