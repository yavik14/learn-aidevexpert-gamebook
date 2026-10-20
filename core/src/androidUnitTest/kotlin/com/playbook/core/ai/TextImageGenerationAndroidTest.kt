package com.playbook.core.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class TextImageGenerationAndroidTest {
    @Test
    fun generatesAndValidatesImages() = runTest {
        verifyTextImageGeneration()
    }
}
