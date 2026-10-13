package com.playbook.core.gdd

import com.playbook.core.model.Note
import com.playbook.core.model.Track

/**
 * Sección del GDD: un [track] (o `null` para "Sin track") con sus notas.
 *
 * `notes` conserva el orden de entrada (el que devuelve `NoteRepository.getAll()`,
 * `updated_at DESC, id DESC`).
 */
data class GddSection(
    val track: Track?,
    val notes: List<Note>,
)

/**
 * Proyecta las Notas en secciones por `track`, en orden fijo:
 * [Track.entries] (`mecánicas`, `personajes`, `historia`) y luego "Sin track".
 *
 * - Las tres secciones de `track` **siempre** están presentes (estructura estable
 *   del GDD), aunque no tengan notas.
 * - "Sin track" sólo aparece si hay notas con `track == null` (edge case
 *   "Nota sin `track`" de `docs/domain-model.md`).
 * - Dentro de cada sección las notas conservan el orden de entrada.
 * - No muta la lista de entrada ni duplica notas: la proyección es determinista.
 *
 * Es una función pura sobre el modelo (sin SQLDelight ni [com.playbook.core.ai.AiClient]):
 * el GDD no es una entidad persistida, es una vista agregada de las Notas.
 */
fun buildGdd(notes: List<Note>): List<GddSection> {
    val sections = Track.entries.map { track ->
        GddSection(track = track, notes = notes.filter { it.track == track })
    }
    val withoutTrack = notes.filter { it.track == null }
    return if (withoutTrack.isEmpty()) {
        sections
    } else {
        sections + GddSection(track = null, notes = withoutTrack)
    }
}
