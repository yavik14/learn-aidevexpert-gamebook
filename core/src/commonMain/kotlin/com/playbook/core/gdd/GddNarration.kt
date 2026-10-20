package com.playbook.core.gdd

import com.playbook.core.model.GameProject
import com.playbook.core.model.Note

/**
 * Construye el texto que se lee en voz alta para el GDD: título del documento,
 * ficha del Proyecto y cuerpos no vacíos de las Notas agrupadas por Track.
 *
 * Es determinista y pura (no muta el Proyecto ni las Notas). No incluye conteos,
 * `status`, Géneros, chips ni marcas de Markdown. Devuelve `""` si no hay nada
 * narrable (sin título, sin campos y sin cuerpos).
 */
fun buildGddNarration(project: GameProject?, notes: List<Note>): String {
    val document = buildGddDocument(project, notes)
    val fields = document.project?.fields.orEmpty()
    val sectionsWithBodies = document.sections.mapNotNull { section ->
        val bodies = section.notes
            .map { it.body.trim() }
            .filter { it.isNotEmpty() }
        if (bodies.isEmpty()) null else section to bodies
    }

    if (document.project?.title == null && fields.isEmpty() && sectionsWithBodies.isEmpty()) {
        return ""
    }

    val parts = mutableListOf<String>()
    parts += document.project?.title?.let { "GDD — $it" } ?: "GDD"
    if (fields.isNotEmpty()) {
        parts += "Proyecto"
        fields.forEach { field ->
            parts += "${field.label}: ${collapseWhitespace(field.value)}"
        }
    }
    sectionsWithBodies.forEach { (section, bodies) ->
        parts += section.title
        bodies.forEach { body -> parts += collapseWhitespace(body) }
    }
    return parts.joinToString("\n").trim()
}

private fun collapseWhitespace(value: String): String =
    value.trim().replace(WHITESPACE, " ")

private val WHITESPACE = Regex("\\s+")
