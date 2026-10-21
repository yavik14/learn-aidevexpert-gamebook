package com.playbook.core.ai

import com.playbook.core.model.GameProjectDraft
import com.playbook.core.model.GenreCatalog
import com.playbook.core.model.MechanicCategory

/** Pasos del wizard que aceptan sugerencias; el nombre se usa en el prompt. */
enum class BrainstormingStep(val label: String) {
    TITLE("Título"),
    GENRE("Género"),
    CORE_LOOP("Core Loop"),
    AUDIENCE("Audiencia"),
    CONCEPT("Concepto"),
    INSPIRATIONS("Inspiraciones"),
    FANTASY("Fantasía"),
    KEY_MECHANICS("Mecánicas clave"),
    OUT_OF_SCOPE("Fuera de alcance"),
}

/** Una idea efímera propuesta por la IA; [category] sólo aplica a mecánicas. */
data class BrainstormingSuggestion(
    val text: String,
    val category: MechanicCategory? = null,
)

/** Resultado de pedir sugerencias para un paso. */
sealed interface BrainstormingSuggestionResult {
    data class Success(
        val suggestions: List<BrainstormingSuggestion>,
        val partial: Boolean = false,
    ) : BrainstormingSuggestionResult

    data class Failed(val cause: AiClientException) : BrainstormingSuggestionResult

    data object InvalidResponse : BrainstormingSuggestionResult
}

/** Marcador estable del prompt que el fake usa para devolver ideas parseables. */
internal const val BRAINSTORMING_PROMPT_MARKER: String = "PLAYBOOK_BRAINSTORMING_SUGGESTIONS_V1"

private const val MAX_SUGGESTIONS = 2
private const val MAX_SUGGESTION_CHARS = 280
private const val MAX_CONTEXT_ENTRIES = 12
private const val MAX_CONTEXT_VALUE_CHARS = 300
private const val SUGGESTION_TEMPERATURE = 0.7
private const val SUGGESTION_MAX_TOKENS = 400

private val HEADING_MARKER = Regex("^\\s*#{1,6}\\s*")
private val BLOCKQUOTE_MARKER = Regex("^\\s*>+\\s*")
private val LIST_MARKER = Regex("^\\s*(?:[-*•—]|\\d+[.)]|\\(\\d+\\))\\s*")
private val IDEA_PREFIX = Regex(
    "^\\s*(?:idea|opción|opcion)\\s*(?:\\d+)?\\s*[:\\-]\\s*",
    RegexOption.IGNORE_CASE,
)
private val MARKDOWN_EMPHASIS = Regex("\\*\\*|__|`")
private val SEPARATOR_LINE = Regex("^\\s*[-*_=—](?:\\s*[-*_=—]){2,}\\s*$")
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
private val MECHANIC_LINE = Regex(
    "^(movimiento|interacci[oó]n|conflicto)\\s*[:\\-]\\s*(.+)$",
    RegexOption.IGNORE_CASE,
)

/**
 * Pide a [AiClient] 2 ideas para un paso del Brainstorming y las normaliza.
 *
 * La respuesta se trata como texto plano: se aceptan líneas con o sin numeración,
 * se deduplican y se descartan las ideas que ya están en el borrador. La IA nunca
 * persiste nada; aceptar una sugerencia es responsabilidad de la UI.
 */
class BrainstormingSuggestionService(private val aiClient: AiClient) {

    suspend fun suggest(
        step: BrainstormingStep,
        draft: GameProjectDraft,
    ): BrainstormingSuggestionResult {
        val request = GenerateRequest(
            prompt = buildBrainstormingPrompt(step),
            context = buildBrainstormingContext(step, draft),
            temperature = SUGGESTION_TEMPERATURE,
            maxTokens = SUGGESTION_MAX_TOKENS,
        )
        val response = try {
            aiClient.generate(request)
        } catch (cause: AiClientException) {
            return BrainstormingSuggestionResult.Failed(cause)
        }

        val suggestions = parseBrainstormingSuggestions(step, response, draft)
        return when (suggestions.size) {
            0 -> BrainstormingSuggestionResult.InvalidResponse
            1 -> BrainstormingSuggestionResult.Success(suggestions, partial = true)
            else -> BrainstormingSuggestionResult.Success(suggestions)
        }
    }
}

