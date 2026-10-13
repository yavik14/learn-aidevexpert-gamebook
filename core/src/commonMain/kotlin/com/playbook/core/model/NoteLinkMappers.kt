package com.playbook.core.model

// SQLDelight genera `com.playbook.core.db.Note_link` para la tabla `note_link`
// (el nombre de la data class se deriva del nombre de la tabla, conservando el
// guión bajo); se aliasa para evitar la colisión con el modelo de dominio.
import com.playbook.core.db.Note_link as NoteLinkRow

/** Mapea una fila SQLDelight (`NoteLinkRow`) al modelo de dominio [NoteLink]. */
fun NoteLinkRow.toDomain(): NoteLink = NoteLink(
    noteAId = note_a_id,
    noteBId = note_b_id,
    score = score,
    updatedAt = updated_at,
)
