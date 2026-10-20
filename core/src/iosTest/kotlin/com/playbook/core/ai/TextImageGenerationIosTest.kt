package com.playbook.core.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TextImageGenerationIosTest {
    @Test
    fun generatesAndValidatesImages() = runTest {
        verifyTextImageGeneration()
    }
}
