# Feature Implementation Spec: Dictado inline de 30s en Nueva nota

## Source Feature

- `id`: `inline-dictation-30s`
- `area`: `ui`
- `depends_on`: `["voice-capture-stt", "voice-attachment-storage", "create-text-note"]`
  (todas `accepted`)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`
- Planificado contra `develop` `e5ff8b9` (Géneros y esquema v8 ya integrados).

> **Alcance.** Hoy el dictado vive en una **pantalla separada**
> (`VoiceCaptureScreen` + `NotesDestination.VoiceCapture`) que transcribe y crea la
> Nota de una. Esta feature **integra el dictado en el editor de Nueva nota**
> (`NoteEditorScreen`): control "Dictar", **barra de progreso de 30s con auto-stop**,
> transcripción parcial en vivo que al final se **anexa al `body`**, y el **Adjunto
> de audio** se persiste al guardar. **Reemplaza** la pantalla de voz separada.
> También agrega un callback `onStarted` al puerto de voz para que el cap de 30s
> empiece cuando la sesión realmente arranca (permiso ya concedido), no mientras
> se muestra el diálogo de permiso.

## Goal

Que el usuario dicte directamente dentro de **Nueva nota**, con un cap de 30s
visible (barra de progreso + auto-stop) y sin salir del editor; el texto final se
anexa al `body` y el audio original se conserva como Adjunto `AUDIO` de la Nota. La
sesión y el temporizador los maneja `App` (sin `ViewModel`/Flow/DI); el editor es
tonto (renderiza el estado y anexa el texto final). Entregable: `:composeApp`
(inline + limpieza de la pantalla vieja + `onStarted` aditivo en el puerto/adaptadores).
Sin deps, sin esquema, sin `:core`.

## Non-Goals

- **Dictado en modo edición** (Nota existente): sólo **Nueva nota** (`Create` y
  `CreateWithImage`).
- **Reproducción** del audio, waveform, edición/recorte del audio.
- Transcriptor **real** de IA (`transcribe`) ni tts: siguen en `ai-real-*`/`tts-read-aloud`;
  el dictado usa el STT nativo ya aceptado (`VoiceTranscriber`).
- Cambiar el comportamiento del STT, la grabación del audio, el formato
  (`audio/<uuid>.m4a`), el `AttachmentRepository` o el esquema. Sólo se permite el
  cambio **aditivo** `onStarted` en `VoiceTranscriber` + sus dos adaptadores y un
  guard de sesión en iOS para callbacks tardíos.
- Agregar un watchdog/timeout para el estado `Processing`: si no llega el final,
  la salida es "Cancelar" (límite declarado).
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

> **Validadas con el autor (2026-10-19).**

1. **Se elimina la pantalla separada.** `VoiceCaptureScreen.kt`,
   `NotesDestination.VoiceCapture` y `VoiceUiState` desaparecen;
   `NotesListScreen` "Dictar nota" abre el editor (`NotesDestination.Create`) y
   **arranca** la sesión de dictado.
2. **`App` es dueña de la sesión + el cap; el editor es tonto.** `App` maneja
   `VoiceTranscriber`, el temporizador de 30s, el estado `DictationUiState` y el
   `RecordedAudio` pendiente; el editor recibe estado + callbacks, dibuja la barra de
   progreso y anexa el texto final al `body`.
3. **Sólo en Nueva nota.** El panel de dictado aparece en `Create` y
   `CreateWithImage` (ambos son "Nueva nota"), nunca en modo edición.
4. **El cap arranca con la sesión real.** Se agrega `onStarted: () -> Unit = {}` a
   `VoiceTranscriber.start(...)`. Android lo emite al iniciar el reconocimiento
   (permiso ya concedido); iOS al arrancar el engine. El tiempo del diálogo de
   permiso **no** consume los 30s. `cancel()` limpia también `onStarted`.
5. **iOS con guard de sesión.** Un contador de generación invalida callbacks
   tardíos de autorización/reconocimiento después de `cancel()`, de modo que una
   sesión cancelada no arranque el micrófono ni el timer.
6. **Sin watchdog.** Si tras `stop()` nunca llega `onFinalResult`, la UI queda en
   `Processing`; "Cancelar" sigue disponible y limpia. Se documenta como límite.
7. **Audio: se adjunta al guardar, se borra al cancelar/reemplazar** (sin huérfanos).
   Al re-dictar, el audio pendiente anterior **se conserva** hasta que el nuevo
   dictado termine con texto; sólo se reemplaza si el nuevo trae audio.
8. **Limpieza:** explícita en Cancelar/Guardar + `DisposableEffect(Unit)` a nivel
   `App`. **No** hay `DisposableEffect` por destino `Create`/`CreateWithImage`
   (el swap de imagen no debe cancelar un dictado válido).
9. **Anexado determinista:** `body.trim() + "\n" + dictatedText.trim()`, omitiendo
   vacíos. `onDictatedTextConsumed(text)` limpia el texto sólo si coincide.
10. **Sin deps ni esquema**; se reutilizan `VoiceTranscriber`,
    `AttachmentRepository`, `AttachmentKind.AUDIO` y `currentTimeMillis()` de
    `:core` (sin modificarlo).

## Acceptance Scenarios

### Scenario 1: entrar a dictar desde la lista
Given la lista de Notas, When el usuario toca "Dictar nota", Then se abre **Nueva
nota** y la sesión de dictado arranca (estado `Listening`, progreso en 0).

### Scenario 2: control "Dictar" en el editor
Given Nueva nota recién abierta (sin dictar), When el usuario toca "Dictar", Then
arranca la sesión y, al confirmarse el permiso/arranque, aparece la barra de
progreso con el texto parcial en vivo.

### Scenario 3: progreso y parcial en vivo
Given una sesión `Listening` ya iniciada, When llegan resultados parciales, Then el
editor muestra la transcripción parcial y la barra avanza según el tiempo
transcurrido (elapsed / 30s), sin reiniciar el elapsed en cada parcial.

### Scenario 4: auto-stop a los 30s
Given una sesión en curso, When transcurren 30s sin que el usuario detenga, Then la
app **detiene** la sesión automáticamente una sola vez y el texto final se
**anexa al `body`** (sin duplicar espacios; el body previo se conserva).

### Scenario 5: detener manualmente
Given una sesión en curso, When el usuario toca "Detener" antes de los 30s, Then la
sesión pasa a `Processing`, se cancela el job del timer y el texto final se anexa
al `body` igual que en el auto-stop. Detener es idempotente.

### Scenario 6: guardar persiste texto + audio
Given un dictado finalizado con texto no vacío, When el usuario toca "Guardar",
Then se crea la Nota con el texto en el `body` y una fila `attachment`
(`kind = "audio"`, `mime_type = "audio/mp4"`, ruta relativa `audio/<uuid>.m4a`); la
lista muestra los chips "Audio" y, si hubo imagen, también "Imagen".

### Scenario 7: transcripción vacía con audio grabado
Given un dictado que termina con texto vacío/`NO_MATCH`, When la sesión termina,
Then no se anexa nada al `body`, se muestra el mensaje de error explícito, se
**elimina el audio nuevo** y se **conserva** el `pendingAudio` anterior si existía
(no queda huérfano).

### Scenario 8: cancelar el editor
Given Nueva nota con dictado (en curso, `Processing` o finalizado) y sin guardar,
When el usuario toca "Cancelar", Then se corta la sesión, se borra el audio
pendiente y no se crea Nota ni Adjunto.

### Scenario 9: re-dictar y reemplazo del audio
Given un dictado finalizado (audio pendiente sin guardar), When el usuario dicta de
nuevo:
- si el nuevo termina con texto **y** audio, el audio anterior se **borra** y sólo
  el nuevo queda pendiente;
- si el nuevo termina con texto pero **sin** audio, se conserva el audio anterior;
- si el nuevo falla o queda vacío, se conserva el audio anterior.

### Scenario 10: degradación tolerante sin grabación
Given que la grabación de audio falla (pero la transcripción funciona), When el
usuario guarda, Then la Nota conserva el texto **sin** Adjunto nuevo de audio
(conservando el pendiente anterior si lo había), sin crash.

### Scenario 11: gate honesto
Given los escenarios 1–16, When corren `:core:testDebugUnitTest`,
`:composeApp:assembleDebug` y `./init.sh`, Then todo pasa en Android/JVM, sin deps
nuevas ni cambios de esquema, y `./init.sh` termina en `0`.

### Scenario 12: permiso denegado
Given el usuario deniega el permiso de micrófono, When la sesión intenta arrancar,
Then no se emite `onStarted`, no corre el timer, no se crea Nota ni Adjunto y se
muestra `Error(PERMISSION_DENIED)` con "Reintentar".

### Scenario 13: re-dictar mientras escucha
Given una sesión `Listening` (o un `pendingAudio` previo), When el usuario toca
"Dictar" de nuevo, Then se cancela la sesión anterior y su audio parcial, no se
pierde el `pendingAudio` previo y arranca una nueva sesión sin crash ni huérfanos.

### Scenario 14: alternar imagen durante el dictado
Given Nueva nota con dictado en curso o finalizado, When el usuario quita/agrega una
imagen (`CreateWithImage ↔ Create`), Then la sesión no se corta por el cambio de
destino y el `pendingAudio` se conserva.

### Scenario 15: permiso lento
Given el diálogo de permiso abierto por varios segundos, When el usuario concede el
permiso, Then el cap de 30s empieza en ese momento (con `onStarted`) y auto-detiene
30s después, no durante el diálogo.

### Scenario 16: guardar durante el dictado
Given una sesión `Listening` o `Processing`, When el usuario toca "Guardar", Then se
cancela la sesión y se descarta el audio parcial; la Nota se crea con el `body`
actual y, si había un `pendingAudio` finalizado, se adjunta ése.

## Repository Research

### Files Inspected

- `feature_list.json` (`inline-dictation-30s`), `DESIGN.md` (§"Dictado inline: barra
  de progreso de 30s con auto-stop"; Core Screen 2 "Nueva nota/Editor"),
  `docs/risks-and-open-questions.md`, `docs/mvp-execution-plan.md`.
- `composeApp/.../App.kt` — `NotesDestination` incluye `VoiceCapture` (~52);
  `voiceState` (~110); `startVoiceCapture` (~193-237) crea la Nota y adjunta audio
  directamente; `stopVoiceCapture`/`cancelVoiceCapture` (~239-248);
  `saveNoteWithImage(image, body, track, genres)` (~251-278); render de
  `Create` (~335-358), `CreateWithImage` (~360-373), `Edit` (~375-414) y
  `VoiceCapture` (~416-428) con `DisposableEffect { voiceTranscriber.cancel() }`.
- `composeApp/.../VoiceCaptureScreen.kt` — `VoiceUiState`
  (`Idle`/`Listening(partial)`/`Processing`/`Error`) (~28-40),
  `VoiceCaptureError.message()` (~43-56) y la pantalla separada (~67-100).
- `composeApp/.../voice/VoiceTranscriber.kt` — puerto `start(onPartialResult,
  onFinalResult(text, RecordedAudio?), onError)` (~50-69), `stop()`, `cancel()`;
  `RecordedAudio` (~36-40).
- `composeApp/.../voice/AndroidVoiceTranscriber.kt` — permiso async (~61-76),
  `stop()` (~78-80), `cancel()` (~82-93), `startRecognition()` (~99-111),
  `onResults`/`onError` (~216-243).
- `composeApp/.../voice/IosVoiceTranscriber.kt` — autorización async (~71-93),
  `stop()` (~95-98), `cancel()` (~100-110), `requestRecordPermissionThenStart()`
  (~112-122), `startRecognition()` (~124-174), `finishWithResult` (~176-189),
  `finishWithError` (~191-198).
- `composeApp/.../NoteEditorScreen.kt` — firma con `initialGenres`,
  `initialImage`, `isEditing`, `onSave(body, track, genres)` (~69-83); estado local
  (~84-89); contenido scrollable con Cuerpo (`heightIn(200..320dp)`, ~151-159),
  Track (~161-179) y `GenrePicker` (~181); fila fija Borrar/Cancelar/Guardar
  (~194-214).
- `composeApp/.../NotesListScreen.kt` — FABs "Nueva nota"/"Foto" (~60-66); fila
  "Dictar nota"/"Ver GDD"/"Preguntar" (~81-99).
- `composeApp/.../GenrePicker.kt` y `LabelChip.kt` — picker/lectura de Géneros
  (sin cambios).
- `composeApp/.../ImageCapture.kt` — `AttachmentFileStore.delete(relativePath)`
  (~46-53) cubre imágenes y audio.
- `core/.../platform/PlatformDefaults.kt` — `currentTimeMillis()` (~14) para medir
  el elapsed del cap (sin cambios en `:core`).
- `core/.../model/Attachment.kt`, `core/.../repository/AttachmentRepository.kt` —
  `AUDIO` y persistencia del Adjunto (ya aceptados por `voice-attachment-storage`).
- `ARCHITECTURE.md` (~525 y ~574-600) — descripción vigente de `VoiceCaptureScreen`
  y de la orquestación de voz en `App`, a actualizar.
- `docs/technical-discovery.md` (~852) — nota de `VoiceCaptureScreen`, a actualizar.

### Existing Patterns To Follow

- UI "tonta" + `App` dueña del estado; coroutines con `rememberCoroutineScope`,
  `LaunchedEffect`, `DisposableEffect`; sin `ViewModel`/Flow/DI.
- Navegación con `NotesDestination` (sealed interface).
- Chips textuales (no sólo color) y componentes `material3`.
- `.m4a` relativo `audio/<uuid>.m4a`; limpieza explícita de archivos huérfanos en
  `App` (patrón de `saveNoteWithImage` y del audio actual).
- Diagnósticos `VOICE`/`STORAGE` best-effort (`DiagnosticSource`, `VOICE_FAILED`,
  `STORAGE_FAILED`).

### Current Gaps

- El dictado exige salir del editor a una pantalla dedicada; no hay cap de 30s ni
  progreso; no se puede escribir y dictar en la misma pantalla.
- El puerto no expone cuándo arrancó realmente la sesión (permiso async), por lo
  que un timer iniciado al tocar "Dictar" contaría el diálogo de permiso.
- `IosVoiceTranscriber.cancel()` no invalida callbacks de autorización ya en vuelo.

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

/** Anexa el texto dictado al body con un salto de línea, sin vacíos ni duplicados. */
fun appendDictation(body: String, dictated: String): String =
    listOf(body.trim(), dictated.trim())
        .filter { it.isNotEmpty() }
        .joinToString("\n")
```

