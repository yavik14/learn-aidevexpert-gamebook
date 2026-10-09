package com.playbook.core.model

// SQLDelight genera `com.playbook.core.db.Attachment` para la tabla `attachment`;
// se aliasa para evitar la colisión con el modelo de dominio `Attachment`.
import com.playbook.core.db.Attachment as AttachmentRow

/** Mapea una fila SQLDelight (`AttachmentRow`) al modelo de dominio [Attachment]. */
fun AttachmentRow.toDomain(): Attachment = Attachment(
    id = id,
    noteId = note_id,
    kind = AttachmentKind.fromCode(kind),
    mimeType = mime_type,
    filePath = file_path,
    byteSize = byte_size,
    createdAt = created_at,
)
