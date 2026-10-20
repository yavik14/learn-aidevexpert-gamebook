package com.playbook.app

import androidx.compose.runtime.Composable

@Composable
expect fun PlaybookBackHandler(enabled: Boolean, onBack: () -> Unit)
