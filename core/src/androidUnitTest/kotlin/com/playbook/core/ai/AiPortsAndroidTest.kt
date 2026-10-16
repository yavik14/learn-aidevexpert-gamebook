package com.playbook.core.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AiPortsAndroidTest {

    @Test
    fun fakeTranscribeSatisfiesContract() = runTest {
        verifyTranscribeClientContract(FakeTranscribeClient())
    }

    @Test
    fun fakeTtsSatisfiesContract() = runTest {
        verifyTtsClientContract(FakeTtsClient())
    }

    @Test
    fun fakeImageSatisfiesContract() = runTest {
        verifyImageClientContract(FakeImageClient())
    }

    @Test
    fun fakeJevSatisfiesContract() = runTest {
        verifyJevClientContract(FakeJevClient())
    }

    @Test
    fun constantTtsSatisfiesContract() = runTest {
        verifyTtsClientContract(ConstantTtsClient())
    }

    @Test
    fun fakesAreDeterministicAndOffline() = runTest {
        verifyFakeAiPorts()
    }
}
