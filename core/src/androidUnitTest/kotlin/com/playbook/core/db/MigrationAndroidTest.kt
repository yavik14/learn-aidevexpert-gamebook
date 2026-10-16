package com.playbook.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test

class MigrationAndroidTest {
    @Test
    fun migratesV1ToV7PreservingNotes() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            verifyMigrationV1ToV7(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun migratesV5ToV6AddingIndexAttempts() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            verifyMigrationV5ToV6(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun migratesV6ToV7RenamingTagsToGenres() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            verifyMigrationV6ToV7(driver)
        } finally {
            driver.close()
        }
    }
}
