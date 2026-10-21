package com.playbook.core.ai

import com.playbook.core.model.GameProject
import com.playbook.core.model.GameProjectDraft
import com.playbook.core.model.KeyMechanic
import com.playbook.core.model.MechanicCategory
import com.playbook.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

private class StubContentAiClient(private val response: String) : AiClient {
    var lastRequest: GenerateRequest? = null

    override suspend fun embed(texts: List<String>): List<Embedding> =
        texts.map { Embedding(listOf(1f)) }

    override suspend fun generate(request: GenerateRequest): String {
        lastRequest = request
        return response
    }
}

private class FailingContentAiClient : AiClient {
    val failure = AiClientException("runtime no disponible")

    override suspend fun embed(texts: List<String>): List<Embedding> = emptyList()

    override suspend fun generate(request: GenerateRequest): String = throw failure
}

private class CancellingContentAiClient : AiClient {
    override suspend fun embed(texts: List<String>): List<Embedding> = emptyList()

    override suspend fun generate(request: GenerateRequest): String =
        throw CancellationException("cancelado")
}

private fun contentProject(): GameProject = GameProject(
    owner = "local-test-owner",
    title = "Aurora",
    genres = listOf("JRPG", "Puzzle"),
    coreLoop = "Explorar constelaciones",
    audience = "Adultos curiosos",
    concept = "Un viaje por el cielo",
    fantasy = "Ser astrónomo",
    keyMechanics = listOf(
        KeyMechanic(MechanicCategory.MOVEMENT, "Saltar"),
        KeyMechanic(MechanicCategory.INTERACTION, "Cantar"),
    ),
    inspirations = listOf("Zelda", "Journey"),
    outOfScope = listOf("Multijugador"),
    createdAt = 111L,
    updatedAt = 222L,
)

private fun contentSuccessOf(
    result: NoteContentSuggestionResult,
): NoteContentSuggestionResult.Success =
    result as? NoteContentSuggestionResult.Success
        ?: fail("se esperaba Success, fue $result")

/** Escenario 1: prompt, contexto acotado y 2 ideas limpias. */
suspend fun verifyNoteContentSuggestionHappyPath() {
    val client = StubContentAiClient("1. Una idea de contenido\n- Otra idea distinta")
    val project = contentProject()

    val success = contentSuccessOf(
        NoteContentSuggestionService(client).suggest(
            body = "Texto previo",
            track = Track.MECHANICS,
            genres = listOf("Puzzle", "JRPG", "Puzzle"),
            project = project,
        ),
    )

    assertEquals(
        listOf("Una idea de contenido", "Otra idea distinta"),
        success.suggestions.map { it.text },
    )
    assertFalse(success.partial)

    val request = assertNotNull(client.lastRequest)
    assertTrue(request.prompt.contains(NOTE_CONTENT_PROMPT_MARKER))
    assertTrue(request.prompt.contains("2 ideas"), "el prompt debe pedir 2 ideas")
    assertTrue(
        request.prompt.contains("español de España"),
        "el prompt debe pedir español de España",
    )
    assertFalse(request.prompt.contains("Sugerí"), "el prompt no debe usar voseo")
    assertFalse(request.prompt.contains("Agregá"), "el prompt no debe usar voseo")
    assertEquals(0.7, request.temperature)
    assertEquals(300, request.maxTokens)

    assertEquals(
        listOf(
            "Título del Proyecto: Aurora",
            "Géneros del Proyecto: JRPG; Puzzle",
            "Core Loop: Explorar constelaciones",
            "Audiencia: Adultos curiosos",
            "Concepto: Un viaje por el cielo",
            "Fantasía: Ser astrónomo",
            "Mecánicas clave: movimiento · Saltar; interacción · Cantar",
            "Inspiraciones: Zelda; Journey",
            "Fuera de alcance: Multijugador",
            "Track de la Nota: mecánicas",
            "Géneros de la Nota: JRPG; Puzzle",
            "Texto actual de la Nota: Texto previo",
        ),
        request.context,
    )
    assertTrue(request.context.size <= 12, "el contexto no debe superar 12 entradas")
    assertTrue(
        request.context.none { entry ->
            entry.contains(project.id) ||
                entry.contains(project.owner) ||
                entry.contains("createdAt") ||
                entry.contains("updatedAt")
        },
        "el contexto no debe incluir ids, owner ni timestamps",
    )
}

