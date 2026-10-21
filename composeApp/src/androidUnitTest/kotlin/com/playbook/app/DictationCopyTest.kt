package com.playbook.app

import com.playbook.app.voice.VoiceCaptureError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Copy es-ES de los errores de dictado: tuteo, “Habilítalo” y “reintenta”. */
class DictationCopyTest {
    @Test
    fun voiceCaptureErrorsUseSpainSpanish() {
        val permissionDenied = VoiceCaptureError.PERMISSION_DENIED.message()
        assertEquals(
            "Permiso de micrófono denegado. Habilítalo en los ajustes del sistema y reintenta.",
            permissionDenied,
        )
        assertTrue(permissionDenied.contains("Habilítalo"))
        assertTrue(permissionDenied.contains("reintenta"))

        assertEquals(
            "No se escuchó nada. Prueba de nuevo.",
            VoiceCaptureError.NO_MATCH.message(),
        )
        assertEquals(
            "No hay conexión para transcribir. Revisa tu red e inténtalo otra vez.",
            VoiceCaptureError.NETWORK.message(),
        )
        assertEquals(
            "El reconocimiento está ocupado. Reintenta en un momento.",
            VoiceCaptureError.BUSY.message(),
        )
        assertEquals(
            "No se pudo transcribir. Prueba de nuevo.",
            VoiceCaptureError.UNKNOWN.message(),
        )

        VoiceCaptureError.entries.forEach { error ->
            val message = error.message()
            assertFalse(
                message.contains("Probá") ||
                    message.contains("Revisá") ||
                    message.contains("Intentá") ||
                    message.contains("Reintentá") ||
                    message.contains("Habilitalo"),
                "el copy visible no debe usar voseo: $message",
            )
        }
    }
}
