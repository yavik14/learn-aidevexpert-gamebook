package com.playbook.core.ai

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class NoteIndexingAndroidTest {
    @Test
    fun generateOverwriteFailureAndInvalidResponse() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyNoteIndexing(driver)
        } finally {
            driver.close()
        }
    }
}
