@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.playbook.app

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Controlador superior de la jerarquía: puede haber `UIViewController`s
 * presentados encima (pickers, share sheets). Se usa para presentar UI nativa
 * desde un `@Composable` sin acceso directo al `UIViewController` raíz.
 */
internal fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    if (controller == null) {
        val scene = UIApplication.sharedApplication.connectedScenes
            .firstOrNull { it is UIWindowScene } as? UIWindowScene
        val window = scene?.windows?.firstOrNull { (it as? UIWindow)?.keyWindow == true } as? UIWindow
        controller = window?.rootViewController
    }
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
