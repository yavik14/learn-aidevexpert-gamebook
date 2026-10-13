package com.playbook.app.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.util.UUID

/**
 * Adaptador Android de [VoiceTranscriber] sobre `SpeechRecognizer`.
 *
 * - Pide `RECORD_AUDIO` en el primer [start] con el permission launcher del
 *   `ComponentActivity`; si se deniega → [VoiceCaptureError.PERMISSION_DENIED].
 * - Usa el servicio de reconocimiento del sistema (idioma por defecto) con
 *   resultados parciales. Los callbacks del `SpeechRecognizer` ya llegan en el
 *   hilo principal, así que no hace falta despacho adicional.
 * - En la misma sesión de micrófono arranca un `MediaRecorder` (AAC en MPEG-4) a
 *   `filesDir/audio/<uuid>.m4a` para conservar el audio original. Si no puede
 *   grabar, la transcripción sigue y el Adjunto es `null` (degradación tolerante);
 *   no se abre un segundo cliente de micrófono aparte del reconocedor.
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
    private var onFinalResult: ((String, RecordedAudio?) -> Unit)? = null
    private var onError: ((VoiceCaptureError) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var pendingStartAfterPermission: (() -> Unit)? = null

    private var recorder: MediaRecorder? = null
    private var audioFile: File? = null

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
        onFinalResult: (String, RecordedAudio?) -> Unit,
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
        discardAudioRecording()
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
        startAudioRecording()
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

    /**
     * Arranca la grabación del audio original en paralelo con el reconocedor.
     * Cualquier fallo (micrófono compartido, archivo no escribible) deja la
     * sesión sin Adjunto pero con transcripción.
     */
    private fun startAudioRecording() {
        try {
            val dir = File(activity.filesDir, AUDIO_DIR).apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.m4a")
            val active = newRecorder()
            active.setAudioSource(MediaRecorder.AudioSource.MIC)
            active.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            active.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            active.setOutputFile(file.absolutePath)
            active.prepare()
            active.start()
            recorder = active
            audioFile = file
        } catch (_: Throwable) {
            // Degradación tolerante: seguir sólo con la transcripción.
            discardAudioRecording()
        }
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(activity)
        } else {
            MediaRecorder()
        }

    /**
     * Cierra el `MediaRecorder` y devuelve el Adjunto ya cerrado, o `null` si no
     * había grabación válida. El archivo inválido se borra para no dejar
     * huérfanos.
     */
    private fun finishAudioRecording(): RecordedAudio? {
        val active = recorder ?: return null
        val file = audioFile
        recorder = null
        audioFile = null
        val stopped = try {
            active.stop()
            true
        } catch (_: Throwable) {
            // `stop()` lanza si no se capturó audio válido.
            false
        }
        try {
            active.release()
        } catch (_: Throwable) {
            // Ignorar: ya no se usará.
        }
        if (!stopped || file == null || !file.exists() || file.length() <= 0L) {
            file?.delete()
            return null
        }
        return RecordedAudio(
            relativePath = "$AUDIO_DIR/${file.name}",
            byteSize = file.length(),
            mimeType = AUDIO_MIME_TYPE,
        )
    }

    /** Detiene y descarta la grabación en curso, borrando el archivo parcial. */
    private fun discardAudioRecording() {
        val active = recorder
        val file = audioFile
        recorder = null
        audioFile = null
        if (active != null) {
            try {
                active.stop()
            } catch (_: Throwable) {
                // No había datos válidos.
            }
            try {
                active.release()
            } catch (_: Throwable) {
                // Ignorar.
            }
        }
        file?.delete()
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
            val audio = finishAudioRecording()
            if (text.isNullOrBlank()) {
                // Sin transcripción no hay Nota: no dejar el audio huérfano.
                audio?.let { fileFor(it).delete() }
                onError?.invoke(VoiceCaptureError.NO_MATCH)
            } else {
                onFinalResult?.invoke(text, audio)
            }
        }

        override fun onError(error: Int) {
            releaseRecognizer()
            discardAudioRecording()
            onError?.invoke(mapError(error))
        }

        override fun onReadyForSpeech(params: Bundle?) = Unit

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun fileFor(audio: RecordedAudio): File =
        File(activity.filesDir, audio.relativePath)

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

    private companion object {
        const val AUDIO_DIR = "audio"
        const val AUDIO_MIME_TYPE = "audio/mp4"
    }
}
