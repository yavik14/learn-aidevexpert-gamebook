package com.playbook.core.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class AiClientIosTest {

    @Test
    fun fakeSatisfiesContract() = runTest {
        verifyAiClientContract(FakeAiClient())
    }

    @Test
    fun constantSatisfiesContract() = runTest {
        verifyAiClientContract(ConstantAiClient())
    }

    @Test
    fun fakeBehaviourIsDeterministic() = runTest {
        verifyFakeAiClient()
    }
}
