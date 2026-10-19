package com.playbook.core.storage

/**
 * Imagen persistida en el almacenamiento privado de la app.
 *
 * [relativePath] es relativa al contenedor (`images/<uuid>.jpg`): en iOS la ruta
 * absoluta del sandbox cambia entre instalaciones/actualizaciones, así que la
 * base sólo guarda la ruta relativa. [byteSize] es el tamaño exacto del archivo.
 */
data class StoredImage(val relativePath: String, val byteSize: Long)

/**
 * Puerto de archivos de Adjuntos (imágenes de bocetos y, a futuro, audio) sobre
 * el almacenamiento privado de la app.
 *
 * Contrato:
 * - [saveImage] escribe los bytes y devuelve la ruta relativa + tamaño.
 * - [read] devuelve los bytes **exactos** del archivo o `null` si no existe / no
 *   se puede leer; nunca lanza por un archivo ausente.
 * - [delete] devuelve `true` sólo si borró el archivo.
 * - [read]/[delete] **nunca** escapan de la base: una ruta relativa con `..` (u
 *   otra forma de traversal) devuelve `null`/`false`.
 *
 * Los bytes **nunca** se guardan en la base; la fila `attachment` conserva sólo
 * metadatos + la ruta relativa.
 */
interface AttachmentFileStore {
    fun saveImage(bytes: ByteArray, mimeType: String): StoredImage

    /** Bytes del archivo en [relativePath], o `null` si no existe / no se puede leer. */
    fun read(relativePath: String): ByteArray?

    fun delete(relativePath: String): Boolean
}

/**
 * Crea un [AttachmentFileStore] anclado a [baseDir] (contenedor privado de la
 * app). Implementación por plataforma vía `expect`/`actual`, igual que
 * `diagnosticStorage(path)`.
 */
expect fun attachmentFileStore(baseDir: String): AttachmentFileStore
