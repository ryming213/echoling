package com.echoling.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.echoling.app.data.local.api.ApiConfigStore
import com.echoling.app.domain.model.ApiConfig
import com.echoling.app.domain.usecase.ClearApiConfigUseCase
import com.echoling.app.domain.usecase.GetApiConfigsUseCase
import com.echoling.app.domain.usecase.SaveApiConfigResult
import com.echoling.app.domain.usecase.SaveApiConfigUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Editable state of the grading card. */
data class GradingForm(
    val appId: String = "",
    val appKey: String = "",
    val enabled: Boolean = false,
    val isConfigured: Boolean = false,
)

/** Save status for the most recent save attempt on the grading card. */
enum class ApiSaveStatus {
    Idle, Saving, Success, EmptyFields
}

/** Aggregated state for the API config page (only the grading card now). */
data class ApiUiState(
    val grading: GradingForm = GradingForm(),
    val saveStatus: ApiSaveStatus = ApiSaveStatus.Idle,
)

/**
 * State holder for the API config page. Holds the grading card form
 * state and persists changes through the dedicated use cases. After
 * the translation API removal there is only one card on the page, so
 * the view model is correspondingly simpler — no per-provider state,
 * no per-kind dispatch.
 */
@HiltViewModel
class ApiViewModel @Inject constructor(
    private val getApiConfigs: GetApiConfigsUseCase,
    private val saveApiConfig: SaveApiConfigUseCase,
    private val clearApiConfig: ClearApiConfigUseCase,
    private val apiConfigStore: ApiConfigStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApiUiState())
    val uiState: StateFlow<ApiUiState> = _uiState.asStateFlow()

    init {
        observeStored()
    }

    fun updateGradingAppId(value: String) = updateGrading { it.copy(appId = value) }
    fun updateGradingAppKey(value: String) = updateGrading { it.copy(appKey = value) }
    fun updateGradingEnabled(value: Boolean) = updateGrading { it.copy(enabled = value) }

    fun saveGrading() {
        _uiState.value = _uiState.value.copy(saveStatus = ApiSaveStatus.Saving)
        viewModelScope.launch {
            val form = _uiState.value.grading
            val result = saveApiConfig(
                ApiConfig(
                    appId = form.appId,
                    appKey = form.appKey,
                    enabled = form.enabled,
                )
            )
            _uiState.value = _uiState.value.copy(
                saveStatus = if (result is SaveApiConfigResult.EmptyFields)
                    ApiSaveStatus.EmptyFields else ApiSaveStatus.Success,
            )
        }
    }

    fun clearGrading() {
        viewModelScope.launch {
            clearApiConfig()
            _uiState.value = _uiState.value.copy(
                grading = GradingForm(),
                saveStatus = ApiSaveStatus.Idle,
            )
        }
    }

    fun resetSaveStatus() {
        _uiState.value = _uiState.value.copy(saveStatus = ApiSaveStatus.Idle)
    }

    private fun updateGrading(transform: (GradingForm) -> GradingForm) {
        _uiState.value = _uiState.value.copy(
            grading = transform(_uiState.value.grading),
            saveStatus = ApiSaveStatus.Idle,
        )
    }

    private fun observeStored() {
        viewModelScope.launch {
            getApiConfigs().collect { configs ->
                _uiState.value = _uiState.value.copy(
                    grading = configs.grading.toForm(),
                )
            }
        }
    }

    private fun ApiConfig.toForm(): GradingForm = GradingForm(
        appId = appId,
        appKey = appKey,
        enabled = enabled,
        isConfigured = isConfigured,
    )
}
