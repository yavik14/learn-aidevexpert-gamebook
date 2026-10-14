package com.playbook.core.linking

import com.playbook.core.model.Note
import com.playbook.core.model.NoteLink
import com.playbook.core.model.otherNoteId

/**
 * Una Nota relacionada con la Nota de origen, junto con el `score` (similitud
 * coseno) del enlace que las conecta.
 */
data class RelatedNote(
    val note: Note,
    val score: Double,
)

/**
 * Proyecta, para [noteId], las Notas del "otro extremo" de sus [links].
 *
 * Es una función **pura, determinista y sin dependencias** (ni SQLDelight ni
 * `AiClient`): sólo resuelve ids contra las [notes] dadas. La pantalla de
 * relacionadas renderiza el resultado (patrón de proyección de `buildGdd`).
 *
 * Reglas:
 * - Resuelve el otro extremo con `NoteLink.otherNoteId(noteId)`; el enlace es no
 *   dirigido, así que sirve abrir "relacionadas" desde cualquiera de los dos lados.
 * - **Omite** los enlaces cuyo otro extremo no está en [notes] (Nota ausente).
 * - La propia Nota nunca aparece (un enlace conecta dos Notas distintas).
 * - Orden: `score` **desc** y, para empates, `note.id` **asc** (desempate estable
 *   y determinista).
 * - Sin enlaces → lista vacía. No muta [links] ni [notes].
 */
fun buildRelatedNotes(
    noteId: String,
    links: List<NoteLink>,
    notes: List<Note>,
): List<RelatedNote> {
    val notesById = notes.associateBy { it.id }
    return links
        .asSequence()
        .filter { it.noteAId == noteId || it.noteBId == noteId }
        .mapNotNull { link ->
            val other = notesById[link.otherNoteId(noteId)] ?: return@mapNotNull null
            RelatedNote(note = other, score = link.score)
        }
        .sortedWith(compareByDescending<RelatedNote> { it.score }.thenBy { it.note.id })
        .toList()
}
