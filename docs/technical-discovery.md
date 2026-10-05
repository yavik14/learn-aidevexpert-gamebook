# Technical Discovery

## Product Surface
App móvil para **Android + iOS en paralelo**. UI en Compose Multiplatform y
lógica de dominio en un core Kotlin Multiplatform (KMP).

## Candidate Stack
- **Core:** Kotlin Multiplatform (lógica compartida real, sin duplicar por
  plataforma).
- **UI:** Compose Multiplatform.
- **Persistencia:** SQLDelight.
- **Búsqueda semántica:** embeddings almacenados como BLOB + similitud coseno
  calculada en memoria.
- **IA:** interfaz `AiClient` como abstracción. Runtime (cloud / on-device /
  híbrido) todavía **sin decidir**.
- **Integraciones nativas:** speech-to-text (voz) y cámara (foto de bocetos).

## Data and Storage
- **Local-first**, sin backend en el MVP.
- SQLDelight en ambas plataformas como única fuente de verdad local.
- Vectores de embeddings como BLOB en SQLDelight; similitud coseno en memoria
  (suficiente para una biblioteca personal).
- `Owner` presente en el modelo aunque el MVP sea single-user.

## Integrations
- **Speech-to-text nativo:** Android `SpeechRecognizer` / iOS `Speech`.
- **Cámara / galería:** captura de bocetos; el OCR (si existe) es texto derivado.
- **Proveedor de IA:** embeddings + LLM detrás de `AiClient` — pendiente de
  elección (ver riesgos).

## Authentication and Authorization
- Ninguna en el MVP (single-user local-first).
- Se reserva la noción de `Owner` para multi-usuario futuro.

## Deployment and Operations
- Distribución en Android (Play / internal testing) e iOS (TestFlight) para
  ejercitar "lanzar a producción".
- Sin operación de servidores en el MVP.

## Testing and Verification
- Tests unitarios del core KMP con un `AiClient` fake (deterministas, sin red).
- Tests de persistencia SQLDelight.
- Prueba manual end-to-end en Android y iOS.

## Observability
- Mínimo: logging local de fallos de indexado e integraciones nativas.
- Sin telemetría remota en el MVP.

## Constraints
- **API keys y costos:** a resolver si se elige cloud; nunca commitear keys.
- **Offline:** la captura siempre debe funcionar; el indexado tolera fallos y se
  reintenta.
- **Costo de KMP × 2 plataformas:** Android + iOS duplica el trabajo nativo
  (permisos, STT, cámara) y requiere entorno macOS/Xcode.

## Bootstrap Decisions (kmp-project-bootstrap)

Implementado 2026-10-01. Estructura y versiones fijadas:

- **Estructura:** `:core` (KMP library, sin UI) + `:composeApp` (Compose MP UI +
  app Android + framework iOS `ComposeApp`) + `iosApp` (host Xcode SwiftUI).
  Detalle y dirección de dependencias en `ARCHITECTURE.md`.
- **Identidad de paquete:** base `com.playbook`; `applicationId` Android y bundle
  id iOS `com.playbook.app`; framework iOS `ComposeApp`.
- **Versiones (combinación compatible, centralizada en
  `gradle/libs.versions.toml`):** Gradle wrapper 8.14.5, Kotlin 2.1.21, Compose
  Multiplatform 1.8.2, AGP 8.10.1, compileSdk 36, minSdk 24, targetSdk 36, JVM
  target 11.
- **Motor de UI Android:** Jetpack Compose (via Compose Multiplatform) con
  `activity-compose` 1.10.1; sin Views/XML de UI (solo `themes.xml` mínimo).
- **iOS:** framework estático `ComposeApp` enlazado por el host Xcode; el script
  phase `Compile Kotlin Framework` invoca
  `:composeApp:embedAndSignAppleFrameworkForXcode`.
