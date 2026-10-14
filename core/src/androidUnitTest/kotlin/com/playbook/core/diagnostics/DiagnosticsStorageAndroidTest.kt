package com.playbook.core.diagnostics

import java.io.File
import kotlin.test.Test

/** Ejercita el `actual` Android/JVM (`java.io.File`) en runtime. */
class DiagnosticsStorageAndroidTest {
    @Test
    fun realFileStorageRoundTripsAndTrims() {
        val file = File(
            System.getProperty("java.io.tmpdir"),
            "playbook-diagnostics-${System.nanoTime()}.log",
        )
        try {
            verifyDiagnosticStorage(diagnosticStorage(file.absolutePath))
        } finally {
            file.delete()
        }
    }
}
