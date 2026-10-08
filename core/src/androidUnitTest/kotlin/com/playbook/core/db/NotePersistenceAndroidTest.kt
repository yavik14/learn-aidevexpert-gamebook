package com.playbook.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test

class NotePersistenceAndroidTest {
    @Test
    fun writesAndReadsNote() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyNotePersistence(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun createDatabaseOpensConnection() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(driver)
            verifyCreateDatabaseOpensConnection(driver)
        } finally {
            driver.close()
        }
    }
}
