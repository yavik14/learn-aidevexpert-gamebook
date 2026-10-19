package com.playbook.core.ai

import com.playbook.core.model.GameProjectDraft
import com.playbook.core.model.MechanicCategory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

private class StubSuggestionAiClient(private val response: String) : AiClient {
    var lastRequest: GenerateRequest? = null

    override suspend fun embed(texts: List<String>): List<Embedding> =
        texts.map { Embedding(listOf(1f)) }

    override suspend fun generate(request: GenerateRequest): String {
        lastRequest = request
        return response
    }
}

private class FailingSuggestionAiClient : AiClient {
    val failure = AiClientException("runtime no disponible")

    override suspend fun embed(texts: List<String>): List<Embedding> = emptyList()

    override suspend fun generate(request: GenerateRequest): String = throw failure
}

private fun emptyDraft(): GameProjectDraft = GameProjectDraft(owner = "local")

private fun successOf(result: BrainstormingSuggestionResult): BrainstormingSuggestionResult.Success =
    result as? BrainstormingSuggestionResult.Success
        ?: fail("se esperaba Success, fue $result")

/** Escenario 1: prompt mágico, contexto del borrador y hasta 2 ideas limpias. */
suspend fun verifyBrainstormingSuggestionHappyPath() {
    val client = StubSuggestionAiClient("1. Una idea\n- Otra idea")
    val draft = GameProjectDraft(
        owner = "local",
        title = "Aurora",
        genres = listOf("JRPG", "Puzzle"),
        concept = "Un viaje por constelaciones",
    )

    val success = successOf(
        BrainstormingSuggestionService(client).suggest(BrainstormingStep.TITLE, draft),
    )

    assertEquals(listOf("Una idea", "Otra idea"), success.suggestions.map { it.text })
    assertFalse(success.partial)
    val request = assertNotNull(client.lastRequest)
    assertTrue(request.prompt.contains(BRAINSTORMING_PROMPT_MARKER))
    assertTrue(request.prompt.contains("STEP=TITLE"))
    assertTrue(request.context.any { it == "Géneros: JRPG; Puzzle" })
    assertTrue(request.context.any { it == "Concepto: Un viaje por constelaciones" })
    assertTrue(request.context.any { it == "Borrador actual: Aurora" })
}

/** Escenario 2: el fake determinista del gate devuelve 2 ideas parseables. */
suspend fun verifyBrainstormingSuggestionFake() {
    val client = FakeAiClient()

    val title = successOf(
        BrainstormingSuggestionService(client).suggest(BrainstormingStep.TITLE, emptyDraft()),
    )
    assertEquals(2, title.suggestions.size, "el fake debe devolver 2 ideas")
    assertFalse(title.partial)
    assertTrue(title.suggestions.all { it.category == null })

    val mechanics = successOf(
        BrainstormingSuggestionService(client).suggest(
            BrainstormingStep.KEY_MECHANICS,
            emptyDraft(),
        ),
    )
    assertEquals(2, mechanics.suggestions.size, "el fake debe devolver 2 mecánicas")
    assertTrue(mechanics.suggestions.all { it.category != null })
    assertEquals(
        setOf(MechanicCategory.MOVEMENT, MechanicCategory.INTERACTION),
        mechanics.suggestions.mapNotNull { it.category }.toSet(),
    )
}

/** Escenario 3: parcial con aviso; sin líneas usables no inventa ideas. */
suspend fun verifyBrainstormingSuggestionPartialAndInvalid() {
    val partial = successOf(
        BrainstormingSuggestionService(StubSuggestionAiClient("1. Solo una"))
            .suggest(BrainstormingStep.TITLE, emptyDraft()),
    )
    assertEquals(1, partial.suggestions.size)
    assertTrue(partial.partial, "una sola idea debe marcarse como parcial")

    val invalid = BrainstormingSuggestionService(StubSuggestionAiClient("  \n- \n2.   "))
        .suggest(BrainstormingStep.TITLE, emptyDraft())
    assertSame(BrainstormingSuggestionResult.InvalidResponse, invalid)
}

