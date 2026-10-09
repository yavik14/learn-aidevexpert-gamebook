package com.playbook.app.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Adaptador Android de [VoiceTranscriber] sobre `SpeechRecognizer`.
 *
 * - Pide `RECORD_AUDIO` en el primer [start] con el permission launcher del
 *   `ComponentActivity`; si se deniega → [VoiceCaptureError.PERMISSION_DENIED].
 * - Usa el servicio de reconocimiento del sistema (idioma por defecto) con
 *   resultados parciales. Los callbacks del `SpeechRecognizer` ya llegan en el
 *   hilo principal, así que no hace falta despacho adicional.
 * - El `SpeechRecognizer` se crea por sesión y se destruye al terminar/cancelar
 *   para no filtrar recursos.
 *
 * El launcher se registra en el constructor, antes de que la actividad arranque;
 * por eso el adaptador debe construirse en `onCreate`.
 */
class AndroidVoiceTranscriber(
    private val activity: ComponentActivity,
) : VoiceTranscriber {

    private var onPartialResult: ((String) -> Unit)? = null
    private var onFinalResult: ((String) -> Unit)? = null
    private var onError: ((VoiceCaptureError) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var pendingStartAfterPermission: (() -> Unit)? = null

    private val permissionLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val startNow = pendingStartAfterPermission
            pendingStartAfterPermission = null
            if (granted) {
                startNow?.invoke()
            } else {
                onError?.invoke(VoiceCaptureError.PERMISSION_DENIED)
            }
        }

    override fun start(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (VoiceCaptureError) -> Unit,
    ) {
        this.onPartialResult = onPartialResult
        this.onFinalResult = onFinalResult
        this.onError = onError

        if (hasAudioPermission()) {
            startRecognition()
        } else {
            pendingStartAfterPermission = { startRecognition() }
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    override fun cancel() {
        recognizer?.let { active ->
            active.cancel()
            active.destroy()
        }
        recognizer = null
        pendingStartAfterPermission = null
        onPartialResult = null
        onFinalResult = null
        onError = null
    }

    private fun hasAudioPermission(): Boolean =
        activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            onError?.invoke(VoiceCaptureError.UNAVAILABLE)
            return
        }
        releaseRecognizer()
        val active = SpeechRecognizer.createSpeechRecognizer(activity).also {
            it.setRecognitionListener(listener)
        }
        recognizer = active
        active.startListening(recognizerIntent())
    }

    private fun recognizerIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

    private fun releaseRecognizer() {
        recognizer?.let { active ->
            active.cancel()
            active.destroy()
        }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResults(partialResults: Bundle?) {
            partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.let { onPartialResult?.invoke(it) }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            releaseRecognizer()
            if (text.isNullOrBlank()) {
                onError?.invoke(VoiceCaptureError.NO_MATCH)
            } else {
                onFinalResult?.invoke(text)
            }
        }

        override fun onError(error: Int) {
            releaseRecognizer()
            onError?.invoke(mapError(error))
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun mapError(error: Int): VoiceCaptureError = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        -> VoiceCaptureError.NO_MATCH

        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> VoiceCaptureError.NETWORK

        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceCaptureError.BUSY

        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            VoiceCaptureError.PERMISSION_DENIED

        else -> VoiceCaptureError.UNKNOWN
    }
}
