package com.playbook.core.model

/**
 * Estado de una Nota dentro del ciclo de indexado (ver `docs/domain-model.md`).
 *
 * Los identificadores Kotlin quedan en inglés; el [code] persistido usa el valor
 * de dominio canónico en español.
 */
enum class NoteStatus(val code: String) {
    CAPTURED("capturada"),
    PENDING("pendiente"),
    INDEXED("indexada"),
    FAILED("fallida"),
    ;

    companion object {
        /**
         * Mapea el `code` persistido al enum. Fail-fast: `status` es `NOT NULL` y
         * sólo lo escribe este código, así que un valor desconocido representa
         * corrupción de datos y debe fallar en vez de degradarse silenciosamente.
         */
        fun fromCode(code: String): NoteStatus =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("NoteStatus desconocido: '$code'")
    }
}
