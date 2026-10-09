package com.playbook.core.ai.spike

import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * Runner del spike en iOS. Imprime el reporte (visible con `--info` o en
 * `core/build/test-results/iosSimulatorArm64Test/`).
 */
class AiRuntimeSpikeIosTest {

    @Test
    fun reportsRuntimeArchetypes() = runTest {
        println(runAiRuntimeSpike())
    }

    @Test
    fun contractRejectsBrokenClient() = runTest {
        verifyContractRejectsBrokenClient()
    }
}
