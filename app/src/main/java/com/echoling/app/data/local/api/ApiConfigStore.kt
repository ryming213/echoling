package com.echoling.app.data.local.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.echoling.app.domain.model.ApiConfig
import com.echoling.app.domain.model.ApiConfigs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single encrypted-prefs file holding the sentence-grading API
 * credentials. Uses the AES256_GCM scheme. Translation API keys used
 * to live here too but were removed when the practice screen switched
 * to the bundled offline dictionary (`gaokao_3500.json`); only the
 * grading config remains.
 *
 * Exposes a [StateFlow] of the current [ApiConfigs] snapshot so the
 * [ApiViewModel] can rebuild its form state whenever the user saves or
 * clears credentials. Callers should never edit the prefs file directly
 * — go through [saveGrading] / [clearGrading] so the in-memory snapshot
 * stays in sync.
 */
@Singleton
class ApiConfigStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val PREFS_NAME = "api_configs_secure"

        private const val KEY_GRADING_APP_ID = "grading.app_id"
        private const val KEY_GRADING_APP_KEY = "grading.app_key"
        private const val KEY_GRADING_ENABLED = "grading.enabled"
    }

    private val masterKey: MasterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _state = MutableStateFlow(readSnapshot())
    val state: StateFlow<ApiConfigs> = _state.asStateFlow()

    fun snapshot(): ApiConfigs = readSnapshot()

    fun saveGrading(appId: String, appKey: String, enabled: Boolean) {
        prefs.edit()
            .putString(KEY_GRADING_APP_ID, appId.trim())
            .putString(KEY_GRADING_APP_KEY, appKey.trim())
            .putBoolean(KEY_GRADING_ENABLED, enabled)
            .apply()
        _state.value = readSnapshot()
    }

    fun clearGrading() {
        prefs.edit()
            .remove(KEY_GRADING_APP_ID)
            .remove(KEY_GRADING_APP_KEY)
            .putBoolean(KEY_GRADING_ENABLED, false)
            .apply()
        _state.value = readSnapshot()
    }

    private fun readSnapshot(): ApiConfigs = ApiConfigs(
        grading = ApiConfig(
            appId = prefs.getString(KEY_GRADING_APP_ID, "").orEmpty(),
            appKey = prefs.getString(KEY_GRADING_APP_KEY, "").orEmpty(),
            enabled = prefs.getBoolean(KEY_GRADING_ENABLED, false),
        ),
    )
}
