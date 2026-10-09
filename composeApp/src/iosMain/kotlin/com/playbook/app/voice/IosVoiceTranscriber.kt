@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.playbook.app.voice

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.setActive
import platform.Foundation.NSError
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
 * - `stop()` cierra el audio y deja llegar el resultado final; `cancel()` cancela
 *   la tarea, quita el tap, detiene el engine y desactiva la sesión, liberando el
 *   micrófono.
 */
class IosVoiceTranscriber : VoiceTranscriber {

    private var onPartialResult: ((String) -> Unit)? = null
    private var onFinalResult: ((String) -> Unit)? = null
    private var onError: ((VoiceCaptureError) -> Unit)? = null

    private var recognizer: SFSpeechRecognizer? = null
    private var request: SFSpeechAudioBufferRecognitionRequest? = null
    private var task: SFSpeechRecognitionTask? = null
    private var audioEngine: AVAudioEngine? = null
    private var tapInstalled = false

    override fun start(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (VoiceCaptureError) -> Unit,
    ) {
        this.onPartialResult = onPartialResult
        this.onFinalResult = onFinalResult
        this.onError = onError

        SFSpeechRecognizer.requestAuthorization { status ->
            onMain {
                val authorized =
                    status ==
                        SFSpeechRecognizerAuthorizationStatus
                            .SFSpeechRecognizerAuthorizationStatusAuthorized
                if (authorized) {
                    requestRecordPermissionThenStart()
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
        task?.cancel()
        task = null
        request = null
        recognizer = null
        teardownAudio()
        onPartialResult = null
        onFinalResult = null
        onError = null
    }

    private fun requestRecordPermissionThenStart() {
        AVAudioSession.sharedInstance().requestRecordPermission { granted ->
            onMain {
                if (granted) {
                    startRecognition()
                } else {
                    onError?.invoke(VoiceCaptureError.PERMISSION_DENIED)
                }
            }
        }
    }

    private fun startRecognition() {
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
        inputNode.installTapOnBus(0u, 1024u, format) { buffer, _ ->
            buffer?.let { recognitionRequest.appendAudioPCMBuffer(it) }
        }
        tapInstalled = true

        task = localRecognizer.recognitionTaskWithRequest(recognitionRequest) { result, error ->
            onMain {
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
        engine.startAndReturnError(null)
    }

    private fun finishWithResult(text: String) {
        teardownAudio()
        task = null
        request = null
        recognizer = null
        if (text.isBlank()) {
            onError?.invoke(VoiceCaptureError.NO_MATCH)
        } else {
            onFinalResult?.invoke(text)
        }
    }

    private fun finishWithError(error: NSError) {
        teardownAudio()
        task = null
        request = null
        recognizer = null
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
}
