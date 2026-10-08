package com.playbook.core.platform

/**
 * Genera un identificador único para una Nota nueva. Implementación por
 * plataforma vía `expect`/`actual`; los tests inyectan un fake determinista en
 * lugar de usarla.
 */
expect fun randomNoteId(): String

/**
 * Timestamp actual en milisegundos desde epoch. Implementación por plataforma
 * vía `expect`/`actual`; los tests inyectan un reloj determinista.
 */
expect fun currentTimeMillis(): Long
