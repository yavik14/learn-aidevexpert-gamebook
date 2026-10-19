package com.playbook.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val THUMBNAIL_SIZE = 72.dp
private val THUMBNAIL_CORNER = 16.dp

/**
 * Miniatura perezosa de un boceto.
 *
 * Carga el archivo sólo cuando el composable entra en composición (tarjeta
 * visible) con [produceState], y decodifica fuera del hilo principal. Si el
 * archivo no existe o no decodifica, no emite nada (no deja hueco, espaciado ni
 * placeholder): la tarjeta conserva su cuerpo y chips. Sin cacheo ni
 * redimensionado (diferidos); la imagen se recorta con `ContentScale.Crop`.
 */
@Composable
fun NoteThumbnail(
    path: String,
    loadThumbnail: suspend (String) -> ImageBitmap?,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = withContext(Dispatchers.Default) {
            runCatching { loadThumbnail(path) }.getOrNull()
        }
    }
    val image = bitmap ?: return
    Column {
        Image(
            bitmap = image,
            contentDescription = "Miniatura del boceto",
            modifier = Modifier.size(THUMBNAIL_SIZE).clip(RoundedCornerShape(THUMBNAIL_CORNER)),
            contentScale = ContentScale.Crop,
        )
        Spacer(modifier = Modifier.height(8.dp))
    }
}
