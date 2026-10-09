package com.playbook.core.model

// SQLDelight genera `com.playbook.core.db.Embedding` para la tabla `embedding`;
// se aliasa para evitar la colisión con el modelo de dominio `NoteEmbedding`.
import com.playbook.core.db.Embedding as EmbeddingRow
import com.playbook.core.repository.EmbeddingBlobCodec

/**
 * Mapea una fila SQLDelight (`EmbeddingRow`) al modelo de dominio
 * [NoteEmbedding]. Decodifica el BLOB y hace fail-fast si `dimension` no coincide
 * con el tamaño del vector: ese desalineamiento significa corrupción.
 */
fun EmbeddingRow.toDomain(): NoteEmbedding {
    val values = EmbeddingBlobCodec.decode(vector)
    check(dimension == values.size.toLong()) {
        "dimension desalineada para note_id=$note_id: columna=$dimension, vector=${values.size}"
    }
    return NoteEmbedding(
        noteId = note_id,
        values = values,
        indexedAt = indexed_at,
    )
}
