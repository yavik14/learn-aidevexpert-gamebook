package com.playbook.core.gdd

import com.playbook.core.model.Note
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ejercita [buildGddMarkdown] contra el "Format Contract" de
 * `docs/specs/gdd-export.md` (escenarios 1–8): encabezado y total (plural y
 * singular), orden fijo y omisión de secciones vacías, título = primera línea /
 * cuerpo = resto, fallback `Nota <now>`, escapado de Markdown, nota sin Géneros
 * y determinismo. Vive en `commonTest` para correr en Android/JVM e iOS.
 */
fun verifyGddMarkdown() {
    val now = "2026-10-15 14:30"

    // --- Base canónica: encabezado, orden/omisión, título/cuerpo, fallback y
    //     metadatos (escenarios 1, 3, 4, 5 y 7) --------------------------------
    val mechanics1 = gddNote(
        id = "m1",
        body = "Jefe final\naparece en el nivel 3",
        track = Track.MECHANICS,
        status = NoteStatus.CAPTURED,
        genres = listOf("combate", "jefe"),
    )
    val mechanics2 = gddNote(
        id = "m2",
        body = "",
        track = Track.MECHANICS,
        status = NoteStatus.PENDING,
    )
    val characters = gddNote(
        id = "c1",
        body = "Idea",
        track = Track.CHARACTERS,
        status = NoteStatus.CAPTURED,
    )
    val orphan = gddNote(
        id = "o1",
        body = "Protagonista sin nombre",
        track = null,
        status = NoteStatus.INDEXED,
        genres = listOf("lore"),
    )
    val canonical = listOf(mechanics1, mechanics2, characters, orphan)

    val expectedCanonical = """
        # GDD — Playbook

        Total: 4 notas

        ## mecánicas

        ### Jefe final
        aparece en el nivel 3
        Géneros: combate, jefe — Estado: capturada

        ### Nota 2026-10-15 14:30
        Estado: pendiente

        ## personajes

        ### Idea
        Estado: capturada

        ## Sin track

        ### Protagonista sin nombre
        Géneros: lore — Estado: indexada
    """.trimIndent() + "\n"

    assertEquals(
        expectedCanonical,
        buildGddMarkdown(canonical, now),
        "el documento canónico completo (encabezado sin fecha, orden fijo, omisión de historia, título/cuerpo, fallback y metadatos)",
    )

    // --- Scenario 2: total en singular y lista vacía --------------------------
    assertEquals(
        "# GDD — Playbook\n\nTotal: 0 notas\n",
        buildGddMarkdown(emptyList(), now),
        "la lista vacía produce sólo el encabezado con 'Total: 0 notas' y sin secciones",
    )
    val single = buildGddMarkdown(listOf(characters), now)
    assertTrue(
        single.startsWith("# GDD — Playbook\n\nTotal: 1 nota\n\n"),
        "una sola Nota usa el singular 'Total: 1 nota'",
    )
    assertFalse(single.contains("Total: 1 notas"), "no se usa el plural con una sola Nota")
    assertFalse(single.contains("## Sin track"), "sin Notas sin track no aparece 'Sin track'")

    // --- Scenario 3: orden fijo y omisión de secciones vacías -----------------
    val story = gddNote("s1", "Arco", Track.STORY, NoteStatus.CAPTURED)
    val allSections = buildGddMarkdown(listOf(mechanics1, characters, story, orphan), now)
    val idxMechanics = allSections.indexOf("## mecánicas")
    val idxCharacters = allSections.indexOf("## personajes")
    val idxStory = allSections.indexOf("## historia")
    val idxWithoutTrack = allSections.indexOf("## Sin track")
    assertTrue(
        idxMechanics in 0 until idxCharacters &&
            idxCharacters < idxStory &&
            idxStory < idxWithoutTrack,
        "orden fijo: mecánicas, personajes, historia, Sin track",
    )

    val onlyStoryAndOrphan = buildGddMarkdown(listOf(orphan, story), now)
    assertFalse(onlyStoryAndOrphan.contains("## mecánicas"), "las secciones de track vacías se omiten")
    assertFalse(onlyStoryAndOrphan.contains("## personajes"), "las secciones de track vacías se omiten")
    assertTrue(onlyStoryAndOrphan.contains("## historia"), "la sección con Notas se mantiene")
    assertTrue(
        onlyStoryAndOrphan.indexOf("## historia") < onlyStoryAndOrphan.indexOf("## Sin track"),
        "'Sin track' va al final",
    )

    // --- Scenario 4: título = primera línea; cuerpo = resto -------------------
    val multiline = gddNote(
        id = "multi",
        body = "Título\nlínea dos\nlínea tres",
        track = Track.STORY,
        status = NoteStatus.CAPTURED,
    )
    val multilineDoc = buildGddMarkdown(listOf(multiline), now)
    assertTrue(
        multilineDoc.contains("### Título\nlínea dos\nlínea tres\nEstado: capturada"),
        "el heading es la primera línea y debajo va sólo el resto del cuerpo",
    )
    assertEquals(
        1,
        Regex("Título").findAll(multilineDoc).count(),
        "la primera línea no se repite en el cuerpo",
    )

    val singleLine = gddNote("line", "Sólo una línea", Track.STORY, NoteStatus.CAPTURED)
    val singleLineDoc = buildGddMarkdown(listOf(singleLine), now)
    assertTrue(
        singleLineDoc.contains("### Sólo una línea\nEstado: capturada"),
        "con un body de una línea sólo hay heading + metadatos",
    )

    // --- Scenario 5: fallback `Nota <fecha>` con body vacío -------------------
    val blankBody = gddNote("blank", "   \n  ", track = null, status = NoteStatus.CAPTURED)
    val blankDoc = buildGddMarkdown(listOf(blankBody), now)
    assertTrue(
        blankDoc.contains("### Nota $now\nEstado: capturada"),
        "un body en blanco cae al fallback 'Nota <now>' sin línea de cuerpo",
    )
    // El fallback es generado: no se escapa aunque `now` tenga caracteres del set.
    val dottedNow = "2026.10.15"
    val dottedDoc = buildGddMarkdown(listOf(blankBody), dottedNow)
    assertTrue(
        dottedDoc.contains("### Nota 2026.10.15\nEstado: capturada"),
        "el fallback 'Nota <now>' no se escapa",
    )
    assertFalse(dottedDoc.contains("Nota 2026\\.10\\.15"), "el fallback no aplica escapado")

    // --- Scenario 6: escapado de Markdown -------------------------------------
    val tricky = gddNote(
        id = "esc",
        body = "# Título *x* _y_\nresto [a] (b) - . ! | > `code` \\",
        track = Track.MECHANICS,
        status = NoteStatus.CAPTURED,
        genres = listOf("a_b", "#c"),
    )
    val trickyDoc = buildGddMarkdown(listOf(tricky), now)
    assertTrue(
        trickyDoc.contains("### \\# Título \\*x\\* \\_y\\_"),
        "el título escapa los caracteres del set",
    )
    assertTrue(
        trickyDoc.contains("resto \\[a\\] \\(b\\) \\- \\. \\! \\| \\> \\`code\\` \\\\"),
        "el cuerpo (resto) escapa los caracteres del set, incluido el backslash",
    )
    assertTrue(
        trickyDoc.contains("Géneros: a\\_b, \\#c — Estado: capturada"),
        "los Géneros se escapan; 'status.code' no",
    )
    assertTrue(trickyDoc.contains("## mecánicas"), "el label de sección (generado) no se escapa")

    // --- Scenario 7: nota sin Géneros -----------------------------------------
    val noGenres = gddNote("nt", "Sin géneros", Track.STORY, NoteStatus.FAILED)
    val noGenresDoc = buildGddMarkdown(listOf(noGenres), now)
    assertTrue(
        noGenresDoc.contains("### Sin géneros\nEstado: fallida"),
        "sin Géneros la línea de metadatos es sólo 'Estado: <status.code>'",
    )
    assertFalse(noGenresDoc.contains("Géneros:"), "sin Géneros no aparece el prefijo 'Géneros:'")
    assertFalse(noGenresDoc.contains("Estado: fallida —"), "sin Géneros no hay separador")

    // --- Scenario 8: determinismo ---------------------------------------------
    assertEquals(
        buildGddMarkdown(canonical, now),
        buildGddMarkdown(canonical, now),
        "dos llamadas con la misma lista y el mismo `now` devuelven lo mismo",
    )
    val withBody = listOf(mechanics1, characters, story)
    assertEquals(
        buildGddMarkdown(withBody, "2026-01-01 00:00"),
        buildGddMarkdown(withBody, "2030-12-31 23:59"),
        "`now` no se usa cuando ninguna Nota necesita el fallback",
    )
    assertTrue(
        buildGddMarkdown(listOf(mechanics2), "A") != buildGddMarkdown(listOf(mechanics2), "B"),
        "`now` sólo influye en el fallback 'Nota <now>'",
    )
}

private fun gddNote(
    id: String,
    body: String,
    track: Track?,
    status: NoteStatus,
    genres: List<String> = emptyList(),
): Note = Note(
    id = id,
    owner = "owner-1",
    body = body,
    track = track,
    status = status,
    createdAt = 1L,
    updatedAt = 1L,
    genres = genres,
)
