package com.playbook.core.repository

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class NoteRepositoryAndroidTest {
    @Test
    fun crudRoundTrip() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyNoteCrud(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun tagsRoundTrip() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyNoteTags(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun migrationV1ToV2PreservesNotes() {
        // Driver in-memory creado como base v1 (sólo la tabla note).
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NoteV1Schema.create(driver)
            verifyNoteTagMigration(driver)
        } finally {
            driver.close()
        }
    }
}
