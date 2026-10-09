package com.playbook.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test

class DatabaseMigrationAndroidTest {
    @Test
    fun migratesV1ToV2AndMatchesCreate() {
        val migrated = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            PlaybookDatabase.Schema.create(fresh)
            verifyDatabaseMigration(migrated, fresh)
        } finally {
            migrated.close()
            fresh.close()
        }
    }
}
