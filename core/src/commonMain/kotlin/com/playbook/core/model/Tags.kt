package com.playbook.core.model

/**
 * Normaliza una lista de Etiquetas libres de una Nota:
 *
 * 1. recorta los espacios sobrantes de cada etiqueta (`trim`),
 * 2. descarta las vacías o que quedan sólo con espacios,
 * 3. deduplica sin distinguir mayúsculas/minúsculas, conservando la **primera**
 *    grafía ingresada,
 * 4. ordena por etiqueta (case-insensitive) para una salida determinista.
 *
 * El orden final no refleja el orden de ingreso (ver `docs/specs/note-category-and-tags.md`).
 */
fun normalizeTags(raw: List<String>): List<String> {
    val seen = mutableSetOf<String>()
    return raw
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filter { seen.add(it.lowercase()) }
        .sortedBy { it.lowercase() }
}
