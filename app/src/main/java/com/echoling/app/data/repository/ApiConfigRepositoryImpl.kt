package com.echoling.app.data.repository

import com.echoling.app.data.local.api.ApiConfigStore
import com.echoling.app.domain.model.ApiConfig
import com.echoling.app.domain.model.ApiConfigs
import com.echoling.app.domain.repository.ApiConfigRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiConfigRepositoryImpl @Inject constructor(
    private val store: ApiConfigStore,
) : ApiConfigRepository {

    override fun observeAll(): Flow<ApiConfigs> = store.state

    override suspend fun getAll(): ApiConfigs = store.snapshot()

    override suspend fun save(config: ApiConfig) {
        store.saveGrading(config.appId, config.appKey, config.enabled)
    }

    override suspend fun clear() {
        store.clearGrading()
    }
}
