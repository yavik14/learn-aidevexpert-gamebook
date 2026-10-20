# Feature Implementation Spec: Transcripción del Adjunto de audio

## Source Feature

- `id`: `transcribe-audio-attachment`
- `area`: `ai`
- `depends_on`: `["ai-fake-real-switch", "voice-attachment-storage"]` (ambas `accepted`)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`; base `origin/develop` `739a87b` (Android-first, iOS congelado).

> **Alcance.** El usuario puede **transcribir el Adjunto `AUDIO` ya persistido de
> una Nota** y aplicar el texto resultante al `body` del editor. Es una
> transcripción **diferida** (modelo `transcribe` vía `TranscribeClient`), no la
> del dictado original (STT nativo `VoiceTranscriber`). El texto se aplica al
> borrador; el usuario **confirma con Guardar** y el guardado re-indexa la Nota
> con el camino existente (`NoteIndexingOrchestrator`). Sin reproducir/grabar
> audio, sin Adjuntos nuevos, sin cambios de esquema ni de `AiRuntime`.

## Goal

Que una Nota que ya conserva el audio de un dictado recupere su texto sin volver
a dictar: acción "Transcribir" en el editor (modo edición), lectura de bytes del
Adjunto `AUDIO` con `AttachmentFileStore.read`, llamada a
`TranscribeClient.transcribe(bytes, mimeType)`, estados de carga/error/vacío y
aplicación controlada al `body`. Entregable: caso de uso en `:core` (tests fake
Android/JVM e iOS), wiring en `App` y acción/estado en `NoteEditorScreen`. Sin deps
nuevas, sin red en el gate, sin `.sqm`.

## Non-Goals

- Reproducción/player de audio, waveform, edición o recorte.
- Grabación, dictado o STT (`VoiceTranscriber`); el dictado inline de 30s no cambia.
- TTS, generación de imágenes, OCR/visión, chat/RAG.
- Streaming, progreso parcial o cancelación granular de la transcripción;
  reintentos automáticos, backoff o watchdog.
- Adjuntar/borrar/reemplazar audios, o cambios en `AttachmentRepository`,
  `AttachmentFileStore`, `AttachmentKind` o el esquema. Cambiar `AiRuntime`,
  `OpenAiTranscribeClient`, `AiClient`, `TranscribeClient` o el build flag fake/real.
- Rediseño del editor o de la lista; cambios de `init.sh`.

## Job Story

When abro una Nota con audio, I want transcribirlo y decidir cómo se integra el
texto en el cuerpo, so I can recuperar la idea sin volver a dictarla.

## Users And Permissions

- Autor (single-user local-first): abre una Nota existente, transcribe y guarda. Sin permisos nuevos; el gate usa IA fake, sin key ni red.

## Scope Decisions (definidas en el pedido del autor, 2026-10-20)

1. **Dónde vive la acción:** en `NoteEditorScreen` **sólo en modo edición**
   (`isEditing = true`, Nota existente). En Nueva nota no hay Adjunto persistido;
   el audio pendiente del dictado no se transcribe acá.
2. **Selector de audio:** sección "Audio adjunto" con la lista de Adjuntos
   `AUDIO` en orden `created_at ASC, id ASC`. Con 0 no se muestra; con 1 se ofrece
   "Transcribir" directo; con >1 se elige uno (opción marcada, default el primero)
   y "Transcribir" usa el seleccionado. Etiquetas deterministas "Audio 1", "Audio
   2", … + tamaño legible, sin playback.
3. **Habilitación:** al menos 1 `AUDIO` seleccionado, `isEditing`, ninguna sesión
   de dictado activa, y no estar ya transcribiendo. Un `body` vacío **no** impide
   transcribir.
4. **Caso de uso en `:core`:** `AudioAttachmentTranscriptionService.transcribe(
   noteId, attachmentId)` valida que el Adjunto pertenezca a la Nota y sea `AUDIO`,
   lee bytes con `AttachmentFileStore.read` y llama
   `TranscribeClient.transcribe(bytes, mimeType)`. No persiste ni indexa; eso sigue
   siendo responsabilidad del editor/`App`.
5. **Texto existente:** si el `body` del borrador está vacío, el texto se aplica
   directamente. Si ya tiene texto, **siempre se confirma**: diálogo con vista
   previa y acciones explícitas "Anexar", "Reemplazar" y "Cancelar". Nunca se
   reemplaza en silencio.
6. **Anexado determinista:** `body.trim()` + `"\n"` + `text.trim()`, omitiendo
   vacíos (misma semántica que `appendDictation`). "Reemplazar" deja exactamente
   `text.trim()`.
7. **Sin auto-guardado:** la transcripción modifica sólo el borrador local. La
   Nota, su `body` y su indexado cambian únicamente al tocar "Guardar" (o no
   cambian si se Cancela/sale del editor).
8. **Re-indexado:** al guardar, el `onSave` existente actualiza la Nota y llama
   `indexBestEffort(edited)`; con el `body` transcripto la Nota pasa por
   `NoteIndexingOrchestrator` (fake determinista en el gate). No se agrega otro
   mecanismo de indexado.
9. **Errores y vacío:** `AiClientException` (incluye red del runtime real) o fallo
   inesperado → mensaje textual + "Reintentar", sin tocar el `body`. Transcripción
   vacía/blanca → "La transcripción quedó vacía" y sin tocar el `body`. Nada se
   persiste en esos casos.
10. **Sin cambios de contrato:** `AttachmentFileStore.read` ya acepta cualquier
    ruta relativa (`audio/<uuid>.m4a`) con guard de traversal; no se agrega API.
    `TranscribeClient.transcribe` no cambia. El wiring pasa
    `aiRuntime.transcribeClient` a `App` (no se agrega nada a `AiRuntime`).
11. **Diagnóstico:** los fallos de transcripción se registran con una nueva fuente
    `DiagnosticSource.AI_TRANSCRIBE` y el resumen estable `transcription_failed`,
    sin texto, bytes, rutas ni ids.
12. **iOS congelado:** la UI y el caso de uso son comunes; `MainViewController`
    inyecta el `transcribeClient` del `fakeAiRuntime()` y debe seguir compilando.

## Acceptance Scenarios

### Scenario 1: Nota con audio, cuerpo vacío
Given una Nota existente con al menos un Adjunto `AUDIO` y `body` vacío, When el
usuario toca "Transcribir", Then se muestra "Transcribiendo…", se leen los bytes
exactos del Adjunto seleccionado y se llama `transcribe(bytes, mimeType)` una vez;
al volver el texto, el `body` queda con ese texto (trim) y aparece el aviso
"Transcripción aplicada. Guardá para conservarla."

### Scenario 2: cuerpo con texto → confirmación
Given un `body` no vacío y una transcripción exitosa, When llega el resultado,
Then se abre un diálogo con vista previa y "Anexar"/"Reemplazar"/"Cancelar".
"Anexar" conserva el cuerpo y agrega la transcripción con un salto de línea;
"Reemplazar" deja sólo la transcripción; "Cancelar" no modifica el cuerpo.

### Scenario 3: guardar persiste y re-indexa
Given una transcripción aplicada al borrador, When el usuario toca "Guardar",
Then se actualiza la Nota con el nuevo `body`, se dispara el indexado existente y
el estado resultante es `INDEXED` (IA fake). Si el usuario Cancela o sale sin
guardar, la Nota conserva su `body` anterior y no se re-indexa.

### Scenario 4: sin audio o varios audios
Given una Nota sin Adjunto `AUDIO`, Then no se muestra la acción. Given una Nota
con varios `AUDIO`, Then se listan en orden determinista, el primero queda
seleccionado y "Transcribir" usa sólo el elegido; cambiar la selección no lee
archivos ni llama al transcriptor hasta confirmar la acción.

### Scenario 5: archivo ausente o ilegible
Given un Adjunto `AUDIO` cuya ruta ya no existe o no se puede leer, When se toca
"Transcribir", Then la UI muestra "No se pudo leer el audio adjunto", no se llama
al transcriptor y el `body` no cambia.

### Scenario 6: transcripción vacía
Given que `TranscribeClient` devuelve `""` o sólo espacios, Then no se modifica el
`body`, se muestra "La transcripción quedó vacía" y la acción vuelve a estar
disponible. No se guarda ni se indexa nada.

### Scenario 7: error de IA / red
Given que `TranscribeClient` lanza `AiClientException` (p. ej. red no disponible),
Then se muestra "No se pudo transcribir. Revisá la conexión e intentá de nuevo",
el `body` no cambia, se registra `AI_TRANSCRIBE`/`transcription_failed` sin datos
del usuario y el botón permite reintentar manualmente.

### Scenario 8: cambio de Nota o salida durante la transcripción
Given una transcripción en curso, When el usuario cancela/guarda, cambia de Nota o
vuelve a la lista, Then el job se cancela o su resultado se descarta; nunca se
aplica texto de una Nota a otra.

### Scenario 9: contrato y determinismo (fake)
Given `FakeTranscribeClient`, When se transcribe dos veces el mismo archivo, Then
el texto es idéntico y el flujo no hace red, no usa key y no accede al micrófono.
El gate corre con `-Pplaybook.ai.mode=fake`.

### Scenario 10: alcance intacto
Given el diff de la feature, Then no hay `.sqm` nuevos, el esquema sigue en v8 sin
cambios, `AiRuntime`/`OpenAiTranscribeClient`/`TranscribeClient` no cambian, no se
crean Adjuntos y no se modifica `init.sh`.

## Repository Research

### Files Inspected

- Core: `AiPorts.kt`/`FakeAiPorts.kt` (`TranscribeClient` fake determinista),
  `openai/AiRuntime.kt` + `OpenAiAdapters.kt` (transcribe multipart ya existente),
  `AttachmentFileStore.kt` Android/iOS (`read` ya soporta `audio/...`),
  `AttachmentRepository`/`SqlDelightAttachmentRepository` (`getByNoteId` ordenado),
  `AttachmentKind.AUDIO`, `NoteIndexingOrchestrator`/`NoteIndexingService` y
  `diagnostics/Diagnostics.kt`.
- Compose: `App.kt` (edición, adjuntos, `save`/`indexBestEffort`, patrón de estado
  y resets), `NoteEditorScreen.kt` (`LaunchedEffect(dictatedText)` +
  `on...Consumed`), `MainActivity.kt`/`MainViewController.kt` (composition roots).
- Docs/specs: `voice-attachment-storage`, `ai-client-ports`, `ai-fake-real-switch`,
  `inline-dictation-30s`, `technical-discovery`, `risks-and-open-questions`.

### Key Findings

- El audio ya se persiste como Adjunto `AUDIO` (`audio/mp4`); `read` ya cubre
  `audio/...` sin API nueva.
- `TranscribeClient` ya está en `AiRuntime`; falta cablearlo a `App`. El editor de
  edición no muestra Adjuntos y el guardado existente ya re-indexa best-effort.

## Data / Contract

```kotlin
// :core — nuevo caso de uso, sin persistencia.
sealed interface AudioTranscriptionResult {
    data class Transcribed(val text: String) : AudioTranscriptionResult
    data object AudioNotFound : AudioTranscriptionResult
    data object AudioUnavailable : AudioTranscriptionResult
    data object EmptyTranscription : AudioTranscriptionResult
    data class Failed(val cause: AiClientException) : AudioTranscriptionResult
}

