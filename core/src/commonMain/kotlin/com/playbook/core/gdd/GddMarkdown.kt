package com.playbook.core.gdd

import com.playbook.core.model.GameProject
import com.playbook.core.model.Note

/**
 * Construye el GDD como texto Markdown determinista, listo para compartir.
 *
 * - Reusa [buildGddDocument] (ficha del Proyecto + secciones por `track`) y
 *   [buildGdd]; **no** duplica Notas.
 * - Cabecera `# GDD — <título>` (título escapado) o `# GDD — Playbook`; línea en
 *   blanco y `Total: N notas` (singular `Total: 1 nota`).
 * - Si el Proyecto tiene campos, emite `## Proyecto` con los campos en el orden
 *   de [GddDocument] y **omite** el bloque cuando está vacío.
 * - Las secciones no vacías conservan el formato vigente de `gdd-export`:
 *   `## <title>`, `### <título>`, resto del `body`, metadatos y escapado.
 * - Bloques separados por una línea en blanco y documento terminado en `\n`.
 *
 * Es una función pura sobre el modelo: sin fecha, SQLDelight ni
 * [com.playbook.core.ai.AiClient]. [now] llega ya formateado por la plataforma
 * (p. ej. `"yyyy-MM-dd HH:mm"`) y **sólo** se usa en el fallback `Nota <now>`.
 */
fun buildGddMarkdown(project: GameProject?, notes: List<Note>, now: String): String {
    val document = buildGddDocument(project, notes)
    val title = document.project?.title
        ?.let(::singleLine)
        ?.takeIf { it.isNotEmpty() }
        ?.let(::escapeMarkdown)
        ?: PLAYBOOK_TITLE
    val header = "# GDD — $title\n\n${totalLine(notes.size)}"
    val projectBlock = document.project
        ?.takeIf { it.fields.isNotEmpty() }
        ?.let { projectBlock(it.fields) }
    val sectionBlocks = document.sections
        .filter { it.notes.isNotEmpty() }
        .map { section ->
            val noteBlocks = section.notes.map { noteBlock(it, now) }
            (listOf("## ${section.title}") + noteBlocks).joinToString(BLOCK_SEPARATOR)
        }
    val blocks = listOfNotNull(header, projectBlock) + sectionBlocks
    return blocks.joinToString(BLOCK_SEPARATOR) + "\n"
}

/**
 * Compatibilidad: exporta sólo las Notas. Delega con `project = null`, por lo que
 * la salida es byte-idéntica al contrato original de `gdd-export`.
 */
fun buildGddMarkdown(notes: List<Note>, now: String): String =
    buildGddMarkdown(project = null, notes = notes, now = now)

private const val PLAYBOOK_TITLE = "Playbook"

/** Línea en blanco entre bloques. */
private const val BLOCK_SEPARATOR = "\n\n"

private fun totalLine(count: Int): String =
    "Total: $count ${if (count == 1) "nota" else "notas"}"

/** `## Proyecto` + un ítem por campo (mecánicas como sub-lista). */
private fun projectBlock(fields: List<GddProjectField>): String {
    val lines = listOf("## Proyecto", "") + fields.flatMap(::projectFieldLines)
    return lines.joinToString("\n")
}

private fun projectFieldLines(field: GddProjectField): List<String> {
    val label = field.label
    if (label == KEY_MECHANICS_LABEL) {
        val mechanics = field.value
            .split('\n')
            .map(::singleLine)
            .filter { it.isNotEmpty() }
            .map { "  - ${escapeMarkdown(it)}" }
        if (mechanics.isEmpty()) return emptyList()
        return listOf("- **$label:**") + mechanics
    }
    return listOf("- **$label:** ${escapeMarkdown(singleLine(field.value))}")
}

private const val KEY_MECHANICS_LABEL = "Mecánicas clave"

/** Colapsa runs de espacios/tabs/saltos a un solo espacio. */
private fun singleLine(value: String): String =
    value.trim().replace(WHITESPACE_RUN, " ")

private val WHITESPACE_RUN = Regex("\\s+")

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
 * cuerpo, Géneros y valores de la ficha). Los valores generados (labels de
 * sección, `status.code` y el fallback `Nota <now>`) no se escapan.
 */
private val MARKDOWN_SPECIAL_CHARS = setOf(
    '\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '|', '>',
)

/**
 * Escapa cada carácter del set anteponiendo un backslash. El backslash va primero
 * (es un carácter más del set), de modo que un `\` de entrada sale como `\\`.
 */
internal fun escapeMarkdown(value: String): String = buildString(value.length) {
    value.forEach { char ->
        if (char in MARKDOWN_SPECIAL_CHARS) append('\\')
        append(char)
    }
}
