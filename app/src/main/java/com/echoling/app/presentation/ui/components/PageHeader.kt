package com.echoling.app.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Slim inline header that replaces [androidx.compose.material3.TopAppBar]
 * across the app. Designed for edge-to-edge layouts where content needs
 * to start right below the status bar.
 *
 * The header has a 48dp **minimum** height (slim vs M3 TopAppBar's 64dp)
 * and grows to fit its content — single-line titles stay 48dp, while
 * two-line brand bars (e.g. `每天进步一点` / `— Every day a little progress —`)
 * expand to ~60dp so the subtitle displays in full. The Row only adds
 * horizontal padding (4dp); vertical space is fully available to the
 * title content.
 *
 * It does NOT itself add [statusBarsPadding] — the caller is expected to
 * apply `.padding(padding)` on the Scaffold body, which already consumes
 * the system bar insets (when the Scaffold has no topBar, the `padding.top`
 * parameter is exactly the status bar height).
 *
 * Slots:
 * - [onBack]: optional back button on the left (ArrowBack icon, 48dp touch)
 * - [title]: any composable, takes the remaining space. Horizontal
 *   alignment is controlled by [titleAlignment] (default
 *   [Alignment.CenterHorizontally] — used by the brand-bar tabs Courses
 *   and Me). Sub-pages like Statistics, Import, Vocabulary, Api pass
 *   [Alignment.Start] to left-align the title.
 * - [actions]: trailing slot (typically IconButton or Surface pill)
 *
 * Background: `MaterialTheme.colorScheme.surface` — matches the rest of
 * the page and provides a visual boundary so the header doesn't blend
 * into the body content.
 */
@Composable
fun PageHeader(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit = {},
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    titleAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.CenterStart.let {
                // Map the requested horizontal alignment to a Box
                // contentAlignment. Center → centered, Start →
                // left-aligned, End → right-aligned.
                when (titleAlignment) {
                    Alignment.Start -> Alignment.CenterStart
                    Alignment.End -> Alignment.CenterEnd
                    else -> Alignment.Center
                }
            },
        ) {
            title()
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            actions()
        }
    }
}