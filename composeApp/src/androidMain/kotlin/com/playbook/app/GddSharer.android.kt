package com.playbook.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Implementación Android: `ACTION_SEND` con `text/plain` y `EXTRA_TEXT = markdown`,
 * lanzado con `Intent.createChooser`. No escribe archivos ni pide permisos; si no
 * hay ninguna app que pueda recibir texto, se devuelve `false` (no se propaga).
 */
@Composable
actual fun rememberGddSharer(): GddSharer {
    val context = LocalContext.current
    return remember(context) { AndroidGddSharer(context) }
}

private class AndroidGddSharer(private val context: Context) : GddSharer {
    override fun share(markdown: String): Boolean = try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, markdown)
        }
        val chooser = Intent.createChooser(send, null)
        // Un contexto no-Activity necesita NEW_TASK para poder iniciar la activity.
        if (context !is Activity) {
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: Throwable) {
        false
    }
}

actual fun currentTimestampLabel(): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
