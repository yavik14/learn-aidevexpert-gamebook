@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.playbook.app.voice

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVEncoderBitRateKey
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatMPEG4AAC
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUUID
import platform.Foundation.numberWithDouble
import platform.Foundation.numberWithInt
import platform.Foundation.numberWithUnsignedInt
import platform.Speech.SFSpeechAudioBufferRecognitionRequest
import platform.Speech.SFSpeechRecognitionTask
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Adaptador iOS de [VoiceTranscriber] sobre `SFSpeechRecognizer` + `AVAudioEngine`.
 *
 * - Pide `SFSpeechRecognizer.requestAuthorization` y
 *   `AVAudioSession.requestRecordPermission`; si alguna no queda autorizada →
 *   [VoiceCaptureError.PERMISSION_DENIED]. Si `SFSpeechRecognizer` no existe o no
 *   está disponible → [VoiceCaptureError.UNAVAILABLE].
 * - Los callbacks de `Speech`/AVFoundation llegan en colas secundarias; se
 *   despachan al main queue antes de tocar estado de Compose.
 * - En la misma sesión de micrófono, el tap de `AVAudioEngine.inputNode` alimenta
 *   al reconocedor **y** escribe el audio original a un `AVAudioFile` (AAC en
 *   `.m4a`) bajo `Application Support/audio/`. Si no puede grabar, la
 *   transcripción sigue y el Adjunto es `null` (degradación tolerante).
 * - `stop()` cierra el audio y deja llegar el resultado final; `cancel()` cancela
 *   la tarea, quita el tap, detiene el engine, descarta el audio parcial y
 *   desactiva la sesión, liberando el micrófono.
 */
class IosVoiceTranscriber : VoiceTranscriber {

    private var onPartialResult: ((String) -> Unit)? = null
    private var onFinalResult: ((String, RecordedAudio?) -> Unit)? = null
    private var onError: ((VoiceCaptureError) -> Unit)? = null
    private var onStarted: (() -> Unit)? = null

    /**
     * Generación de la sesión vigente. Los callbacks asíncronos de autorización y
     * reconocimiento capturan el valor al arrancar y lo verifican antes de tocar
     * estado; [cancel] la incrementa para invalidar callbacks tardíos.
     */
    private var sessionGeneration = 0

    private var recognizer: SFSpeechRecognizer? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var audioEngine: AVAudioEngine? = null
    private var tapInstalled = false

    private val fileManager = NSFileManager.defaultManager
    private var audioFile: AVAudioFile? = null
    private var audioFilePath: String? = null
    private var audioRelativePath: String? = null
    private var audioRecordingFailed = false

