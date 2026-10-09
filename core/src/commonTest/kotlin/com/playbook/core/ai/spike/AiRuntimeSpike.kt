package com.playbook.core.ai.spike

import com.playbook.core.ai.AiClient
import com.playbook.core.ai.AiClientException
import com.playbook.core.ai.Embedding
import com.playbook.core.ai.FakeAiClient
import com.playbook.core.ai.GenerateRequest
import com.playbook.core.ai.verifyAiClientContract
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.TimeSource

/*
 * Spike comparativo de runtime de IA (test-only).
 *
 * NO es producción: vive sólo en `commonTest`. Instancia dos arquetipos de
 * adaptador detrás del puerto [AiClient] —uno local/on-device-like y uno
 * cloud-like— y verifica lo que es honestamente ejecutable en este repo:
 *
 * - conformidad del contrato del puerto sobre ambos,
 * - comportamiento offline del adaptador local (determinista, sin red),
 * - mapeo de fallo del adaptador cloud-like a [AiClientException] y degradación
 *   de un consumidor tipado,
 * - sustituibilidad (el mismo consumidor contra ambos, sin tocar producción),
 * - latencia local informativa con [TimeSource.Monotonic] (NO se asertan
 *   latencias absolutas).
 *
 * Lo que NO se puede medir aquí (latencia/calidad/costo cloud reales, footprint
 * de un modelo on-device real) se documenta como estimación/limitación en
 * `docs/spikes/ai-runtime-spike.md` y en el ADR, nunca como medición.
 */

/**
 * Arquetipo on-device-like: corre localmente, sin red. Delega en el
 * [FakeAiClient] determinista de `commonMain` (no hay runtime nativo real en el
 * repo; ver `docs/spikes/ai-runtime-spike.md`, sección de límites).
 */
class LocalSpikeAiClient(
    val dimension: Int = FakeAiClient.DEFAULT_DIMENSION,
) : AiClient {

    private val delegate = FakeAiClient(dimension)

    override suspend fun embed(texts: List<String>): List<Embedding> =
        delegate.embed(texts)

    override suspend fun generate(request: GenerateRequest): String =
        delegate.generate(request)
}

/**
 * Arquetipo cloud-like **sin red real**: es un doble de la frontera cloud. No
 * importa SDKs, no hace requests y no lee variables de entorno ni API keys.
 * Con `failMode = true` simula que el proveedor no está disponible (red/cuota)
 * lanzando [AiClientException]; con `failMode = false` se comporta como el fake.
 */
class RemoteSpikeAiClient(
    val dimension: Int = FakeAiClient.DEFAULT_DIMENSION,
    private val failMode: Boolean = false,
) : AiClient {

    private val delegate = FakeAiClient(dimension)

    override suspend fun embed(texts: List<String>): List<Embedding> {
        failIfUnavailable()
        return delegate.embed(texts)
    }

    override suspend fun generate(request: GenerateRequest): String {
        failIfUnavailable()
        return delegate.generate(request)
    }

    private fun failIfUnavailable() {
        if (failMode) {
            throw AiClientException(
                "cloud-like: proveedor no disponible (simulado, sin red real)",
            )
        }
    }
}

/** Consumidor tipado contra el puerto: el mismo código sirve para cualquier runtime. */
internal suspend fun consumeViaPort(client: AiClient, texts: List<String>): String {
    val embeddings = client.embed(texts)
    val answer = client.generate(GenerateRequest(prompt = "resumen", context = texts))
    val dimension = embeddings.firstOrNull()?.values?.size ?: 0
    return "n=${embeddings.size},dim=$dimension,answerNoVacia=${answer.isNotEmpty()}"
}

/** Consumidor resiliente: degrada si el runtime falla con [AiClientException]. */
internal suspend fun consumeViaPortOrDegrade(client: AiClient, texts: List<String>): String =
    try {
        consumeViaPort(client, texts)
    } catch (failure: AiClientException) {
        "degradado:${failure.message}"
    }

private class LatencySample(
    val warmupRounds: Int,
    val measuredRounds: Int,
    val textsPerRound: Int,
    val elapsed: Duration,
) {
    val notes: Int get() = measuredRounds * textsPerRound
}

