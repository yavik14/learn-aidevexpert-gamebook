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
 * Puerto de captura por voz (speech-to-text) del MVP.
 *
 * Los adaptadores por plataforma viven en `androidMain`/`iosMain` y garantizan
 * que [onPartialResult]/[onFinalResult]/[onError] se invoquen en el hilo
 * principal. El puerto no conoce la persistencia: [com.playbook.app.App] crea la
 * Nota con el texto final.
 */
interface VoiceTranscriber {
    /**
     * Inicia una sesión de dictado. Debe pedir/verificar permisos y disponibilidad
     * y reportar el fallo por [onError] sin crashear. Los callbacks se invocan en
     * el hilo principal.
     */
    fun start(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (VoiceCaptureError) -> Unit,
    )

    /** Pide el resultado final (sigue [onFinalResult]/[onError]). */
    fun stop()

    /** Cancela la sesión y libera el micrófono sin emitir resultado. */
    fun cancel()
}
