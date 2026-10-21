package com.playbook.core.ai

/**
 * Implementación determinista y sin red de [AiClient]. Es el runtime por defecto
 * mientras `ai-runtime-decision` no elija el real: permite desarrollar y testear
 * sin conectividad, y es inyectable desde el composition root.
 *
 * Los vectores derivan de [String.hashCode] (estable entre plataformas): el
 * mismo texto produce siempre el mismo vector y textos distintos producen
 * vectores distintos salvo colisión de hash.
 */
class FakeAiClient(private val dimension: Int = DEFAULT_DIMENSION) : AiClient {

    init {
        require(dimension > 0) { "dimension debe ser > 0, fue $dimension" }
    }

    override suspend fun embed(texts: List<String>): List<Embedding> =
        texts.map(::embeddingFor)

    override suspend fun generate(request: GenerateRequest): String {
        if (request.prompt.contains(BRAINSTORMING_PROMPT_MARKER)) {
            return if (request.prompt.contains("STEP=${BrainstormingStep.KEY_MECHANICS.name}")) {
                "1. Movimiento: Idea de prueba A\n2. Interacción: Idea de prueba B"
            } else {
                "1. Idea de prueba A\n2. Idea de prueba B"
            }
        }
        if (request.prompt.contains(NOTE_CONTENT_PROMPT_MARKER)) {
            return "1. Añade un detalle sensorial que refuerce la fantasía del juego\n" +
                "2. Describe una variante del core loop para probar en la próxima iteración"
        }
        return "[fake] ${request.prompt} (context=${request.context.size})"
    }

    private fun embeddingFor(text: String): Embedding {
        var state = text.hashCode()
        val values = List(dimension) {
            state = state * LCG_MULTIPLIER + LCG_INCREMENT
            // Toma los 24 bits altos como fracción en [0, 1).
            (state ushr 8) / DIVISOR_2_POW_24
        }
        return Embedding(values)
    }

    companion object {
        /** Dimensión por defecto de los vectores del fake. */
        const val DEFAULT_DIMENSION: Int = 8

        // Constantes de un LCG de 32 bits: la recurrencia es una biyección, así
        // que textos con distinto hash divergen de forma estable.
        private const val LCG_MULTIPLIER = 1664525
        private const val LCG_INCREMENT = 1013904223
        private const val DIVISOR_2_POW_24 = 16777216f
    }
}
