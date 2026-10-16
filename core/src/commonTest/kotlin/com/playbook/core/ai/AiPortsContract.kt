package com.playbook.core.ai

import com.playbook.core.model.Track
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Segunda implementación de [TtsClient], sólo de test. Ignora el texto y
 * devuelve siempre el mismo audio, pero cumple el contrato del puerto (bytes y
 * `mimeType` no vacíos, determinismo). Sirve para demostrar la sustituibilidad
 * del contrato sin tocar archivos de producción de `:core`.
 */
internal class ConstantTtsClient : TtsClient {
    override suspend fun synthesize(text: String): SynthesizedAudio =
        SynthesizedAudio("audio-constante".encodeToByteArray(), "audio/constant")
}

/**
 * Contrato genérico del puerto [TranscribeClient], independiente de la
 * implementación. Debe pasar con cualquier runtime (fake, cloud, on-device).
 */
suspend fun verifyTranscribeClientContract(port: TranscribeClient) {
    val audio = "fake audio payload".encodeToByteArray()

    // Texto no vacío y determinista (misma entrada → mismo texto).
    val first = port.transcribe(audio, "audio/mp4")
    assertTrue(first.isNotEmpty(), "la transcripción no debe ser vacía")
    assertEquals(first, port.transcribe(audio, "audio/mp4"), "transcribe debe ser determinista")

    // Un byte-array vacío no rompe el contrato (no lanza).
    port.transcribe(ByteArray(0), "audio/mp4")
}

/**
 * Contrato genérico del puerto [TtsClient], independiente de la implementación.
 */
suspend fun verifyTtsClientContract(port: TtsClient) {
    val first = port.synthesize("hola")
    assertTrue(first.bytes.isNotEmpty(), "los bytes del audio sintetizado no deben ser vacíos")
    assertTrue(first.mimeType.isNotEmpty(), "el mimeType del audio no debe ser vacío")

    // Determinismo: misma entrada → mismos bytes y mismo mime.
    val second = port.synthesize("hola")
    assertContentEquals(first.bytes, second.bytes, "synthesize debe ser determinista")
    assertEquals(first.mimeType, second.mimeType, "el mimeType debe ser determinista")
}

/**
 * Contrato genérico del puerto [ImageClient], independiente de la implementación.
 */
suspend fun verifyImageClientContract(port: ImageClient) {
    val first = port.generateImage("un jefe final")
    assertTrue(first.bytes.isNotEmpty(), "los bytes de la imagen no deben ser vacíos")
    assertTrue(first.mimeType.isNotEmpty(), "el mimeType de la imagen no debe ser vacío")

    // Determinismo: mismo prompt → mismos bytes y mismo mime.
    val second = port.generateImage("un jefe final")
    assertContentEquals(first.bytes, second.bytes, "generateImage debe ser determinista")
    assertEquals(first.mimeType, second.mimeType, "el mimeType debe ser determinista")

    // Prompts distintos → bytes distintos.
    val other = port.generateImage("un nivel tutorial")
    assertFalse(
        first.bytes.contentEquals(other.bytes),
        "prompts distintos deben producir imágenes distintas",
    )
}

/**
 * Contrato genérico del puerto [JevClient], independiente de la implementación.
 * Sólo propone: no auto-asigna nada.
 */
suspend fun verifyJevClientContract(port: JevClient) {
    val first = port.suggest("un RPG por turnos")

    assertTrue(
        first.track == null || first.track in Track.entries,
        "el track sugerido debe ser un Track conocido o null",
    )
    assertTrue(first.genres.isNotEmpty(), "la lista de géneros no debe ser vacía")
    assertTrue(first.genres.all { it.isNotEmpty() }, "cada género debe ser no vacío")

    // Determinismo (misma entrada → misma propuesta).
    assertEquals(first, port.suggest("un RPG por turnos"), "suggest debe ser determinista")
}

/**
 * Comportamiento específico de los fakes de [FakeAiPorts]: determinismo y
 * estabilidad entre invocaciones (Scenario 6). No hay red ni key.
 */
suspend fun verifyFakeAiPorts() {
    // transcribe: mismo audio → mismo texto; tolera audio vacío.
    val transcribe = FakeTranscribeClient()
    val audio = "audio de prueba".encodeToByteArray()
    val transcribedOnce = transcribe.transcribe(audio, "audio/mp4")
    assertEquals(
        transcribedOnce,
        transcribe.transcribe(audio, "audio/mp4"),
        "FakeTranscribeClient debe ser determinista",
    )
    assertTrue(
        transcribe.transcribe(ByteArray(0), "audio/mp4").isNotEmpty(),
        "FakeTranscribeClient debe tolerar audio vacío",
    )

    // tts: mismo texto → mismos bytes; mime estable.
    val tts = FakeTtsClient()
    val audioOnce = tts.synthesize("leer en voz alta")
    val audioTwice = tts.synthesize("leer en voz alta")
    assertContentEquals(audioOnce.bytes, audioTwice.bytes, "FakeTtsClient debe ser determinista")
    assertEquals("audio/mpeg", audioOnce.mimeType, "FakeTtsClient debe usar audio/mpeg")

    // image: mismo prompt → mismos bytes; prompts distintos → bytes distintos.
    val image = FakeImageClient()
    val imageOnce = image.generateImage("un jefe final")
    val imageTwice = image.generateImage("un jefe final")
    assertContentEquals(imageOnce.bytes, imageTwice.bytes, "FakeImageClient debe ser determinista")
    assertEquals("image/png", imageOnce.mimeType, "FakeImageClient debe usar image/png")
    assertFalse(
        imageOnce.bytes.contentEquals(image.generateImage("otro prompt").bytes),
        "FakeImageClient debe producir bytes distintos por prompt",
    )

    // jev: misma propuesta por hash; no auto-asigna (el caller decide).
    val jev = FakeJevClient()
    assertEquals(jev.suggest("un RPG por turnos"), jev.suggest("un RPG por turnos"))
}
