package com.playbook.core.ai

import com.playbook.core.diagnostics.Diagnostics
import com.playbook.core.diagnostics.DiagnosticSource
import com.playbook.core.diagnostics.NoOpDiagnostics
import com.playbook.core.model.Note
import com.playbook.core.repository.EmbeddingRepository
import com.playbook.core.repository.NoteRepository

/** Instrucción de sistema del Q&A: grounding estricto en las Notas recuperadas. */
internal const val RAG_INSTRUCTIONS: String =
    "Respondé la pregunta usando exclusivamente el contexto provisto. Si el " +
        "contexto no alcanza para responder, decilo explícitamente y no inventes. " +
        "No uses conocimiento externo. No enumeres ni cites fuentes: la app las " +
        "muestra por separado. Escribí en español, en texto plano, sin markdown, " +
        "de forma breve y concreta."

private const val RAG_TEMPERATURE = 0.2
private const val RAG_MAX_TOKENS = 600

/** Nota recuperada por la consulta, con su score coseno contra la pregunta. */
data class RetrievedNote(val note: Note, val score: Float)

/**
 * Resultado de una consulta en lenguaje natural sobre las Notas propias.
 *
 * - [Answer]: respuesta generada a partir de las Notas recuperadas ([sources]).
 * - [NoIndexedNotes]: no había Notas con embedding; el runtime no se invocó.
 * - [Failed]: el runtime de IA falló; se propaga la causa sin inventar respuesta.
 */
sealed interface RagAnswer {
    data class Answer(val text: String, val sources: List<RetrievedNote>) : RagAnswer
    data object NoIndexedNotes : RagAnswer
    data class Failed(val cause: AiClientException) : RagAnswer
}

/**
 * Consulta RAG sobre las Notas del usuario: embebe la pregunta con [AiClient],
 * recupera por **similitud coseno en memoria** las Notas con embedding persistido
 * más cercanas (top-K) y genera la respuesta con
 * `AiClient.generate(GenerateRequest(prompt = pregunta, context = cuerpos))`.
 *
 * Nunca usa conocimiento externo: si no hay Notas indexadas devuelve
 * [RagAnswer.NoIndexedNotes] **sin** llamar al runtime. El retrieval es efímero
 * (por consulta): no crea Enlaces persistidos (`semantic-linking`). Los fallos del
 * runtime se mapean a [RagAnswer.Failed] sin propagar la excepción.
 *
 * @param maxSources máximo de Notas fuente a recuperar (top-K).
 * @param diagnostics sink de diagnóstico (default [NoOpDiagnostics]); los fallos
 *   del runtime se reportan como `AI_QUERY` de forma best-effort y sin registrar
 *   la pregunta.
 */
class RagQueryService(
    private val aiClient: AiClient,
    private val noteRepository: NoteRepository,
    private val embeddingRepository: EmbeddingRepository,
    private val maxSources: Int = DEFAULT_MAX_SOURCES,
    private val diagnostics: Diagnostics = NoOpDiagnostics,
) {
    /**
     * Responde [question] a partir de las Notas indexadas. `question` no puede
     * estar en blanco ([IllegalArgumentException]); la UI deshabilita la acción
     * en ese caso y el core es defensivo.
     */
    suspend fun ask(question: String): RagAnswer {
        require(question.isNotBlank()) { "la pregunta no puede estar en blanco" }

        val embeddings = embeddingRepository.getAll()
        if (embeddings.isEmpty()) return RagAnswer.NoIndexedNotes

        val notesById = noteRepository.getAll().associateBy { it.id }
        // Descarta embeddings sin Nota (defensivo): sin candidatos no hay RAG.
        val candidates = embeddings.mapNotNull { embedding ->
            val note = notesById[embedding.noteId] ?: return@mapNotNull null
            note to embedding.values
        }
        if (candidates.isEmpty()) return RagAnswer.NoIndexedNotes

        val questionEmbedding = try {
            aiClient.embed(listOf(question.trim()))
        } catch (cause: AiClientException) {
            diagnostics.recordFailure(DiagnosticSource.AI_QUERY, SUMMARY_FAILED, cause.message)
            return RagAnswer.Failed(cause)
        }
        if (questionEmbedding.size != 1) {
            val cause = AiClientException(
                "Se esperaba exactamente 1 embedding, se recibieron ${questionEmbedding.size}",
            )
            diagnostics.recordFailure(DiagnosticSource.AI_QUERY, SUMMARY_FAILED, cause.message)
            return RagAnswer.Failed(cause)
        }
        val questionVector = questionEmbedding.single().values
        if (questionVector.isEmpty()) {
            val cause = AiClientException("El runtime devolvió un embedding de dimensión 0")
            diagnostics.recordFailure(DiagnosticSource.AI_QUERY, SUMMARY_FAILED, cause.message)
            return RagAnswer.Failed(cause)
        }

        // Similitud coseno sólo contra candidatos de dimensión compatible; el
        // resto se descarta sin romper el cálculo.
        val scored = candidates.mapNotNull { (note, values) ->
            if (values.size != questionVector.size) return@mapNotNull null
            RetrievedNote(note = note, score = cosineSimilarity(questionVector, values))
        }

        // Top-K por score descendente con desempate estable por `note.id`.
        val top = scored
            .sortedWith(compareByDescending<RetrievedNote> { it.score }.thenBy { it.note.id })
            .take(maxSources)
        if (top.isEmpty()) return RagAnswer.NoIndexedNotes

        val text = try {
            aiClient.generate(
                GenerateRequest(
                    prompt = question.trim(),
                    context = top.map { it.note.body },
                    instructions = RAG_INSTRUCTIONS,
                    temperature = RAG_TEMPERATURE,
                    maxTokens = RAG_MAX_TOKENS,
                ),
            )
        } catch (cause: AiClientException) {
            diagnostics.recordFailure(DiagnosticSource.AI_QUERY, SUMMARY_FAILED, cause.message)
            return RagAnswer.Failed(cause)
        }

        val normalized = normalizeRagAnswer(text)
        if (normalized == null) {
            val cause = AiClientException("chat devolvió una respuesta vacía")
            diagnostics.recordFailure(DiagnosticSource.AI_QUERY, SUMMARY_FAILED, cause.message)
            return RagAnswer.Failed(cause)
        }

        return RagAnswer.Answer(text = normalized, sources = top)
    }

    companion object {
        /** Máximo de Notas fuente recuperadas por defecto. */
        const val DEFAULT_MAX_SOURCES: Int = 5

        /** Resumen estable (sin datos del usuario) para los fallos de consulta. */
        private const val SUMMARY_FAILED = "query_failed"
    }
}

/**
 * Normaliza el texto del modelo para mostrarlo en texto plano: quita un code
 * fence que envuelva toda la respuesta, encabezados y marcas `**`/`__`, colapsa
 * saltos de línea excesivos y recorta. No reinterpreta enlaces/código ni elimina
 * citas escritas por el modelo. `null` si no queda contenido.
 */
internal fun normalizeRagAnswer(raw: String): String? {
    var text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
    val fence = Regex("^```[^\\n]*\\n?([\\s\\S]*?)\\n?```$")
    text = fence.matchEntire(text)?.groupValues?.get(1) ?: text
    text = text
        .lines()
        .joinToString("\n") { line -> line.replace(Regex("^\\s*#{1,6}\\s*"), "") }
        .replace(Regex("\\*\\*|__"), "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    return text.ifEmpty { null }
}
