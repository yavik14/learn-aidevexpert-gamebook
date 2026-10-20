package com.playbook.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.playbook.core.ai.SynthesizedAudio

/**
 * Implementación iOS mínima: iOS está congelado (ADR 0003) y esta feature sólo
 * exige que compile. El playback no está disponible, así que [play] devuelve
 * `false` y la UI muestra su estado de error sin crashear.
 */
@Composable
actual fun rememberReadAloudPlayer(): ReadAloudPlayer = remember { IosReadAloudPlayer() }

private class IosReadAloudPlayer : ReadAloudPlayer {
    override fun play(
        audio: SynthesizedAudio,
        onCompleted: () -> Unit,
        onError: () -> Unit,
    ): Boolean = false

    override fun stop() = Unit

    override fun release() = Unit
}