/** Prompt estable por paso; el marcador permite al fake de tests/dev responder. */
internal fun buildBrainstormingPrompt(step: BrainstormingStep): String = buildString {
    appendLine(BRAINSTORMING_PROMPT_MARKER)
    appendLine("STEP=${step.name}")
    appendLine(
        "Eres Playbook, un asistente de diseño de videojuegos. Sugiere exactamente 2 ideas " +
            "para el paso \"${step.label}\".",
    )
    appendLine(
        "Responde solo con 2 líneas, una idea por línea, sin títulos, " +
            "explicaciones ni markdown.",
    )
    appendLine(
        "No incluyas frases introductorias, encabezados, viñetas, negritas " +
            "ni comillas.",
    )
    appendLine(
        "Escribe en español de España (castellano), sin voseo, máximo 200 caracteres " +
            "por idea y no repitas lo que " +
            "ya está en el contexto.",
    )
    if (step == BrainstormingStep.KEY_MECHANICS) {
        appendLine(
            "Para cada idea usa el formato \"Movimiento: descripción\", " +
                "\"Interacción: descripción\" o \"Conflicto: descripción\".",
        )
    }
}

/** Contexto acotado del borrador: sin owner, ids ni timestamps. */
internal fun buildBrainstormingContext(
    step: BrainstormingStep,
    draft: GameProjectDraft,
): List<String> {
    val entries = mutableListOf<String>()

    fun add(label: String, value: String?) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return
        entries += "$label: ${normalized.take(MAX_CONTEXT_VALUE_CHARS)}"
    }

    fun addList(label: String, values: List<String>) {
        if (values.isNotEmpty()) add(label, values.joinToString("; "))
    }

    fun addMechanics() {
        val mechanics = draft.keyMechanics.map { "${it.category.code}: ${it.description}" }
        addList("Mecánicas actuales", mechanics)
    }

    when (step) {
        BrainstormingStep.TITLE -> {
            addList("Géneros", draft.genres)
            add("Concepto", draft.concept)
            add("Borrador actual", draft.title)
        }

        BrainstormingStep.GENRE -> {
            add("Título", draft.title)
            add("Concepto", draft.concept)
            addList("Géneros actuales", draft.genres)
            add("Catálogo de Géneros", GenreCatalog.GENRES.joinToString(", "))
        }

        BrainstormingStep.CORE_LOOP -> {
            add("Título", draft.title)
            addList("Géneros", draft.genres)
            add("Concepto", draft.concept)
            add("Fantasía", draft.fantasy)
            add("Borrador actual", draft.coreLoop)
        }

        BrainstormingStep.AUDIENCE -> {
            add("Título", draft.title)
            addList("Géneros", draft.genres)
            add("Core Loop", draft.coreLoop)
            add("Concepto", draft.concept)
            add("Borrador actual", draft.audience)
        }

        BrainstormingStep.CONCEPT -> {
            add("Título", draft.title)
            addList("Géneros", draft.genres)
            add("Core Loop", draft.coreLoop)
            add("Audiencia", draft.audience)
            add("Borrador actual", draft.concept)
        }

        BrainstormingStep.INSPIRATIONS -> {
            add("Título", draft.title)
            addList("Géneros", draft.genres)
            add("Concepto", draft.concept)
            addList("Inspiraciones actuales", draft.inspirations)
        }

        BrainstormingStep.FANTASY -> {
            add("Título", draft.title)
            addList("Géneros", draft.genres)
            add("Core Loop", draft.coreLoop)
            add("Audiencia", draft.audience)
            add("Concepto", draft.concept)
            add("Borrador actual", draft.fantasy)
        }

        BrainstormingStep.KEY_MECHANICS -> {
            add("Título", draft.title)
            addList("Géneros", draft.genres)
            add("Core Loop", draft.coreLoop)
            addMechanics()
        }

        BrainstormingStep.OUT_OF_SCOPE -> {
            add("Título", draft.title)
            addList("Géneros", draft.genres)
            add("Concepto", draft.concept)
            add("Core Loop", draft.coreLoop)
            addList("Fuera de alcance actual", draft.outOfScope)
        }
    }

    return entries.take(MAX_CONTEXT_ENTRIES)
}