Se **elimina** `VoiceCaptureScreen.kt` (y `VoiceUiState`).

### 2. Puerto `VoiceTranscriber` (cambio aditivo)

```kotlin
fun start(
    onPartialResult: (String) -> Unit,
    onFinalResult: (String, RecordedAudio?) -> Unit,
    onError: (VoiceCaptureError) -> Unit,
    onStarted: () -> Unit = {},
)
```

- `onStarted` se invoca **una vez por sesión**, cuando el reconocimiento/grabación
  ya arrancó (permiso concedido). No se invoca si hay error de permiso,
  disponibilidad o arranque.
- `cancel()` limpia también `onStarted` (junto con el resto de callbacks).

### 3. Adaptador Android

- Guardar `onStarted` junto con los demás callbacks.
- Invocarlo en `startRecognition()` después de `active.startListening(...)` (ahí el
  permiso ya está concedido y el `MediaRecorder` ya arrancó).
- No invocarlo en la ruta de permiso denegado, `UNAVAILABLE` ni errores de arranque.
- Limpiarlo en `cancel()`.
- Sin cambios en STT, grabación, formato, mapeo de errores ni limpieza de audio.

### 4. Adaptador iOS

- Guardar `onStarted` y limpiarlo en `cancel()`.
- Invocarlo en `startRecognition()` sólo si `engine.startAndReturnError(null)` es
  `true`; si el engine no arranca, limpiar y reportar
  `VoiceCaptureError.UNKNOWN` (sin `onStarted`).