    override fun start(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String, RecordedAudio?) -> Unit,
        onError: (VoiceCaptureError) -> Unit,
        onStarted: () -> Unit,
    ) {
        this.onPartialResult = onPartialResult
        this.onFinalResult = onFinalResult
        this.onError = onError
        this.onStarted = onStarted
        val generation = ++sessionGeneration

        SFSpeechRecognizer.requestAuthorization { status ->
            onMain {
                if (!isSessionCurrent(generation)) return@onMain
                val authorized =
                    status ==
                        SFSpeechRecognizerAuthorizationStatus
                            .SFSpeechRecognizerAuthorizationStatusAuthorized
                if (authorized) {
                    requestRecordPermissionThenStart(generation)
                } else {
                    onError?.invoke(VoiceCaptureError.PERMISSION_DENIED)
                }
            }
        }
    }

    override fun stop() {
        request?.endAudio()
        audioEngine?.stop()
    }

    override fun cancel() {
        sessionGeneration++
        task?.cancel()
        task = null
        request = null
        recognizer = null
        teardownAudio()
        discardAudioRecording()
        onPartialResult = null
        onFinalResult = null
        onError = null
        onStarted = null
    }

    private fun requestRecordPermissionThenStart(generation: Int) {
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            onMain {
                if (!isSessionCurrent(generation)) return@onMain
                if (granted) {
                    startRecognition(generation)
                } else {
                    onError?.invoke(VoiceCaptureError.PERMISSION_DENIED)
                }
            }
        }
    }

    private fun startRecognition(generation: Int) {
        if (!isSessionCurrent(generation)) return
        val localRecognizer = createRecognizer()
        if (localRecognizer == null || !localRecognizer.isAvailable()) {
            onError?.invoke(VoiceCaptureError.UNAVAILABLE)
            return
        }
        recognizer = localRecognizer

        val recognitionRequest = SFSpeechAudioBufferRecognitionRequest().apply {
            shouldReportPartialResults = true
        }
        request = recognitionRequest

        val engine = AVAudioEngine()
        audioEngine = engine

        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryRecord, error = null)
        session.setActive(true, error = null)

        val inputNode = engine.inputNode
        val format = inputNode.outputFormatForBus(0u)
        prepareAudioRecording()
        inputNode.installTapOnBus(0u, 1024u, format) { buffer, _ ->
            buffer?.let {
                recognitionRequest.appendAudioPCMBuffer(it)
                appendAudioBuffer(it)
            }
        }
        tapInstalled = true

        task = localRecognizer.recognitionTaskWithRequest(recognitionRequest) { result, error ->
            onMain {
                if (!isSessionCurrent(generation)) return@onMain
                when {
                    result != null -> {
                        val text = result.bestTranscription.formattedString
                        if (result.isFinal()) {
                            finishWithResult(text)
                        } else if (text.isNotBlank()) {
                            onPartialResult?.invoke(text)
                        }
                    }

                    error != null -> finishWithError(error)
                }
            }
        }

        engine.prepare()
        val started = engine.startAndReturnError(null)
        if (!started) {
            // El motor no arrancó: limpiar sin emitir onStarted (el cap no corre).
            onStarted = null
            task?.cancel()
            task = null
            request = null
            recognizer = null
            teardownAudio()
            discardAudioRecording()
            onError?.invoke(VoiceCaptureError.UNKNOWN)
            return
        }
        // Recién acá la sesión arrancó de verdad (permiso concedido + engine vivo).
        val startedCallback = onStarted
        onStarted = null
        startedCallback?.invoke()
    }

    private fun isSessionCurrent(generation: Int): Boolean =
        generation == sessionGeneration

    private fun finishWithResult(text: String) {
        teardownAudio()
        task = null
        request = null
        recognizer = null
        val audio = closeAudioRecording()
        if (text.isBlank()) {
            // Sin transcripción no hay Nota: no dejar el audio huérfano.
            audio?.let { removeAudio(it) }
            onError?.invoke(VoiceCaptureError.NO_MATCH)
        } else {
            onFinalResult?.invoke(text, audio)
        }
    }

    private fun finishWithError(error: NSError) {
        teardownAudio()
        task = null
        request = null
        recognizer = null
        discardAudioRecording()
        onError?.invoke(mapError(error))
    }

    private fun teardownAudio() {
        val engine = audioEngine
        if (engine != null && tapInstalled) {
            engine.inputNode.removeTapOnBus(0u)
            tapInstalled = false
        }
        engine?.stop()
        audioEngine = null
        AVAudioSession.sharedInstance().setActive(false, error = null)
    }

    /** Prepara el directorio y la ruta del audio original de esta sesión. */
    private fun prepareAudioRecording() {
        audioFile = null
        audioRecordingFailed = false
        val dir = "${applicationSupportDir()}/$AUDIO_DIR"
        if (!fileManager.fileExistsAtPath(dir)) {
            fileManager.createDirectoryAtPath(
                path = dir,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )
        }
        val name = "${NSUUID().UUIDString}.m4a"
        audioFilePath = "$dir/$name"
        audioRelativePath = "$AUDIO_DIR/$name"
    }

    /** Crea el `AVAudioFile` AAC perezosamente usando el formato del primer buffer. */
    private fun appendAudioBuffer(buffer: AVAudioPCMBuffer) {
        if (audioRecordingFailed) return
        val file = audioFile ?: createAudioFile(buffer) ?: run {
            audioRecordingFailed = true
            return
        }
        audioFile = file
        val written = try {
            file.writeFromBuffer(buffer, error = null)
        } catch (_: Throwable) {
            false
        }
        if (!written) {
            audioRecordingFailed = true
        }
    }

    private fun createAudioFile(buffer: AVAudioPCMBuffer): AVAudioFile? {
        val path = audioFilePath ?: return null
        val format = buffer.format
        val settings = mapOf<Any?, Any>(
            AVFormatIDKey to NSNumber.numberWithUnsignedInt(kAudioFormatMPEG4AAC),
            AVSampleRateKey to NSNumber.numberWithDouble(format.sampleRate),
            AVNumberOfChannelsKey to NSNumber.numberWithUnsignedInt(format.channelCount),
            AVEncoderBitRateKey to NSNumber.numberWithInt(AUDIO_BIT_RATE),
        )
        return try {
            AVAudioFile(
                forWriting = NSURL.fileURLWithPath(path),
                settings = settings,
                error = null,
            )
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Cierra el archivo y devuelve el Adjunto ya cerrado, o `null` si la
     * grabación no quedó válida (y en ese caso borra el parcial).
     */
    private fun closeAudioRecording(): RecordedAudio? {
        val file = audioFile
        audioFile = null
        val path = audioFilePath
        val relative = audioRelativePath
        audioFilePath = null
        audioRelativePath = null
        if (file == null || audioRecordingFailed || path == null || relative == null) {
            path?.let { fileManager.removeItemAtPath(it, error = null) }
            return null
        }
        val size = fileSize(path)
        if (size <= 0L) {
            fileManager.removeItemAtPath(path, error = null)
            return null
        }
        return RecordedAudio(
            relativePath = relative,
            byteSize = size,
            mimeType = AUDIO_MIME_TYPE,
        )
    }

    /** Descarta el audio parcial de la sesión (cancelación o fallo). */
    private fun discardAudioRecording() {
        audioFile = null
        audioRecordingFailed = false
        val path = audioFilePath
        audioFilePath = null
        audioRelativePath = null
        path?.let { fileManager.removeItemAtPath(it, error = null) }
    }

    private fun removeAudio(audio: RecordedAudio) {
        fileManager.removeItemAtPath(
            "${applicationSupportDir()}/${audio.relativePath}",
            error = null,
        )
    }

    private fun fileSize(path: String): Long {
        val attributes = fileManager.attributesOfItemAtPath(path, error = null) ?: return 0L
        return (attributes[NSFileSize] as? NSNumber)?.longLongValue ?: 0L
    }

    private fun applicationSupportDir(): String =
        NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true,
        ).firstOrNull() as? String ?: error("No se encontró Application Support")

    private fun createRecognizer(): SFSpeechRecognizer? =
        try {
            SFSpeechRecognizer()
        } catch (t: Throwable) {
            null
        }

    private fun mapError(error: NSError): VoiceCaptureError = when (error.code.toInt()) {
        // SFSpeechRecognitionErrorCode: Network = 200, RecognizerUnavailable = 201,
        // Audio = 203, NoSpeech = 204. El resto se normaliza a UNKNOWN.
        200 -> VoiceCaptureError.NETWORK
        201 -> VoiceCaptureError.UNAVAILABLE
        203, 204 -> VoiceCaptureError.NO_MATCH
        else -> VoiceCaptureError.UNKNOWN
    }

    private fun onMain(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue()) { block() }
    }

    private companion object {
        const val AUDIO_DIR = "audio"
        const val AUDIO_MIME_TYPE = "audio/mp4"
        const val AUDIO_BIT_RATE = 64_000
    }
}
