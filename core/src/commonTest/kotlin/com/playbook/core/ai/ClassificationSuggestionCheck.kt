package com.playbook.core.ai

import com.playbook.core.model.GenreCatalog
import com.playbook.core.model.Track
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/** Stub test-only del puerto `jev`: devuelve una propuesta fija y cuenta llamadas. */
private class StubJevClient(
    private val response: ClassificationSuggestion,
) : JevClient {
    var calls: Int = 0
    var lastText: String? = null

    override suspend fun suggest(text: String): ClassificationSuggestion {
        calls++
        lastText = text
        return response
    }
}

/** Stub test-only que falla como lo haría el runtime real. */
private class FailingJevClient(private val failure: Throwable) : JevClient {
    override suspend fun suggest(text: String): ClassificationSuggestion = throw failure
}

/** Stub test-only que demora más que el timeout inyectado. */
private class SlowJevClient(private val delayMillis: Long) : JevClient {
    override suspend fun suggest(text: String): ClassificationSuggestion {
        delay(delayMillis)
        return ClassificationSuggestion(Track.MECHANICS, listOf("Puzzle"))
    }
}

private fun successOf(result: ClassificationSuggestionResult): ClassificationSuggestionResult.Success =
    result as? ClassificationSuggestionResult.Success
        ?: fail("se esperaba Success, fue $result")

private fun failedOf(result: ClassificationSuggestionResult): ClassificationSuggestionResult.Failed =
    result as? ClassificationSuggestionResult.Failed
        ?: fail("se esperaba Failed, fue $result")

/**
 * Check 1: el `FakeJevClient` de producción es determinista y devuelve un Género
 * del catálogo (el que el smoke verá como chip de catálogo).
 */
suspend fun verifyClassificationSuggestionFakeDeterministic() {
    val service = ClassificationSuggestionService(FakeJevClient())
    val text = "un RPG por turnos con puzzles"

    val first = successOf(service.suggest(text))
    val second = successOf(service.suggest(text))

    assertTrue(first.suggestion.track != null, "el fake debe proponer un Track")
    assertEquals(first.suggestion, second.suggestion, "el fake debe ser determinista")
    assertEquals(1, first.suggestion.genres.size, "el fake propone un Género")
    assertTrue(
        first.suggestion.genres.single() in GenreCatalog.GENRES,
        "el Género del fake debe pertenecer al catálogo: ${first.suggestion.genres}",
    )
}

/**
 * Check 2: normalización de Géneros — trim, descarte de vacíos, canonización
 * contra el catálogo, conservación de personalizados, dedupe y orden.
 */
suspend fun verifyClassificationSuggestionNormalization() {
    val client = StubJevClient(
        ClassificationSuggestion(
            track = Track.STORY,
            genres = listOf(" jrpg ", "Puzzle", "JRPG", "  ", "zelda-like", "Zelda-Like"),
        ),
    )

    val success = successOf(
        ClassificationSuggestionService(client).suggest("  Una historia  "),
    )

    assertEquals(Track.STORY, success.suggestion.track)
    // El catálogo se canoniza (`jrpg` → `JRPG`); el personalizado conserva la
    // primera grafía (`zelda-like`) y la variante case-insensitive se deduplica.
    assertEquals(listOf("JRPG", "Puzzle", "zelda-like"), success.suggestion.genres)
    assertEquals("Una historia", client.lastText, "el servicio debe enviar el texto recortado")
}

/**
 * Check 3: sin `Track` ni Géneros usables (incluidos strings en blanco) el
 * resultado es `Empty`, no una propuesta vacía confirmable.
 */
suspend fun verifyClassificationSuggestionEmpty() {
    val noSuggestion = StubJevClient(ClassificationSuggestion(null, emptyList()))
    assertSame(
        ClassificationSuggestionResult.Empty,
        ClassificationSuggestionService(noSuggestion).suggest("texto con cuerpo"),
    )

    val blanks = StubJevClient(ClassificationSuggestion(null, listOf(" ", "\t", "")))
    assertSame(
        ClassificationSuggestionResult.Empty,
        ClassificationSuggestionService(blanks).suggest("otro texto"),
    )
}

/**
 * Check 4: `AiClientException` y timeout se reportan como `Failed` sin lanzar
 * hacia el llamador (la UI nunca ve una excepción técnica).
 */
suspend fun verifyClassificationSuggestionFailureAndTimeout() {
    val failure = AiClientException("runtime no disponible")
    val failed = failedOf(
        ClassificationSuggestionService(FailingJevClient(failure)).suggest("texto"),
    )
    // `runTest` recupera el stacktrace de excepciones que cruzan coroutines, así
    // que se verifica el tipo/mensaje y no la identidad de la instancia.
    assertTrue(failed.cause is AiClientException, "fue ${failed.cause}")
    assertEquals(failure.message, failed.cause?.message)

    val timedOut = failedOf(
        ClassificationSuggestionService(
            jevClient = SlowJevClient(delayMillis = 5_000),
            timeoutMillis = 50,
        ).suggest("texto"),
    )
    assertTrue(
        timedOut.cause is TimeoutCancellationException,
        "el timeout debe mapearse a Failed(TimeoutCancellationException), fue ${timedOut.cause}",
    )
}

/**
 * Check 5: aplicación al borrador — el `Track` no nulo reemplaza, `null` conserva,
 * los Géneros se fusionan normalizados sin duplicados ni pérdidas, es idempotente.
 */
fun verifyClassificationSuggestionApply() {
    val suggestion = ClassificationSuggestion(
        track = Track.STORY,
        genres = listOf(" jrpg ", "Puzzle", "JRPG", "  "),
    )

    val applied = applyClassificationSuggestion(
        currentTrack = Track.MECHANICS,
        currentGenres = listOf("Puzzle"),
        suggestion = suggestion,
    )
    assertEquals(Track.STORY, applied.track)
    assertEquals(listOf("JRPG", "Puzzle"), applied.genres)

    val withoutTrack = applyClassificationSuggestion(
        currentTrack = Track.MECHANICS,
        currentGenres = listOf("Puzzle"),
        suggestion = ClassificationSuggestion(track = null, genres = emptyList()),
    )
    assertEquals(Track.MECHANICS, withoutTrack.track, "un Track null conserva el actual")
    assertEquals(listOf("Puzzle"), withoutTrack.genres)

    val reApplied = applyClassificationSuggestion(applied.track, applied.genres, suggestion)
    assertEquals(applied, reApplied, "aplicar la misma sugerencia debe ser idempotente")

    val fromScratch = applyClassificationSuggestion(
        currentTrack = null,
        currentGenres = emptyList(),
        suggestion = ClassificationSuggestion(track = null, genres = emptyList()),
    )
    assertEquals(AppliedClassification(track = null, genres = emptyList()), fromScratch)
}

/**
 * Check 6: un cuerpo vacío devuelve `Empty` sin llamar al cliente (el botón ya
 * está deshabilitado, pero el caso de uso también se protege).
 */
suspend fun verifyClassificationSuggestionBlankBodyDoesNotCallClient() {
    val client = StubJevClient(ClassificationSuggestion(Track.STORY, listOf("Puzzle")))
    val service = ClassificationSuggestionService(client)

    assertSame(ClassificationSuggestionResult.Empty, service.suggest("   "))
    assertSame(ClassificationSuggestionResult.Empty, service.suggest(""))
    assertSame(ClassificationSuggestionResult.Empty, service.suggest("\n\t "))

    assertEquals(0, client.calls, "un body vacío no debe llamar a JevClient")
}
