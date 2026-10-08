package com.playbook.core.ai

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Segunda implementación de [AiClient], sólo de test. Devuelve un embedding
 * constante por texto y una respuesta fija; sirve para demostrar que el contrato
 * del puerto se cumple con una implementación distinta de [FakeAiClient] sin
 * tocar archivos de producción de `:core`.
 */
internal class ConstantAiClient(private val dimension: Int = 4) : AiClient {

    override suspend fun embed(texts: List<String>): List<Embedding> =
        texts.map { Embedding(List(dimension) { 0.5f }) }

    override suspend fun generate(request: GenerateRequest): String =
        "respuesta constante para: ${request.prompt} (context=${request.context.size})"
}

/**
 * Contrato genérico del puerto [AiClient], independiente de la implementación.
 * Debe pasar con cualquier runtime (fake, cloud, on-device).
 */
suspend fun verifyAiClientContract(client: AiClient) {
    // embed(emptyList()) == emptyList()
    assertEquals(
        emptyList<Embedding>(),
        client.embed(emptyList()),
        "embed(emptyList()) debe devolver emptyList()",
    )

    val texts = listOf("jefe final", "nivel tutorial", "economía de recursos")
    val first = client.embed(texts)

    // Un embedding por texto, en el mismo orden (mismo tamaño de salida).
    assertEquals(texts.size, first.size, "debe haber un embedding por texto")

    // Dimensión consistente y > 0.
    val dimension = first.first().values.size
    assertTrue(dimension > 0, "la dimensión del embedding debe ser > 0")
    assertTrue(
        first.all { it.values.size == dimension },
        "todos los embeddings deben compartir la misma dimensión",
    )

    // Determinismo de embed.
    assertEquals(first, client.embed(texts), "embed debe ser determinista")

    // Determinismo de generate y respuesta no vacía.
    val request = GenerateRequest(prompt = "resumí mis notas", context = listOf("nota a", "nota b"))
    val generatedOnce = client.generate(request)
    assertEquals(generatedOnce, client.generate(request), "generate debe ser determinista")
    assertTrue(generatedOnce.isNotEmpty(), "la respuesta de generate no debe ser vacía")
}

/**
 * Comportamiento específico de [FakeAiClient]: dimensión por defecto,
 * determinismo, textos distintos → vectores distintos, dimensión configurable y
 * validación de la dimensión.
 */
suspend fun verifyFakeAiClient() {
    val fake = FakeAiClient()
    val texts = listOf("mecánicas de salto", "curva de dificultad")
    val embeddings = fake.embed(texts)

    assertEquals(texts.size, embeddings.size, "debe haber un embedding por texto")
    assertEquals(
        FakeAiClient.DEFAULT_DIMENSION,
        embeddings.first().values.size,
        "el fake debe usar DEFAULT_DIMENSION por defecto",
    )

    // Determinismo: misma entrada → mismos vectores.
    assertEquals(embeddings, fake.embed(texts), "el fake debe ser determinista")

    // Textos distintos → vectores distintos (salvo colisión de hash).
    assertNotEquals(
        embeddings[0],
        embeddings[1],
        "textos distintos deben producir vectores distintos",
    )

    // Dimensión configurable.
    val custom = FakeAiClient(dimension = 3)
    assertTrue(
        custom.embed(texts).all { it.values.size == 3 },
        "la dimensión del fake debe ser configurable",
    )

    // Dimensión inválida → falla temprano.
    assertFailsWith<IllegalArgumentException> { FakeAiClient(dimension = 0) }
}
