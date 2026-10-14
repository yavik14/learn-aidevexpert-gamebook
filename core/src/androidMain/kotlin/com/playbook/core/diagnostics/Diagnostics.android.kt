package com.playbook.core.diagnostics

import java.io.File

/** `actual` Android/JVM: archivo de texto plano en el almacenamiento privado. */
actual fun diagnosticStorage(path: String): DiagnosticStorage = AndroidDiagnosticStorage(File(path))

private class AndroidDiagnosticStorage(private val file: File) : DiagnosticStorage {

    override fun append(line: String) {
        file.parentFile?.mkdirs()
        file.appendText("$line\n")
    }

    override fun read(): String = if (file.exists()) file.readText() else ""

    override fun overwrite(text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}
