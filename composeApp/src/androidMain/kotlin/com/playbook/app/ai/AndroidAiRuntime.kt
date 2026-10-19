package com.playbook.app.ai

import com.playbook.app.BuildConfig
import com.playbook.core.ai.openai.AiRuntime
import com.playbook.core.ai.openai.AiRuntimeConfig
import com.playbook.core.ai.openai.fakeAiRuntime
import com.playbook.core.ai.openai.openAiAiRuntime

/**
 * Construye el runtime de IA del build Android a partir de `BuildConfig`. En
 * modo fake no lee ni usa la key (el campo ya viene vacío del build).
 */
internal fun createAndroidAiRuntime(): AiRuntime {
    if (BuildConfig.AI_MODE != "real") return fakeAiRuntime()

    val config = AiRuntimeConfig(
        baseUrl = BuildConfig.AI_BASE_URL,
        apiKey = BuildConfig.AI_API_KEY,
        embeddingDimension = BuildConfig.AI_EMBEDDING_DIMENSION,
        modelChat = BuildConfig.AI_MODEL_CHAT,
        modelEmbedding = BuildConfig.AI_MODEL_EMBEDDING,
        modelTranscribe = BuildConfig.AI_MODEL_TRANSCRIBE,
        modelTts = BuildConfig.AI_MODEL_TTS,
        modelImage = BuildConfig.AI_MODEL_IMAGE,
        modelJev = BuildConfig.AI_MODEL_JEV,
        ttsVoice = BuildConfig.AI_TTS_VOICE,
        imageSize = BuildConfig.AI_IMAGE_SIZE,
        imageQuality = BuildConfig.AI_IMAGE_QUALITY,
        imageBackground = BuildConfig.AI_IMAGE_BACKGROUND,
        imageOutputFormat = BuildConfig.AI_IMAGE_OUTPUT_FORMAT,
        transcribeResponseFormat = BuildConfig.AI_TRANSCRIBE_RESPONSE_FORMAT,
        connectTimeoutMs = BuildConfig.AI_HTTP_CONNECT_TIMEOUT_MS,
        readTimeoutMs = BuildConfig.AI_HTTP_READ_TIMEOUT_MS,
    )
    return openAiAiRuntime(
        config = config,
        transport = UrlConnectionHttpTransport(
            connectTimeoutMs = config.connectTimeoutMs,
            readTimeoutMs = config.readTimeoutMs,
        ),
    )
}
