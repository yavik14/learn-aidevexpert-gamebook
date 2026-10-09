package com.playbook.core.db

import app.cash.sqldelight.driver.native.inMemoryDriver
import kotlin.test.Test

class DatabaseMigrationIosTest {
    @Test
    fun migratesV1ToV2AndMatchesCreate() {
        val migrated = inMemoryDriver(EmptySqlSchema)
        val fresh = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyDatabaseMigration(migrated, fresh)
        } finally {
            migrated.close()
            fresh.close()
        }
    }
}
