@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package com.playbook.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.playbook.core.storage.AttachmentFileStore
import com.playbook.core.storage.attachmentFileStore
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.memcpy

/**
 * Implementación iOS de la captura de imagen.
 *
 * - Cámara: `UIImagePickerController` (`sourceType = camera`) presentado desde el
 *   `UIViewController` superior; en simulador no hay cámara →
 *   [ImageCaptureError.CAMERA_UNAVAILABLE]. Requiere `NSCameraUsageDescription`.
 * - Galería: `PHPickerViewController` (sin permiso de fototeca).
 * - La imagen se re-encodea a JPEG; la previsualización decodifica con Skia.
 */
@Composable
actual fun rememberImagePicker(
    onResult: (PickedImage) -> Unit,
    onError: (ImageCaptureError) -> Unit,
): ImagePicker {
    val picker = remember { IosImagePicker() }
    SideEffect {
        picker.onResult = onResult
        picker.onError = onError
    }
    return picker
}

// Los delegates de UIKit son weak: hay que retenerlos para que no se liberen
// mientras el picker está presentado.
private object IosPickerDelegateHolder {
    var cameraDelegate: Any? = null
    var galleryDelegate: Any? = null
}

private class IosImagePicker : ImagePicker {
    var onResult: (PickedImage) -> Unit = {}
    var onError: (ImageCaptureError) -> Unit = {}

    override fun launch(source: ImageSource) {
        when (source) {
            ImageSource.CAMERA -> launchCamera()
            ImageSource.GALLERY -> launchGallery()
        }
    }

    private fun launchCamera() {
        if (AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) == null) {
            onError(ImageCaptureError.CAMERA_UNAVAILABLE)
            return
        }
        val presenter = topViewController()
        if (presenter == null) {
            onError(ImageCaptureError.UNKNOWN)
            return
        }
        when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> presentCamera(presenter)
            AVAuthorizationStatusNotDetermined ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (granted) presentCamera(presenter)
                        else onError(ImageCaptureError.PERMISSION_DENIED)
                    }
                }

            else -> onError(ImageCaptureError.PERMISSION_DENIED)
        }
    }

    private fun presentCamera(presenter: UIViewController) {
        val delegate = CameraPickerDelegate(
            onImage = ::handleImage,
            onCancel = {},
        )
        IosPickerDelegateHolder.cameraDelegate = delegate
        val picker = UIImagePickerController().apply {
            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
            this.delegate = delegate
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    private fun launchGallery() {
        val presenter = topViewController()
        if (presenter == null) {
            onError(ImageCaptureError.UNKNOWN)
            return
        }
        val configuration = PHPickerConfiguration().apply {
            selectionLimit = 1
            filter = PHPickerFilter.imagesFilter
        }
        val delegate = GalleryPickerDelegate(
            onImage = ::handleImage,
            onCancel = {},
        )
        IosPickerDelegateHolder.galleryDelegate = delegate
        val picker = PHPickerViewController(configuration = configuration).apply {
            this.delegate = delegate
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    /** Re-encodea a JPEG y emite el resultado. Se llama ya en el main thread. */
    private fun handleImage(image: UIImage) {
        val data = UIImageJPEGRepresentation(image, 0.9)
        if (data == null) {
            onError(ImageCaptureError.READ_FAILED)
            return
        }
        onResult(PickedImage(data.toByteArray(), "image/jpeg"))
    }
}

private class CameraPickerDelegate(
    private val onImage: (UIImage) -> Unit,
    private val onCancel: () -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        picker.dismissViewControllerAnimated(true, null)
        if (image != null) onImage(image) else onCancel()
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, null)
        onCancel()
    }
}

private class GalleryPickerDelegate(
    private val onImage: (UIImage) -> Unit,
    private val onCancel: () -> Unit,
) : NSObject(), PHPickerViewControllerDelegateProtocol {

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onCancel()
            return
        }
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data, _ ->
            dispatch_async(dispatch_get_main_queue()) {
                val image = data?.let { UIImage.imageWithData(it) }
                if (image != null) onImage(image) else onCancel()
            }
        }
    }
}

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    try {
        org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Throwable) {
        null
    }

@Composable
actual fun rememberAttachmentFileStore(): AttachmentFileStore =
    remember { attachmentFileStore(applicationSupportDir()) }

/**
 * Store de `:core` anclado a `Application Support` (almacenamiento privado). La
 * ruta guardada en la base sigue siendo relativa al contenedor para sobrevivir a
 * reinstalaciones/actualizaciones de iOS.
 */
private fun applicationSupportDir(): String =
    NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: error("No se encontró Application Support")

/** Convierte los bytes de un `NSData` del picker a `ByteArray` (previsualización). */
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return result
}
