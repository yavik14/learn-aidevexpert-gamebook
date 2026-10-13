package com.playbook.core.repository

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class NoteLinkRepositoryIosTest {
    @Test
    fun canonicalUpsertReadsBothSidesAndCascade() {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyNoteLinkRepository(driver)
        } finally {
            driver.close()
        }
    }
}
