package com.playbook.app.voice

/**
 * Errores de captura de voz normalizados por plataforma.
 *
 * Cada adaptador nativo mapea sus códigos (Android `SpeechRecognizer`, iOS
 * `Speech`) a este conjunto para que la UI tenga un único contrato.
 */
enum class VoiceCaptureError {
    /** El usuario denegó el permiso de micrófono o de reconocimiento. */
    PERMISSION_DENIED,

    /** El motor de reconocimiento no está disponible en el dispositivo. */
    UNAVAILABLE,

    /** No se detectó habla o no hubo coincidencia. */
    NO_MATCH,

    /** Fallo de red del servicio de reconocimiento. */
    NETWORK,

    /** El reconocedor está ocupado. */
    BUSY,

    /** Cualquier otro fallo no clasificado. */
    UNKNOWN,
}

/**
 * Audio original grabado durante una sesión de dictado y ya cerrado en
 * almacenamiento privado con **ruta relativa** al contenedor de la app (p. ej.
 * `audio/<uuid>.m4a`). Es el Adjunto de la Nota; el texto transcripto vive en el
 * `body`. Se entrega en `onFinalResult` junto con el texto para evitar la carrera
 * entre el resultado del STT y el cierre del archivo.
 */
data class RecordedAudio(
    val relativePath: String,
    val byteSize: Long,
    val mimeType: String,
)

/**
 * Puerto de captura por voz (speech-to-text) del MVP.
 *
 * Los adaptadores por plataforma viven en `androidMain`/`iosMain` y garantizan
 * que [onPartialResult]/[onFinalResult]/[onError] se invoquen en el hilo
 * principal. El puerto no conoce la persistencia: [com.playbook.app.App] crea la
 * Nota con el texto final y persiste el Adjunto [RecordedAudio] si lo hay.
 */
interface VoiceTranscriber {
    /**
     * Inicia una sesión de dictado. Debe pedir/verificar permisos y disponibilidad
     * y reportar el fallo por [onError] sin crashear. Además alimenta al motor de
     * STT, graba el audio original (misma sesión de micrófono) y lo entrega en
     * [onFinalResult]; si no se pudo grabar, entrega `null` y la transcripción
     * sigue (degradación tolerante). Los callbacks se invocan en el hilo
     * principal.
     *
     * [onStarted] se invoca **una vez por sesión** cuando el reconocimiento y la
     * grabación ya arrancaron de verdad (permiso concedido). No se invoca si hay
     * error de permiso, disponibilidad o arranque; permite que el cap de 30s no
     * cuente el diálogo de permiso. [cancel] lo descarta junto con el resto de
     * callbacks.
     */
    fun start(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String, RecordedAudio?) -> Unit,
        onError: (VoiceCaptureError) -> Unit,
        onStarted: () -> Unit = {},
    )

    /** Pide el resultado final (sigue [onFinalResult]/[onError]). */
    fun stop()

    /** Cancela la sesión, libera el micrófono y descarta el audio parcial. */
    fun cancel()
}
