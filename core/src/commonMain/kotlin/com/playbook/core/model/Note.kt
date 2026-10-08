package com.playbook.core.model

/**
 * Nota persistida: unidad mínima de captura del dominio. `track` es nullable
 * (edge case "Nota sin `track`"); `status` nunca es null.
 */
data class Note(
    val id: String,
    val owner: String,
    val body: String,
    val track: Track?,
    val status: NoteStatus,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Datos de entrada para crear una Nota. `id`, `createdAt` y `updatedAt` los
 * genera el repositorio; `status` default `CAPTURED`.
 */
data class NoteDraft(
    val owner: String,
    val body: String,
    val track: Track?,
    val status: NoteStatus = NoteStatus.CAPTURED,
)
