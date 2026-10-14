package com.playbook.core.diagnostics

import com.playbook.core.platform.currentTimeMillis

/**
 * Almacenamiento de texto plano por plataforma del log local (un archivo en
 * almacenamiento privado). La ruta la provee el composition root.
 *
 * Contrato: [append]/[overwrite] escriben texto y [read] lo recupera. Las
 * implementaciones concretas pueden fallar (IO); [FileDiagnostics] las envuelve
 * en try/catch para cumplir el contrato best-effort del puerto.
 */
interface DiagnosticStorage {
    /** Agrega [line] como una línea nueva al final del contenido. */
    fun append(line: String)

    /** Devuelve todo el contenido actual (vacío si no existe). */
    fun read(): String

    /** Reemplaza todo el contenido por [text]. */
    fun overwrite(text: String)
}

/**
 * Crea el [DiagnosticStorage] concreto para [path] según la plataforma:
 * `java.io.File` en Android/JVM y `NSFileManager`/`NSString` en iOS.
 */
expect fun diagnosticStorage(path: String): DiagnosticStorage

/**
 * Sink local persistente en un archivo acotado, sin dependencias ni librería de
 * logging. Cada entrada es una línea `<timestampMillis>\t<source>\t<summary>\t<detail|->`
 * y la retención es **por cantidad**: al superar [maxEntries] se reescribe el
 * archivo con las últimas [maxEntries] líneas.
 *
 * Privacidad: sólo persiste la fuente, un resumen estable y (si viene) el
 * [detail] recortado; nunca `body`, preguntas, rutas, IDs ni secrets.
 *
 * Best-effort: [recordFailure] **nunca** lanza aunque el storage falle.
 *
 * @param storage storage por plataforma (o fake en tests).
 * @param clock reloj inyectable (determinista en tests).
 * @param maxEntries retención máxima (`> 0`).
 */
class FileDiagnostics(
    private val storage: DiagnosticStorage,
    private val clock: () -> Long = ::currentTimeMillis,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : Diagnostics {

    init {
        require(maxEntries > 0) { "maxEntries debe ser > 0, fue $maxEntries" }
    }

    override fun recordFailure(source: DiagnosticSource, summary: String, detail: String?) {
        try {
            storage.append(formatLine(clock(), source, summary, detail))
            enforceRetention()
        } catch (_: Throwable) {
            // Best-effort: un diagnóstico jamás rompe el flujo observado.
        }
    }

    /**
     * Contenido crudo del log (sin el `\n` final), recuperable para
     * inspección/diagnóstico. Nunca lanza: si el storage falla devuelve `""`.
     */
    fun rawLog(): String =
        try {
            storage.read().trimEnd('\n')
        } catch (_: Throwable) {
            ""
        }

    private fun enforceRetention() {
        val lines = readLines()
        if (lines.size > maxEntries) {
            storage.overwrite(lines.takeLast(maxEntries).joinToString(separator = "\n", postfix = "\n"))
        }
    }

    private fun readLines(): List<String> = storage.read().split('\n').filter { it.isNotEmpty() }

    private fun formatLine(
        timestampMillis: Long,
        source: DiagnosticSource,
        summary: String,
        detail: String?,
    ): String {
        val safeSummary = sanitize(summary)
        val safeDetail = detail?.let { sanitize(it).take(MAX_DETAIL_CHARS) } ?: EMPTY_DETAIL
        return "$timestampMillis\t${source.name}\t$safeSummary\t$safeDetail"
    }

    /** Normaliza el campo a una sola línea (sin tabs/saltos que rompan el formato). */
    private fun sanitize(value: String): String =
        value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()

    companion object {
        /** Retención por defecto del sink en archivo. */
        const val DEFAULT_MAX_ENTRIES: Int = 200

        /** Recorte del `detail` por línea (privacidad y tamaño acotado). */
        const val MAX_DETAIL_CHARS: Int = 200

        private const val EMPTY_DETAIL = "-"
    }
}
