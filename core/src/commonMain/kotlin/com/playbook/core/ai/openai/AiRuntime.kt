package com.playbook.core.ai.openai

import com.playbook.core.ai.AiAvailability
import com.playbook.core.ai.AiClient
import com.playbook.core.ai.AlwaysAvailableAiAvailability
import com.playbook.core.ai.FakeAiClient
import com.playbook.core.ai.FakeImageClient
import com.playbook.core.ai.FakeJevClient
import com.playbook.core.ai.FakeTranscribeClient
import com.playbook.core.ai.FakeTtsClient
import com.playbook.core.ai.ImageClient
import com.playbook.core.ai.JevClient
import com.playbook.core.ai.TranscribeClient
import com.playbook.core.ai.TtsClient

/** Modo de runtime de IA elegido por build flag. */
enum class AiMode { FAKE, REAL }

/**
 * Bundle de puertos de IA + disponibilidad. El composition root elige fake o real
 * y lo inyecta en los servicios; las features de caso de uso consumen los puertos
 * sin conocer el proveedor.
 */
class AiRuntime(
    val mode: AiMode,
    val aiClient: AiClient,
    val transcribeClient: TranscribeClient,
    val ttsClient: TtsClient,
    val imageClient: ImageClient,
    val jevClient: JevClient,
    val availability: AiAvailability,
    val embeddingDimension: Int?,
)

/** Runtime fake determinista, sin red ni key. Default del repo y de tests/gate. */
fun fakeAiRuntime(): AiRuntime = AiRuntime(
    mode = AiMode.FAKE,
    aiClient = FakeAiClient(),
    transcribeClient = FakeTranscribeClient(),
    ttsClient = FakeTtsClient(),
    imageClient = FakeImageClient(),
    jevClient = FakeJevClient(),
    availability = AlwaysAvailableAiAvailability,
    embeddingDimension = FakeAiClient.DEFAULT_DIMENSION,
)

/**
 * Runtime real OpenAI-compatible. Exige una [AiRuntimeConfig] completa y un
 * [HttpTransport] inyectado; no hace red por sí mismo ni conoce la key más allá
 * de los headers que construye.
 */
fun openAiAiRuntime(config: AiRuntimeConfig, transport: HttpTransport): AiRuntime {
    require(config.isComplete) {
        "Configuración de IA real incompleta. Faltan: ${config.missingFields().joinToString(", ")}"
    }
    val http = OpenAiHttp(config, transport)
    return AiRuntime(
        mode = AiMode.REAL,
        aiClient = OpenAiAiClient(config, http),
        transcribeClient = OpenAiTranscribeClient(config, http),
        ttsClient = OpenAiTtsClient(config, http),
        imageClient = OpenAiImageClient(config, http),
        jevClient = OpenAiJevClient(config, http),
        availability = ConfiguredAiAvailability(config),
        embeddingDimension = config.embeddingDimension,
    )
}

/**
 * Disponibilidad del runtime real: sólo valida que la configuración esté
 * completa. La sonda de conectividad real es follow-up (no se hace red acá).
 */
internal class ConfiguredAiAvailability(
    private val config: AiRuntimeConfig,
) : AiAvailability {
    override suspend fun isAvailable(): Boolean = config.isComplete
}
