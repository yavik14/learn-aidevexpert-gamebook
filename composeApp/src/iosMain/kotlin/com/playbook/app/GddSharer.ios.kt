@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.playbook.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.UIKit.UIActivityViewController

/**
 * Implementación iOS: presenta un `UIActivityViewController` con el texto
 * Markdown como único `activityItem`, desde el controlador superior. No escribe
 * archivos ni pide permisos; si no hay presenter, se devuelve `false`.
 */
@Composable
actual fun rememberGddSharer(): GddSharer = remember { IosGddSharer() }

private class IosGddSharer : GddSharer {
    override fun share(markdown: String): Boolean {
        val presenter = topViewController() ?: return false
        val controller = UIActivityViewController(
            activityItems = listOf(markdown),
            applicationActivities = null,
        )
        presenter.presentViewController(controller, animated = true, completion = null)
        return true
    }
}

actual fun currentTimestampLabel(): String =
    NSDateFormatter().apply { dateFormat = "yyyy-MM-dd HH:mm" }.stringFromDate(NSDate())
