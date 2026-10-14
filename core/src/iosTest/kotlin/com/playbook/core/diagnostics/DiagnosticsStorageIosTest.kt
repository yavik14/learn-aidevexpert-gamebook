@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.playbook.core.diagnostics

import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.Test

/** Ejercita el `actual` iOS (`NSFileManager`/`NSString`) en runtime. */
class DiagnosticsStorageIosTest {
    @Test
    fun realFileStorageRoundTripsAndTrims() {
        val path = "${NSTemporaryDirectory()}playbook-diagnostics-${NSUUID().UUIDString}.log"
        try {
            verifyDiagnosticStorage(diagnosticStorage(path))
        } finally {
            NSFileManager.defaultManager.removeItemAtPath(path, error = null)
        }
    }
}
