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

    @Test
    fun tagsRoundTrip() {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyNoteTags(driver)
        } finally {
            driver.close()
        }
    }

    @Test
    fun migrationV1ToV2PreservesNotes() {
        // Driver in-memory creado como base v1 (sólo la tabla note).
        val driver = inMemoryDriver(NoteV1Schema)
        try {
            verifyNoteTagMigration(driver)
        } finally {
            driver.close()
        }
    }
}
