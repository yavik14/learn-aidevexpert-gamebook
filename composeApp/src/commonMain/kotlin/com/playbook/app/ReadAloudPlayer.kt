package com.playbook.app

import androidx.compose.runtime.Composable
import com.playbook.core.ai.SynthesizedAudio

/** Superficie que puede estar leyéndose; sólo una está activa por vez. */
enum class ReadAloudSurface { QA_ANSWER, NOTE, GDD }

/** Estado único de lectura en voz alta, propiedad de [App]. */
sealed interface ReadAloudUiState {
    data object Detenido : ReadAloudUiState
    data class Preparando(val surface: ReadAloudSurface) : ReadAloudUiState
    data class Reproduciendo(val surface: ReadAloudSurface) : ReadAloudUiState
    data class Error(val surface: ReadAloudSurface) : ReadAloudUiState
}

/**
 * Reproductor de audio efímero en memoria. La implementación nativa vive en
 * `androidMain`/`iosMain`; no escribe archivos ni conoce la persistencia.
 */
interface ReadAloudPlayer {
    /**
     * Reproduce [audio]. Devuelve `false` si no se pudo iniciar.
     * [onCompleted]/[onError] se invocan una sola vez si el playback arrancó.
     */
    fun play(
        audio: SynthesizedAudio,
        onCompleted: () -> Unit,
        onError: () -> Unit,
    ): Boolean

    /** Detiene el playback actual; idempotente. */
    fun stop()

    /** Libera recursos; idempotente. */
    fun release()
}

/** Crea un [ReadAloudPlayer] ligado al ciclo de vida de la composición. */
@Composable
expect fun rememberReadAloudPlayer(): ReadAloudPlayer
