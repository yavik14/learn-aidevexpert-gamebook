package com.playbook.core.linking

import com.playbook.core.model.Note
import com.playbook.core.model.NoteLink
import com.playbook.core.model.NoteStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Scenario 1: [buildRelatedNotes] como proyección pura de Notas relacionadas.
 * Corre como `@Test` directo en `commonTest` (Android/JVM e iOS) sin driver ni
 * base de datos: sólo modelo + enlaces en memoria.
 */
class RelatedNotesTest {

    @Test
    fun resolvesOtherEndpointFromEitherSide() {
        val origin = note("n-1")
        val other = note("n-2")
        // El enlace es canónico (n-1 < n-2); se proyecta desde ambos extremos.
        val link = NoteLink(noteAId = "n-1", noteBId = "n-2", score = 0.92, updatedAt = 1)

        val fromOrigin = buildRelatedNotes("n-1", listOf(link), listOf(origin, other))
        assertEquals(listOf(RelatedNote(other, 0.92)), fromOrigin)

        val fromOther = buildRelatedNotes("n-2", listOf(link), listOf(origin, other))
        assertEquals(
            listOf(RelatedNote(origin, 0.92)),
            fromOther,
            "abrir relacionadas desde el otro extremo resuelve la Nota de origen",
        )
    }

    @Test
    fun ordersByScoreDescending() {
        val origin = note("n-0")
        val low = note("n-1")
        val high = note("n-2")
        val mid = note("n-3")
        val links = listOf(
            NoteLink("n-0", "n-1", 0.82, 1),
            NoteLink("n-0", "n-2", 0.99, 1),
            NoteLink("n-0", "n-3", 0.90, 1),
        )

        val related = buildRelatedNotes("n-0", links, listOf(origin, low, high, mid))

        assertEquals(listOf(high, mid, low), related.map { it.note }, "score desc")
        assertEquals(listOf(0.99, 0.90, 0.82), related.map { it.score })
    }

    @Test
    fun tiesBreakByNoteIdAscending() {
        val origin = note("n-0")
        val b = note("n-b")
        val a = note("n-a")
        // Mismo score: el desempate es por `note.id` asc (a antes que b), sin
        // depender del orden de entrada.
        val links = listOf(
            NoteLink("n-0", "n-b", 0.90, 1),
            NoteLink("n-0", "n-a", 0.90, 1),
        )

        val related = buildRelatedNotes("n-0", links, listOf(origin, b, a))

        assertEquals(listOf(a, b), related.map { it.note }, "desempate por id asc")
    }

    @Test
    fun omitsLinksWhoseOtherNoteIsMissing() {
        val origin = note("n-0")
        val present = note("n-1")
        val links = listOf(
            NoteLink("n-0", "n-1", 0.95, 1),
            NoteLink("n-0", "n-ausente", 0.99, 1),
        )

        val related = buildRelatedNotes("n-0", links, listOf(origin, present))

        assertEquals(
            listOf(RelatedNote(present, 0.95)),
            related,
            "un enlace a una Nota ausente se omite sin romper",
        )
    }

    @Test
    fun excludesOriginNoteAndIgnoresUnrelatedLinks() {
        val origin = note("n-0")
        val other = note("n-1")
        val stranger = note("n-2")
        val links = listOf(
            NoteLink("n-0", "n-1", 0.9, 1),
            // Enlace entre otras dos Notas: no pertenece a la Nota de origen.
            NoteLink("n-1", "n-2", 0.99, 1),
        )

        val related = buildRelatedNotes("n-0", links, listOf(origin, other, stranger))

        assertEquals(listOf(other), related.map { it.note }, "sólo el otro extremo de la origen")
        assertTrue(related.none { it.note.id == "n-0" }, "la propia Nota nunca aparece")
    }

    @Test
    fun returnsEmptyWhenThereAreNoLinks() {
        val origin = note("n-0")
        assertEquals(emptyList(), buildRelatedNotes("n-0", emptyList(), listOf(origin)), "sin enlaces")
        assertEquals(
            emptyList(),
            buildRelatedNotes("n-0", listOf(NoteLink("n-1", "n-2", 0.9, 1)), listOf(origin, note("n-1"))),
            "sin enlaces de la Nota de origen",
        )
    }

    @Test
    fun doesNotMutateInputsAndIsDeterministic() {
        val origin = note("n-0")
        val b = note("n-b")
        val a = note("n-a")
        val links = listOf(NoteLink("n-0", "n-b", 0.9, 1), NoteLink("n-0", "n-a", 0.9, 1))
        val notes = listOf(origin, b, a)

        val first = buildRelatedNotes("n-0", links, notes)
        val second = buildRelatedNotes("n-0", links, notes)

        assertEquals(first, second, "buildRelatedNotes es determinista")
        assertEquals(listOf(a, b), first.map { it.note })
        assertEquals(
            listOf(NoteLink("n-0", "n-b", 0.9, 1), NoteLink("n-0", "n-a", 0.9, 1)),
            links,
            "no muta la lista de enlaces",
        )
        assertEquals(listOf(origin, b, a), notes, "no muta la lista de Notas")
    }
}

private fun note(id: String): Note = Note(
    id = id,
    owner = "owner-1",
    body = "cuerpo $id",
    track = null,
    status = NoteStatus.CAPTURED,
    createdAt = 0,
    updatedAt = 0,
)