- **Guard de generación:** `start()` incrementa un contador de sesión y captura el
  valor; los callbacks async de autorización y el callback de reconocimiento
  verifican que la generación siga vigente antes de tocar estado. `cancel()`
  incrementa el contador. Esto evita que una sesión cancelada arranque el
  micrófono, emita `onStarted` o entregue resultados tardíos.
- Sin cambios en grabación/formato.

### 5. `App` (sesión + cap + audio pendiente + limpieza)

Estado: `dictation: DictationUiState`, `dictatedText: String?`,
`pendingAudio: RecordedAudio?`, `dictationJob: Job?`.

- `startDictation()`:
  - cancela `dictationJob`;
  - `voiceTranscriber.cancel()` (corta cualquier sesión previa y descarta su audio
    parcial);
  - **no** borra `pendingAudio` (se conserva hasta reemplazo exitoso);
  - fija `Listening("", 0)` y llama `voiceTranscriber.start(..., onStarted = ...)`.
- `onStarted`:
  - si `dictation` es `Listening`: cancela el job previo y lanza el timer;
  - si es `Processing` (el usuario detuvo antes de que arrancara): llama
    `voiceTranscriber.stop()`;
  - si es `Idle`/`Error`: ignora (sesión cancelada).
- Timer: `startedAt = currentTimeMillis()`; con `delay(DICTATION_TICK_MILLIS)`
  actualiza `elapsedMillis = (now - startedAt).coerceAtMost(MAX)` mientras siga
  `Listening`; al llegar a `DICTATION_MAX_MILLIS` llama `stopDictation()` una vez.
