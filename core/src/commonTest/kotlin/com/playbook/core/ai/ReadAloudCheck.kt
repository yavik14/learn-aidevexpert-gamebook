package com.playbook.core.ai

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class RecordingTtsClient(
    private val audio: SynthesizedAudio = SynthesizedAudio(byteArrayOf(1, 2, 3), "audio/mpeg"),
) : TtsClient {
    var calls: Int = 0
    var lastText: String? = null

    override suspend fun synthesize(text: String): SynthesizedAudio {
        calls++
        lastText = text
        return audio
    }
}

private class FailingTtsClient(private val failure: Throwable) : TtsClient {
    override suspend fun synthesize(text: String): SynthesizedAudio = throw failure
}

private class EmptyAudioTtsClient : TtsClient {
    override suspend fun synthesize(text: String): SynthesizedAudio =
        SynthesizedAudio(ByteArray(0), "")
}

/** Check: trim, vacío y límite exacto de 4096 caracteres. */
fun verifyReadAloudNormalization() {
    assertNull(normalizeReadAloudText("   "))
    assertNull(normalizeReadAloudText("\n\t"))
    assertEquals("hola", normalizeReadAloudText("  hola \n"))

    val long = "a".repeat(5_000)
    val normalized = normalizeReadAloudText(long)
    assertEquals(READ_ALOUD_MAX_CHARS, normalized?.length)
    assertEquals("a".repeat(READ_ALOUD_MAX_CHARS), normalized)

    val exact = "b".repeat(READ_ALOUD_MAX_CHARS)
    assertEquals(exact, normalizeReadAloudText(exact))
}

/** Check: el servicio normaliza, llama una vez y devuelve el audio del puerto. */
suspend fun verifyReadAloudServiceReady() {
    val audio = SynthesizedAudio(byteArrayOf(9, 8, 7), "audio/mpeg")
    val client = RecordingTtsClient(audio)
    val result = ReadAloudService(client).synthesize("  leer esto  ")

    val ready = assertIs<ReadAloudResult.Ready>(result)
    assertEquals(audio, ready.audio)
    assertEquals(1, client.calls)
    assertEquals("leer esto", client.lastText)
}

/** Check: texto vacío no llama al proveedor y devuelve EmptyText. */
suspend fun verifyReadAloudEmptyDoesNotCallClient() {
    val client = RecordingTtsClient()
    val result = ReadAloudService(client).synthesize("   ")

    assertIs<ReadAloudResult.EmptyText>(result)
    assertEquals(0, client.calls)
}

/** Check: fallo del runtime y audio inválido se reportan como Failed. */
suspend fun verifyReadAloudFailures() {
    val failure = AiClientException("falló")
    assertIs<ReadAloudResult.Failed>(
        ReadAloudService(FailingTtsClient(failure)).synthesize("texto"),
    )
    assertIs<ReadAloudResult.Failed>(
        ReadAloudService(EmptyAudioTtsClient()).synthesize("texto"),
    )
}

/** Check: el fake devuelve un WAV determinista y reproducible. */
suspend fun verifyFakeTtsIsPlayable() {
    val first = FakeTtsClient().synthesize("hola")
    val second = FakeTtsClient().synthesize("hola")

    assertEquals("audio/wav", first.mimeType)
    assertTrue(first.bytes.size > 44, "el WAV debe incluir cabecera y samples")
    assertEquals("RIFF", first.bytes.copyOfRange(0, 4).decodeToString())
    assertEquals("WAVE", first.bytes.copyOfRange(8, 12).decodeToString())
    assertTrue(first.bytes.contentEquals(second.bytes), "el fake debe ser determinista")
}
