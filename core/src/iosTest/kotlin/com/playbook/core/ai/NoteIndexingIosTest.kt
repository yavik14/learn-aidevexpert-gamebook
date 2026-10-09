package com.playbook.core.ai

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NoteIndexingIosTest {
    @Test
    fun generateOverwriteFailureAndInvalidResponse() = runTest {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyNoteIndexing(driver)
        } finally {
            driver.close()
        }
    }
}
