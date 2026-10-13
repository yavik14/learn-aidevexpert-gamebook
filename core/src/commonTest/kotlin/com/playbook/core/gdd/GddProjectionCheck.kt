package com.playbook.core.gdd

import com.playbook.core.model.Note
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ejercita [buildGdd] como proyección pura: orden fijo de secciones, agrupación
 * correcta, conservación del orden de entrada, edge case "Sin track" y ausencia
 * de mutación/duplicados. Vive en `commonTest` para correr en Android/JVM e iOS.
 */
fun verifyGddProjection() {
    // --- Base vacía: las tres secciones de track, sin "Sin track" ----------
    val empty = buildGdd(emptyList())
    assertEquals(
        listOf(Track.MECHANICS, Track.CHARACTERS, Track.STORY),
        empty.map { it.track },
        "las tres secciones de track siempre están, en orden fijo",
    )
    assertTrue(empty.all { it.notes.isEmpty() }, "las secciones arrancan vacías")

    // --- Agrupación por track con una nota sin track -----------------------
    val mechanics1 = note("m1", Track.MECHANICS, updatedAt = 400)
    val mechanics2 = note("m2", Track.MECHANICS, updatedAt = 300)
    val characters = note("c1", Track.CHARACTERS, updatedAt = 200)
    val story = note("s1", Track.STORY, updatedAt = 100)
    val orphan = note("o1", track = null, updatedAt = 50)
    // Orden de entrada = orden de `getAll()` (updated_at DESC): m1, m2, c1, s1, o1.
    val input = listOf(mechanics1, mechanics2, characters, story, orphan)

    val sections = buildGdd(input)

    assertEquals(
        listOf(Track.MECHANICS, Track.CHARACTERS, Track.STORY, null),
        sections.map { it.track },
        "orden fijo de secciones y 'Sin track' al final cuando hay notas sin track",
    )
    assertEquals(listOf(mechanics1, mechanics2), sections[0].notes, "mecánicas conserva el orden")
    assertEquals(listOf(characters), sections[1].notes, "personajes agrupa su nota")
    assertEquals(listOf(story), sections[2].notes, "historia agrupa su nota")
    assertEquals(listOf(orphan), sections[3].notes, "las notas sin track van a 'Sin track'")

    // --- Ninguna nota queda fuera ni duplicada -----------------------------
    val projected = sections.flatMap { it.notes }
    assertEquals(input.size, projected.size, "la proyección no duplica ni pierde notas")
    assertEquals(input.toSet(), projected.toSet(), "el conjunto de notas proyectadas es el de entrada")

    // --- La proyección no muta la lista de entrada -------------------------
    assertEquals(input, listOf(mechanics1, mechanics2, characters, story, orphan))

    // --- Determinismo: dos llamadas devuelven lo mismo ---------------------
    assertEquals(sections, buildGdd(input), "buildGdd es determinista")

    // --- Notas sólo sin track: las tres secciones de track siguen presenten -
    val onlyOrphans = buildGdd(listOf(orphan, note("o2", track = null, updatedAt = 10)))
    assertEquals(
        listOf(Track.MECHANICS, Track.CHARACTERS, Track.STORY, null),
        onlyOrphans.map { it.track },
        "con sólo notas sin track, las tres secciones de track y 'Sin track' están",
    )
    assertEquals(2, onlyOrphans.last().notes.size, "'Sin track' agrupa todas las notas sin track")
    assertTrue(
        onlyOrphans.dropLast(1).all { it.notes.isEmpty() },
        "las secciones de track quedan vacías pero presentes",
    )
}

private fun note(id: String, track: Track?, updatedAt: Long): Note = Note(
    id = id,
    owner = "owner-1",
    body = "cuerpo $id",
    track = track,
    status = NoteStatus.CAPTURED,
    createdAt = updatedAt,
    updatedAt = updatedAt,
)
