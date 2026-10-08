package com.playbook.core.repository

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlin.test.Test

class NoteRepositoryIosTest {
    @Test
    fun crudRoundTrip() {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyNoteCrud(driver)
        } finally {
            driver.close()
        }
    }
}
