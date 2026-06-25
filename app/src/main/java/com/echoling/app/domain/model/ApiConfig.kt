package com.echoling.app.domain.model

/**
 * Configuration for the sentence-grading API. Reserved for a future
 * speaking-scoring feature — credentials can be filled in but no network
 * call uses them yet. `isConfigured` is true once both [appId] and
 * [appKey] are non-blank, matching the validation in
 * [com.echoling.app.domain.usecase.SaveApiConfigUseCase].
 */
data class ApiConfig(
    val appId: String = "",
    val appKey: String = "",
    val enabled: Boolean = true,
) {
    val isConfigured: Boolean
        get() = appId.isNotBlank() && appKey.isNotBlank()
}

/**
 * Snapshot of the stored grading API configuration at one point in
 * time. Kept as a wrapper (rather than exposing [ApiConfig] directly) so
 * future per-card UI state can grow without breaking the snapshot
 * contract — mirrors the shape the previous translation+grading bundle
 * used.
 */
data class ApiConfigs(
    val grading: ApiConfig,
)
