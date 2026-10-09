package com.playbook.core.model

/**
 * Adjunto de una Nota: archivo original asociado (imagen de boceto hoy; audio en
 * una feature posterior). No guarda el texto derivado (OCR/transcripción) ni los
 * bytes: el archivo vive en almacenamiento privado de la app y la base sólo
 * conserva metadatos + la ruta relativa al contenedor.
 */
data class Attachment(
    val id: String,
    val noteId: String,
    val kind: AttachmentKind,
    val mimeType: String,
    /** Ruta relativa dentro del contenedor de la app (p. ej. `images/<uuid>.jpg`). */
    val filePath: String,
    val byteSize: Long,
    val createdAt: Long,
)

/**
 * Tipo de Adjunto. Los identificadores Kotlin quedan en inglés; el [code]
 * persistido usa el valor de dominio canónico en español. Sólo `IMAGE` existe en
 * `image-capture-camera`.
 */
enum class AttachmentKind(val code: String) {
    IMAGE("imagen"),
    ;

    companion object {
        /**
         * Mapea el `code` persistido al enum. Fail-fast: `kind` es `NOT NULL` y
         * sólo lo escribe este código, así que un valor desconocido representa
         * corrupción de datos y debe fallar en vez de degradarse silenciosamente.
         */
        fun fromCode(code: String): AttachmentKind =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("AttachmentKind desconocido: '$code'")
    }
}
