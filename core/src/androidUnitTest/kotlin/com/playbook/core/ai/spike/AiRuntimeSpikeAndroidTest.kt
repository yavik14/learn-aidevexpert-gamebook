package com.playbook.core.ai.spike

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Runner del spike en Android/JVM. Imprime el reporte (visible con
 * `--info` o en `core/build/test-results/testDebugUnitTest/`).
 */
class AiRuntimeSpikeAndroidTest {

    @Test
    fun reportsRuntimeArchetypes() = runTest {
        println(runAiRuntimeSpike())
    }

    @Test
    fun contractRejectsBrokenClient() = runTest {
        verifyContractRejectsBrokenClient()
    }
}