private suspend fun measureLocalLatency(
    local: LocalSpikeAiClient,
    texts: List<String>,
    measuredRounds: Int,
    warmupRounds: Int,
): LatencySample {
    // Warm-up: excluye el primer cómputo (JIT/caches) de la muestra informativa.
    repeat(warmupRounds) { local.embed(texts) }
    val mark = TimeSource.Monotonic.markNow()
    repeat(measuredRounds) { local.embed(texts) }
    val elapsed = mark.elapsedNow()
    return LatencySample(warmupRounds, measuredRounds, texts.size, elapsed)
}

/** Cliente deliberadamente roto para el control negativo del contrato. */
private class EmptyEmbeddingClient : AiClient {
    override suspend fun embed(texts: List<String>): List<Embedding> =
        texts.map { Embedding(emptyList()) }

    override suspend fun generate(request: GenerateRequest): String = "no-op"
}

/**
 * Control negativo: un adaptador que devuelve un embedding vacío/inválido debe
 * romper el contrato del puerto. Se deja como test persistente para que el
 * contrato no se debilite en silencio.
 */
suspend fun verifyContractRejectsBrokenClient() {
    assertFailsWith<AssertionError> {
        verifyAiClientContract(EmptyEmbeddingClient())
    }
}

/**
 * Corre el spike completo y devuelve el reporte estable. Los runners por
 * plataforma lo imprimen; el implementer captura la salida en
 * `docs/spikes/ai-runtime-spike.md`.
 *
 * Todo lo verificado aquí es determinista salvo la latencia, que es sólo
 * informativa.
 */
suspend fun runAiRuntimeSpike(): String {
    val local = LocalSpikeAiClient()
    val remote = RemoteSpikeAiClient()
    val remoteFailing = RemoteSpikeAiClient(failMode = true)
    val texts = listOf("jefe final", "curva de dificultad", "economía de recursos")

    // 1) Contrato sobre ambos arquetipos (modo éxito).
    verifyAiClientContract(local)
    verifyAiClientContract(remote)

    // 2) Offline: el local opera sin red y es determinista (sin red por diseño).
    assertEquals(local.embed(texts), local.embed(texts), "el local debe ser determinista")
    assertTrue(
        local.embed(texts).all { it.values.size == local.dimension },
        "el local debe respetar su dimensión",
    )

    // 3) Mapeo de fallos: cloud-like en modo fallo → AiClientException.
    assertFailsWith<AiClientException> { remoteFailing.embed(texts) }
    assertFailsWith<AiClientException> { remoteFailing.generate(GenerateRequest("x")) }

    // 4) Un consumidor puede degradar sin romper.
    val degraded = consumeViaPortOrDegrade(remoteFailing, texts)
    assertTrue(degraded.startsWith("degradado:"), "el consumidor debe degradar ante el fallo")

    // 5) Sustituibilidad: el mismo consumidor contra ambos arquetipos.
    assertEquals(
        consumeViaPort(local, texts),
        consumeViaPort(remote, texts),
        "el mismo consumidor debe funcionar igual contra local y cloud-like",
    )

    // 6) Control negativo del contrato (roto → falla).
    verifyContractRejectsBrokenClient()

    // 7) Latencia local informativa (no se asertan valores absolutos).
    val latency = measureLocalLatency(local, texts, measuredRounds = 200, warmupRounds = 1)

    return buildReport(local, remote, latency)
}

private fun buildReport(
    local: LocalSpikeAiClient,
    remote: RemoteSpikeAiClient,
    latency: LatencySample,
): String = buildString {
    appendLine("=== ai-runtime-spike (test-only, sin red real) ===")
    appendLine("adapter=local/on-device-like contract=OK offline=OK determinism=OK")
    appendLine("adapter=remote/cloud-like contract=OK failure-mapping=OK degrade=OK")
    appendLine("contract-negative-control=OK (embedding vacío rechazado)")
    appendLine("substitution=OK (mismo consumidor contra ambos, sin tocar producción)")
    appendLine(
        "local.latency warmup_rounds=${latency.warmupRounds} " +
            "measured_rounds=${latency.measuredRounds} " +
            "texts_per_round=${latency.textsPerRound} " +
            "notes=${latency.notes} " +
            "elapsed_us=${latency.elapsed.inWholeMicroseconds} " +
            "ns_per_note=${latency.elapsed.inWholeNanoseconds / latency.notes}",
    )
    appendLine("local.dimension=${local.dimension} remote.dimension=${remote.dimension}")
    appendLine(
        "note=la latencia local mide el fake determinista del repo, " +
            "NO un modelo on-device real (eso es estimación documentada)",
    )
}