/** Escenario 2: el fake determinista devuelve 2 ideas sin alterar otras ramas. */
suspend fun verifyNoteContentSuggestionFake() {
    val client = FakeAiClient()

    val success = contentSuccessOf(
        NoteContentSuggestionService(client).suggest(
            body = "",
            track = null,
            genres = emptyList(),
            project = null,
        ),
    )
    assertEquals(
        listOf(
            "Añade un detalle sensorial que refuerce la fantasía del juego",
            "Describe una variante del core loop para probar en la próxima iteración",
        ),
        success.suggestions.map { it.text },
        "el fake debe devolver las 2 ideas deterministas de su rama",
    )
    assertFalse(success.partial)

    // La rama de Brainstorming y el fallback del fake no cambian.
    val brainstorming = BrainstormingSuggestionService(client).suggest(
        BrainstormingStep.TITLE,
        GameProjectDraft(owner = "local"),
    ) as BrainstormingSuggestionResult.Success
    assertEquals(2, brainstorming.suggestions.size)
    assertTrue(client.generate(GenerateRequest(prompt = "otra cosa")).startsWith("[fake] "))
}

/** Escenario 3: parcial, inválida, fallo y cancelación propagada. */
suspend fun verifyNoteContentSuggestionPartialInvalidAndFailure() {
    val partial = contentSuccessOf(
        NoteContentSuggestionService(StubContentAiClient("1. Solo una idea"))
            .suggest("Texto", null, emptyList(), null),
    )
    assertEquals(1, partial.suggestions.size)
    assertTrue(partial.partial, "una sola idea debe marcarse como parcial")

    val invalid = NoteContentSuggestionService(
        StubContentAiClient("  \n- \n2.   \nAquí tienes dos ideas:"),
    ).suggest("Texto", null, emptyList(), null)
    assertSame(NoteContentSuggestionResult.InvalidResponse, invalid)

    val failing = FailingContentAiClient()
    val failed = NoteContentSuggestionService(failing)
        .suggest("Texto", null, emptyList(), null) as? NoteContentSuggestionResult.Failed
        ?: fail("se esperaba Failed")
    assertSame(failing.failure, failed.cause)

    var cancelled = false
    try {
        NoteContentSuggestionService(CancellingContentAiClient())
            .suggest("Texto", null, emptyList(), null)
    } catch (_: CancellationException) {
        cancelled = true
    }
    assertTrue(cancelled, "la cancelación del llamador debe propagarse")
}

/** Escenario 4: dedupe case-insensitive, descarte del body y truncado a 200. */
suspend fun verifyNoteContentSuggestionDedupeAndTruncation() {
    val deduped = parseNoteContentSuggestions(
        "1. aurora\n2. Idea A\n- idea   a\n3. Idea B\n4. Idea C",
        body = "Aurora",
    )
    assertEquals(listOf("Idea A", "Idea B"), deduped.map { it.text })

    val normalizedBody = parseNoteContentSuggestions(
        "1. un   mundo\n2. Otra idea",
        body = "  Un mundo ",
    )
    assertEquals(listOf("Otra idea"), normalizedBody.map { it.text })

    val long = "x".repeat(400)
    val truncated = parseNoteContentSuggestions("1. $long", body = "").single().text
    assertEquals(200, truncated.length)
    assertTrue(truncated.endsWith("…"), "una idea larga debe truncarse con ellipsis")
}

