package com.playbook.core.ai

import com.playbook.core.model.GenreCatalog
import com.playbook.core.model.Track

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

/** `tts` fake: devuelve los propios bytes UTF-8 del texto como audio MP3. */
class FakeTtsClient : TtsClient {
    override suspend fun synthesize(text: String): SynthesizedAudio =
        SynthesizedAudio(text.encodeToByteArray(), "audio/mpeg")
}

/** `image` fake: devuelve un PNG simbólico derivado del prompt. */
class FakeImageClient : ImageClient {
    override suspend fun generateImage(prompt: String): GeneratedImage =
        GeneratedImage("[fake image] $prompt".encodeToByteArray(), "image/png")
}

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
