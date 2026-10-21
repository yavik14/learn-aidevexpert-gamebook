package com.playbook.core.ai

import com.playbook.core.model.GameProject
import com.playbook.core.model.Track
import com.playbook.core.model.normalizeGenres

/** Una idea efímera de contenido para el `body` de una Nota. */
data class NoteContentSuggestion(val text: String)

/**
 * Resultado de pedir ideas de contenido para una Nota:
 *
 * - [Success]: hay al menos una idea usable; [Success.partial] indica que el
 *   modelo devolvió una sola.
 * - [InvalidResponse]: la respuesta no aportó ninguna idea usable.
 * - [Failed]: el runtime de IA falló ([AiClientException]).
 *
 * La sugerencia **nunca** se persiste ni se auto-añade: sólo "Añadir" muta el
 * borrador local y sólo "Guardar" lo persiste.
 */
sealed interface NoteContentSuggestionResult {
    data class Success(
        val suggestions: List<NoteContentSuggestion>,
        val partial: Boolean = false,
    ) : NoteContentSuggestionResult

    data object InvalidResponse : NoteContentSuggestionResult

    data class Failed(val cause: AiClientException) : NoteContentSuggestionResult
}

/** Marcador estable del prompt que el fake usa para devolver ideas parseables. */
internal const val NOTE_CONTENT_PROMPT_MARKER: String = "PLAYBOOK_NOTE_CONTENT_SUGGESTIONS_V1"

private const val MAX_SUGGESTIONS = 2
private const val MAX_SUGGESTION_CHARS = 200
private const val MAX_CONTEXT_ENTRIES = 12
private const val MAX_CONTEXT_VALUE_CHARS = 300
private const val MAX_CONTEXT_BODY_CHARS = 1200
private const val SUGGESTION_TEMPERATURE = 0.7
private const val SUGGESTION_MAX_TOKENS = 300

private val HEADING_MARKER = Regex("^\\s*#{1,6}\\s*")
private val BLOCKQUOTE_MARKER = Regex("^\\s*>+\\s*")
private val LIST_MARKER = Regex("^\\s*(?:[-*•—]|\\d+[.)]|\\(\\d+\\))\\s*")
private val IDEA_PREFIX = Regex(
    "^\\s*(?:idea|opción|opcion)\\s*(?:\\d+)?\\s*[:\\-]\\s*",
    RegexOption.IGNORE_CASE,
)
private val MARKDOWN_EMPHASIS = Regex("\\*\\*|__|`")
private val SEPARATOR_LINE = Regex("^\\s*[-*_=—](?:\\s*[-*_=—]){2,}\\s*$")
private val WHITESPACE = Regex("\\s+")
private val EXACT_NOISE = setOf("ideas", "sugerencias", "opciones")
private val PREAMBLE_PREFIXES = listOf(
    "aquí",
    "claro",
    "por supuesto",
    "estas son",
    "estos son",
    "te propongo",
    "te sugiero",
)

/**
 * Pide a [AiClient] hasta 2 ideas de contenido para una Nota.
 *
 * El contexto se acota a la ficha del Proyecto (si existe), el `Track`, los
 * Géneros y el texto actual; nunca incluye `owner`, ids ni timestamps. El
 * servicio no persiste, no indexa, no diagnostica contenido y no aplica un
 * timeout propio: la cancelación la gobierna el `Job` de la UI.
 */
class NoteContentSuggestionService(private val aiClient: AiClient) {

    suspend fun suggest(
        body: String,
        track: Track?,
        genres: List<String>,
        project: GameProject?,
    ): NoteContentSuggestionResult {
        val request = GenerateRequest(
            prompt = buildNoteContentPrompt(),
            context = buildNoteContentContext(body, track, genres, project),
            temperature = SUGGESTION_TEMPERATURE,
            maxTokens = SUGGESTION_MAX_TOKENS,
        )
        val response = try {
            aiClient.generate(request)
        } catch (cause: AiClientException) {
            return NoteContentSuggestionResult.Failed(cause)
        }

        val suggestions = parseNoteContentSuggestions(response, body)
        return when (suggestions.size) {
            0 -> NoteContentSuggestionResult.InvalidResponse
            1 -> NoteContentSuggestionResult.Success(suggestions, partial = true)
            else -> NoteContentSuggestionResult.Success(suggestions)
        }
    }
}

/**
 * Prompt estable en español de España (tuteo, sin voseo). El marcador permite
 * al fake determinista del gate responder sin red ni key.
 */
internal fun buildNoteContentPrompt(): String = buildString {
    appendLine(NOTE_CONTENT_PROMPT_MARKER)
    appendLine(
        "Eres Playbook, un asistente de diseño de videojuegos. Propón exactamente 2 ideas de " +
            "contenido para una Nota del Proyecto.",
    )
    appendLine(
        "Responde solo con 2 líneas, una idea por línea, sin títulos, preámbulos, " +
            "numeración, viñetas, negritas ni comillas.",
    )
    appendLine(
        "Escribe en español de España (castellano), sin voseo, máximo 200 caracteres " +
            "por idea y no repitas el texto actual ni el contexto.",
    )
    appendLine("Propón ideas concretas y accionables para el Proyecto.")
}

