@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package com.playbook.core.diagnostics

import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.writeToFile

/**
 * `actual` iOS: archivo de texto plano vía `NSFileManager`/`NSString`.
 * `append` = `read + overwrite` (según el contrato de [DiagnosticStorage]).
 */
actual fun diagnosticStorage(path: String): DiagnosticStorage = IosDiagnosticStorage(path)

private class IosDiagnosticStorage(private val path: String) : DiagnosticStorage {

    private val fileManager = NSFileManager.defaultManager

    override fun append(line: String) {
        overwrite(read() + line + "\n")
    }

    override fun read(): String {
        val contents = NSString.create(
            contentsOfFile = path,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
        return contents?.toString() ?: ""
    }

    override fun overwrite(text: String) {
        ensureParentDirectory()
        (text as NSString).writeToFile(path, true, NSUTF8StringEncoding, null)
    }

    private fun ensureParentDirectory() {
        val directory = path.substringBeforeLast('/', missingDelimiterValue = "")
        if (directory.isNotEmpty() && !fileManager.fileExistsAtPath(directory)) {
            fileManager.createDirectoryAtPath(directory, true, null, null)
        }
    }
}
