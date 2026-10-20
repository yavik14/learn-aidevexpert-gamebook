package com.playbook.core.gdd

import com.playbook.core.model.GameProject
import com.playbook.core.model.KeyMechanic
import com.playbook.core.model.MechanicCategory
import com.playbook.core.model.Note
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun gddNote(id: String, body: String, track: Track? = null): Note =
    Note(
        id = id,
        owner = "local",
        body = body,
        track = track,
        status = NoteStatus.CAPTURED,
        createdAt = 0,
        updatedAt = 0,
    )

/** Check: título, ficha en orden, categorías capitalizadas y cuerpos por sección. */
fun verifyGddNarrationComplete() {
    val project = GameProject(
        owner = "local",
        title = "  Saga  ",
        genres = listOf("RPG", "Puzzle"),
        coreLoop = "Explorar y resolver",
        concept = "Un viaje\npor islas",
        keyMechanics = listOf(
            KeyMechanic(MechanicCategory.MOVEMENT, "  correr\nsaltar  "),
        ),
        createdAt = 0,
        updatedAt = 0,
    )
    val notes = listOf(
        gddNote("1", "Mecánica uno", Track.MECHANICS),
        gddNote("2", "   ", Track.CHARACTERS),
        gddNote("3", "Historia\ncon salto", Track.STORY),
        gddNote("4", "Suelta", null),
    )

    val narration = buildGddNarration(project, notes)

    assertEquals(
        """
        GDD — Saga
        Proyecto
        Géneros: RPG, Puzzle
        Core Loop: Explorar y resolver
        Concepto: Un viaje por islas
        Mecánicas clave: Movimiento · correr saltar
        mecánicas
        Mecánica uno
        historia
        Historia con salto
        Sin track
        Suelta
        """.trimIndent(),
        narration,
    )
}

/** Check: sin Proyecto ni contenido narrable devuelve vacío; sólo título narra. */
fun verifyGddNarrationEmptyAndTitleOnly() {
    assertEquals("", buildGddNarration(null, emptyList()))
    assertEquals("", buildGddNarration(GameProject(owner = "local", createdAt = 0, updatedAt = 0), emptyList()))
    assertEquals(
        "GDD — Sólo título",
        buildGddNarration(
            GameProject(owner = "local", title = "Sólo título", createdAt = 0, updatedAt = 0),
            emptyList(),
        ),
    )
}

/** Check: con Proyecto vacío pero Notas, la cabecera es GDD y no se muta la entrada. */
fun verifyGddNarrationWithoutProjectKeepsNotes() {
    val notes = listOf(
        gddNote("1", "Mecánica", Track.MECHANICS),
        gddNote("2", "Historia", Track.STORY),
    )
    val original = notes.toList()

    val narration = buildGddNarration(null, notes)

    assertEquals(
        "GDD\nmecánicas\nMecánica\nhistoria\nHistoria",
        narration,
    )
    assertEquals(original, notes)
    assertTrue(narration.contains("Mecánica"))
}
