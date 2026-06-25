package com.echoling.app.domain.repository

import com.echoling.app.domain.model.ApiConfig
import com.echoling.app.domain.model.ApiConfigs
import kotlinx.coroutines.flow.Flow

/**
 * Read/write API credentials in a secure store. After the translation
 * API removal the repository holds a single grading configuration;
 * the contract keeps [ApiConfigs] as a wrapper so additional cards
 * can be added later without changing call sites.
 */
interface ApiConfigRepository {
    /** Reactive view of the stored grading API configuration. */
    fun observeAll(): Flow<ApiConfigs>

    /** One-shot read of the stored grading API configuration. */
    suspend fun getAll(): ApiConfigs

    /** Persist [config]. Overwrites any previous values. */
    suspend fun save(config: ApiConfig)

    /**
     * Clear the stored credentials and disable the feature. Replaces
     * the previous per-[com.echoling.app.domain.model.ApiKind]
     * overload — there is only one kind now.
     */
    suspend fun clear()
}
