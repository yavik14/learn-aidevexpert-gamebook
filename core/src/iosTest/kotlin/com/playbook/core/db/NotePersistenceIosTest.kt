package com.playbook.core.db

import app.cash.sqldelight.driver.native.inMemoryDriver
import kotlin.test.Test

class NotePersistenceIosTest {
    @Test
    fun writesAndReadsNote() {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyNotePersistence(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun createDatabaseOpensConnection() {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyCreateDatabaseOpensConnection(driver)
        } finally {
            driver.close()
        }
    }
}
