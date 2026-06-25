package com.echoling.app.presentation.ui.screens.api

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Small success chip rendered at the top of an API config card once the
 * credentials for that provider are stored. Mirrors the visual cue that
 * used to live on the deleted translation card so users get the same
 * "you are configured" affordance on the grading card.
 */
@Composable
internal fun ConfiguredBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = "已配置",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * Bottom-of-card action row shared by every API config card. Renders a
 * "清除" (clear) button when the card is already configured, plus a
 * primary "保存" (save) button that reflects the saving state in its
 * label. Keeps the button cluster visually consistent across cards.
 */
@Composable
internal fun ApiFormActions(
    isConfigured: Boolean,
    isSaving: Boolean,
    onClear: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isConfigured) {
            OutlinedButton(
                onClick = onClear,
                enabled = !isSaving,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("清除")
            }
        }
        Button(
            onClick = onSave,
            enabled = !isSaving,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(if (isSaving) "保存中…" else "保存")
        }
    }
}
