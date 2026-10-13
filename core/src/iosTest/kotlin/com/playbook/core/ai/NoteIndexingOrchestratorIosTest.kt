package com.playbook.core.ai

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NoteIndexingOrchestratorIosTest {
    @Test
    fun transitionsNoteStatusByIndexingResult() = runTest {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyNoteIndexingOrchestrator(driver)
        } finally {
            driver.close()
        }
    }
}
