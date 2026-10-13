package com.playbook.core.ai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NoteIndexingOrchestratorAndroidTest {
    @Test
    fun transitionsNoteStatusByIndexingResult() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyNoteIndexingOrchestrator(driver)
        } finally {
            driver.close()
        }
    }
}
