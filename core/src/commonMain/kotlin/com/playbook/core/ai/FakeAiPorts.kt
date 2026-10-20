package com.playbook.core.ai

import com.playbook.core.model.GenreCatalog
import com.playbook.core.model.Track
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.PI
import kotlin.math.sin

/**
 * Implementaciones deterministas y sin red de los puertos de IA por modelo
 * ([TranscribeClient], [TtsClient], [ImageClient], [JevClient]). Son el runtime
 * por defecto mientras `ai-fake-real-switch` no habilite el real: permiten
 * desarrollar y testear cada superficie sin proveedor ni red, y son inyectables
 * desde el composition root.
 *
 * Determinismo: el mismo input produce siempre el mismo output (salvo colisión
 * de hash donde aplique), sin usar fuentes de entropía ni `Random`.
 */

/** `transcribe` fake: deriva el texto del tamaño del audio y del `mimeType`. */
class FakeTranscribeClient : TranscribeClient {
    override suspend fun transcribe(audio: ByteArray, mimeType: String): String =
        "[fake] transcripción (${audio.size} bytes, $mimeType)"
}

/**
 * `tts` fake: devuelve un WAV PCM determinista y reproducible (16 kHz, mono,
 * 16-bit, ~2 s) con un tono suave. No simula voz: existe para que el smoke
 * Android pueda ejercitar la reproducción real sin red ni key.
 */
class FakeTtsClient : TtsClient {
    override suspend fun synthesize(text: String): SynthesizedAudio =
        SynthesizedAudio(fakeTtsWave(), "audio/wav")
}

private const val FAKE_TTS_SAMPLE_RATE = 16_000
private const val FAKE_TTS_DURATION_MILLIS = 2_000
private const val FAKE_TTS_HEADER_BYTES = 44
private const val FAKE_TTS_AMPLITUDE = 4_000
private const val FAKE_TTS_FREQUENCY = 440.0

/** Construye en memoria un WAV PCM 16-bit mono, sin entropía ni archivos. */
private fun fakeTtsWave(): ByteArray {
    val sampleCount = FAKE_TTS_SAMPLE_RATE * FAKE_TTS_DURATION_MILLIS / 1_000
    val dataSize = sampleCount * 2
    val wave = ByteArray(FAKE_TTS_HEADER_BYTES + dataSize)
    wave.writeAscii(0, "RIFF")
    wave.writeIntLittleEndian(4, 36 + dataSize)
    wave.writeAscii(8, "WAVE")
    wave.writeAscii(12, "fmt ")
    wave.writeIntLittleEndian(16, 16)
    wave.writeShortLittleEndian(20, 1)
    wave.writeShortLittleEndian(22, 1)
    wave.writeIntLittleEndian(24, FAKE_TTS_SAMPLE_RATE)
    wave.writeIntLittleEndian(28, FAKE_TTS_SAMPLE_RATE * 2)
    wave.writeShortLittleEndian(32, 2)
    wave.writeShortLittleEndian(34, 16)
    wave.writeAscii(36, "data")
    wave.writeIntLittleEndian(40, dataSize)
    for (index in 0 until sampleCount) {
        val angle = 2 * PI * FAKE_TTS_FREQUENCY * index / FAKE_TTS_SAMPLE_RATE
        val sample = (sin(angle) * FAKE_TTS_AMPLITUDE).toInt()
        wave.writeShortLittleEndian(FAKE_TTS_HEADER_BYTES + index * 2, sample)
    }
    return wave
}

private fun ByteArray.writeAscii(offset: Int, value: String) {
    value.encodeToByteArray().copyInto(this, destinationOffset = offset)
}

private fun ByteArray.writeIntLittleEndian(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
    this[offset + 2] = ((value ushr 16) and 0xFF).toByte()
    this[offset + 3] = ((value ushr 24) and 0xFF).toByte()
}

private fun ByteArray.writeShortLittleEndian(offset: Int, value: Int) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value ushr 8) and 0xFF).toByte()
}

/**
 * `image` fake: devuelve un PNG 1×1 válido y decodificable. El prompt viaja en un
 * chunk `tEXt` (Base64 ASCII, para admitir cualquier Unicode) insertado antes de
 * `IEND`; decoders como Android/Skia ignoran los chunks ancillary. Así los bytes
 * siguen siendo deterministas y distintos por prompt sin depender de la red.
 */
@OptIn(ExperimentalEncodingApi::class)
class FakeImageClient : ImageClient {
    override suspend fun generateImage(prompt: String): GeneratedImage =
        GeneratedImage(pngWithPrompt(prompt), "image/png")

    private fun pngWithPrompt(prompt: String): ByteArray {
        val payload = Base64.encode(prompt.encodeToByteArray())
        val text = "Comment\u0000fake image: $payload".encodeToByteArray()
        val chunkData = TEXT_CHUNK_TYPE + text
        val chunk = text.size.toBigEndianBytes() +
            chunkData +
            crc32(chunkData).toBigEndianBytes()
        val iendIndex = MINIMAL_PNG.size - IEND_CHUNK_SIZE
        return MINIMAL_PNG.copyOfRange(0, iendIndex) +
            chunk +
            MINIMAL_PNG.copyOfRange(iendIndex, MINIMAL_PNG.size)
    }
}

private val MINIMAL_PNG: ByteArray = hexToBytes(
    "89504E470D0A1A0A0000000D49484452000000010000000108060000001F15C489" +
        "0000000D4944415478DA63F8CFC0F01F00050001FF56C72F0D0000000049454E44AE426082",
)

private val TEXT_CHUNK_TYPE = byteArrayOf(
    't'.code.toByte(),
    'E'.code.toByte(),
    'X'.code.toByte(),
    't'.code.toByte(),
)

private const val IEND_CHUNK_SIZE = 12

private fun hexToBytes(value: String): ByteArray =
    ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }

private fun Int.toBigEndianBytes(): ByteArray = byteArrayOf(
    (ushr(24) and 0xFF).toByte(),
    (ushr(16) and 0xFF).toByte(),
    (ushr(8) and 0xFF).toByte(),
    (this and 0xFF).toByte(),
)

private fun crc32(bytes: ByteArray): Int {
    var crc = -1
    for (byte in bytes) {
        crc = crc xor (byte.toInt() and 0xFF)
        repeat(8) {
            crc = if ((crc and 1) != 0) {
                (crc ushr 1) xor CRC32_POLYNOMIAL
            } else {
                crc ushr 1
            }
        }
    }
    return crc.inv()
}

private const val CRC32_POLYNOMIAL = -0x12477CE0 // 0xEDB88320

/**
 * `jev` fake: propone un `Track` y un Género de [GenreCatalog.GENRES]
 * deterministas por hash del texto. El mismo `hashCode` elige ambos (sin
 * `Random` ni red), de modo que el smoke ejercita los chips de catálogo. Sigue
 * siendo sólo una propuesta: el consumidor normaliza y el usuario confirma.
 */
class FakeJevClient : JevClient {
    override suspend fun suggest(text: String): ClassificationSuggestion {
        val hash = text.hashCode()
        return ClassificationSuggestion(
            track = Track.entries[hash.mod(Track.entries.size)],
            genres = listOf(GenreCatalog.GENRES[hash.mod(GenreCatalog.GENRES.size)]),
        )
    }
}