- Callbacks:
  - `onPartialResult(partial)`: si sigue `Listening`, actualiza el parcial
    preservando `elapsedMillis`;
  - `onFinalResult(text, audio)`: cancela el job; si `text.trim()` está vacío,
    borra **sólo** `audio` (nuevo) y fija `Error(NO_MATCH)` conservando
    `pendingAudio`; si no, si `audio != null` borra el `pendingAudio` anterior y
    guarda el nuevo; si `audio == null` conserva el anterior; fija
    `dictatedText = trimmed` y `Idle`;
  - `onError`: cancela el job y fija `Error(error)` conservando `pendingAudio`.
- `stopDictation()`: idempotente; sólo desde `Listening`, cancela el job, fija
  `Processing` y llama `voiceTranscriber.stop()`.
- `cancelDictation()`: cancela el job, `voiceTranscriber.cancel()` y `Idle`.
- `discardPendingAudio()`: borra el archivo pendiente y limpia (idempotente).
- `attachAudio(noteId)`: si hay `pendingAudio`, crea el Adjunto `AUDIO`
  (best-effort: si falla, registra `STORAGE` y borra el archivo); siempre limpia
  `pendingAudio`.
- Guardado (create, con/sin imagen): primero `cancelDictation()` (corta una sesión
  en curso y descarta el parcial), crea la Nota + `attachAudio(note.id)` +
  indexado. Si la creación de la Nota falla, `discardPendingAudio()` y registra
  `STORAGE`; no queda archivo huérfano.
