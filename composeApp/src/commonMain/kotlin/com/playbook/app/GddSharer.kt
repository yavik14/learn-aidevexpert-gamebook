package com.playbook.app

import androidx.compose.runtime.Composable

/**
 * Comparte texto Markdown con el share sheet del sistema, **en memoria** (sin
 * archivos, permisos ni persistencia). Es un puerto `expect`/`actual`: la
 * implementación nativa vive en cada plataforma.
 */
interface GddSharer {
    /**
     * Presenta el share sheet con [markdown].
     *
     * @return `true` si se pudo presentar; `false` si el share no está disponible
     *   en este dispositivo.
     */
    fun share(markdown: String): Boolean
}

/** Crea un [GddSharer] ligado al ciclo de vida de la composición. */
@Composable
expect fun rememberGddSharer(): GddSharer

/**
 * Timestamp local pre-formateado (`"yyyy-MM-dd HH:mm"`), generado por la
 * plataforma para que `:core` no dependa de APIs de fecha. Sólo alimenta el
 * fallback `Nota <fecha>` del Markdown.
 */
expect fun currentTimestampLabel(): String
