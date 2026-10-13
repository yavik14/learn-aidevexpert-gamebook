package com.playbook.core.model

/**
 * Enlace Nota↔Nota (relación N—N con `score`) generado por similitud coseno.
 *
 * Es siempre **canónico**: [noteAId] < [noteBId] (orden lexicográfico de `id`).
 * El enlace es no dirigido por contrato; un par se persiste una sola vez y los
 * consumidores resuelven "la otra Nota" con [otherNoteId].
 *
 * @param score similitud coseno en `[-1, 1]`.
 * @param updatedAt instante del último recomputo que selló el enlace.
 */
data class NoteLink(
    val noteAId: String,
    val noteBId: String,
    val score: Double,
    val updatedAt: Long,
)

/**
 * Devuelve el id de "la otra Nota" del enlace respecto de [of]. Falla rápido
 * (`IllegalArgumentException`) si [of] no es ninguno de los dos extremos: es un
 * error de programación del consumidor, no un caso de datos.
 */
fun NoteLink.otherNoteId(of: String): String = when (of) {
    noteAId -> noteBId
    noteBId -> noteAId
    else -> throw IllegalArgumentException(
        "el id '$of' no pertenece al enlace ($noteAId, $noteBId)",
    )
}
