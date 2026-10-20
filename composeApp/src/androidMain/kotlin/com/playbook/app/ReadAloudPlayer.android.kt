package com.playbook.app

import android.media.MediaDataSource
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.playbook.core.ai.SynthesizedAudio

/**
 * Implementación Android: `MediaPlayer` alimentado por un `MediaDataSource` en
 * memoria. No escribe archivos ni pide permisos; los bytes del TTS nunca tocan
 * disco.
 */
@Composable
actual fun rememberReadAloudPlayer(): ReadAloudPlayer {
    val player = remember { AndroidReadAloudPlayer() }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    return player
}

private class AndroidReadAloudPlayer : ReadAloudPlayer {
    private var player: MediaPlayer? = null
    private var source: ByteArrayMediaDataSource? = null
    private var onCompleted: (() -> Unit)? = null
    private var onError: (() -> Unit)? = null

    override fun play(
        audio: SynthesizedAudio,
        onCompleted: () -> Unit,
        onError: () -> Unit,
    ): Boolean {
        teardown()
        if (audio.bytes.isEmpty()) return false
        val mediaPlayer = MediaPlayer()
        val dataSource = ByteArrayMediaDataSource(audio.bytes)
        return try {
            this.onCompleted = onCompleted
            this.onError = onError
            mediaPlayer.setDataSource(dataSource)
            mediaPlayer.setOnPreparedListener { prepared -> prepared.start() }
            mediaPlayer.setOnCompletionListener {
                val callback = this.onCompleted
                teardown()
                callback?.invoke()
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                val callback = this.onError
                teardown()
                callback?.invoke()
                true
            }
            player = mediaPlayer
            source = dataSource
            mediaPlayer.prepareAsync()
            true
        } catch (_: Throwable) {
            teardown()
            false
        }
    }

    override fun stop() {
        teardown()
    }

    override fun release() {
        teardown()
    }

    private fun teardown() {
        val active = player
        player = null
        onCompleted = null
        onError = null
        try {
            active?.setOnPreparedListener(null)
            active?.setOnCompletionListener(null)
            active?.setOnErrorListener(null)
            active?.stop()
        } catch (_: Throwable) {
            // El player pudo no haber llegado a started; igual se libera.
        }
        try {
            active?.release()
        } catch (_: Throwable) {
            // Liberación best-effort.
        }
        val data = source
        source = null
        try {
            data?.close()
        } catch (_: Throwable) {
            // No hay recurso externo que cerrar.
        }
    }
}

/** `MediaDataSource` de sólo lectura sobre bytes en memoria. */
private class ByteArrayMediaDataSource(
    private val data: ByteArray,
) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position < 0L || position >= data.size) return -1
        val start = position.toInt()
        val length = minOf(size, data.size - start)
        data.copyInto(buffer, destinationOffset = offset, startIndex = start, endIndex = start + length)
        return length
    }

    override fun getSize(): Long = data.size.toLong()

    override fun close() = Unit
}
