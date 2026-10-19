package com.playbook.app.ai

import com.playbook.app.BuildConfig
import com.playbook.core.ai.FakeAiClient
import com.playbook.core.ai.openai.AiMode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidAiRuntimeTest {

    @Test
    fun runtimeMatchesBuildFlag() {
        val runtime = createAndroidAiRuntime()

        if (BuildConfig.AI_MODE == "real") {
            assertEquals(AiMode.REAL, runtime.mode)
            assertEquals(BuildConfig.AI_EMBEDDING_DIMENSION, runtime.embeddingDimension)
        } else {
            assertEquals(AiMode.FAKE, runtime.mode)
            assertIs<FakeAiClient>(runtime.aiClient)
            assertEquals(FakeAiClient.DEFAULT_DIMENSION, runtime.embeddingDimension)
        }
    }

    @Test
    fun fakeBuildNeverCarriesApiKey() = runBlocking {
        if (BuildConfig.AI_MODE != "real") {
            assertEquals("", BuildConfig.AI_API_KEY)
            assertTrue(createAndroidAiRuntime().availability.isAvailable())
        }
    }
}
