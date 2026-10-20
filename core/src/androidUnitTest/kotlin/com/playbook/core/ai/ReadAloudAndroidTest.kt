package com.playbook.core.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class ReadAloudAndroidTest {

    @Test
    fun normalizesTrimEmptyAndLimit() {
        verifyReadAloudNormalization()
    }

    @Test
    fun serviceReturnsReadyAudio() = runTest {
        verifyReadAloudServiceReady()
    }

    @Test
    fun emptyTextDoesNotCallClient() = runTest {
        verifyReadAloudEmptyDoesNotCallClient()
    }

    @Test
    fun failuresAreReported() = runTest {
        verifyReadAloudFailures()
    }

    @Test
    fun fakeTtsIsPlayableAndDeterministic() = runTest {
        verifyFakeTtsIsPlayable()
    }
}
