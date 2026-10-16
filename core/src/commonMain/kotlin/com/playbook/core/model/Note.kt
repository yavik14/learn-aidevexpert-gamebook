package com.playbook.core.model

/**
 * Nota persistida: unidad mínima de captura del dominio. `track` es nullable
 * (edge case "Nota sin `track`"); `status` nunca es null. `genres` son los
 * Géneros del usuario (múltiples; de catálogo o personalizados); se normalizan
 * al persistir ([normalizeGenres]) y por defecto están vacíos. `indexAttempts`
 * es el presupuesto durable de reintentos de indexado (default `0`; lo consume
 * `PendingIndexingRetry`, esquema v7).
 */
data class Note(
    val id: String,
    val owner: String,
    val body: String,
    val track: Track?,
    val status: NoteStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val genres: List<String> = emptyList(),
    val indexAttempts: Int = 0,
)

/**
 * Datos de entrada para crear una Nota. `id`, `createdAt` y `updatedAt` los
 * genera el repositorio; `status` default `CAPTURED`; `genres` default vacío.
 */
data class NoteDraft(
    val owner: String,
    val body: String,
    val track: Track?,
    val status: NoteStatus = NoteStatus.CAPTURED,
    val genres: List<String> = emptyList(),
)
