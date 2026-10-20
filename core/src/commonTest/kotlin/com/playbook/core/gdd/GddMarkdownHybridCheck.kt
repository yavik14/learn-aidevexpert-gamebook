package com.playbook.core.gdd

import com.playbook.core.model.GameProject
import com.playbook.core.model.KeyMechanic
import com.playbook.core.model.MechanicCategory
import com.playbook.core.model.Note
import com.playbook.core.model.NoteStatus
import com.playbook.core.model.Track
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Ejercita [buildGddMarkdown] con la ficha del Proyecto contra el "Format
 * Contract" de `docs/specs/gdd-export-hybrid.md` (escenarios 1–8): documento
 * completo, compatibilidad sin Proyecto, Proyecto sin Notas, Proyecto sin campos,
 * estados vacíos, orden/listas/mecánicas, escapado, determinismo y no duplicación.
 * Vive en `commonTest` para correr en Android/JVM e iOS.
 */
fun verifyGddMarkdownHybrid() {
    val now = "2026-10-15 14:30"
    val mechanics = hybridNote(
        id = "m1",
        body = "Jefe final\naparece en el nivel 3",
        track = Track.MECHANICS,
        status = NoteStatus.CAPTURED,
        genres = listOf("combate", "jefe"),
    )
    val orphan = hybridNote(
        id = "o1",
        body = "Protagonista sin nombre",
        track = null,
        status = NoteStatus.INDEXED,
        genres = listOf("lore"),
    )
    val project = hybridProject(
        title = "Crónica de Ceniza",
        genres = listOf("JRPG", "Zelda-like"),
        coreLoop = "Explorar y combatir",
        audience = "Adultos",
        concept = "Un reino en cenizas",
        inspirations = listOf("Dark Souls", "Hollow Knight"),
        fantasy = "Ser un héroe",
        keyMechanics = listOf(
            KeyMechanic(MechanicCategory.MOVEMENT, "Correr"),
            KeyMechanic(MechanicCategory.INTERACTION, "Hablar"),
            KeyMechanic(MechanicCategory.CONFLICT, "Pelear"),
        ),
        outOfScope = listOf("Multijugador"),
    )

    // --- Scenario 1: documento completo --------------------------------------
    val expectedCanonical = """
        # GDD — Crónica de Ceniza

        Total: 2 notas

        ## Proyecto

        - **Géneros:** JRPG, Zelda\-like
        - **Core Loop:** Explorar y combatir
        - **Audiencia:** Adultos
        - **Concepto:** Un reino en cenizas
        - **Inspiraciones:** Dark Souls, Hollow Knight
        - **Fantasía:** Ser un héroe
        - **Mecánicas clave:**
          - Movimiento · Correr
          - Interacción · Hablar
          - Conflicto · Pelear
        - **Fuera de alcance:** Multijugador

        ## mecánicas

        ### Jefe final
        aparece en el nivel 3
        Géneros: combate, jefe — Estado: capturada

        ## Sin track

        ### Protagonista sin nombre
        Géneros: lore — Estado: indexada
    """.trimIndent() + "\n"
    val canonicalNotes = listOf(mechanics, orphan)
    assertEquals(
        expectedCanonical,
        buildGddMarkdown(project, canonicalNotes, now),
        "el documento híbrido completo (cabecera, ficha y secciones)",
    )

    // --- Scenario 2: sin Proyecto (compatibilidad) ---------------------------
    val legacy = buildGddMarkdown(canonicalNotes, now)
    assertEquals(
        legacy,
        buildGddMarkdown(null, canonicalNotes, now),
        "la firma nueva con project = null es igual al overload viejo",
    )
    assertTrue(legacy.startsWith("# GDD — Playbook\n\nTotal: 2 notas\n"), "sin título cae a Playbook")
    assertFalse(legacy.contains("## Proyecto"), "sin Proyecto no se emite la ficha")

    // --- Scenario 3: Proyecto sin Notas --------------------------------------
    val projectOnly = buildGddMarkdown(project, emptyList(), now)
    assertTrue(projectOnly.startsWith("# GDD — Crónica de Ceniza\n\nTotal: 0 notas\n"))
    assertTrue(projectOnly.contains("## Proyecto"))
    assertTrue(projectOnly.contains("- **Géneros:** JRPG, Zelda\\-like"))
    assertFalse(projectOnly.contains("## mecánicas"), "sin Notas no hay secciones")

    // --- Scenario 4: Proyecto sin campos -------------------------------------
    val titleOnly = hybridProject(title = "Sólo título")
    assertEquals(
        "# GDD — Sólo título\n\nTotal: 0 notas\n",
        buildGddMarkdown(titleOnly, emptyList(), now),
        "un Proyecto sin campos no emite '## Proyecto'",
    )

    // --- Scenario 5: sin Proyecto y sin Notas --------------------------------
    assertEquals(
        "# GDD — Playbook\n\nTotal: 0 notas\n",
        buildGddMarkdown(null, emptyList(), now),
    )

    // --- Scenario 6: orden, listas y mecánicas -------------------------------
    val orderDoc = buildGddMarkdown(project, canonicalNotes, now)
    val labels = listOf(
        "Géneros", "Core Loop", "Audiencia", "Concepto",
        "Inspiraciones", "Fantasía", "Mecánicas clave", "Fuera de alcance",
    )
    val labelIndexes = labels.map { orderDoc.indexOf("- **$it:**") }
    assertTrue(labelIndexes.all { it >= 0 }, "todos los campos de la ficha aparecen")
    assertEquals(
        labelIndexes.sorted(),
        labelIndexes,
        "los campos respetan el orden de GddDocument",
    )
    assertTrue(
        orderDoc.indexOf("  - Movimiento · Correr") <
            orderDoc.indexOf("  - Interacción · Hablar") &&
            orderDoc.indexOf("  - Interacción · Hablar") <
            orderDoc.indexOf("  - Conflicto · Pelear"),
        "las mecánicas conservan su orden y formato",
    )
    val collapsed = hybridProject(
        title = "Uno\ndos",
        coreLoop = "Explorar\n  y   combatir",
    )
    val collapsedDoc = buildGddMarkdown(collapsed, emptyList(), now)
    assertTrue(collapsedDoc.startsWith("# GDD — Uno dos\n"), "el título se colapsa a una línea")
    assertTrue(
        collapsedDoc.contains("- **Core Loop:** Explorar y combatir"),
        "los valores de la ficha se colapsan a una línea",
    )

    // --- Scenario 7: escapado ------------------------------------------------
    val escapedProject = hybridProject(
        title = "# Título *x*",
        genres = listOf("a_b", "#c"),
        coreLoop = "uno | dos",
        concept = "v1.2!",
        keyMechanics = listOf(KeyMechanic(MechanicCategory.MOVEMENT, "Corre . rápido")),
    )
    val escapedDoc = buildGddMarkdown(escapedProject, canonicalNotes, now)
    assertTrue(escapedDoc.startsWith("# GDD — \\# Título \\*x\\*"), "el título se escapa")
    assertTrue(escapedDoc.contains("- **Géneros:** a\\_b, \\#c"), "la lista se escapa")
    assertTrue(escapedDoc.contains("- **Core Loop:** uno \\| dos"), "el escalar se escapa")
    assertTrue(escapedDoc.contains("- **Concepto:** v1\\.2\\!"), "los signos se escapan")
    assertTrue(
        escapedDoc.contains("  - Movimiento · Corre \\. rápido"),
        "la descripción de la mecánica se escapa",
    )
    assertTrue(escapedDoc.contains("- **Géneros:**"), "los labels generados no se escapan")
    assertTrue(escapedDoc.contains("## mecánicas"), "los títulos de sección no se escapan")

    // --- Scenario 8: determinismo y no duplicación ---------------------------
    val projectBefore = project.copy()
    val notesBefore = canonicalNotes.toList()
    assertEquals(
        buildGddMarkdown(project, canonicalNotes, now),
        buildGddMarkdown(project, canonicalNotes, now),
        "dos llamadas con la misma entrada devuelven lo mismo",
    )
    assertEquals(projectBefore, project, "la proyección no muta el Proyecto")
    assertEquals(notesBefore, canonicalNotes, "la proyección no muta las Notas")
    assertEquals(
        1,
        Regex("### Jefe final").findAll(orderDoc).count(),
        "cada Nota aparece una sola vez",
    )
    assertEquals(
        "Total: 2 notas",
        orderDoc.substringAfter("# GDD — Crónica de Ceniza\n\n").substringBefore("\n\n"),
        "el total cuenta las Notas sin duplicarlas",
    )
    assertTrue(
        orderDoc.indexOf("## mecánicas") < orderDoc.indexOf("## Sin track"),
        "las secciones conservan el orden de buildGdd",
    )
}

private fun hybridProject(
    title: String? = null,
    genres: List<String> = emptyList(),
    coreLoop: String? = null,
    audience: String? = null,
    concept: String? = null,
    inspirations: List<String> = emptyList(),
    fantasy: String? = null,
    keyMechanics: List<KeyMechanic> = emptyList(),
    outOfScope: List<String> = emptyList(),
): GameProject = GameProject(
    owner = "local",
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

private fun hybridNote(
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
