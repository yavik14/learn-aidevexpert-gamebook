package com.playbook.core.storage

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Escenarios 1–4 de `sketch-thumbnails` contra un [AttachmentFileStore] real.
 *
 * El runner debe crear la base **vacía** y, para que el Scenario 4 sea un control
 * real, sembrar dos archivos fuera de ella (un nivel arriba para `../secreto` y
 * dos niveles arriba para `../../x`); sin el guard de path traversal esas rutas
 * devolverían bytes/`true` y las aserciones fallarían.
 */
fun verifyAttachmentFileStore(store: AttachmentFileStore) {
    // Scenario 1: save → read round-trip con los bytes exactos.
    val original = ByteArray(256) { index -> (index % 251).toByte() }
    val stored = store.saveImage(original, "image/jpeg")
    assertTrue(
        stored.relativePath.startsWith("images/"),
        "la ruta debe ser relativa a images/ (fue '${stored.relativePath}')",
    )
    assertEquals(original.size.toLong(), stored.byteSize, "byteSize debe ser el tamaño exacto")
    assertContentEquals(
        original,
        store.read(stored.relativePath),
        "read debe devolver exactamente los bytes guardados",
    )

    // Scenario 2: leer una ruta inexistente devuelve null sin lanzar.
    assertNull(store.read("images/no-existe.jpg"), "una ruta inexistente debe devolver null")

    // Scenario 3: delete borra; read queda null y un segundo delete es false.
    assertTrue(store.delete(stored.relativePath), "delete debe borrar el archivo guardado")
    assertNull(store.read(stored.relativePath), "tras delete, read debe devolver null")
    assertFalse(store.delete(stored.relativePath), "un segundo delete debe devolver false")

    // Scenario 4: rutas que escapan de la base se rechazan (no hay traversal).
    assertNull(store.read("../secreto"), "read no debe escapar de baseDir")
    assertFalse(store.delete("../../x"), "delete no debe escapar de baseDir")
}
