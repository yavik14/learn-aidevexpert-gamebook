package com.playbook.core.model

// SQLDelight genera `com.playbook.core.db.Note` para la tabla `note`; se aliasa
// para evitar la colisión con el modelo de dominio `Note`.
import com.playbook.core.db.Note as NoteRow

/** Mapea una fila SQLDelight (`NoteRow`) al modelo de dominio [Note]. */
fun NoteRow.toDomain(tags: List<String> = emptyList()): Note = Note(
    id = id,
    owner = owner,
    body = body,
    track = Track.fromCode(track),
    status = NoteStatus.fromCode(status),
    createdAt = created_at,
    updatedAt = updated_at,
    tags = tags,
)