/**
 * Contexto acotado y determinista: ficha del Proyecto, `Track`, Géneros y texto
 * actual de la Nota. Sólo valores no vacíos, sin `owner`, ids ni timestamps.
 */
internal fun buildNoteContentContext(
    body: String,
    track: Track?,
    genres: List<String>,
    project: GameProject?,
): List<String> {
    val entries = mutableListOf<String>()

    fun add(label: String, value: String?, maxChars: Int = MAX_CONTEXT_VALUE_CHARS) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return
        entries += "$label: ${normalized.take(maxChars)}"
    }

    fun addList(label: String, values: List<String>) {
        if (values.isNotEmpty()) add(label, values.joinToString("; "))
    }

    if (project != null) {
        add("Título del Proyecto", project.title)
        addList("Géneros del Proyecto", project.genres)
        add("Core Loop", project.coreLoop)
        add("Audiencia", project.audience)
        add("Concepto", project.concept)
        add("Fantasía", project.fantasy)
        addList(
            "Mecánicas clave",
            project.keyMechanics.map { "${it.category.code} · ${it.description}" },
        )
        addList("Inspiraciones", project.inspirations)
        addList("Fuera de alcance", project.outOfScope)
    }

    add("Track de la Nota", track?.code)
    addList("Géneros de la Nota", normalizeGenres(genres))
    add("Texto actual de la Nota", body, MAX_CONTEXT_BODY_CHARS)

    return entries.take(MAX_CONTEXT_ENTRIES)
}

/**
 * Parsea la respuesta en hasta 2 ideas únicas, limpias y truncadas a 200
 * caracteres. Descarta líneas de ruido, preámbulos, separadores y una idea
 * idéntica al [body] normalizado.
 */
internal fun parseNoteContentSuggestions(
    raw: String,
    body: String,
): List<NoteContentSuggestion> {
    val bodyKey = normalizeForCompare(body)
    val seen = mutableSetOf<String>()
    return stripWrappingFence(raw)
        .lineSequence()
        .mapNotNull(::cleanContentLine)
        .filter { line ->
            val key = normalizeForCompare(line)
            seen.add(key) && (bodyKey.isEmpty() || key != bodyKey)
        }
        .take(MAX_SUGGESTIONS)
        .map { NoteContentSuggestion(text = truncateSuggestion(it)) }
        .toList()
}

private fun stripWrappingFence(raw: String): String {
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
    val fence = Regex("^```[^\\n]*\\n?([\\s\\S]*?)\\n?```$")
    return fence.matchEntire(normalized)?.groupValues?.get(1) ?: normalized
}

private fun cleanContentLine(rawLine: String): String? {
    if (SEPARATOR_LINE.matches(rawLine.trim())) return null
    var line = rawLine
        .replace(HEADING_MARKER, "")
        .replace(BLOCKQUOTE_MARKER, "")
        .replace(MARKDOWN_EMPHASIS, "")
        .replace(LIST_MARKER, "")
        .replace(IDEA_PREFIX, "")
        .trim()
        .trim('"', '\'', '“', '”', '‘', '’')
        .trim('*', '_')
        .trim()
    line = line.replace(WHITESPACE, " ").trim()
    if (line.isEmpty()) return null
    if (isNoiseLine(line)) return null
    return line
}

private fun isNoiseLine(line: String): Boolean {
    if (SEPARATOR_LINE.matches(line)) return true
    val lower = line.lowercase()
    if (lower in EXACT_NOISE) return true
    val isPreamble = PREAMBLE_PREFIXES.any { prefix ->
        lower == prefix ||
            lower.startsWith("$prefix ") ||
            lower.startsWith("$prefix:") ||
            lower.startsWith("$prefix,")
    }
    if (isPreamble) return true
    val words = line.split(' ').count { it.isNotBlank() }
    return line.endsWith(":") && words <= 6
}

private fun normalizeForCompare(text: String): String =
    text.trim().replace(WHITESPACE, " ").lowercase()

private fun truncateSuggestion(text: String): String =
    if (text.length <= MAX_SUGGESTION_CHARS) {
        text
    } else {
        text.take(MAX_SUGGESTION_CHARS - 1).trimEnd() + "…"
    }

/**
 * Anexa [suggestion] al final de [body] como párrafo separado por una línea en
 * blanco, preservando el texto existente (sólo recorta el espacio final). Una
 * idea en blanco es no-op; un `body` vacío queda igual a la idea.
 */
fun appendNoteContentSuggestion(body: String, suggestion: String): String {
    val idea = suggestion.trim()
    if (idea.isEmpty()) return body
    val existing = body.trimEnd()
    return if (existing.isEmpty()) idea else "$existing\n\n$idea"
}
