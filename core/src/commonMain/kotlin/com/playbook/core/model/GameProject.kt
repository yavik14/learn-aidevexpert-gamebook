package com.playbook.core.model

/** Identificador fijo del Proyecto de Juego singleton del MVP. */
const val GAME_PROJECT_SINGLETON_ID: String = "singleton"

/** Máximo de inspiraciones de la ficha del Proyecto. */
internal const val MAX_PROJECT_INSPIRATIONS: Int = 3

/** Máximo de elementos "fuera de alcance" de la ficha del Proyecto. */
internal const val MAX_PROJECT_OUT_OF_SCOPE: Int = 3

/** Máximo de Mecánicas clave de la ficha del Proyecto. */
internal const val MAX_PROJECT_KEY_MECHANICS: Int = 5

/**
 * Proyecto de Juego persistido (singleton por instalación). Es la ficha
 * estructurada que comparten el Brainstorming, el GDD híbrido y el export; no
 * duplica Notas ni contiene el GDD.
 *
 * Todos los campos de contenido son opcionales: un Proyecto recién creado puede
 * estar vacío (`get() != null` con todo `null`/vacío). Los escalares se
 * normalizan con `trim` y los vacíos quedan `null`; las listas descartan vacíos
 * y respetan sus límites. `owner`/`createdAt` se preservan en updates.
 */
data class GameProject(
    val id: String = GAME_PROJECT_SINGLETON_ID,
    val owner: String,
    val title: String? = null,
    val genres: List<String> = emptyList(),
    val coreLoop: String? = null,
    val audience: String? = null,
    val concept: String? = null,
    val inspirations: List<String> = emptyList(),
    val fantasy: String? = null,
    val keyMechanics: List<KeyMechanic> = emptyList(),
    val outOfScope: List<String> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Datos de entrada para crear o reemplazar la ficha del Proyecto. `id`,
 * `createdAt` y `updatedAt` los genera el repositorio; en updates, `owner` debe
 * coincidir con el persistido.
 */
data class GameProjectDraft(
    val owner: String,
    val title: String? = null,
    val genres: List<String> = emptyList(),
    val coreLoop: String? = null,
    val audience: String? = null,
    val concept: String? = null,
    val inspirations: List<String> = emptyList(),
    val fantasy: String? = null,
    val keyMechanics: List<KeyMechanic> = emptyList(),
    val outOfScope: List<String> = emptyList(),
)

/** Una de las Mecánicas clave del Proyecto: categoría + descripción corta. */
data class KeyMechanic(
    val category: MechanicCategory,
    val description: String,
)

/**
 * Categoría fija de una Mecánica clave. Los identificadores Kotlin quedan en
 * inglés; el [code] persistido usa el valor de dominio canónico en español.
 */
enum class MechanicCategory(val code: String) {
    MOVEMENT("movimiento"),
    INTERACTION("interacción"),
    CONFLICT("conflicto"),
    ;

    companion object {
        /**
         * Mapea el `code` persistido al enum. Fail-fast: `category` es
         * `NOT NULL` y sólo lo escribe este código.
         */
        fun fromCode(code: String): MechanicCategory =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("MechanicCategory desconocido: '$code'")
    }
}

/** Normaliza un campo de texto opcional: `trim`; vacío o sólo espacios → `null`. */
internal fun normalizeProjectText(raw: String?): String? =
    raw?.trim()?.takeIf { it.isNotEmpty() }

/**
 * Normaliza una lista de texto de la ficha: `trim`, descarta vacíos, conserva el
 * orden de ingreso y valida el límite **después** de normalizar.
 */
internal fun normalizeProjectList(raw: List<String>, max: Int, field: String): List<String> {
    val normalized = raw
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    require(normalized.size <= max) { "$field admite como máximo $max elementos" }
    return normalized
}

/**
 * Normaliza las Mecánicas clave: descripción con `trim`, descarta las
 * incompletas, conserva el orden y valida el límite después de normalizar.
 */
internal fun normalizeKeyMechanics(raw: List<KeyMechanic>): List<KeyMechanic> {
    val normalized = raw
        .map { mechanic -> mechanic.copy(description = mechanic.description.trim()) }
        .filter { it.description.isNotEmpty() }
    require(normalized.size <= MAX_PROJECT_KEY_MECHANICS) {
        "keyMechanics admite como máximo $MAX_PROJECT_KEY_MECHANICS elementos"
    }
    return normalized
}
