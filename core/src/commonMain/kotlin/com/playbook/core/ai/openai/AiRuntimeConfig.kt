package com.playbook.core.ai.openai

/**
 * Configuración del runtime real OpenAI-compatible. La key se inyecta desde
 * fuera del repo (`local.properties`, env o `-P`) y **nunca** se imprime: el
 * `toString` la redacta.
 *
 * Los defaults de modelo son los aliases del proveedor (`chat`, `embedding`,
 * `transcribe`, `tts`, `image`, `jev`); se pueden overridear por build flag.
 */
data class AiRuntimeConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val embeddingDimension: Int = DEFAULT_EMBEDDING_DIMENSION,
    val modelChat: String = "chat",
    val modelEmbedding: String = "embedding",
    val modelTranscribe: String = "transcribe",
    val modelTts: String = "tts",
    val modelImage: String = "image",
    val modelJev: String = "jev",
    val ttsVoice: String = "alloy",
    val imageSize: String = "1024x1024",
    val imageQuality: String = "low",
    val imageBackground: String = "transparent",
    val imageOutputFormat: String = "png",
    val transcribeResponseFormat: String = "json",
    val connectTimeoutMs: Int = 30_000,
    val readTimeoutMs: Int = 60_000,
) {
    /** `true` si no falta ningún dato obligatorio para el runtime real. */
    val isComplete: Boolean
        get() = missingFields().isEmpty()

    /** Lista de propiedades faltantes, sin incluir valores sensibles. */
    fun missingFields(): List<String> = buildList {
        if (baseUrl.isBlank()) add("playbook.ai.baseUrl")
        if (apiKey.isBlank()) add("playbook.ai.apiKey")
        if (embeddingDimension <= 0) add("playbook.ai.embeddingDimension")
        if (modelChat.isBlank()) add("playbook.ai.model.chat")
        if (modelEmbedding.isBlank()) add("playbook.ai.model.embedding")
        if (modelTranscribe.isBlank()) add("playbook.ai.model.transcribe")
        if (modelTts.isBlank()) add("playbook.ai.model.tts")
        if (modelImage.isBlank()) add("playbook.ai.model.image")
        if (modelJev.isBlank()) add("playbook.ai.model.jev")
    }

    /** URL absoluta del endpoint [path] (p. ej. `/embeddings`). */
    fun endpoint(path: String): String = baseUrl.trimEnd('/') + path

    override fun toString(): String =
        "AiRuntimeConfig(baseUrl=$baseUrl, apiKey=***, " +
            "embeddingDimension=$embeddingDimension, modelChat=$modelChat, " +
            "modelEmbedding=$modelEmbedding, modelTranscribe=$modelTranscribe, " +
            "modelTts=$modelTts, modelImage=$modelImage, modelJev=$modelJev, " +
            "ttsVoice=$ttsVoice, imageSize=$imageSize, imageQuality=$imageQuality, " +
            "imageBackground=$imageBackground, imageOutputFormat=$imageOutputFormat)"

    companion object {
        const val DEFAULT_BASE_URL: String = "https://inference.devexpert.io/v1"
        const val DEFAULT_EMBEDDING_DIMENSION: Int = 1536
    }
}
