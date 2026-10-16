package com.playbook.core.model

/**
 * Catálogo **fijo** de Géneros del dominio (12 valores). El Género es la
 * clasificación descriptiva y múltiple de una Nota; proviene del catálogo o es un
 * **personalizado** escrito por el usuario (que vive por Nota y **no** engrosa
 * este catálogo global). Ver `CONTEXT.md` y `CONSTRAINTS.md` §"Géneros y
 * clasificación asistida".
 */
object GenreCatalog {
    val GENRES: List<String> = listOf(
        "Platformer", "Metroidvania", "JRPG", "ActionRPG", "Roguelike", "Shooter",
        "Puzzle", "Adventure", "Towerdefense", "Novel", "Farming", "Arcade",
    )

    /** Grafía canónica del catálogo si [raw] coincide case-insensitive; si no, [raw]. */
    fun canonicalize(raw: String): String =
        GENRES.firstOrNull { it.equals(raw, ignoreCase = true) } ?: raw
}

/**
 * Normaliza una lista de Géneros de una Nota:
 *
 * 1. recorta los espacios sobrantes de cada género (`trim`),
 * 2. descarta los vacíos o que quedan sólo con espacios,
 * 3. **canoniza** la grafía contra [GenreCatalog] (case-insensitive); un
 *    personalizado se conserva tal cual,
 * 4. deduplica sin distinguir mayúsculas/minúsculas, conservando la **primera**
 *    grafía resultante,
 * 5. ordena por género (case-insensitive) para una salida determinista.
 *
 * El orden final no refleja el orden de ingreso.
 */
fun normalizeGenres(raw: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    return raw
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map(GenreCatalog::canonicalize)
        .filter { seen.add(it.lowercase()) } // dedupe case-insensitive, conserva la 1ª grafía
        .sortedBy { it.lowercase() }
}