/** Escenario 4: un fallo del runtime se propaga como Failed, sin excepción. */
suspend fun verifyBrainstormingSuggestionFailure() {
    val client = FailingSuggestionAiClient()
    val result = BrainstormingSuggestionService(client)
        .suggest(BrainstormingStep.CONCEPT, emptyDraft())

    val failed = result as? BrainstormingSuggestionResult.Failed
        ?: fail("se esperaba Failed, fue $result")
    assertSame(client.failure, failed.cause)
}

/** Escenario 5: dedupe dentro de la respuesta y contra el borrador. */
suspend fun verifyBrainstormingSuggestionDedupeAndFilter() {
    val deduped = parseBrainstormingSuggestions(
        BrainstormingStep.TITLE,
        "1. aurora\n2. Idea A\n- idea   a\n3. Idea B\n4. Idea C",
        GameProjectDraft(owner = "local", title = "Aurora"),
    )
    assertEquals(listOf("Idea A", "Idea B"), deduped.map { it.text })

    val filtered = parseBrainstormingSuggestions(
        BrainstormingStep.INSPIRATIONS,
        "1. zelda\n2. Mario\n3. Sonic",
        GameProjectDraft(owner = "local", inspirations = listOf("Zelda")),
    )
    assertEquals(listOf("Mario", "Sonic"), filtered.map { it.text })
}

/** Escenario 6: mecánicas con categoría válida, inválidas descartadas y truncado. */
suspend fun verifyBrainstormingSuggestionMechanicsAndTruncation() {
    val draft = GameProjectDraft(owner = "local")
    val suggestions = parseBrainstormingSuggestions(
        BrainstormingStep.KEY_MECHANICS,
        "1. movimiento: Correr\n2. INTERACCIÓN - Abrir puertas\n3. volar: Nada\n4. Conflicto: Pelear",
        draft,
    )
    assertEquals(2, suggestions.size)
    assertEquals(MechanicCategory.MOVEMENT, suggestions[0].category)
    assertEquals("Correr", suggestions[0].text)
    assertEquals(MechanicCategory.INTERACTION, suggestions[1].category)
    assertEquals("Abrir puertas", suggestions[1].text)

    val invalid = BrainstormingSuggestionService(
        StubSuggestionAiClient("1. volar: Nada\n2. saltar: Tampoco"),
    ).suggest(BrainstormingStep.KEY_MECHANICS, draft)
    assertSame(BrainstormingSuggestionResult.InvalidResponse, invalid)

    val long = "x".repeat(400)
    val truncated = parseBrainstormingSuggestions(
        BrainstormingStep.TITLE,
        "1. $long",
        draft,
    ).single().text
    assertEquals(280, truncated.length)
    assertTrue(truncated.endsWith("…"), "una idea larga debe truncarse con ellipsis")
}

/** Escenario 6 (contexto): el paso Género incluye catálogo y no datos internos. */
fun verifyBrainstormingSuggestionContext() {
    val context = buildBrainstormingContext(
        BrainstormingStep.GENRE,
        GameProjectDraft(owner = "local", genres = listOf("JRPG")),
    )
    assertTrue(context.any { it.startsWith("Catálogo de Géneros: ") && it.contains("Platformer") })
    assertTrue(context.any { it == "Géneros actuales: JRPG" })
    assertTrue(
        context.none { it.startsWith("owner", ignoreCase = true) || it.contains("singleton") },
        "el contexto no debe incluir owner ni ids",
    )

    val prompt = buildBrainstormingPrompt(BrainstormingStep.KEY_MECHANICS)
    assertTrue(prompt.contains("Movimiento: descripción"))
    assertTrue(prompt.contains("STEP=KEY_MECHANICS"))
}
