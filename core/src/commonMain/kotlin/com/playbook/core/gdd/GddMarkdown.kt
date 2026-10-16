package com.playbook.core.gdd

import com.playbook.core.model.Note

/**
 * Construye el GDD como texto Markdown determinista, listo para compartir.
 *
 * - Reusa [buildGdd] (misma agrupación/orden por `track`) y **omite** las
 *   secciones sin Notas.
 * - Encabezado sin fecha: `# GDD — Playbook`, línea en blanco y `Total: N notas`
 *   (singular `Total: 1 nota`, `Total: 0 notas` si la lista está vacía).
 * - `label` de sección = `track.code` o `Sin track` (generados, **no** escapados).
 * - Por Nota: `### <título>` (primera línea del `body`, trim, escapada; fallback
 *   `Nota <now>` si el `body` está vacío, tampoco escapado), debajo sólo el
 *   **resto** del `body` (escapado) y la línea de metadatos
 *   `Géneros: a, b — Estado: <status.code>` (Géneros escapados, `status.code`
 *   no; sin Géneros, sólo `Estado: <status.code>`).
 * - Bloques separados por una línea en blanco y documento terminado en `\n`.
 *
 * Es una función pura sobre el modelo: sin fecha, SQLDelight ni
 * [com.playbook.core.ai.AiClient]. [now] llega ya formateado por la plataforma
 * (p. ej. `"yyyy-MM-dd HH:mm"`) y **sólo** se usa en el fallback `Nota <now>`.
 */
fun buildGddMarkdown(notes: List<Note>, now: String): String {
    val header = "# GDD — Playbook\n\n${totalLine(notes.size)}"
    val sectionBlocks = buildGdd(notes)
        .filter { it.notes.isNotEmpty() }
        .map { section ->
            val label = section.track?.code ?: WITHOUT_TRACK_LABEL
            val noteBlocks = section.notes.map { noteBlock(it, now) }
            (listOf("## $label") + noteBlocks).joinToString(BLOCK_SEPARATOR)
        }
    val blocks = listOf(header) + sectionBlocks
    return blocks.joinToString(BLOCK_SEPARATOR) + "\n"
}

private const val WITHOUT_TRACK_LABEL = "Sin track"

/** Línea en blanco entre bloques. */
private const val BLOCK_SEPARATOR = "\n\n"

private fun totalLine(count: Int): String =
    "Total: $count ${if (count == 1) "nota" else "notas"}"

/** `### <título>`, cuerpo restante y metadatos, unidos por salto de línea. */
private fun noteBlock(note: Note, now: String): String {
    val lines = mutableListOf("### ${title(note, now)}")
    bodyRest(note.body)?.let { lines += it }
    lines += metadataLine(note)
    return lines.joinToString("\n")
}

/**
 * Título = primera línea del `body` (trim, escapada). Si el `body` está vacío o
 * compuesto sólo por espacios, cae al fallback `Nota <now>` (generado, no
 * escapado).
 */
private fun title(note: Note, now: String): String {
    val trimmed = note.body.trim()
    if (trimmed.isEmpty()) return "Nota $now"
    return escapeMarkdown(trimmed.lineSequence().first().trim())
}

/**
 * Resto del `body` tras la primera línea (escapado), o `null` si no hay: así el
 * heading queda solo y **nunca** se repite la primera línea.
 */
private fun bodyRest(body: String): String? {
    val lines = body.trim().lineSequence().drop(1).toList()
    if (lines.isEmpty()) return null
    return escapeMarkdown(lines.joinToString("\n"))
}

/** Géneros (escapados) + `status.code` (generado, no escapado). */
private fun metadataLine(note: Note): String {
    val status = "Estado: ${note.status.code}"
    if (note.genres.isEmpty()) return status
    val genres = note.genres.joinToString(", ") { escapeMarkdown(it) }
    return "Géneros: $genres — $status"
}

/**
 * Set de caracteres que se backslash-escan en **contenido de usuario** (título,
 * cuerpo y Géneros). Los valores generados (labels de sección, `status.code` y
 * el fallback `Nota <now>`) no se escapan.
 */
private val MARKDOWN_SPECIAL_CHARS = setOf(
    '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '|', '>',
)

/**
 * Escapa cada carácter del set anteponiendo un backslash. El backslash va primero
 * (es un carácter más del set), de modo que un `\` de entrada sale como `\\`.
 */
private fun escapeMarkdown(value: String): String = buildString(value.length) {
    value.forEach { char ->
        if (char in MARKDOWN_SPECIAL_CHARS) append('\\')
        append(char)
    }
}
