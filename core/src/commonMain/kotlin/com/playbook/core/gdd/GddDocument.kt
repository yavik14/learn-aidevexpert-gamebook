package com.playbook.core.gdd

import com.playbook.core.model.GameProject
import com.playbook.core.model.KeyMechanic
import com.playbook.core.model.MechanicCategory
import com.playbook.core.model.Note
import com.playbook.core.model.Track

/** Campo de la ficha estructurada del Proyecto dentro del GDD híbrido. */
data class GddProjectField(val label: String, val value: String)

/**
 * Ficha del Proyecto proyectada para el GDD. [title] va en la cabecera fija de la
 * pantalla y **no** se repite como campo. Un Proyecto sin datos produce
 * `fields = emptyList()`.
 */
data class GddProjectBlock(
    val title: String?,
    val fields: List<GddProjectField>,
)

/**
 * Sección del documento: un [track] (o `null` para "Sin track") con sus Notas.
 * Reusa la agrupación/orden de [buildGdd].
 */
data class GddDocumentSection(
    val track: Track?,
    val title: String,
    val notes: List<Note>,
)

/**
 * Documento híbrido del GDD: ficha del Proyecto (opcional) arriba y secciones de
 * Notas por Track debajo. No duplica Notas ni persiste nada.
 */
data class GddDocument(
    val project: GddProjectBlock?,
    val sections: List<GddDocumentSection>,
)

/**
 * Proyecta la ficha del Proyecto y las Notas en el documento híbrido del GDD.
 *
 * - `project == null` → `project = null` (la UI muestra su placeholder).
 * - La ficha usa los campos actuales de [GameProject] en orden fijo y omite los
 *   vacíos. Los escalares hacen `trim`; las listas hacen `trim` por elemento,
 *   descartan vacíos y conservan el orden. `Título` vive en la cabecera.
 * - Las secciones reusan [buildGdd] (mismo orden/agrupación/títulos) para que el
 *   export siga siendo compatible.
 * - Es pura y determinista: no muta el Proyecto ni la lista de Notas.
 */
fun buildGddDocument(project: GameProject?, notes: List<Note>): GddDocument {
    val sections = buildGdd(notes).map { section ->
        GddDocumentSection(
            track = section.track,
            title = sectionTitle(section.track),
            notes = section.notes,
        )
    }
    return GddDocument(
        project = project?.let { projectBlock(it) },
        sections = sections,
    )
}

private fun projectBlock(project: GameProject): GddProjectBlock {
    val fields = buildList {
        projectListField("Géneros", project.genres)?.let(::add)
        projectTextField("Core Loop", project.coreLoop)?.let(::add)
        projectTextField("Audiencia", project.audience)?.let(::add)
        projectTextField("Concepto", project.concept)?.let(::add)
        projectListField("Inspiraciones", project.inspirations)?.let(::add)
        projectTextField("Fantasía", project.fantasy)?.let(::add)
        projectMechanicsField("Mecánicas clave", project.keyMechanics)?.let(::add)
        projectListField("Fuera de alcance", project.outOfScope)?.let(::add)
    }
    return GddProjectBlock(
        title = project.title?.trim()?.takeIf { it.isNotEmpty() },
        fields = fields,
    )
}

private fun projectTextField(label: String, raw: String?): GddProjectField? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return GddProjectField(label = label, value = value)
}

private fun projectListField(label: String, raw: List<String>): GddProjectField? {
    val normalized = raw.map { it.trim() }.filter { it.isNotEmpty() }
    if (normalized.isEmpty()) return null
    return GddProjectField(label = label, value = normalized.joinToString(", "))
}

private fun projectMechanicsField(
    label: String,
    mechanics: List<KeyMechanic>,
): GddProjectField? {
    val normalized = mechanics
        .map { mechanic -> mechanic.copy(description = mechanic.description.trim()) }
        .filter { it.description.isNotEmpty() }
    if (normalized.isEmpty()) return null
    val value = normalized.joinToString("\n") { mechanic ->
        "${mechanicCategoryLabel(mechanic.category)} · ${mechanic.description}"
    }
    return GddProjectField(label = label, value = value)
}

/** Etiqueta visible de la categoría; el `code` persistido no cambia. */
private fun mechanicCategoryLabel(category: MechanicCategory): String = when (category) {
    MechanicCategory.MOVEMENT -> "Movimiento"
    MechanicCategory.INTERACTION -> "Interacción"
    MechanicCategory.CONFLICT -> "Conflicto"
}

/** Títulos en minúsculas de las secciones, compatibles con el export actual. */
private fun sectionTitle(track: Track?): String = when (track) {
    Track.MECHANICS -> "mecánicas"
    Track.CHARACTERS -> "personajes"
    Track.STORY -> "historia"
    null -> "Sin track"
}
