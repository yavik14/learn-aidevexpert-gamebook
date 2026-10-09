package com.playbook.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * Implementación Android de la captura de imagen.
 *
 * - Cámara: pide `CAMERA` en runtime (si falta) y usa `TakePicture` con un `Uri`
 *   de `FileProvider` sobre un archivo temporal en `cacheDir/images/`.
 * - Galería: `PickVisualMedia` (photo picker del sistema; sin permiso de
 *   almacenamiento, compatible con scoped storage).
 * - Cancelar cualquiera de las dos es un no-op; la denegación de permiso se mapea
 *   a [ImageCaptureError.PERMISSION_DENIED].
 */
@Composable
actual fun rememberImagePicker(
    onResult: (PickedImage) -> Unit,
    onError: (ImageCaptureError) -> Unit,
): ImagePicker {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnError by rememberUpdatedState(onError)
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            readPickedImage(context, uri, currentOnResult, currentOnError)
        }
        // Cancelar (o fallo de la app de cámara) es un no-op.
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCamera(context, cameraLauncher) { pendingCameraUri = it }
        } else {
            currentOnError(ImageCaptureError.PERMISSION_DENIED)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            readPickedImage(context, uri, currentOnResult, currentOnError)
        }
        // Cancelar el picker es un no-op.
    }

    return remember(context) {
        object : ImagePicker {
            override fun launch(source: ImageSource) {
                when (source) {
                    ImageSource.CAMERA -> {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            launchCamera(context, cameraLauncher) { pendingCameraUri = it }
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }

                    ImageSource.GALLERY -> galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                }
            }
        }
    }
}

/**
 * Abre `ACTION_IMAGE_CAPTURE` con el `Uri` de `FileProvider` como salida.
 *
 * No se usa `ActivityResultContracts.TakePicture`: ese contrato no agrega los
 * flags de permiso de URI, y la app de cámara falla al escribir el output con
 * `SecurityException`/`RemoteException` en `ContentResolver.openOutputStream`. Se
 * lanzan explícitamente `FLAG_GRANT_READ/WRITE_URI_PERMISSION` (+ `clipData` para
 * apps de cámara que sólo propagan el grant por el clipData).
 */
private fun launchCamera(
    context: Context,
    launcher: androidx.activity.result.ActivityResultLauncher<Intent>,
    onUri: (Uri) -> Unit,
) {
    val uri = createCameraUri(context)
    onUri(uri)
    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, uri)
        clipData = ClipData.newRawUri("playbook-camera-output", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }
    launcher.launch(intent)
}

/** Crea un archivo temporal y su `Uri` de `FileProvider` para la cámara. */
private fun createCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    val file = File(dir, "camera-${UUID.randomUUID()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private fun readPickedImage(
    context: Context,
    uri: Uri,
    onResult: (PickedImage) -> Unit,
    onError: (ImageCaptureError) -> Unit,
) {
    try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        if (bytes == null || bytes.isEmpty()) {
            onError(ImageCaptureError.READ_FAILED)
            return
        }
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        onResult(PickedImage(bytes, mimeType))
    } catch (_: Throwable) {
        onError(ImageCaptureError.READ_FAILED)
    }
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()

@Composable
actual fun rememberAttachmentFileStore(): AttachmentFileStore {
    val context = LocalContext.current
    return remember(context) { AndroidAttachmentFileStore(context) }
}

/**
 * Archivos de adjuntos en `filesDir/images/` (almacenamiento privado). La ruta
 * devuelta es relativa al contenedor de la app.
 */
private class AndroidAttachmentFileStore(private val context: Context) : AttachmentFileStore {
    override fun saveImage(bytes: ByteArray, mimeType: String): StoredImage {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val name = "${UUID.randomUUID()}.jpg"
        File(dir, name).writeBytes(bytes)
        return StoredImage(relativePath = "images/$name", byteSize = bytes.size.toLong())
    }

    override fun delete(relativePath: String): Boolean =
        try {
            File(context.filesDir, relativePath).delete()
        } catch (_: Throwable) {
            false
        }
}
