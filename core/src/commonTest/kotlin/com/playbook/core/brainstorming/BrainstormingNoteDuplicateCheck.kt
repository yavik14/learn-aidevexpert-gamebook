package com.playbook.core.brainstorming

import com.playbook.core.model.Note
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regresión del bloqueo de duplicados: los Géneros de la Nota existente se
 * normalizan antes de comparar, por lo que un orden/grafía distinto (p. ej.
 * `["Zeta", "omega"]` vs `["omega", "Zeta"]` o `"jrpg"` vs `"JRPG"`) sigue
 * detectando el duplicado.
 */
fun verifyBrainstormingNoteDuplicate() {
    fun note(
        genres: List<String>,
        body: String = "Idea",
        track: Track? = null,
    ) = Note(
        id = "note-1",
        owner = "local",
        body = body,
        track = track,
        status = NoteStatus.CAPTURED,
        createdAt = 1,
        updatedAt = 1,
        genres = genres,
    )

    assertTrue(
        isDuplicateBrainstormingNote(
            note(genres = listOf("Zeta", "omega")),
            body = "Idea",
            track = null,
            genres = listOf("omega", "Zeta"),
        ),
        "el orden distinto no debe impedir detectar el duplicado",
    )
    assertTrue(
        isDuplicateBrainstormingNote(
            note(genres = listOf("jrpg", "Platformer")),
            body = "Idea",
            track = null,
            genres = listOf("JRPG", "platformer"),
        ),
        "la grafía distinta no debe impedir detectar el duplicado",
    )
    assertTrue(
        isDuplicateBrainstormingNote(
            note(genres = emptyList()),
            body = "Idea",
            track = null,
            genres = emptyList(),
        ),
        "sin Géneros también debe comparar",
    )

    assertFalse(
        isDuplicateBrainstormingNote(
            note(genres = listOf("omega", "Zeta")),
            body = "Otra idea",
            track = null,
            genres = listOf("omega", "Zeta"),
        ),
        "un body distinto no es duplicado",
    )
    assertFalse(
        isDuplicateBrainstormingNote(
            note(genres = listOf("omega", "Zeta")),
            body = "Idea",
            track = Track.STORY,
            genres = listOf("omega", "Zeta"),
        ),
        "un Track distinto no es duplicado",
    )
    assertFalse(
        isDuplicateBrainstormingNote(
            note(genres = listOf("omega")),
            body = "Idea",
            track = null,
            genres = listOf("omega", "Zeta"),
        ),
        "un conjunto de Géneros distinto no es duplicado",
    )
}
