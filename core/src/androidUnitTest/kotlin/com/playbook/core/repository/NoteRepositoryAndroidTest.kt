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
    fun genresRoundTrip() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyGenres(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun migrationV1ToV7PreservesNotes() {
        // Driver in-memory creado como base v1 (sólo la tabla note).
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            NoteV1Schema.create(driver)
            verifyGenreMigration(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun retryQueriesRoundTrip() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyNoteRetryQueries(driver)
        } finally {
            driver.close()
        }
    }
}