/**
 * Parsea la respuesta en hasta 2 ideas únicas, sin las que ya están en [draft].
 * Público `internal` para que los tests ejerciten el parser directamente.
 */
internal fun parseBrainstormingSuggestions(
    step: BrainstormingStep,
    raw: String,
    draft: GameProjectDraft,
): List<BrainstormingSuggestion> {
    val seen = mutableSetOf<String>()
    return stripWrappingFence(raw)
        .lineSequence()
        .mapNotNull { line -> parseLine(step, line) }
        .filter { suggestion ->
            val key = normalizeForCompare(suggestion.text)
            seen.add(key) && !isAlreadyInDraft(step, suggestion, draft)
        }
        .take(MAX_SUGGESTIONS)
        .toList()
}

private fun stripWrappingFence(raw: String): String {
    val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
    val fence = Regex("^```[^\\n]*\\n?([\\s\\S]*?)\\n?```$")
    return fence.matchEntire(normalized)?.groupValues?.get(1) ?: normalized
}

private fun parseLine(step: BrainstormingStep, rawLine: String): BrainstormingSuggestion? {
    if (SEPARATOR_LINE.matches(rawLine.trim())) return null
    val line = cleanLine(rawLine) ?: return null
    if (isNoiseLine(line)) return null

    if (step != BrainstormingStep.KEY_MECHANICS) {
        return BrainstormingSuggestion(text = truncateSuggestion(line))
    }

    val match = MECHANIC_LINE.matchEntire(line) ?: return null
    val category = when (match.groupValues[1].lowercase().replace("ó", "o")) {
        "movimiento" -> MechanicCategory.MOVEMENT
        "interaccion" -> MechanicCategory.INTERACTION
        "conflicto" -> MechanicCategory.CONFLICT
        else -> return null
    }
    val description = match.groupValues[2].trim()
    if (description.isEmpty()) return null
    return BrainstormingSuggestion(
        text = truncateSuggestion(description),
        category = category,
    )
}

private fun cleanLine(rawLine: String): String? {
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
    line = line.replace(Regex("\\s+"), " ").trim()
    return line.ifEmpty { null }
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
    text.trim().replace(Regex("\\s+"), " ").lowercase()

private fun truncateSuggestion(text: String): String =
    if (text.length <= MAX_SUGGESTION_CHARS) {
        text
    } else {
        text.take(MAX_SUGGESTION_CHARS - 1).trimEnd() + "…"
    }

private fun isAlreadyInDraft(
    step: BrainstormingStep,
    suggestion: BrainstormingSuggestion,
    draft: GameProjectDraft,
): Boolean {
    val suggestionKey = normalizeForCompare(suggestion.text)
    return when (step) {
        BrainstormingStep.TITLE ->
            normalizeForCompare(draft.title.orEmpty()) == suggestionKey

        BrainstormingStep.GENRE ->
            draft.genres.any { normalizeForCompare(it) == suggestionKey }

        BrainstormingStep.CORE_LOOP ->
            normalizeForCompare(draft.coreLoop.orEmpty()) == suggestionKey

        BrainstormingStep.AUDIENCE ->
            normalizeForCompare(draft.audience.orEmpty()) == suggestionKey

        BrainstormingStep.CONCEPT ->
            normalizeForCompare(draft.concept.orEmpty()) == suggestionKey

        BrainstormingStep.INSPIRATIONS ->
            draft.inspirations.any { normalizeForCompare(it) == suggestionKey }

        BrainstormingStep.FANTASY ->
            normalizeForCompare(draft.fantasy.orEmpty()) == suggestionKey

        BrainstormingStep.KEY_MECHANICS -> draft.keyMechanics.any {
            it.category == suggestion.category &&
                normalizeForCompare(it.description) == suggestionKey
        }

        BrainstormingStep.OUT_OF_SCOPE ->
            draft.outOfScope.any { normalizeForCompare(it) == suggestionKey }
    }
}
