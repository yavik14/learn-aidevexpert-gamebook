package com.playbook.core.brainstorming

import com.playbook.core.model.GameProjectDraft
import com.playbook.core.model.KeyMechanic
import com.playbook.core.model.MechanicCategory
import com.playbook.core.model.Track
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Ejercita [buildBrainstormingStepNote] contra el mapeo de
 * `docs/specs/brainstorming-note-per-step.md`: los 9 pasos, listas/vacíos,
 * Mecánicas con categoría + descripción, normalización de Géneros, contenido
 * vacío, determinismo y rango de `step`.
 */
fun verifyBrainstormingNoteMapper() {
    val genres = listOf(" platformer ", "JRPG", "JRPG")
    val expectedGenres = listOf("JRPG", "Platformer")
    val draft = GameProjectDraft(
        owner = "local",
        title = " Proyecto Aurora ",
        genres = genres,
        coreLoop = " Explorar y combatir ",
        audience = " Jugadores ",
        concept = " Metroidvania ",
        inspirations = listOf("Hollow Knight", " ", "Celeste"),
        fantasy = " Poder ",
        keyMechanics = listOf(
            KeyMechanic(MechanicCategory.MOVEMENT, " Dash "),
            KeyMechanic(MechanicCategory.INTERACTION, "Abrir puertas"),
            KeyMechanic(MechanicCategory.CONFLICT, "   "),
        ),
        outOfScope = listOf("Multijugador", " ", "VR"),
    )

    val expectedBodies = mapOf(
        0 to "Proyecto Aurora",
        1 to "JRPG, Platformer",
        2 to "Explorar y combatir",
        3 to "Jugadores",
        4 to "Metroidvania",
        5 to "Hollow Knight\nCeleste",
        6 to "Poder",
        7 to "Movimiento: Dash\nInteracción: Abrir puertas",
        8 to "Multijugador\nVR",
    )
    val expectedTracks = mapOf(
        2 to Track.MECHANICS,
        7 to Track.MECHANICS,
        4 to Track.STORY,
        6 to Track.STORY,
    )

    expectedBodies.forEach { (step, expectedBody) ->
        val note = buildBrainstormingStepNote(step, draft)
        assertEquals(expectedBody, note.body, "body del paso $step")
        assertEquals(expectedTracks[step], note.track, "track del paso $step")
        assertEquals(expectedGenres, note.genres, "genres del paso $step")
    }

    // Todos los pasos con un draft vacío producen un body vacío; el mapeo de
    // Track sigue siendo estable.
    val emptyDraft = GameProjectDraft(owner = "local")
    for (step in 0 until BRAINSTORMING_STEP_COUNT) {
        val note = buildBrainstormingStepNote(step, emptyDraft)
        assertEquals("", note.body, "body vacío del paso $step")
        assertEquals(emptyList(), note.genres, "genres vacíos del paso $step")
    }
    assertNull(buildBrainstormingStepNote(0, emptyDraft).track)
    assertEquals(Track.MECHANICS, buildBrainstormingStepNote(2, emptyDraft).track)
    assertEquals(Track.STORY, buildBrainstormingStepNote(4, emptyDraft).track)

    // Determinismo: el mismo draft produce el mismo resultado.
    assertEquals(
        buildBrainstormingStepNote(7, draft),
        buildBrainstormingStepNote(7, draft),
    )

    // Rango inválido: fail-fast, no un mapeo silencioso.
    assertFailsWith<IllegalArgumentException> { buildBrainstormingStepNote(-1, draft) }
    assertFailsWith<IllegalArgumentException> { buildBrainstormingStepNote(9, draft) }
}