class AudioAttachmentTranscriptionService(
    transcribeClient: TranscribeClient,
    attachmentRepository: AttachmentRepository,
    fileStore: AttachmentFileStore,
    diagnostics: Diagnostics = NoOpDiagnostics,
) {
    suspend fun transcribe(noteId: String, attachmentId: String): AudioTranscriptionResult
}
```

Reglas: exige un Adjunto de la Nota con `attachmentId` y `AttachmentKind.AUDIO`
(si no → `AudioNotFound`, sin llamar al puerto); `read == null` →
`AudioUnavailable`; `transcribe` trim; vacío → `EmptyTranscription`;
`AiClientException` → `Failed` + diagnóstico; texto no vacío → `Transcribed`. No
escribe repositorios.

UI (en `:composeApp`, compartida): `AudioTranscriptionUiState` con `Idle`,
`Transcribing`, `Empty`, `Unavailable` y `Failed`; `App` es dueña del job y del
último texto; `NoteEditorScreen` recibe los Adjuntos `AUDIO`, mantiene la selección,
aplica/confirma el texto y notifica `onTranscribedTextConsumed` una sola vez. El
estado y la selección se resetean al entrar/salir de `Edit` y el resultado se
descarta si cambió la Nota.

## Implementation Plan

1. `:core`: `AudioAttachmentTranscription.kt` (servicio + resultado) y
   `AI_TRANSCRIBE` en `DiagnosticSource`.
2. `:core/commonTest`: `TranscribeAudioAttachmentCheck.kt` con fake determinista,
   store en memoria y SQLDelight in-memory (Scenarios 1, 4–7, 9 y re-indexado de
   Scenario 3); cubrir Adjunto de otra Nota/inexistente y elección entre varios;
   runners Android (Jdbc) e iOS (driver nativo).
3. `:composeApp`: `AudioTranscriptionUiState` + helper de anexado; sección "Audio
   adjunto" con selector, botón/estado y diálogo Anexar/Reemplazar/Cancelar.
4. `App`: recibir `transcribeClient`, crear el servicio, pasar los `AUDIO` en
   `Edit`, ejecutar con job cancelable y el `attachmentId` elegido, aplicar/limpiar
   texto y conservar `onSave` + `indexBestEffort`; roots pasan
   `aiRuntime.transcribeClient`.
5. Docs (al implementar): `PROGRESS.md`, `feature_list.json`, `ARCHITECTURE.md` y
   `technical-discovery.md` si cambia el comportamiento/arquitectura.

## Verification

- `CI=true ./init.sh` → exit 0 (assembleDebug + `:core:testDebugUnitTest`, fake).
- `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks
  -Pplaybook.ai.mode=fake`: tests nuevos en Android/JVM e iOS, sin red ni key.
- `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64
  -Pplaybook.ai.mode=fake`: Android compila y el framework iOS linkea (sin smoke UI).
- Smoke Android: sembrar Nota + Adjunto `AUDIO` con `adb run-as` (fila `note`/
  `attachment` y `files/audio/<uuid>.m4a`), abrir → "Transcribir" → carga → texto
  aplicado → "Guardar" → badge de indexado. Repetir con `body` existente
  (Anexar/Reemplazar/Cancelar), varios audios (selector), archivo ausente y vacío.
- Evidencia: comandos, tests Android/iOS, capturas/dumps, `Schema.version == 8`, sin `.sqm` ni cambios en paths protegidos (`init.sh`, `AiRuntime`, esquema).

## Risks / Assumptions

- El smoke con audio real no es determinista; el fake depende de tamaño+mime, así
  que un archivo sembrado alcanza para la UI.
- `AttachmentFileStore.read` no valida formato: un archivo corrupto produce el error
  del runtime real y se muestra igual.
- El diálogo mantiene el borrador local: salir sin Guardar descarta el texto y deja
  la Nota intacta. Con varios audios no hay metadatos de origen: las etiquetas son
  ordinales + tamaño y dependen del orden estable del repositorio.
- El diagnóstico nunca registra texto, bytes, rutas ni ids; sin telemetría.
