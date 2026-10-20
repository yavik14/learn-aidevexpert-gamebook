package com.playbook.core.gdd

import com.playbook.core.model.GameProject
import com.playbook.core.model.KeyMechanic
import com.playbook.core.model.MechanicCategory
import com.playbook.core.model.Note
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Ejercita [buildGddDocument] contra los escenarios 1–7 de
 * `docs/specs/gdd-document-hybrid.md`: ficha completa/parcial/vacía, estados sin
 * Proyecto o sin Notas, "Sin track", orden fijo, determinismo y pureza. Vive en
 * `commonTest` para correr en Android/JVM e iOS.
 */
fun verifyGddDocument() {
    // --- Scenario 1: documento completo (ficha + Notas) ---------------------
    val completeProject = project(
        title = "  Mi Juego  ",
        genres = listOf("RPG", " acción "),
        coreLoop = "Explorar\nCombatir",
        audience = "  Jugadores de acción  ",
        concept = "Una aventura breve",
        inspirations = listOf("Zelda", " Hades "),
        fantasy = "Convertirse en héroe",
        keyMechanics = listOf(
            KeyMechanic(MechanicCategory.MOVEMENT, "  correr  "),
            KeyMechanic(MechanicCategory.INTERACTION, "hablar"),
            KeyMechanic(MechanicCategory.CONFLICT, "combate"),
        ),
        outOfScope = listOf("Multijugador", " VR "),
    )
    val mechanics1 = note("m1", Track.MECHANICS, updatedAt = 500)
    val mechanics2 = note("m2", Track.MECHANICS, updatedAt = 400)
    val characters = note("c1", Track.CHARACTERS, updatedAt = 300)
    val story = note("s1", Track.STORY, updatedAt = 200)
    val orphan = note("o1", track = null, updatedAt = 100)
    val canonicalNotes = listOf(mechanics1, mechanics2, characters, story, orphan)

    val document = buildGddDocument(completeProject, canonicalNotes)

    assertEquals("Mi Juego", document.project?.title, "el título se normaliza y va en la ficha")
    assertEquals(
        listOf(
            GddProjectField("Géneros", "RPG, acción"),
            GddProjectField("Core Loop", "Explorar\nCombatir"),
            GddProjectField("Audiencia", "Jugadores de acción"),
            GddProjectField("Concepto", "Una aventura breve"),
            GddProjectField("Inspiraciones", "Zelda, Hades"),
            GddProjectField("Fantasía", "Convertirse en héroe"),
            GddProjectField(
                "Mecánicas clave",
                "Movimiento · correr\nInteracción · hablar\nConflicto · combate",
            ),
            GddProjectField("Fuera de alcance", "Multijugador, VR"),
        ),
        document.project?.fields,
        "la ficha respeta el orden fijo, omite Título y capitaliza las categorías",
    )
    assertTrue(
        document.project?.fields.orEmpty().none { it.label == "Título" },
        "el Título no se repite como campo de la ficha",
    )

    assertEquals(
        listOf(Track.MECHANICS, Track.CHARACTERS, Track.STORY, null),
        document.sections.map { it.track },
        "las secciones conservan el orden/agrupación de buildGdd",
    )
    assertEquals(
        listOf("mecánicas", "personajes", "historia", "Sin track"),
        document.sections.map { it.title },
        "los títulos de sección están en minúsculas y 'Sin track' va al final",
    )
    assertEquals(listOf(mechanics1, mechanics2), document.sections[0].notes)
    assertEquals(listOf(characters), document.sections[1].notes)
    assertEquals(listOf(story), document.sections[2].notes)
    assertEquals(listOf(orphan), document.sections[3].notes)

    val projected = document.sections.flatMap { it.notes }
    assertEquals(canonicalNotes.size, projected.size, "ninguna Nota se pierde ni se duplica")
    assertEquals(canonicalNotes.toSet(), projected.toSet(), "las Notas proyectadas son las de entrada")

    // --- Scenario 2: ficha parcial o vacía ----------------------------------
    val partial = buildGddDocument(
        project(
            title = "   ",
            genres = listOf(" ", "RPG", ""),
            coreLoop = "   ",
            audience = null,
            concept = " Concepto ",
            inspirations = listOf("", "  "),
            fantasy = "  ",
            keyMechanics = listOf(
                KeyMechanic(MechanicCategory.MOVEMENT, "   "),
                KeyMechanic(MechanicCategory.CONFLICT, " Combate "),
            ),
            outOfScope = listOf(" Online ", ""),
        ),
        emptyList(),
    )
    assertNull(partial.project?.title, "un título en blanco se normaliza a null")
    assertEquals(
        listOf(
            GddProjectField("Géneros", "RPG"),
            GddProjectField("Concepto", "Concepto"),
            GddProjectField("Mecánicas clave", "Conflicto · Combate"),
            GddProjectField("Fuera de alcance", "Online"),
        ),
        partial.project?.fields,
        "sólo aparecen campos no vacíos, en orden y con listas normalizadas",
    )

    val emptyProject = project(
        title = "Sólo título",
        genres = emptyList(),
        coreLoop = null,
        audience = " ",
        concept = null,
        inspirations = emptyList(),
        fantasy = null,
        keyMechanics = emptyList(),
        outOfScope = listOf(" "),
    )
    val emptyDocument = buildGddDocument(emptyProject, emptyList())
    assertEquals(
        GddProjectBlock(title = "Sólo título", fields = emptyList()),
        emptyDocument.project,
        "un Proyecto sin datos conserva el título y una ficha vacía",
    )
    assertNull(
        buildGddDocument(null, emptyList()).project,
        "sin Proyecto la ficha proyectada es null",
    )

    // La proyección no muta el Proyecto ni sus listas.
    val genresSnapshot = completeProject.genres.toList()
    val inspirationsSnapshot = completeProject.inspirations.toList()
    val mechanicsSnapshot = completeProject.keyMechanics.toList()
    val outOfScopeSnapshot = completeProject.outOfScope.toList()
    buildGddDocument(completeProject, canonicalNotes)
    assertEquals(genresSnapshot, completeProject.genres, "Géneros no se mutan")
    assertEquals(inspirationsSnapshot, completeProject.inspirations, "Inspiraciones no se mutan")
    assertEquals(mechanicsSnapshot, completeProject.keyMechanics, "Mecánicas no se mutan")
    assertEquals(outOfScopeSnapshot, completeProject.outOfScope, "Fuera de alcance no se muta")

    // --- Scenario 3: sin Proyecto, con Notas --------------------------------
    val withoutProject = buildGddDocument(null, canonicalNotes)
    assertNull(withoutProject.project, "sin Proyecto no hay ficha")
    assertEquals(
        listOf(Track.MECHANICS, Track.CHARACTERS, Track.STORY, null),
        withoutProject.sections.map { it.track },
        "las Notas se siguen proyectando sin Proyecto",
    )
    assertEquals(canonicalNotes.size, withoutProject.sections.flatMap { it.notes }.size)

    // --- Scenario 4: con Proyecto, sin Notas --------------------------------
    val withoutNotes = buildGddDocument(completeProject, emptyList())
    assertEquals("Mi Juego", withoutNotes.project?.title)
    assertEquals(8, withoutNotes.project?.fields?.size)
    assertEquals(
        listOf(Track.MECHANICS, Track.CHARACTERS, Track.STORY),
        withoutNotes.sections.map { it.track },
        "sin Notas la proyección mantiene las tres secciones de Track vacías",
    )
    assertTrue(withoutNotes.sections.all { it.notes.isEmpty() })

    // --- Scenario 5: sin Proyecto y sin Notas -------------------------------
    val empty = buildGddDocument(null, emptyList())
    assertNull(empty.project)
    assertEquals(
        listOf(Track.MECHANICS, Track.CHARACTERS, Track.STORY),
        empty.sections.map { it.track },
        "sin Proyecto ni Notas no hay 'Sin track' y sí las tres secciones vacías",
    )
    assertEquals(empty.sections, buildGddDocument(null, emptyList()).sections)

    // --- Scenario 6: Notas sin Track ----------------------------------------
    val orphan2 = note("o2", track = null, updatedAt = 50)
    val withOrphans = buildGddDocument(null, listOf(mechanics1, orphan, orphan2))
    assertEquals(
        listOf(Track.MECHANICS, Track.CHARACTERS, Track.STORY, null),
        withOrphans.sections.map { it.track },
        "con Notas sin track, 'Sin track' aparece al final",
    )
    assertEquals(
        listOf(orphan, orphan2),
        withOrphans.sections.last().notes,
        "'Sin track' conserva el orden de entrada",
    )
    assertEquals(3, withOrphans.sections.flatMap { it.notes }.size, "no se duplica ninguna Nota")
    assertEquals(
        listOf(Track.MECHANICS, Track.CHARACTERS, Track.STORY),
        buildGddDocument(null, listOf(mechanics1)).sections.map { it.track },
        "sin Notas huérfanas no aparece 'Sin track'",
    )

    // --- Scenario 7: determinismo y pureza ----------------------------------
    val notesSnapshot = canonicalNotes.toList()
    val projectSnapshot = completeProject.copy()
    val first = buildGddDocument(completeProject, canonicalNotes)
    val second = buildGddDocument(completeProject, canonicalNotes)
    assertEquals(first, second, "la misma entrada produce el mismo documento")
    assertEquals(notesSnapshot, canonicalNotes, "la lista de Notas no se muta")
    assertEquals(projectSnapshot, completeProject, "el Proyecto no se muta")
    assertEquals(
        canonicalNotes.map { it.id },
        first.sections.flatMap { it.notes }.map { it.id },
        "el orden relativo de las Notas se conserva",
    )
}

private fun project(
    title: String?,
    genres: List<String> = emptyList(),
    coreLoop: String? = null,
    audience: String? = null,
    concept: String? = null,
    inspirations: List<String> = emptyList(),
    fantasy: String? = null,
    keyMechanics: List<KeyMechanic> = emptyList(),
    outOfScope: List<String> = emptyList(),
): GameProject = GameProject(
    owner = "owner-1",
    title = title,
    genres = genres,
    coreLoop = coreLoop,
    audience = audience,
    concept = concept,
    inspirations = inspirations,
    fantasy = fantasy,
    keyMechanics = keyMechanics,
    outOfScope = outOfScope,
    createdAt = 1L,
    updatedAt = 1L,
)

private fun note(id: String, track: Track?, updatedAt: Long): Note = Note(
    id = id,
    owner = "owner-1",
    body = "cuerpo $id",
    track = track,
    status = NoteStatus.CAPTURED,
    createdAt = updatedAt,
    updatedAt = updatedAt,
)
