package com.playbook.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Botón compartido de lectura en voz alta. Muestra "Leer en voz alta" cuando está
 * detenido, "Detener" mientras prepara/reproduce y "Reintentar" tras un error.
 * El estado se comunica además con texto, y el botón expone un
 * `contentDescription` específico por superficie (`DESIGN.md`).
 */
@Composable
fun ReadAloudButton(
    surface: ReadAloudSurface,
    surfaceLabel: String,
    state: ReadAloudUiState,
    enabled: Boolean,
    onRead: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = when (state) {
        is ReadAloudUiState.Preparando -> state.surface == surface
        is ReadAloudUiState.Reproduciendo -> state.surface == surface
        else -> false
    }
    val failed =
        state is ReadAloudUiState.Error && state.surface == surface
    val label = when {
        active -> "Detener"
        failed -> "Reintentar"
        else -> "Leer en voz alta"
    }
    val description = when {
        active -> "Detener lectura en voz alta"
        failed -> "Reintentar lectura en voz alta"
        else -> "Leer $surfaceLabel en voz alta"
    }
    val message = when (state) {
        is ReadAloudUiState.Preparando -> if (active) "Preparando audio…" else null
        is ReadAloudUiState.Reproduciendo -> if (active) "Reproduciendo…" else null
        is ReadAloudUiState.Error -> if (failed) "No se pudo leer en voz alta." else null
        ReadAloudUiState.Detenido -> null
    }

    Column(modifier = modifier) {
        OutlinedButton(
            onClick = if (active) onStop else onRead,
            enabled = active || enabled,
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .semantics { contentDescription = description },
        ) {
            Text(text = label)
        }
        message?.let { text ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = text, style = MaterialTheme.typography.bodySmall)
        }
    }
}
