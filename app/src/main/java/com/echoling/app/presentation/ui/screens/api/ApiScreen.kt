package com.echoling.app.presentation.ui.screens.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echoling.app.presentation.ui.components.PageHeader
import com.echoling.app.presentation.viewmodel.ApiSaveStatus
import com.echoling.app.presentation.viewmodel.ApiViewModel
import kotlinx.coroutines.delay

/**
 * API config page. After the translation API was removed (the practice
 * screen now uses the bundled offline dictionary) the page only hosts
 * the sentence-grading card. Reachable from the Me tab → "API 配置"
 * row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiScreen(
    onNavigateBack: (() -> Unit)? = null,
    viewModel: ApiViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saveStatus) {
        if (uiState.saveStatus == ApiSaveStatus.Success) {
            delay(2000)
            viewModel.resetSaveStatus()
        }
    }

    Scaffold(
        // No topBar — the "API 配置" title sits in the PageHeader below (§12.18)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PageHeader(
                onBack = onNavigateBack,
                title = {
                    Text(
                        text = "API 配置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val isSaving = uiState.saveStatus == ApiSaveStatus.Saving
                val errorMessage = when (uiState.saveStatus) {
                    ApiSaveStatus.EmptyFields -> "请填写 App ID 和 App Key"
                    else -> null
                }
                val successMessage = when (uiState.saveStatus) {
                    ApiSaveStatus.Success -> "保存成功"
                    else -> null
                }

                GradingApiCard(
                    appId = uiState.grading.appId,
                    appKey = uiState.grading.appKey,
                    enabled = uiState.grading.enabled,
                    isConfigured = uiState.grading.isConfigured,
                    isSaving = isSaving,
                    errorMessage = errorMessage,
                    successMessage = successMessage,
                    onAppIdChange = viewModel::updateGradingAppId,
                    onAppKeyChange = viewModel::updateGradingAppKey,
                    onEnabledChange = viewModel::updateGradingEnabled,
                    onSave = viewModel::saveGrading,
                    onClear = viewModel::clearGrading,
                )
            }
        }
    }
}
