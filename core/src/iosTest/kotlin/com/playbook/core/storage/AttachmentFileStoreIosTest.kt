@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package com.playbook.core.storage

import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import kotlin.test.Test

/** Ejercita el `actual` iOS (`NSFileManager`/`NSData`) del store en runtime. */
class AttachmentFileStoreIosTest {
    @Test
    fun realFileStoreRoundTripsAndBlocksTraversal() {
        val fileManager = NSFileManager.defaultManager
        val root = "${NSTemporaryDirectory()}playbook-attachments-${NSUUID().UUIDString}".trimEnd('/')
        val base = "$root/a/b"
        fileManager.createDirectoryAtPath(base, true, null, null)
        // Archivos fuera de la base: si el guard faltara, read/delete los tocaría.
        writeText("$root/a/secreto", "SECRETO")
        writeText("$root/x", "X")
        try {
            verifyAttachmentFileStore(attachmentFileStore(base))
        } finally {
            fileManager.removeItemAtPath(root, error = null)
        }
    }

    private fun writeText(path: String, text: String) {
        (text as NSString).writeToFile(
            path = path,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
    }
}
