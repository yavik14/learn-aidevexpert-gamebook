package com.playbook.core.model

/**
 * Embedding persistido de una Nota (relación 1:0..1 con [Note]). El vector se
 * guarda como BLOB big-endian; `dimension` se materializa como `values.size` en
 * `EmbeddingRepository`. `indexedAt` es el instante de la última indexación.
 */
data class NoteEmbedding(
    val noteId: String,
    val values: List<Float>,
    val indexedAt: Long,
)
