package com.playbook.app

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PlaybookBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
