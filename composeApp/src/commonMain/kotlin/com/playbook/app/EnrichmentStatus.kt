package com.playbook.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.playbook.core.model.NoteStatus

/**
 * Etiqueta legible (español) del [NoteStatus] para la UI. Es la única fuente del
 * texto mostrado al usuario y hoy coincide con `NoteStatus.code`; se mantiene
 * separada para no acoplar la presentación al valor persistido de dominio.
 */
internal fun statusLabel(status: NoteStatus): String = when (status) {
    NoteStatus.CAPTURED -> "capturada"
    NoteStatus.PENDING -> "pendiente"
    NoteStatus.INDEXED -> "indexada"
    NoteStatus.FAILED -> "fallida"
}

/**
 * Badge de Estado reutilizable (`DESIGN.md` → "Badge de Estado"): muestra la
 * etiqueta textual de [status] con una forma propia (borde + superficie
 * distinta de los chips de track/Género), de modo que el estado **nunca** se
 * comunica sólo con color. Lo usan la lista de Notas y el editor.
 */
@Composable
internal fun StatusBadge(status: NoteStatus) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Text(
            text = statusLabel(status),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
