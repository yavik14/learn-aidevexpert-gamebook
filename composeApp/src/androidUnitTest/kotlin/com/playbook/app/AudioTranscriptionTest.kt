package com.playbook.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Contrato de los helpers puros de la transcripción de audio: etiqueta
 * determinista del selector, mensajes/acción por estado y semántica de anexado
 * (la misma de `appendDictation`, usada por "Anexar").
 */
class AudioTranscriptionTest {
    @Test
    fun audioLabelIsOrdinalPlusReadableSize() {
        assertEquals("Audio 1 · 13 B", audioAttachmentLabel(0, 13L))
        assertEquals("Audio 2 · 20 B", audioAttachmentLabel(1, 20L))
        assertEquals("Audio 3 · 2 KB", audioAttachmentLabel(2, 1_536L))
        assertEquals("Audio 4 · 1.5 MB", audioAttachmentLabel(3, 1_572_864L))
    }

    @Test
    fun transcriptionStatesMapToStableText() {
        assertEquals(
            "Transcripción aplicada. Guarda para conservarla.",
            TRANSCRIPTION_APPLIED_NOTICE,
        )
        assertNull(AudioTranscriptionUiState.Idle.message())
        assertNull(AudioTranscriptionUiState.Transcribing.message())
        assertEquals("La transcripción quedó vacía", AudioTranscriptionUiState.Empty.message())
        assertEquals(
            "No se pudo leer el audio adjunto",
            AudioTranscriptionUiState.Unavailable.message(),
        )
        assertEquals(
            "No se pudo transcribir. Revisa la conexión e inténtalo de nuevo",
            AudioTranscriptionUiState.Failed.message(),
        )

        assertEquals("Transcribir", AudioTranscriptionUiState.Idle.actionLabel())
        assertEquals("Transcribiendo…", AudioTranscriptionUiState.Transcribing.actionLabel())
        assertEquals("Reintentar", AudioTranscriptionUiState.Empty.actionLabel())
        assertEquals("Reintentar", AudioTranscriptionUiState.Unavailable.actionLabel())
        assertEquals("Reintentar", AudioTranscriptionUiState.Failed.actionLabel())
    }

    @Test
    fun anexarKeepsExistingBodyAndOmitsBlanks() {
        assertEquals(
            "Idea existente\n[fake] transcripción",
            appendDictation(" Idea existente ", " [fake] transcripción "),
        )
        assertEquals("sólo nueva", appendDictation("   ", "sólo nueva"))
        assertEquals("sólo previa", appendDictation("sólo previa", ""))
    }
}
