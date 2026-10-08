package com.playbook.core.platform

import java.util.UUID

actual fun randomNoteId(): String = UUID.randomUUID().toString()

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
