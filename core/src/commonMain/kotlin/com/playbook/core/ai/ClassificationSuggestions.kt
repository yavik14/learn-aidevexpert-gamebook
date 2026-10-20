package com.playbook.core.ai

import com.playbook.core.model.Track
import com.playbook.core.model.normalizeGenres
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout

/** Tope de espera de una sugerencia de clasificación (además del transporte). */
const val CLASSIFICATION_SUGGESTION_TIMEOUT_MILLIS: Long = 20_000

/**
 * Resultado de pedir una [ClassificationSuggestion] a `jev`:
 *
 * - [Success]: hay algo usable (un `Track` y/o al menos un Género).
 * - [Empty]: el proveedor no propuso `Track` ni Géneros usables.
 * - [Failed]: el runtime falló ([AiClientException]) o se agotó el timeout.
 *
 * La sugerencia **nunca** se persiste ni se auto-asigna: aplicarla es una acción
 * explícita del usuario ([applyClassificationSuggestion] + Guardar).
 */
sealed interface ClassificationSuggestionResult {
    data class Success(val suggestion: ClassificationSuggestion) : ClassificationSuggestionResult

    data object Empty : ClassificationSuggestionResult

    data class Failed(val cause: Throwable?) : ClassificationSuggestionResult
}

/**
 * Caso de uso de sugerencia de clasificación: envuelve [JevClient] con un
 * timeout y normaliza la propuesta contra el catálogo de Géneros.
 *
 * - Un texto en blanco devuelve [ClassificationSuggestionResult.Empty] **sin**
 *   llamar al cliente.
 * - Los Géneros se recortan, se descartan los vacíos, se canonizan con
 *   [normalizeGenres], se deduplican y se ordenan.
 * - Sin `Track` ni Géneros usables → [ClassificationSuggestionResult.Empty].
 * - [AiClientException] o [TimeoutCancellationException] → [ClassificationSuggestionResult.Failed];
 *   la cancelación del llamador se propaga.
 *
 * No loguea el texto ni la propuesta y no toca `NoteStatus`, indexado ni esquema.
 */
class ClassificationSuggestionService(
    private val jevClient: JevClient,
    private val timeoutMillis: Long = CLASSIFICATION_SUGGESTION_TIMEOUT_MILLIS,
) {
    suspend fun suggest(text: String): ClassificationSuggestionResult {
        val body = text.trim()
        if (body.isEmpty()) return ClassificationSuggestionResult.Empty

        val suggestion = try {
            withTimeout(timeoutMillis) { jevClient.suggest(body) }
        } catch (cause: AiClientException) {
            return ClassificationSuggestionResult.Failed(cause)
        } catch (cause: TimeoutCancellationException) {
            // `withTimeout` deja activo al llamador; si en cambio fue el
            // llamador quien se canceló/agotó su propio timeout, se propaga.
            if (!currentCoroutineContext().isActive) throw cause
            return ClassificationSuggestionResult.Failed(cause)
        }

        val genres = normalizeGenres(suggestion.genres)
        if (suggestion.track == null && genres.isEmpty()) {
            return ClassificationSuggestionResult.Empty
        }
        return ClassificationSuggestionResult.Success(
            suggestion.copy(genres = genres),
        )
    }
}

/** Clasificación resultante de aplicar una sugerencia a un borrador local. */
data class AppliedClassification(val track: Track?, val genres: List<String>)

/**
 * Aplica [suggestion] sobre la clasificación actual de un borrador:
 *
 * - el `Track` sugerido **reemplaza** al actual; un `null` conserva el actual;
 * - los Géneros se **fusionan** (unión) con los actuales y se normalizan con
 *   [normalizeGenres]: no se pierden Géneros del usuario ni se duplican.
 *
 * Es una función pura e idempotente: no persiste ni toca repositorios.
 */
fun applyClassificationSuggestion(
    currentTrack: Track?,
    currentGenres: List<String>,
    suggestion: ClassificationSuggestion,
): AppliedClassification = AppliedClassification(
    track = suggestion.track ?: currentTrack,
    genres = normalizeGenres(currentGenres + suggestion.genres),
)
