# Feature Implementation Spec: Dictado inline de 30s en Nueva nota

## Source Feature

- `id`: `inline-dictation-30s`
- `area`: `ui`
- `depends_on`: `["voice-capture-stt", "voice-attachment-storage", "create-text-note"]`
  (todas `accepted`)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`

> **Alcance.** Hoy el dictado vive en una **pantalla separada**
> (`VoiceCaptureScreen` + `NotesDestination.VoiceCapture`) que transcribe y crea la
> Nota de una. Esta feature **integra el dictado en el editor de Nueva nota**
> (`NoteEditorScreen`): control "Dictar", **barra de progreso de 30s con auto-stop**,
> transcripción parcial en vivo que al final se **anexa al `body`**, y el **Adjunto
> de audio** se persiste al guardar. **Reemplaza** la pantalla de voz separada.

## Goal

Que el usuario dicte directamente dentro de **Nueva nota**, con un cap de 30s
visible (barra de progreso + auto-stop) y sin salir del editor; el texto final se
anexa al `body` y el audio original se conserva como Adjunto `AUDIO` de la Nota. La
sesión y el temporizador los maneja `App` (sin `ViewModel`/Flow/DI); el editor es
tonto (renderiza el estado y anexa el texto final). Entregable: `:composeApp`
(inline + limpieza de la pantalla vieja). Sin deps, sin esquema, sin core.

## Non-Goals

- **Dictado en modo edición** (Nota existente): sólo **Nueva nota** (crear).
- **Reproducción** del audio, waveform, edición/recorte del audio.
- Transcriptor **real** de IA (`transcribe`) ni tts: siguen en `ai-real-*`/`tts-read-aloud`;
  el dictado usa el STT nativo ya aceptado (`VoiceTranscriber`).
- Cambiar el adaptador nativo de voz, el formato (`audio/<uuid>.m4a`), el
  `AttachmentRepository` o el esquema.
- Rediseño de la pantalla principal (FAB/top bar): es `main-screen-playbook-shell`.
- Deps nuevas, `init.sh`, iOS (congelado, sólo debe seguir compilando).

## Job Story

When quiero capturar una idea hablando mientras la escribo, I want dictar dentro de
Nueva nota con un límite claro de 30s, so I can ver el texto aparecer en el cuerpo y
guardarlo junto al audio sin cambiar de pantalla.

## Users And Permissions

- Autor (único usuario, single-user local-first): dicta y guarda.
- **Micrófono:** ya se declara/pide (`RECORD_AUDIO` en Android; `NSMicrophoneUsageDescription`
  en iOS, vía `voice-capture-stt`). **Sin permisos nuevos**.
- Desarrollador: corre Gradle/tests. Sin auth ni secrets.

## Scope Decisions

> **Validada con el autor (2026-10-16)**.

1. **Se elimina la pantalla separada.** `VoiceCaptureScreen.kt`,
   `NotesDestination.VoiceCapture` y `VoiceUiState` desaparecen;
   `NotesListScreen` "Dictar nota" abre el editor (`NotesDestination.Create`) y
   **arranca** la sesión de dictado.
2. **`App` es dueña de la sesión + el cap; el editor es tonto.** `App` maneja
   `VoiceTranscriber`, el temporizador de 30s, el estado `DictationUiState` y el
   `RecordedAudio` pendiente; el editor recibe estado + callbacks, dibuja la barra de
   progreso y anexa el texto final al `body`.
3. **Sólo en Nueva nota.** El panel de dictado no aparece en modo edición.
4. **Audio: se adjunta al guardar, se borra al cancelar/reemplazar** (sin huérfanos).
5. **Sin deps ni esquema**; se reutilizan `VoiceTranscriber`, `AttachmentRepository` y
   `AttachmentKind.AUDIO`.

## Acceptance Scenarios

### Scenario 1: entrar a dictar desde la lista
Given la lista de Notas, When el usuario toca "Dictar nota", Then se abre **Nueva
nota** y la sesión de dictado arranca (estado `Listening`, progreso en 0).

### Scenario 2: control "Dictar" en el editor
Given Nueva nota recién abierta (sin dictar), When el usuario toca "Dictar", Then
arranca la sesión y aparece la barra de progreso con el texto parcial en vivo.

### Scenario 3: progreso y parcial en vivo
Given una sesión `Listening`, When llegan resultados parciales, Then el editor
muestra la transcripción parcial y la barra avanza según el tiempo transcurrido
(elapsed / 30s).

### Scenario 4: auto-stop a los 30s
Given una sesión en curso, When transcurren 30s sin que el usuario detenga, Then la
app **detiene** la sesión automáticamente y el texto final se **anexa al `body`**
(sin duplicar espacios; el body previo se conserva).

### Scenario 5: detener manualmente
Given una sesión en curso, When el usuario toca "Detener" antes de los 30s, Then la
sesión termina y el texto final se anexa al `body` igual que en el auto-stop.

### Scenario 6: guardar persiste texto + audio
Given un dictado finalizado con texto no vacío, When el usuario toca "Guardar",
Then se crea la Nota con el texto en el `body` y una fila `attachment`
(`kind = "audio"`, `mime_type = "audio/mp4"`, ruta relativa `audio/<uuid>.m4a`); la
lista muestra los chips "Audio" y, si hubo imagen, también "Imagen".

### Scenario 7: transcripción vacía con audio grabado
Given un dictado que termina con texto vacío/`NO_MATCH`, When la sesión termina,
Then no se anexa nada al `body`, se muestra el mensaje de error explícito y el
archivo de audio grabado se **elimina** (no queda huérfano).

### Scenario 8: cancelar el editor
Given Nueva nota con dictado (en curso o finalizado) y sin guardar, When el usuario
toca "Cancelar", Then se corta la sesión, se borra el audio pendiente y no se crea
Nota ni Adjunto.

### Scenario 9: re-dictar reemplaza el audio
Given un dictado finalizado (audio pendiente sin guardar), When el usuario dicta de
nuevo y termina, Then el audio anterior se **borra** y sólo el nuevo queda pendiente.

### Scenario 10: degradación tolerante sin grabación
Given que la grabación de audio falla (pero la transcripción funciona), When el
usuario guarda, Then la Nota conserva el texto **sin** Adjunto de audio, sin crash.

### Scenario 11: gate honesto
Given los escenarios 1–10, When corren `:core:testDebugUnitTest`,
`:composeApp:assembleDebug` y `./init.sh`, Then todo pasa en Android/JVM, sin deps
nuevas ni cambios de esquema, y `./init.sh` termina en `0`.

## Repository Research

### Files Inspected

- `feature_list.json` (`inline-dictation-30s`), `DESIGN.md` (§"Dictado inline: barra
  de progreso de 30s con auto-stop"; Core Screen 2 "Nueva nota/Editor"), `docs/risks-and-open-questions.md`.
- `composeApp/.../App.kt` — `NotesDestination` (incluye `VoiceCapture`), `voiceState`,
  `startVoiceCapture`/`stopVoiceCapture`/`cancelVoiceCapture`, `saveNoteWithImage`,
  render del destino `VoiceCapture` con `DisposableEffect { voiceTranscriber.cancel() }`.
- `composeApp/.../VoiceCaptureScreen.kt` — `VoiceUiState`
  (`Idle`/`Listening(partial)`/`Processing`/`Error`) + `VoiceCaptureError.message()` +
  la pantalla separada.
- `composeApp/.../voice/VoiceTranscriber.kt` — puerto `start(onPartialResult,
  onFinalResult(text, RecordedAudio?), onError)`, `stop()`, `cancel()`; `RecordedAudio`
  (relativePath/byteSize/mimeType).
- `composeApp/.../{NoteEditorScreen,NotesListScreen}.kt` — editor (body/track/géneros/imagen,
  `onSave(body, track, tags)`), lista con botón "Dictar nota".
- `core/.../platform/PlatformDefaults.kt` — `currentTimeMillis()` (expect/actual) para
  medir el elapsed del cap.
- `core/.../model/Attachment.kt`, `core/.../repository/AttachmentRepository.kt` — `AUDIO`
  y persistencia del Adjunto (ya aceptados por `voice-attachment-storage`).

### Existing Patterns To Follow

- UI "tonta" + `App` dueña del estado; coroutines con `rememberCoroutineScope`,
  `LaunchedEffect`, `DisposableEffect`; sin `ViewModel`/Flow/DI.
- Navegación con `NotesDestination` (sealed interface).
- Chips textuales (no sólo color) y componentes `material3`.
- `.m4a` relativo `audio/<uuid>.m4a`; limpieza explícita de archivos huérfanos en
  `App` (patrón de `saveNoteWithImage` y del audio actual).

### Current Gaps

- El dictado exige salir del editor a una pantalla dedicada; no hay cap de 30s ni
  progreso; no se puede escribir y dictar en la misma pantalla.

## Technical Approach

### 1. Estado de dictado (`composeApp/.../Dictation.kt`, crear)

```kotlin
sealed interface DictationUiState {
    data object Idle : DictationUiState
    data class Listening(val partial: String, val elapsedMillis: Long) : DictationUiState
    data object Processing : DictationUiState
    data class Error(val error: VoiceCaptureError) : DictationUiState
}

