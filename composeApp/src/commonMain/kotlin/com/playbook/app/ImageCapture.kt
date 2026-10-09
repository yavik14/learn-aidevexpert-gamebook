package com.playbook.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Fuente de la imagen a capturar. */
enum class ImageSource { CAMERA, GALLERY }

/**
 * Imagen recién elegida/capturada, todavía en memoria (no persistida). El archivo
 * se escribe a almacenamiento privado recién al guardar la nota.
 */
class PickedImage(val bytes: ByteArray, val mimeType: String)

/** Errores de captura que la UI traduce a un mensaje accionable. */
enum class ImageCaptureError { PERMISSION_DENIED, CAMERA_UNAVAILABLE, READ_FAILED, UNKNOWN }

/**
 * Lanza la captura de una fuente concreta. La implementación es nativa por
 * plataforma; la selección/permisos se manejan adentro y se reportan vía los
 * callbacks de `rememberImagePicker`.
 */
interface ImagePicker {
    fun launch(source: ImageSource)
}

/**
 * Crea un [ImagePicker] ligado al ciclo de vida de la composición. `onResult`
 * recibe la imagen elegida; `onError` un fallo accionable (permiso denegado,
 * cámara no disponible, lectura fallida). Cancelar la fuente es un no-op.
 */
@Composable
expect fun rememberImagePicker(
    onResult: (PickedImage) -> Unit,
    onError: (ImageCaptureError) -> Unit,
): ImagePicker

/** Imagen persistida en almacenamiento privado: ruta relativa + tamaño. */
data class StoredImage(val relativePath: String, val byteSize: Long)

/**
 * Guarda/borra los archivos de adjuntos en el almacenamiento privado de la app.
 * La ruta devuelta es relativa al contenedor para sobrevivir reinstalaciones en
 * iOS (donde el path absoluto cambia).
 */
interface AttachmentFileStore {
    fun saveImage(bytes: ByteArray, mimeType: String): StoredImage
    fun delete(relativePath: String): Boolean
}

/** Crea un [AttachmentFileStore] para el contenedor de la app. */
@Composable
expect fun rememberAttachmentFileStore(): AttachmentFileStore

/** Decodifica bytes de imagen a un [ImageBitmap], o `null` si no se pudo. */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?
