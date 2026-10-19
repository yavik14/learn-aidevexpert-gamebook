@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package com.playbook.core.storage

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.posix.memcpy

/** `actual` iOS: archivos vía `NSFileManager`/`NSData`, dentro de [baseDir]. */
actual fun attachmentFileStore(baseDir: String): AttachmentFileStore =
    IosAttachmentFileStore(baseDir)

/**
 * Archivos de Adjuntos en `<baseDir>/images/` (el caller pasa `Application
 * Support`). La ruta devuelta es relativa al contenedor para sobrevivir a
 * reinstalaciones/actualizaciones de iOS.
 *
 * [read]/[delete] normalizan `..` y separadores y verifican que la ruta quede
 * **dentro** de la base; si no, devuelven `null`/`false` (guard de path
 * traversal). No se usan APIs de canonicalización exóticas.
 */
private class IosAttachmentFileStore(private val baseDir: String) : AttachmentFileStore {

    private val fileManager = NSFileManager.defaultManager

    override fun saveImage(bytes: ByteArray, mimeType: String): StoredImage {
        val root = normalizePath(baseDir.trimEnd('/'))
        val imagesDir = "$root/images"
        if (!fileManager.fileExistsAtPath(imagesDir)) {
            fileManager.createDirectoryAtPath(
                path = imagesDir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
        val name = "${NSUUID().UUIDString}.jpg"
        bytes.toNSData().writeToFile("$imagesDir/$name", atomically = true)
        return StoredImage(relativePath = "images/$name", byteSize = bytes.size.toLong())
    }

    override fun read(relativePath: String): ByteArray? {
        val path = resolveInsideBase(relativePath) ?: return null
        val data = NSData.dataWithContentsOfFile(path) ?: return null
        return data.toByteArray()
    }

    override fun delete(relativePath: String): Boolean {
        val path = resolveInsideBase(relativePath) ?: return false
        return try {
            fileManager.removeItemAtPath(path, error = null)
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolveInsideBase(relativePath: String): String? {
        val base = normalizePath(baseDir.trimEnd('/'))
        val candidate = normalizePath("$base/$relativePath")
        return if (candidate.startsWith("$base/")) candidate else null
    }
}

/**
 * Normaliza separadores, `.` y `..` sin tocar el filesystem. Mantiene el
 * resultado absoluto si [path] lo era. Suficiente para el guard de traversal del
 * store (no se usan APIs de canonicalización específicas de Foundation).
 */
private fun normalizePath(path: String): String {
    val isAbsolute = path.startsWith("/")
    val segments = mutableListOf<String>()
    path.split('/').forEach { segment ->
        when (segment) {
            "", "." -> Unit
            ".." -> {
                if (segments.isNotEmpty() && segments.last() != "..") {
                    segments.removeAt(segments.lastIndex)
                } else if (!isAbsolute) {
                    segments.add("..")
                }
            }

            else -> segments.add(segment)
        }
    }
    val joined = segments.joinToString("/")
    return if (isAbsolute) "/$joined" else joined
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}

private fun ByteArray.toNSData(): NSData =
    if (isEmpty()) {
        NSData.create(bytes = null, length = 0uL)
    } else {
        usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
        }
    }
