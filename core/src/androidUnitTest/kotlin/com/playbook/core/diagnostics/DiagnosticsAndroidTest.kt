package com.playbook.core.diagnostics

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class DiagnosticsAndroidTest {
    @Test
    fun recordsFailuresAndKeepsSinkBestEffort() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyDiagnostics(driver)
        } finally {
            driver.close()
        }
    }
}