- Con imagen: guardar imagen → crear Nota → Adjunto `IMAGE` (si falla, borrar la
  imagen y conservar la Nota) → `attachAudio(note.id)`. Si la Nota no llegó a
  crearse, borrar imagen y audio pendiente.
- `onCancel` del editor: `cancelDictation()` + `discardPendingAudio()` + volver a
  la lista.
- `DisposableEffect(Unit)` a nivel `App`: al salir de composición cancela el job,
  `voiceTranscriber.cancel()` y `discardPendingAudio()`. **No** se agrega
  `DisposableEffect` en los destinos `Create`/`CreateWithImage`.
- `onDictate` (lista) → `destination = NotesDestination.Create; startDictation()`.
- Se elimina `NotesDestination.VoiceCapture` y su render.

### 6. Editor (`NoteEditorScreen`)

Props nuevas (create; con defaults para no tocar edición):

```kotlin
dictationState: DictationUiState = DictationUiState.Idle,
dictatedText: String? = null,
onStartDictation: (() -> Unit)? = null,
onStopDictation: (() -> Unit)? = null,
onDictatedTextConsumed: ((String) -> Unit)? = null,
```

El panel se muestra sólo si `!isEditing` y `onStartDictation`/`onStopDictation` no
son null. Se ubica **dentro del contenido scrollable, después del campo "Cuerpo" y
antes de "Track"**:

- `Idle` → botón "Dictar".
- `Listening` → `LinearProgressIndicator(progress = { elapsed / 30_000f })`,
  texto "Escuchando… (Ns / 30s)", preview del parcial y botón "Detener".
- `Processing` → "Transcribiendo…" (acción deshabilitada).
- `Error` → `error.message()` + "Reintentar".

Anexado:

```kotlin
LaunchedEffect(dictatedText) {
    val dictated = dictatedText
    if (!dictated.isNullOrBlank()) {
        body = appendDictation(body, dictated)
        onDictatedTextConsumed?.invoke(dictated)
    }
}
```

- `canSave = (body.isNotBlank() || initialImage != null) && dictatedText == null`:
  deshabilita "Guardar" durante el frame en que el final está pendiente de
  consumirse, evitando guardar el audio sin su texto.
- `onSave` no cambia (`App` usa su propio `pendingAudio`).

### 7. Lista y navegación

- `NotesListScreen` no cambia su contrato: sigue emitiendo `onDictate`.
- `App` cambia el handler: abre `Create` y llama `startDictation()`.
- Se elimina el destino/render `VoiceCapture`.

## Expected File Changes

