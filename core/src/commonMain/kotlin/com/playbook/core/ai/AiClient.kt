package com.playbook.core.ai

/** Vector de embedding (valores en punto flotante). */
data class Embedding(val values: List<Float>)

/**
 * Entrada de [AiClient.generate]; `context` ancla la respuesta (RAG).
 *
 * [instructions] es una instrucción de sistema opcional (p. ej. grounding del
 * Q&A). [temperature] y [maxTokens] son parámetros de chat opcionales: el
 * adaptador real sólo los envía cuando no son `null`, por lo que las llamadas
 * existentes conservan el comportamiento previo.
 */
data class GenerateRequest(
    val prompt: String,
    val context: List<String> = emptyList(),
    val instructions: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null,
) {
    init {
        require(temperature == null || temperature in 0.0..2.0) {
            "temperature debe estar entre 0.0 y 2.0"
        }
        require(maxTokens == null || maxTokens > 0) {
            "maxTokens debe ser > 0"
        }
    }
}

/** Falla del runtime de IA (red, cuota, modelo, etc.). */
class AiClientException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Puerto del core hacia el runtime de IA. El core depende sólo de esta
 * abstracción; la implementación concreta (fake, cloud, on-device) se inyecta
 * desde afuera y puede cambiarse sin tocar el core.
 */
interface AiClient {
    /** Devuelve un [Embedding] por cada texto, en el mismo orden. */
    suspend fun embed(texts: List<String>): List<Embedding>

    /** Genera una respuesta de texto a partir del [request]. */
    suspend fun generate(request: GenerateRequest): String
}
