package com.playbook.core.storage

import java.io.File
import kotlin.test.Test

/** Ejercita el `actual` Android/JVM (`java.io.File`) del store en runtime. */
class AttachmentFileStoreAndroidTest {
    @Test
    fun realFileStoreRoundTripsAndBlocksTraversal() {
        val root = File(
            System.getProperty("java.io.tmpdir"),
            "playbook-attachments-${System.nanoTime()}",
        )
        val base = File(root, "a/b").apply { mkdirs() }
        // Archivos fuera de la base: si el guard faltara, read/delete los tocaría.
        File(root, "a/secreto").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(9, 9, 9))
        }
        File(root, "x").writeBytes(byteArrayOf(7, 7, 7))
        try {
            verifyAttachmentFileStore(attachmentFileStore(base.absolutePath))
        } finally {
            root.deleteRecursively()
        }
    }
}
