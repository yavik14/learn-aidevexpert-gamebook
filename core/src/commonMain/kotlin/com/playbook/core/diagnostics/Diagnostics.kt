package com.playbook.core.diagnostics

import com.playbook.core.platform.currentTimeMillis

/**
 * Origen de un fallo registrado. Delimita los caminos que el MVP observa: los
 * fallos de IA (indexado/consulta) y los de las integraciones nativas (voz,
 * imagen, persistencia de adjuntos).
 */
enum class DiagnosticSource { AI_INDEXING, AI_QUERY, VOICE, IMAGE, STORAGE }

/**
 * Entrada de diagnóstico ya registrada. [summary] es un resumen **estable** (no
 * contiene datos del usuario) y [detail] es opcional (p. ej. el mensaje de la
 * excepción recortado). Nunca lleva `body`, preguntas, rutas, IDs ni secrets.
 */
data class DiagnosticEntry(
    val timestampMillis: Long,
    val source: DiagnosticSource,
    val summary: String,
    val detail: String? = null,
)

/**
 * Puerto de observabilidad mínima: registra **fallos** de forma **best-effort**.
 *
 * Contrato durable: [recordFailure] **NUNCA** lanza ni bloquea al llamador; una
 * implementación que falle al escribir (storage, IO, etc.) debe tragarse el error.
 * No es un logger general: no registra caminos de éxito ni datos de contenido.
 * Sin telemetría remota.
 */
interface Diagnostics {
    fun recordFailure(source: DiagnosticSource, summary: String, detail: String? = null)
}

/**
 * Implementación por defecto: no hace nada. Es el default inyectable para no
 * romper a los llamadores que todavía no cablean un sink real.
 */
object NoOpDiagnostics : Diagnostics {
    override fun recordFailure(source: DiagnosticSource, summary: String, detail: String?) = Unit
}

/**
 * Sink en memoria, pensado para tests (o sinks efímeros). Mantiene a lo sumo
 * [maxEntries] entradas, descartando las más viejas. [snapshot] devuelve una
 * copia en orden cronológico (la más vieja primero).
 *
 * Best-effort: [recordFailure] nunca lanza. No garantiza thread-safety.
 *
 * @param maxEntries cantidad máxima de entradas retenidas (`> 0`).
 * @param clock reloj inyectable (determinista en tests).
 */
class InMemoryDiagnostics(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val clock: () -> Long = ::currentTimeMillis,
) : Diagnostics {

    init {
        require(maxEntries > 0) { "maxEntries debe ser > 0, fue $maxEntries" }
    }

    private val entries = mutableListOf<DiagnosticEntry>()

    override fun recordFailure(source: DiagnosticSource, summary: String, detail: String?) {
        try {
            entries += DiagnosticEntry(clock(), source, summary, detail)
            while (entries.size > maxEntries) entries.removeAt(0)
        } catch (_: Throwable) {
            // Best-effort: un diagnóstico jamás rompe el flujo observado.
        }
    }

    /** Copia inmutable y en orden cronológico de las entradas retenidas. */
    fun snapshot(): List<DiagnosticEntry> = entries.toList()

    /** Vacía las entradas retenidas. */
    fun clear() {
        entries.clear()
    }

    companion object {
        /** Retención por defecto del sink en memoria. */
        const val DEFAULT_MAX_ENTRIES: Int = 100
    }
}
