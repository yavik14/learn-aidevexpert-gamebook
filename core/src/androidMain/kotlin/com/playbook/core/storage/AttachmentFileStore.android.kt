package com.playbook.core.storage

import java.io.File
import java.util.UUID

/** `actual` Android/JVM: archivos en `java.io.File`, dentro de [baseDir]. */
actual fun attachmentFileStore(baseDir: String): AttachmentFileStore =
    AndroidAttachmentFileStore(File(baseDir))

/**
 * Archivos de Adjuntos en `<baseDir>/images/`. La ruta devuelta es relativa al
 * contenedor (`images/<uuid>.jpg`), preservando el contrato previo del store de
 * `:composeApp`.
 *
 * [read]/[delete] resuelven la ruta canónica y verifican que quede **dentro** de
 * la base; si no, devuelven `null`/`false` (guard de path traversal).
 */
private class AndroidAttachmentFileStore(private val baseDir: File) : AttachmentFileStore {

    override fun saveImage(bytes: ByteArray, mimeType: String): StoredImage {
        val dir = File(baseDir, "images").apply { mkdirs() }
        val name = "${UUID.randomUUID()}.jpg"
        File(dir, name).writeBytes(bytes)
        return StoredImage(relativePath = "images/$name", byteSize = bytes.size.toLong())
    }

    override fun read(relativePath: String): ByteArray? {
        val file = resolveInsideBase(relativePath) ?: return null
        return try {
            if (file.isFile) file.readBytes() else null
        } catch (_: Throwable) {
            null
        }
    }

    override fun delete(relativePath: String): Boolean {
        val file = resolveInsideBase(relativePath) ?: return false
        return try {
            file.delete()
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolveInsideBase(relativePath: String): File? =
        try {
            val base = baseDir.canonicalFile
            val candidate = File(base, relativePath).canonicalFile
            val prefix = base.path + File.separator
            if (candidate.path.startsWith(prefix)) candidate else null
        } catch (_: Throwable) {
            null
        }
}
