package com.playbook.core.db

import app.cash.sqldelight.driver.native.inMemoryDriver
import kotlin.test.Test

class MigrationIosTest {
    @Test
    fun migratesV1ToV7PreservingNotes() {
        val driver = inMemoryDriver(BlankTestSchema)
        try {
            verifyMigrationV1ToV7(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun migratesV5ToV6AddingIndexAttempts() {
        val driver = inMemoryDriver(BlankTestSchema)
        try {
            verifyMigrationV5ToV6(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun migratesV6ToV7RenamingTagsToGenres() {
        val driver = inMemoryDriver(BlankTestSchema)
        try {
            verifyMigrationV6ToV7(driver)
        } finally {
            driver.close()
        }
    }
}
