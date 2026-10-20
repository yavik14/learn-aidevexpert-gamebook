package com.playbook.core.ai

import com.playbook.core.diagnostics.DiagnosticSource
import com.playbook.core.diagnostics.InMemoryDiagnostics
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(),
    0x50.toByte(),
    0x4E.toByte(),
    0x47.toByte(),
)

private class FixedImageClient(
    private val image: GeneratedImage,
    private val failure: AiClientException? = null,
) : ImageClient {
    var calls = 0
        private set
    var lastPrompt: String? = null
        private set

    override suspend fun generateImage(prompt: String): GeneratedImage {
        calls++
        lastPrompt = prompt
        failure?.let { throw it }
        return image
    }
}

suspend fun verifyTextImageGeneration() {
    verifyGeneratedImageWithFake()
    verifyEmptyPromptSkipsClient()
    verifyPromptAndMimeAreTrimmed()
    verifyInvalidGeneratedImageIsRejected()
    verifyClientFailureMapsToFailed()
}

private suspend fun verifyGeneratedImageWithFake() {
    val service = TextImageGenerationService(FakeImageClient())
    val result = assertIs<ImageGenerationResult.Generated>(service.generate("un jefe final"))

    assertTrue(result.image.bytes.isNotEmpty(), "la imagen debe tener bytes")
    assertEquals("image/png", result.image.mimeType, "el fake debe devolver image/png")
    assertTrue(
        result.image.bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE),
        "la imagen fake debe empezar con la firma PNG",
    )

    val generated = result.image.bytes
    assertTrue(
        service.generate("un jefe final").let {
            it is ImageGenerationResult.Generated && it.image.bytes.contentEquals(generated)
        },
        "el mismo prompt debe producir los mismos bytes",
    )
    val other = assertIs<ImageGenerationResult.Generated>(service.generate("un nivel tutorial"))
    assertFalse(other.image.bytes.contentEquals(generated), "prompts distintos deben diferir")
}

private suspend fun verifyEmptyPromptSkipsClient() {
    val client = FixedImageClient(GeneratedImage(byteArrayOf(1.toByte()), "image/png"))
    val service = TextImageGenerationService(client)

    assertEquals(ImageGenerationResult.EmptyPrompt, service.generate("   "))
    assertEquals(0, client.calls, "un prompt vacío no debe llamar al cliente")
}

private suspend fun verifyPromptAndMimeAreTrimmed() {
    val client = FixedImageClient(GeneratedImage(byteArrayOf(1.toByte(), 2.toByte()), " image/jpeg "))
    val service = TextImageGenerationService(client)
    val result = assertIs<ImageGenerationResult.Generated>(service.generate("  un jefe  "))

    assertEquals("un jefe", client.lastPrompt, "el prompt debe viajar con trim")
    assertEquals("image/jpeg", result.image.mimeType, "el mime debe normalizarse con trim")
}

private suspend fun verifyInvalidGeneratedImageIsRejected() {
    val diagnostics = InMemoryDiagnostics()
    val emptyBytes = TextImageGenerationService(
        FixedImageClient(GeneratedImage(ByteArray(0), "image/png")),
        diagnostics,
    )
    assertEquals(ImageGenerationResult.InvalidImage, emptyBytes.generate("prompt"))

    val wrongMime = TextImageGenerationService(
        FixedImageClient(GeneratedImage(byteArrayOf(1.toByte(), 2.toByte()), "application/octet-stream")),
        diagnostics,
    )
    assertEquals(ImageGenerationResult.InvalidImage, wrongMime.generate("prompt"))

    val emptyMime = TextImageGenerationService(
        FixedImageClient(GeneratedImage(byteArrayOf(1.toByte(), 2.toByte()), "  ")),
        diagnostics,
    )
    assertEquals(ImageGenerationResult.InvalidImage, emptyMime.generate("prompt"))

    val entries = diagnostics.snapshot()
    assertEquals(3, entries.size, "cada imagen inválida debe registrar un diagnóstico")
    assertTrue(entries.all { it.source == DiagnosticSource.IMAGE })
    assertTrue(entries.all { it.summary == TextImageGenerationService.SUMMARY_INVALID })
}

private suspend fun verifyClientFailureMapsToFailed() {
    val diagnostics = InMemoryDiagnostics()
    val service = TextImageGenerationService(
        FixedImageClient(
            image = GeneratedImage(byteArrayOf(1.toByte()), "image/png"),
            failure = AiClientException("image falló con HTTP 500"),
        ),
        diagnostics,
    )

    assertIs<ImageGenerationResult.Failed>(service.generate("prompt"))
    val entry = diagnostics.snapshot().single()
    assertEquals(DiagnosticSource.IMAGE, entry.source)
    assertEquals(TextImageGenerationService.SUMMARY_FAILED, entry.summary)
    assertEquals(null, entry.detail, "el diagnóstico no debe incluir datos del prompt")
}