/** Escenario 5: body vacío con Proyecto y contexto disponible sin Proyecto. */
suspend fun verifyNoteContentSuggestionEmptyBodyAndNoProject() {
    val client = StubContentAiClient("1. Idea uno\n2. Idea dos")
    val service = NoteContentSuggestionService(client)

    val withProject = contentSuccessOf(
        service.suggest(
            body = "   ",
            track = Track.STORY,
            genres = listOf("Novel"),
            project = contentProject(),
        ),
    )
    assertEquals(2, withProject.suggestions.size)
    val projectContext = assertNotNull(client.lastRequest).context
    assertTrue(projectContext.any { it == "Título del Proyecto: Aurora" })
    assertTrue(projectContext.any { it == "Track de la Nota: historia" })
    assertTrue(projectContext.any { it == "Géneros de la Nota: Novel" })
    assertTrue(
        projectContext.none { it.startsWith("Texto actual de la Nota") },
        "sin body no debe añadirse una entrada de texto vacía",
    )

    val withoutProject = contentSuccessOf(
        service.suggest("Un texto", null, emptyList(), null),
    )
    assertEquals(2, withoutProject.suggestions.size)
    val noteContext = assertNotNull(client.lastRequest).context
    assertEquals(listOf("Texto actual de la Nota: Un texto"), noteContext)

    val emptyContext = contentSuccessOf(service.suggest("", null, emptyList(), null))
    assertEquals(2, emptyContext.suggestions.size)
    assertTrue(assertNotNull(client.lastRequest).context.isEmpty())
}

/** Escenario 6: límites de contexto (12 entradas, 300 y 1200 caracteres). */
suspend fun verifyNoteContentSuggestionContextLimits() {
    val client = StubContentAiClient("1. Idea\n2. Otra")
    val project = contentProject().copy(
        title = "T".repeat(400),
        genres = listOf("G".repeat(400)),
        coreLoop = "C".repeat(400),
        audience = "A".repeat(400),
        concept = "P".repeat(400),
        fantasy = "F".repeat(400),
        inspirations = listOf("I".repeat(400)),
        outOfScope = listOf("O".repeat(400)),
    )

    val result = contentSuccessOf(
        NoteContentSuggestionService(client).suggest(
            body = "B".repeat(2000),
            track = Track.CHARACTERS,
            genres = listOf("Puzzle"),
            project = project,
        ),
    )
    assertEquals(2, result.suggestions.size)

    val context = assertNotNull(client.lastRequest).context
    assertEquals(12, context.size)
    assertTrue(context.all { entry -> entry.substringAfter(": ").length <= 300 || entry.startsWith("Texto actual de la Nota") })
    val bodyEntry = context.first { it.startsWith("Texto actual de la Nota") }
    assertEquals(1200, bodyEntry.substringAfter(": ").length)
    assertEquals(300, context.first { it.startsWith("Título del Proyecto") }.substringAfter(": ").length)
}

/** Escenario 7: parser tolerante con markdown, fences y ruido real. */
suspend fun verifyNoteContentSuggestionParserFormat() {
    val markdown = """
        ```
        Aquí tienes dos ideas:

        ## Ideas
        **1. Idea:** "Un mundo que respira"
        **2. Idea:** Ciudades que cantan
        — Una tercera idea que no debe entrar
        ```
    """.trimIndent()

    val success = contentSuccessOf(
        NoteContentSuggestionService(StubContentAiClient(markdown))
            .suggest("", null, emptyList(), null),
    )
    assertEquals(
        listOf("Un mundo que respira", "Ciudades que cantan"),
        success.suggestions.map { it.text },
    )
    assertFalse(success.partial)

    val blockquoted = parseNoteContentSuggestions(
        "> Sugerencias:\n> (1) Primera idea\n> (2) Segunda idea\n---",
        body = "",
    )
    assertEquals(listOf("Primera idea", "Segunda idea"), blockquoted.map { it.text })
}

/** Escenario 8: helper de anexado con línea en blanco y no-op en idea vacía. */
fun verifyAppendNoteContentSuggestion() {
    assertEquals("Hola\n\nIdea", appendNoteContentSuggestion("Hola  \n\n", " Idea "))
    assertEquals("Idea", appendNoteContentSuggestion("", " Idea "))
    assertEquals("Idea", appendNoteContentSuggestion("   ", "Idea"))
    assertEquals("Hola", appendNoteContentSuggestion("Hola", "   "))
    assertEquals("Primera\n\nSegunda", appendNoteContentSuggestion("Primera", "Segunda"))
    assertEquals(
        "Primera línea\nSegunda línea del body\n\nIdea",
        appendNoteContentSuggestion("Primera línea\nSegunda línea del body\n", "Idea"),
    )
}
