package com.playbook.core.ai

import com.playbook.core.model.Track

/**
 * Puertos de IA por modelo (ADR 0004). `chat` y `embedding` ya los cubre
 * [AiClient] (`generate`/`embed`); este archivo define los contratos restantes
 * —`transcribe`, `tts`, `image` y `jev`— como interfaces independientes que se
 * inyectan desde afuera. El core depende sólo de estos contratos; los
 * adaptadores concretos (fake, cloud, on-device) se proveen desde el
 * composition root sin tocar el core.
 *
 * Todos los métodos son `suspend` (llamadas asíncronas/cancelables) y todos los
 * fallos del runtime se señalan con el tipo existente [AiClientException].
 */

/** Salida de `tts`: audio sintetizado (bytes + mime). */
class SynthesizedAudio(val bytes: ByteArray, val mimeType: String)

/** Salida de `image`: imagen generada (bytes + mime). */
class GeneratedImage(val bytes: ByteArray, val mimeType: String)

/**
 * Propuesta de `jev`: `Track` y/o Géneros. **Nunca** se auto-asigna: el usuario
 * confirma o edita la clasificación sugerida.
 */
data class ClassificationSuggestion(val track: Track?, val genres: List<String>)

/** `transcribe`: convierte audio en texto (el Adjunto AUDIO → body). */
interface TranscribeClient {
    /** Devuelve el texto transcripto del audio; `audio` vacío no debe romper. */
    suspend fun transcribe(audio: ByteArray, mimeType: String): String
}

/** `tts`: lee un texto en voz alta (Q&A/Nota/GDD). */
interface TtsClient {
    /** Devuelve el audio sintetizado para [text]. */
    suspend fun synthesize(text: String): SynthesizedAudio
}

/** `image`: genera una imagen desde un texto (se adjunta a una Nota). */
interface ImageClient {
    /** Devuelve la imagen generada para [prompt]. */
    suspend fun generateImage(prompt: String): GeneratedImage
}

/** `jev`: sugiere `Track`/`Género` a partir de texto (el usuario confirma). */
interface JevClient {
    /** Propone una clasificación para [text]; no la persiste ni la asigna. */
    suspend fun suggest(text: String): ClassificationSuggestion
}
