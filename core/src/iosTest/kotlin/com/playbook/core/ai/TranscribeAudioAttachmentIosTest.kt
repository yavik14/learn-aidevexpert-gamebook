package com.playbook.core.ai

import app.cash.sqldelight.driver.native.inMemoryDriver
import com.playbook.core.db.PlaybookDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TranscribeAudioAttachmentIosTest {
    @Test
    fun transcribesPersistedAudioAttachment() = runTest {
        val driver = inMemoryDriver(PlaybookDatabase.Schema)
        try {
            verifyAudioAttachmentTranscription(driver)
        } finally {
            driver.close()
        }
    }
}