- `composeApp/.../VoiceCaptureScreen.kt` — **eliminar**.
- `composeApp/.../Dictation.kt` — **crear**; `DictationUiState`, constantes,
  `VoiceCaptureError.message()` y `appendDictation()`.
- `composeApp/.../App.kt` — modificar; sesión inline + cap + `pendingAudio` +
  limpieza; quitar `VoiceCapture`.
- `composeApp/.../NoteEditorScreen.kt` — modificar; panel de dictado + anexado +
  guard de `canSave`.
- `composeApp/.../voice/VoiceTranscriber.kt` — modificar; `onStarted` aditivo.
- `composeApp/.../voice/AndroidVoiceTranscriber.kt` — modificar; emitir `onStarted`.
- `composeApp/.../voice/IosVoiceTranscriber.kt` — modificar; `onStarted` + guard de
  generación.
- `NotesListScreen.kt` — sin cambios esperados (el handler vive en `App`).
- `ARCHITECTURE.md`, `docs/technical-discovery.md` — **update**.
- `AGENTS.md` — update **acotado** (sólo "próxima feature en cola").
- `feature_list.json`, `PROGRESS.md` — update al cerrar.

No se esperan cambios en `:core`, el esquema/`.sqm`, `gradle/libs.versions.toml`,
build files, `init.sh`, `iosApp/**` ni dependencias.

## Visual Design Impact

- UI involved: yes. Design source: `DESIGN.md` (provisional) — ya define "Dictado
  inline: barra de progreso de 30s con auto-stop".
- Screens or states affected: `NoteEditorScreen` (Nueva nota) suma el panel de
  dictado; se retira `VoiceCaptureScreen`.
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — la captura por voz pasa a ser **inline** en el
  editor (se retira la pantalla/destino separado); sesión + cap de 30s en `App`;
  `onStarted` en el puerto/adaptadores y guard de sesión iOS.
- `CONSTRAINTS.md`: **not needed** — sin regla durable nueva.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` **update** ("Inline Dictation Decisions
  (inline-dictation-30s)": cap, `onStarted`, auto-stop, audio pendiente, limpieza).
  `DESIGN.md` **not needed**. `feature_list.json`/`PROGRESS.md` **update** al
  cerrar.

## Implementation Plan

1. Crear `Dictation.kt` (estado + constantes + `message()` + `appendDictation()`) y
   borrar `VoiceCaptureScreen.kt`.
2. Agregar `onStarted` al puerto y a los adaptadores Android/iOS; guard de
   generación en iOS.
3. Reescribir el flujo de voz en `App` (sesión inline + cap + `pendingAudio` +
   guardado/limpieza).
4. Añadir el panel de dictado, el anexado y el guard de `canSave` en
   `NoteEditorScreen`; quitar `NotesDestination.VoiceCapture`.
5. Ajustar el handler `onDictate` en `App` ("Dictar nota" abre el editor y
   arranca).
6. Verificación + smoke Android, actualizar docs y cerrar.

## Implementation Tasks

- [x] `Dictation.kt` + eliminar `VoiceCaptureScreen.kt`.
- [x] `VoiceTranscriber.start(..., onStarted)` + Android lo emite + iOS lo emite y
      agrega guard de generación; `cancel()` limpia `onStarted`.
- [x] `App`: `DictationUiState`/`pendingAudio`/timer de 30s (post-`onStarted`) +
      auto-stop + cleanup.
- [x] `App`: `attachAudio` en los guardados de Nueva nota (texto y texto+imagen);
      quitar `VoiceCapture`; handler `onDictate`.
- [x] `NoteEditorScreen`: panel de dictado (progreso/parcial/detener/error) +
      anexado + `canSave` guard.
- [x] Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`; `AGENTS.md` (cola).
- [x] Correr verificación + smoke Android y registrar evidencia.
- [x] Actualizar `feature_list.json` y `PROGRESS.md`.

## Verification Plan

- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL.
- `./gradlew :core:testDebugUnitTest --rerun-tasks` → sin regresiones (la feature no
  toca `:core`).
- `CI=true ./init.sh` → exit 0; gate Android-only, sin dev servers ni red.
- `git status`/`git diff` → `:core`, esquema, build files, deps e `init.sh` sin
  cambios; sin `.sqm` nuevos.
- **iOS (no bloqueante, macOS):** `:composeApp:linkDebugFrameworkIosSimulatorArm64`
  compila.
