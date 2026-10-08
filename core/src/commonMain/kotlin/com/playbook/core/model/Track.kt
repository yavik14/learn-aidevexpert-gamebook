package com.playbook.core.model

/**
 * Pilar de diseño al que pertenece una Nota (ver `CONTEXT.md` y
 * `docs/domain-model.md`). Clasificación única y fija elegida por el usuario.
 *
 * Los identificadores Kotlin quedan en inglés; el [code] persistido usa el valor
 * de dominio canónico en español.
 */
enum class Track(val code: String) {
    MECHANICS("mecánicas"),
    CHARACTERS("personajes"),
    STORY("historia"),
    ;

    companion object {
        /**
         * Mapea el `code` persistido al enum. Devuelve `null` si el código es
         * `null` o desconocido: preserva el edge case "Nota sin `track`" y tolera
         * datos migrados sin romper la lectura.
         */
        fun fromCode(code: String?): Track? = entries.firstOrNull { it.code == code }
    }
}