const val DICTATION_MAX_MILLIS: Long = 30_000L   // cap (auto-stop)
const val DICTATION_TICK_MILLIS: Long = 100L      // cadencia del progreso

fun VoiceCaptureError.message(): String = /* movido desde VoiceCaptureScreen */ ...
```

Se **elimina** `VoiceCaptureScreen.kt` (y `VoiceUiState`).

### 2. `App` (sesión + cap + audio pendiente)

Estado: `dictation: DictationUiState`, `dictatedText: String?`,
`pendingAudio: RecordedAudio?`, `dictationJob: Job?`.

- `startDictation()`: cancela el job previo, limpia `dictatedText`, fija
  `Listening("", 0)`, llama a `voiceTranscriber.start(...)` y lanza un job que, con
  `currentTimeMillis()` y `delay(DICTATION_TICK_MILLIS)`, actualiza `elapsedMillis` y
  **al llegar a `DICTATION_MAX_MILLIS`** llama `voiceTranscriber.stop()` (auto-stop).
- Callbacks: `onPartialResult` → `Listening(partial, elapsed)`; `onFinalResult(text,
  audio)` → cancela el job; si `text` en blanco → borra `audio` y `Error(NO_MATCH)`;
  si no → borra un `pendingAudio` previo (reemplazo), guarda el nuevo, fija
  `dictatedText = text` y `Idle`; `onError` → cancela el job y `Error`.
- `stopDictation()`: `Processing` + `voiceTranscriber.stop()`.
- `cancelDictation()`: cancela el job, `voiceTranscriber.cancel()`, `Idle`.
- `attachAudio(noteId)`: si hay `pendingAudio`, crea el Adjunto `AUDIO` (best-effort:
  si falla, registra `STORAGE` y borra el archivo); siempre limpia `pendingAudio`.
- `discardPendingAudio()`: borra el archivo pendiente y limpia (idempotente).
- Guardado (crear, con/sin imagen): crea la Nota + `attachAudio(note.id)`; `onCancel`
  y `DisposableEffect` del destino Crear llaman `cancelDictation()` +
  `discardPendingAudio()`.
- `onDictate` (lista) → `destination = NotesDestination.Create; startDictation()`.
- Se elimina `NotesDestination.VoiceCapture` y su render.

### 3. Editor (`NoteEditorScreen`)

Props nuevas (create): `dictationState`, `onStartDictation`, `onStopDictation`,
`dictatedText`, `onDictatedTextConsumed`. Panel "Dictado":
- `Idle` → botón "Dictar".
- `Listening` → `LinearProgressIndicator(progress = { elapsed/30_000f })`, texto
  "Escuchando… (Ns / 30s)", preview del parcial y botón "Detener".
- `Processing` → "Transcribiendo…" (deshabilitado).
- `Error` → `error.message()` + "Reintentar".
Anexado:
```kotlin
LaunchedEffect(dictatedText) {
    if (!dictatedText.isNullOrBlank()) {
        body = listOf(body.trim(), dictatedText.trim())
            .filter { it.isNotEmpty() }.joinToString("\n")
        onDictatedTextConsumed()
    }
}
```
`onSave` no cambia (`App` usa su propio `pendingAudio`).

## Expected File Changes

- `composeApp/.../VoiceCaptureScreen.kt` — **eliminar**.
- `composeApp/.../Dictation.kt` — **crear**; `DictationUiState`, constantes y
  `VoiceCaptureError.message()`.
- `composeApp/.../NoteEditorScreen.kt` — modificar; panel de dictado + anexado.
- `composeApp/.../App.kt` — modificar; sesión inline + cap + `pendingAudio` +
  limpieza; quitar `VoiceCapture`.
- `composeApp/.../NotesListScreen.kt` — modificar (si hace falta) el handler/etiqueta
  de "Dictar nota" (abre el editor).
- `ARCHITECTURE.md`, `docs/technical-discovery.md` — **update**.
- `AGENTS.md` — update **acotado** (sólo "próxima feature en cola").
- `feature_list.json`, `PROGRESS.md` — update al cerrar.

No se esperan cambios en `:core`, `voice/VoiceTranscriber.kt`, los adaptadores
nativos, el esquema/`.sqm`, `gradle/libs.versions.toml`, build files, `init.sh` ni
`iosApp/**`.

## Visual Design Impact

- UI involved: yes. Design source: `DESIGN.md` (provisional) — ya define "Dictado
  inline: barra de progreso de 30s con auto-stop".
- Screens or states affected: `NoteEditorScreen` (Nueva nota) suma el panel de
  dictado; se retira `VoiceCaptureScreen`.
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — la captura por voz pasa a ser **inline** en el
  editor (se retira la pantalla/destino separado); sesión + cap de 30s en `App`.
- `CONSTRAINTS.md`: **not needed** — sin regla durable nueva.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` **update** ("Inline Dictation Decisions
  (inline-dictation-30s)": cap, auto-stop, audio pendiente, limpieza). `DESIGN.md`
  **not needed**. `feature_list.json`/`PROGRESS.md` **update** al cerrar.

## Implementation Plan

1. Crear `Dictation.kt` (estado + constantes + `message()`) y borrar
   `VoiceCaptureScreen.kt`.
2. Reescribir el flujo de voz en `App` (sesión inline + cap + `pendingAudio`).
3. Añadir el panel de dictado y el anexado en `NoteEditorScreen`; quitar
   `NotesDestination.VoiceCapture`.
4. Ajustar `NotesListScreen` ("Dictar nota" abre el editor y arranca).
5. Verificación + smoke Android, actualizar docs y cerrar.

## Implementation Tasks

- [ ] `Dictation.kt` + eliminar `VoiceCaptureScreen.kt`.
- [ ] `App`: `DictationUiState`/`pendingAudio`/timer de 30s + auto-stop + limpieza.
- [ ] `App`: `attachAudio` en los guardados de Nueva nota; quitar `VoiceCapture`.
- [ ] `NoteEditorScreen`: panel de dictado (progreso/parcial/detener/error) + anexado.
- [ ] `NotesListScreen`: "Dictar nota" abre el editor y arranca la sesión.
- [ ] Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`; `AGENTS.md` (cola).
- [ ] Correr verificación + smoke Android y registrar evidencia.
- [ ] Actualizar `feature_list.json` y `PROGRESS.md`.

## Verification Plan

- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL.
- `./gradlew :core:testDebugUnitTest --rerun-tasks` → sin regresiones (la feature no
  toca `:core`).
- `./init.sh` → exit 0; gate Android-only, sin dev servers ni red.
- `git status`/`git diff` → `:core`, adaptadores nativos, esquema y build files sin
  cambios; sin deps ni `.sqm` nuevos.
- **iOS (no bloqueante, macOS):** `:composeApp:linkDebugFrameworkIosSimulatorArm64`
  compila.
- **Smoke manual Android** (emulador): desde la lista, "Dictar nota" abre Nueva nota
  y arranca; la barra de progreso avanza y hay auto-stop a los **30s**; con permiso
  denegado/cancelar/no-habla se muestran los mensajes y no se crea Nota ni quedan
  archivos huérfanos; con transcripción, guardar persiste texto + chip "Audio".
- **Límite honesto:** el STT real no es inyectable de forma determinista (misma
  limitación que `voice-capture-stt`); el smoke valida los **estados de UI, el cap de
  30s y las rutas de error/cancelación**; la persistencia del Adjunto de audio ya
  está cubierta por los tests aceptados de `voice-attachment-storage`.
- **Controles negativos** (restaurar y re-verificar): (a) subir el cap a `Long.MAX`
  → el smoke ya no auto-detiene a los 30s; (b) no limpiar `pendingAudio` al cancelar
  → queda un `audio/<uuid>.m4a` huérfano en `filesDir`; (c) no anexar `dictatedText`
  → el `body` queda vacío tras dictar.
- E2E persistente: **no aplica** (sin harness E2E; `init.sh` no levanta simuladores).

## Evidence To Capture

- Salida de `:composeApp:assembleDebug` y `:core:testDebugUnitTest` (sin regresiones);
  `./init.sh` exit 0.
- Smoke Android: capturas/UI dumps del panel de dictado (progreso avanzando, estado a
  los 30s) y de una Nota guardada con chip "Audio"; verificación de que cancelar no
  deja `audio/*.m4a` huérfano (`run-as ... ls files/audio`).
- Controles negativos (a)/(b)/(c) reproducidos y restaurados.
- `git diff` sin cambios en `:core`/esquema/adaptadores nativos/build files; sin deps.

## Validator Checklist

- [ ] Alcance respetado: dictado **inline en Nueva nota** + cap 30s + audio al
      guardar; **sin** modo edición, reproducción, IA/tss, rediseño de principal ni
      cambios de `:core`/esquema/deps.
- [ ] Escenarios de aceptación 1–11 pasan (donde el smoke lo permite, con límites
      declarados).
- [ ] `VoiceCaptureScreen.kt` y `NotesDestination.VoiceCapture` ya no existen; el STT
      nativo y `AttachmentRepository` sin cambios.
- [ ] Auto-stop a los 30s; audio pendiente se adjunta al guardar y se borra al
      cancelar/reemplazar (sin huérfanos).
- [ ] `:composeApp` compila; `init.sh` sin cambios; iOS sigue compilando.
- [ ] `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados; `AGENTS.md`
      sólo la línea de cola.
- [ ] `feature_list.json` y `PROGRESS.md` actualizados con la evidencia.
- [ ] No hay E2E persistente y la spec lo justifica.