- **Smoke manual Android** (emulador): desde la lista, "Dictar nota" abre Nueva
  nota y arranca; la barra avanza y hay auto-stop a los **30s**; el cap no corre
  durante el diálogo de permiso (Scenario 15); permiso denegado/cancelar/no-habla
  muestran los mensajes y no crean Nota ni dejan archivos huérfanos; con
  transcripción, guardar persiste texto + chip "Audio"; con imagen, chip "Imagen"
  + "Audio"; re-dictar reemplaza/conserva según Scenario 9; cancelar y guardar
  durante el dictado limpian sin huérfanos.
- **Límite honesto:** el STT real no es inyectable de forma determinista (misma
  limitación que `voice-capture-stt`); el smoke valida los **estados de UI, el cap
  de 30s, el arranque post-permiso y las rutas de error/cancelación**; la
  persistencia del Adjunto de audio ya está cubierta por los tests aceptados de
  `voice-attachment-storage`. No se agrega watchdog para `Processing` (límite
  declarado).
- **Controles negativos** (restaurar y re-verificar): (a) arrancar el timer al tocar
  "Dictar" → el diálogo de permiso consume el cap/auto-stop antes de la sesión;
  (b) no limpiar `pendingAudio` al cancelar → queda un `audio/<uuid>.m4a` huérfano
  en `filesDir`; (c) no anexar `dictatedText` → el `body` queda vacío tras dictar;
  (d) no reemplazar el pendiente al terminar un nuevo dictado con audio → se
  conserva el audio viejo.
- E2E persistente: **no aplica** (sin harness E2E; `init.sh` no levanta simuladores).

## Evidence To Capture

- Salida de `:composeApp:assembleDebug`, `:core:testDebugUnitTest` (sin
  regresiones) y `./init.sh` exit 0.
- Smoke Android: capturas/UI dumps del panel de dictado (progreso avanzando, estado
  a los 30s) y de una Nota guardada con chips "Audio" y "Imagen"; verificación de
  que cancelar no deja `audio/*.m4a` huérfano (`run-as ... ls files/audio`).
- Prueba de permiso lento: concesión tras varios segundos y auto-stop 30s después
  (no durante el diálogo).
- Controles negativos (a)/(b)/(c)/(d) reproducidos y restaurados.
- `git diff` sin cambios en `:core`/esquema/build files/`init.sh`/`iosApp`; sin deps.

## Validator Checklist

- [ ] Alcance respetado: dictado **inline en Nueva nota** (`Create` y
      `CreateWithImage`) + cap 30s + audio al guardar; **sin** modo edición,
      reproducción, IA/tts, rediseño de principal ni cambios de `:core`/esquema/deps.
- [ ] Escenarios de aceptación 1–16 pasan (donde el smoke lo permite, con límites
      declarados).
- [ ] `VoiceCaptureScreen.kt`, `VoiceUiState` y `NotesDestination.VoiceCapture` ya
      no existen; el STT, grabación y formato no cambiaron.
- [ ] `onStarted` se emite sólo con la sesión realmente iniciada (no con permiso
      denegado/error) y el timer arranca ahí; Android/iOS compilan.
- [ ] iOS invalida callbacks tardíos tras `cancel()` (guard de generación).
- [ ] Auto-stop a los 30s; audio pendiente se adjunta al guardar y se borra al
      cancelar; re-dictar conserva/reemplaza según lo especificado (sin huérfanos).
- [ ] `:composeApp` compila; `init.sh` sin cambios; iOS sigue compilando.
- [ ] `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados; `AGENTS.md`
      sólo la línea de cola.
- [ ] `feature_list.json` y `PROGRESS.md` actualizados con la evidencia.
- [ ] No hay E2E persistente ni watchdog y la spec lo justifica.

## Risks And Open Questions

- **Conflicto de merge:** `brainstorming-wizard` también toca `App.kt` y
  `NoteEditorScreen.kt`. Se resuelve rebaseando esta rama después de cada
  integración, según `docs/mvp-execution-plan.md`.
- **`Processing` sin final:** límite aceptado y documentado; "Cancelar" es la
  salida. No se agrega timeout.
- **Background:** sin lifecycle observer; el cap de 30s acota la grabación y el
  `DisposableEffect` de `App` libera al salir de composición.
- **iOS congelado:** sólo se exige que compile el framework; el smoke de dictado es
  Android.
- **Preguntas abiertas de producto:** ninguna.
