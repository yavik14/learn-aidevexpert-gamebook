package com.playbook.core.diagnostics

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DiagnosticsIosTest {
    @Test
    fun recordsFailuresAndKeepsSinkBestEffort() = runTest {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyDiagnostics(driver)
        } finally {
            driver.close()
        }
    }
}
