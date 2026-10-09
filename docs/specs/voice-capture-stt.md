# Feature Implementation Spec: Captura por voz con speech-to-text nativo

## Source Feature

- `id`: `voice-capture-stt`
- `area`: `capture-voice`
- `depends_on`: `["create-text-note"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Habilitar la **captura por voz** como segundo modo de entrada del MVP
(`docs/build-brief.md`, slice 2): el usuario toca "Dictar nota", habla y obtiene
una **Nota persistida con el texto transcripto** por el motor nativo — Android
`SpeechRecognizer`, iOS `Speech` framework (`SFSpeechRecognizer` +
`AVAudioEngine`). Es el primer uso de una integración nativa y un criterio de
éxito explícito del MVP ("al menos una integración nativa resuelta
correctamente").

La feature sigue la arquitectura vigente: la lógica de dominio y la persistencia
siguen en `:core` (sin cambios); la captura de voz vive en `:composeApp` como un
puerto con adaptadores por plataforma, cableado en los entry points y consumido
por `App` con el mismo patrón sin DI/`ViewModel`/Flow. La transcripción se guarda
como `body` de una `Note` creada por el camino existente
(`noteRepository.create(NoteDraft(...))`).

## Non-Goals

- **Guardar el audio original como `Adjunto`** (grabar/retener archivo de audio,
  reproducirlo o persistirlo): es la feature siguiente, `voice-attachment-storage`.
  No se usa `MediaRecorder`/`AVAudioRecorder` ni se escribe ningún archivo.
- Captura por imagen/cámara/OCR (`image-capture-camera`, `image-ocr-optional`).
- IA, embeddings, indexado, enlaces, enriquecimiento, GDD, tags, búsqueda o RAG.
- Edición del `status` o del `track` desde la pantalla de voz: la Nota se crea
  `CAPTURED` con `track = null`; el usuario puede corregirla luego con el editor
  de texto existente.
- Cambios de esquema/migraciones (`schema.version` sigue en 1, sin `.sqm`).
- Framework DI, `ViewModel`/`Lifecycle`, Flow o librería de navegación. La
  navegación sigue resolviéndose con `NotesDestination`.
- Publicación/distribución (`android-release-pipeline`, `ios-release-pipeline`).

## Job Story

When tengo una idea y escribir me saca del ritmo (o estoy manejando, caminando o
con las manos ocupadas),
I want dictarla y que la app la transcriba a una nota,
so I can capturarla casi al instante sin teclear y sin perder la idea.

## Users And Permissions

- Autor/usuario único del MVP (local-first, sin auth). Todas las notas son suyas;
  `owner` se sigue proveyendo desde la UI (`LOCAL_OWNER_ID = "local"`).
- **Micrófono (obligatorio, runtime):** Android `RECORD_AUDIO`; iOS
  `NSMicrophoneUsageDescription` + `NSSpeechRecognitionUsageDescription`. La
  feature debe funcionar sin permiso (mostrando un error explícito) y pedirlo en
  el primer uso. Si el usuario niega, no se crea ninguna nota ni crashea la app.
- **Reconocimiento de voz (iOS):** requiere autorización de `SFSpeechRecognizer`.
- No hay otros actores ni roles.

## Acceptance Scenarios

### Scenario 1: Dictado crea una nota (happy path)

Given la app abierta con permiso de micrófono concedido y STT nativo disponible,
When el usuario toca "Dictar nota", luego "Grabar", dicta una frase y toca
"Detener" (o el motor entrega un resultado final),
Then se crea y persiste una `Note` con el texto transcripto como `body`
(`track = null`, `status = capturada`) y la lista la muestra primera
(`updated_at DESC, id DESC`).

### Scenario 2: Permiso denegado

Given que el usuario deniega el permiso de micrófono (Android) o de micrófono/
reconocimiento (iOS),
When intenta iniciar el dictado,
Then la pantalla muestra un mensaje de error explícito en texto ("Permiso de
micrófono denegado…"), no se crea ninguna Nota, no hay crash y el usuario puede
reintentar o cancelar.

### Scenario 3: Sin habla / sin coincidencia / vacío

Given el dictado iniciado,
When el motor devuelve "sin coincidencia", timeout de habla o una transcripción
vacía/sólo espacios,
Then no se persiste ninguna Nota, se muestra un mensaje explícito y el usuario
puede reintentar o cancelar.

### Scenario 4: Cancelar

Given el dictado en curso ("Escuchando…"),
When el usuario toca "Cancelar",
Then la sesión de reconocimiento se detiene y se libera el micrófono, no se crea
ninguna Nota y se vuelve a la lista.

### Scenario 5: STT no disponible

Given un dispositivo/emulador sin servicio de reconocimiento (`isRecognitionAvailable`
falso en Android o `SFSpeechRecognizer` no disponible en iOS),
When el usuario intenta dictar,
Then se muestra un mensaje explícito ("El dictado no está disponible en este
dispositivo"), no hay crash y no se crea ninguna Nota.

### Scenario 6: Captura de texto intacta y gate honesto

Given la feature implementada,
When se usan las funciones existentes (crear/editar/borrar nota de texto) y se
ejecuta `./init.sh`,
Then el CRUD de texto sigue funcionando igual y `./init.sh` termina en 0 sin dev
servers ni simuladores (tests del core verdes, `assembleDebug` compila, framework
iOS enlaza); la verificación de voz es manual y no forma parte del gate.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `create-text-note` `accepted`
  (Sessions 011/013). Próxima en cola declarada: `note-category-and-tags`; esta
  spec trabaja `voice-capture-stt` por pedido explícito.
- `feature_list.json` — metadata de `voice-capture-stt` (verificación provisional:
  dictado real en Android/iOS; "Manejo de permisos de micrófono") y de
  `voice-attachment-storage`.
- `docs/build-brief.md` — slice 2: "Captura por voz (speech-to-text nativo) →
  `Nota` con transcripción"; éxito del MVP: "al menos una integración nativa".
- `docs/technical-discovery.md` — "Speech-to-text nativo: Android
  `SpeechRecognizer` / iOS `Speech`"; constraints de KMP × 2 plataformas
  (permisos/STT duplican trabajo nativo).
- `docs/risks-and-open-questions.md` — "Modelo de STT y cámara por plataforma,
  más manejo de permisos" como pregunta de implementación.
- `CONTEXT.md`, `docs/domain-model.md` — `Captura` (texto/voz/imagen), `Adjunto`
  (audio original) vs. texto derivado (transcripción vive en la Nota).
- `DESIGN.md` — pantalla 5 "Captura de Voz: grabando / transcribiendo"; reglas de
  accesibilidad (estados no sólo por color, targets ≥ 48dp).
- `docs/specs/create-text-note.md` — contrato precedente: `App(noteRepository)`,
  `NotesDestination`, `LOCAL_OWNER_ID`, `NoteEditorScreen`, `reload()`.
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — `App(noteRepository)`
  hoistea `notes`/`destination`/`refreshKey`; `LaunchedEffect(refreshKey)`; crea con
  `create(NoteDraft(LOCAL_OWNER_ID, body.trim(), track))`.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` — sólo
  `onCreate`/`onEdit`; FAB "Nueva nota" y CTA "Crear primera nota".
- `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt` —
  `ComponentActivity`; construye `createDatabase(...)` + `SqlDelightNoteRepository`
  y llama `App(noteRepository)`.
- `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt` —
  construye base + repositorio y envuelve `App(repository)`.
- `composeApp/src/androidMain/AndroidManifest.xml` — sin `uses-permission` de
  micrófono.
- `iosApp/iosApp/Info.plist` — sin `NSMicrophoneUsageDescription` ni
  `NSSpeechRecognitionUsageDescription`.
- `composeApp/build.gradle.kts` — `commonMain`: `:core` + `compose.runtime/
  foundation/material3/ui`; `androidMain`: `androidx.activity:activity-compose`
  1.10.1 (aporta `registerForActivityResult`). Sin source set de test.
- `core/.../repository/NoteRepository.kt` y `model/Note.kt` — `create(NoteDraft)` y
  `NoteDraft(owner, body, track, status = CAPTURED)`; no requieren cambios.
- `init.sh` — gate no bloqueante: `assembleDebug`, tests del core (Android/JVM e
  iOS), link del framework iOS; sin simuladores.
- No existe `CONSTRAINTS.md` ni `docs/adr/`. No existe código de voz/STT previo.

### Existing Patterns To Follow

- **Puerto + adaptadores por plataforma**: igual que `DatabaseDriverFactory`
  (interfaz en commonMain, impl en androidMain/iosMain) y que `AiClient` (puerto
  agnóstico, adaptador concreto afuera). No se usa `expect`/`actual` cuando la
  implementación necesita contexto de plataforma en el constructor.
- **Wiring sin DI**: las dependencias se construyen en los entry points y se pasan
  a `App(...)` por parámetro (`App(noteRepository)` → `App(noteRepository,
  voiceTranscriber)`).
- **UI "tonta" + dueño de estado**: `App` es dueño del estado; las pantallas
  reciben estado y emiten callbacks.
- **Verificación**: path de datos en tests de `:core`; la UI/UI nativa con smoke
  manual en Android e iOS (no hay harness de UI tests).
- **`init.sh` no bloqueante**, sin dev servers ni simuladores.
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- No hay abstracción de STT, ni implementación Android/iOS, ni manejo de permisos.
- `App` no conoce ningún destino de voz ni un segundo modo de captura.
- `NotesListScreen` no ofrece acción de voz.
- AndroidManifest e Info.plist no declaran micrófono/uso de reconocimiento.
- El emulador/simulador puede no tener STT funcional; la verificación real
  requiere un entorno con motor de reconocimiento y entrada de audio.

## Technical Approach

### Puerto `VoiceTranscriber` (commonMain)

Nuevo archivo `composeApp/src/commonMain/kotlin/com/playbook/app/voice/VoiceTranscriber.kt`
(paquete provisional `com.playbook.app.voice`):

```kotlin
/** Errores de captura de voz normalizados por plataforma. */
enum class VoiceCaptureError { PERMISSION_DENIED, UNAVAILABLE, NO_MATCH, NETWORK, BUSY, UNKNOWN }

/** Sesión y resultado de dictado. Los callbacks se invocan en el hilo principal. */
interface VoiceTranscriber {
    fun start(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit,
        onError: (VoiceCaptureError) -> Unit,
    )
    /** Pide el resultado final (sigue `onFinalResult`/`onError`). */
    fun stop()
    /** Cancela la sesión y libera el micrófono sin emitir resultado. */
    fun cancel()
}
```

- Vive en `:composeApp` (no en `:core`): es una integración nativa que consume
  sólo la UI y necesita contexto de plataforma; `:core` no cambia.
- Puerto + adaptadores (no `expect`/`actual`): los adaptadores reciben
  `ComponentActivity` (Android) o no reciben nada (iOS) en su constructor, patrón
  de `DatabaseDriverFactory`.
- `onPartialResult` es opcional en la práctica (feedback "transcribiendo"); el
  contrato igual lo pide para no reabrir la interfaz.

### Implementación Android (`androidMain`)

Nuevo `AndroidVoiceTranscriber(activity: ComponentActivity)`:

- Registra `activity.registerForActivityResult(ActivityResultContracts.RequestPermission())`
  en la construcción (aporta `androidx.activity:activity-compose`, ya presente).
- En `start()`: si `activity.checkSelfPermission(RECORD_AUDIO) != GRANTED`, lanza
  el request; si denegado → `onError(PERMISSION_DENIED)`. Si está concedido y
  `SpeechRecognizer.isRecognitionAvailable(activity)` es `false` →
  `onError(UNAVAILABLE)`.
- Crea `SpeechRecognizer.createSpeechRecognizer(activity)` con un
  `RecognitionListener`; dispara `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` con
  `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM` y `EXTRA_PARTIAL_RESULTS = true`
  (idioma por defecto del sistema).
- `onPartialResults` → `onPartialResult`; `onResults` → `onFinalResult` (primer
  resultado) y libera el recognizer; `onError(código)` → mapea a
  `VoiceCaptureError` (`ERROR_NO_MATCH`/`ERROR_SPEECH_TIMEOUT` → `NO_MATCH`,
  `ERROR_NETWORK*` → `NETWORK`, `ERROR_RECOGNIZER_BUSY` → `BUSY`,
  `ERROR_INSUFFICIENT_PERMISSIONS` → `PERMISSION_DENIED`, resto → `UNKNOWN`).
- `stop()` → `stopListening()`; `cancel()` → `cancel()` + `destroy()` y limpia la
  sesión. El recognizer se recrea en el siguiente `start()` (evita leaks).
- Todo ocurre en el hilo principal (callbacks del `SpeechRecognizer`).
- Añadir `<uses-permission android:name="android.permission.RECORD_AUDIO"/>` al
  manifest.

### Implementación iOS (`iosMain`)

Nuevo `IosVoiceTranscriber()`:

- En `start()`: pide `SFSpeechRecognizer.requestAuthorization` y
  `AVAudioSession.sharedInstance().requestRecordPermission`; si alguna no queda
  autorizada → `onError(PERMISSION_DENIED)`. Si `SFSpeechRecognizer(locale)` es
  `null` o `!isAvailable` → `onError(UNAVAILABLE)`.
- Configura `AVAudioSession` (categoría `.record`, `setActive(true)`); crea
  `SFSpeechAudioBufferRecognitionRequest`, un `SFSpeechRecognitionTask` y un
  `AVAudioEngine`; instala un tap en `inputNode` que hace `append` de los buffers.
- Callbacks del task → `onPartialResult` (transcripción parcial), `onFinalResult`
  (texto final, si no vacío) o `onError` mapeado (`NO_MATCH`/`NETWORK`/etc.).
  Como los callbacks de `Speech` llegan en una cola secundaria, se despachan al
  main queue (`dispatch_async(dispatch_get_main_queue()) { ... }`) antes de tocar
  estado de Compose.
- `stop()` → `recognitionRequest.endAudio()` + `audioEngine.stop()`;
  `cancel()` → `recognitionTask.cancel()`, quita el tap, detiene el engine y
  desactiva la sesión de audio.
- Añadir a `iosApp/iosApp/Info.plist`: `NSMicrophoneUsageDescription` y
  `NSSpeechRecognitionUsageDescription` (strings en español). Sin la segunda,
  iOS termina el proceso al primer uso.

### UI: destino de captura y creación de la Nota

- `NotesDestination` suma `data object VoiceCapture`.
- `NotesListScreen` suma `onDictate: () -> Unit` y una acción secundaria
  "Dictar nota" (p. ej. `FilledTonalButton`/`OutlinedButton` bajo el título
  "Notas"), visible tanto en el estado vacío como con notas; el FAB "Nueva nota"
  y el CTA "Crear primera nota" no cambian.
- Nuevo `VoiceCaptureScreen.kt` (commonMain), pantalla "tonta" que recibe estado y
  emite callbacks:
  - Estado visible: `Idle` ("Tocá Grabar y dictá tu idea."), `Listening`
    ("Escuchando…" + transcripción parcial), `Processing` ("Transcribiendo…") y
    `Error` (mensaje explícito en texto, no sólo color).
  - Acción primaria: "Grabar" (Idle/Error → "Reintentar"), "Detener" (Listening);
    acción secundaria "Cancelar".
  - No mantiene el `VoiceTranscriber`: sólo notifica `onStart`/`onStop`/`onCancel`.
- `App(noteRepository, voiceTranscriber)` orquesta en el destino `VoiceCapture`:
  - `LaunchedEffect`/callbacks inician la sesión sólo cuando el usuario toca
    "Grabar" (no auto-start), para que el prompt de permiso sea explícito.
  - `onPartialResult` actualiza la transcripción parcial; `onError` pasa a
    `Error`; `onFinalResult` con texto no vacío **crea la Nota**:
    `noteRepository.create(NoteDraft(owner = LOCAL_OWNER_ID, body = text.trim(),
    track = null))` → `goToList()` → `reload()`.
  - `onFinalResult` vacío/blank se trata como `NO_MATCH` (no crea Nota).
  - Al salir del destino (cancelar o tras crear) se llama `voiceTranscriber.cancel()`
    (`DisposableEffect`) para liberar el micrófono.
  - El `status` queda `CAPTURED` (default de `NoteDraft`) y `track = null`; el
    usuario puede corregir después con el editor existente.

### Hilos, ciclo de vida y dependencias

- Los adaptadores garantizan que los callbacks se invoquen en el hilo principal;
  `App` sólo toca estado de Compose desde esos callbacks.
- Sin dependencias nuevas: `SpeechRecognizer`/`RecognizerIntent` son framework
  Android; `activity-compose` 1.10.1 ya está para el permission launcher;
  `Speech`/`AVFoundation`/`dispatch` vienen de Kotlin/Native. **No** se agrega
  `kotlinx-coroutines` a `:composeApp` ni se usa Flow.
- Sin cambios en `:core`, `Note.sq`, `gradle/libs.versions.toml` ni `init.sh`.

### Riesgos y decisiones abiertas

- **STT no garantizado en emulador/simulador**: el emulador Android necesita una
  imagen con servicios de reconocimiento (p. ej. Google APIs/Play) y entrada de
  audio; el simulador iOS puede dictar usando el micrófono del host pero puede no
  entregar audio. La verificación real puede requerir dispositivo físico. Se
  documenta como limitación de entorno, no como fallo del código.
- **Creación directa vs. revisar en el editor**: se opta por crear la Nota en
  cuanto hay transcripción final (cumple "obtiene una nota" y respeta la captura
  inmediata); la corrección posterior usa el editor de texto existente. Se
  descarta precargar el editor y exigir "Guardar" para no duplicar el flujo de
  creación.
- **`track` null en la nota de voz**: consistente con el edge case "Nota sin
  `track`"; la asignación de `track`/etiquetas es de `note-category-and-tags`.
- **Reconocimiento en la nube vs. on-device**: `SpeechRecognizer` puede usar el
  servicio del sistema y `SFSpeechRecognizer` por defecto usa red; no se fija el
  modo. No afecta el contrato (la Nota se crea igual); se documenta.
- **Sin tests automáticos de la integración nativa**: no hay harness de UI ni
  forma de simular el motor de voz en `init.sh`; se verifica manualmente en ambas
  plataformas. El path de persistencia ya está cubierto por tests de `:core`.

## Expected File Changes

- `composeApp/src/commonMain/kotlin/com/playbook/app/voice/VoiceTranscriber.kt` —
  crear; puerto + `VoiceCaptureError` (paquete provisional `com.playbook.app.voice`).
- `composeApp/src/commonMain/kotlin/com/playbook/app/VoiceCaptureScreen.kt` —
  crear; pantalla "tonta" de dictado (estado + acciones).
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — modificar;
  parámetro `voiceTranscriber`, `NotesDestination.VoiceCapture`, estado de voz,
  orquestación y creación de la Nota con `reload()`.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` —
  modificar; `onDictate` y acción secundaria "Dictar nota".
- `composeApp/src/androidMain/kotlin/com/playbook/app/voice/AndroidVoiceTranscriber.kt` —
  crear; `SpeechRecognizer` + permiso runtime.
- `composeApp/src/iosMain/kotlin/com/playbook/app/voice/IosVoiceTranscriber.kt` —
  crear; `SFSpeechRecognizer` + `AVAudioEngine` + permisos.
- `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt` — modificar;
  construir `AndroidVoiceTranscriber(this)` y pasarlo a `App(...)`.
- `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt` —
  modificar; construir `IosVoiceTranscriber()` y pasarlo a `App(...)`.
- `composeApp/src/androidMain/AndroidManifest.xml` — modificar; `RECORD_AUDIO`.
- `iosApp/iosApp/Info.plist` — modificar; `NSMicrophoneUsageDescription` y
  `NSSpeechRecognitionUsageDescription`.
- `ARCHITECTURE.md` — modificar; superficie de integración nativa de voz.
- `docs/technical-discovery.md` — modificar; decisiones de STT/permisos/hilos.
- `docs/risks-and-open-questions.md` — modificar; cierra la pregunta de "modelo de
  STT y permisos", registra la limitación de emulador/simulador.
- `AGENTS.md` — modificar al cerrar; corregir la línea "Próxima feature en cola".
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado y evidencia.

No se esperan cambios en `core/**`, `core/src/**/Note.sq`,
`gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `init.sh`,
`iosApp/iosApp/*.swift` ni el `.pbxproj` (el `Info.plist` ya está referenciado).

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` (provisional). Se sigue la pantalla 5 "Captura de
  Voz: grabando / transcribiendo" y las reglas de accesibilidad ("estados no sólo
  color", targets ≥ 48dp); no se fijan tokens.
- Screens or states affected: "Lista de Notas" (nueva acción "Dictar nota") y
  nueva "Captura de Voz" (`Idle`/`Listening`/`Processing`/`Error`, con
  transcripción parcial y mensajes de error en texto).
- New design artifact required: no — la entrega de UI/UX sigue pendiente.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — aparece una integración nativa (puerto
  `VoiceTranscriber` + adaptadores Android/iOS), permisos y un nuevo destino de UI.
- `CONSTRAINTS.md`: no existe y **not needed** — no surge una regla MUST/MUST NOT
  durable distinta de las ya documentadas en `ARCHITECTURE.md`.
- `AGENTS.md`: **update** — cambia la línea "Próxima feature en cola"; no cambian
  workflow ni gate.
- Other docs: `docs/technical-discovery.md` — update (STT/permisos);
  `docs/risks-and-open-questions.md` — update; `feature_list.json`/`PROGRESS.md` —
  update al cerrar; `CONTEXT.md`/`docs/domain-model.md`/`DESIGN.md` — not needed.
- `docs/adr/`: no se crea — la decisión no es arquitectónica de alto nivel (no
  elige runtime/proveedor; usa las APIs nativas del sistema).

## Implementation Plan

1. Definir `VoiceTranscriber` + `VoiceCaptureError` en `:composeApp/commonMain`.
2. Implementar `AndroidVoiceTranscriber` (SpeechRecognizer + permiso runtime) y
   declarar `RECORD_AUDIO`; cablearlo en `MainActivity`.
3. Implementar `IosVoiceTranscriber` (SFSpeechRecognizer + AVAudioEngine +
   permisos) y agregar las usage descriptions al `Info.plist`; cablearlo en
   `MainViewController`.
4. Crear `VoiceCaptureScreen` y sumar `NotesDestination.VoiceCapture` + estado de
   voz + creación de Nota con `reload()` en `App`.
5. Sumar la acción "Dictar nota" (`onDictate`) en `NotesListScreen`.
6. Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y
   `docs/risks-and-open-questions.md`.
7. Correr la verificación, hacer smoke manual en Android e iOS y registrar
   evidencia; actualizar `feature_list.json`/`PROGRESS.md`/`AGENTS.md` al cerrar.

## Implementation Tasks

- [x] Crear `VoiceTranscriber` + `VoiceCaptureError` en `com.playbook.app.voice`.
- [x] Implementar `AndroidVoiceTranscriber` con `SpeechRecognizer`,
      `RecognitionListener` y mapeo de errores.
- [x] Pedir/verificar `RECORD_AUDIO` con `registerForActivityResult` y manejar
      negación; agregar el `uses-permission` al manifest.
- [x] Implementar `IosVoiceTranscriber` con `SFSpeechRecognizer`/`AVAudioEngine`,
      despacho a main y liberación de recursos en `cancel()`.
- [x] Pedir autorizaciones de micrófono/reconocimiento iOS y agregar
      `NSMicrophoneUsageDescription` + `NSSpeechRecognitionUsageDescription` al
      `Info.plist`.
- [x] Cablear los adaptadores en `MainActivity`/`MainViewController` y pasar el
      `VoiceTranscriber` a `App(...)`.
- [x] Crear `VoiceCaptureScreen` con estados `Idle`/`Listening`/`Processing`/`Error`
      y acciones Grabar/Detener/Reintentar/Cancelar.
- [x] Agregar `NotesDestination.VoiceCapture` y la orquestación en `App` (inicio
      manual, parciales, error, creación de la Nota, `cancel()` al salir).
- [x] Agregar la acción "Dictar nota" (`onDictate`) en `NotesListScreen`.
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y
      `docs/risks-and-open-questions.md`.
- [x] Correr `./init.sh` y la verificación; smoke manual Android e iOS (permiso
      concedido/denegado, dictado → Nota, cancelar). Registrar evidencia y
      limitaciones del entorno. (El happy path de audio real → Nota no pudo
      ejecutarse en emulador por falta de audio inyectable; ver evidencia.)
- [x] Actualizar `feature_list.json`, `PROGRESS.md` y `AGENTS.md` al cerrar.

## Verification Plan

- `./gradlew :core:testDebugUnitTest` → BUILD SUCCESSFUL; el CRUD y el orden
  `updated_at DESC, id DESC` siguen verdes (path de persistencia de la Nota).
- `./gradlew :core:iosSimulatorArm64Test` (macOS) → BUILD SUCCESSFUL.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (adaptador Android +
  permiso + UI).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (adaptador iOS + UI); `xcodebuild` del scheme `iosApp` → BUILD SUCCEEDED.
- Smoke manual **Android** (emulador con servicios de reconocimiento y micrófono o
  dispositivo):
  - primer uso pide permiso; concedido → "Grabar" → dictar → "Detener" → aparece
    la Nota con el texto en la lista;
  - denegar permiso → mensaje explícito, sin Nota ni crash;
  - "Cancelar" durante "Escuchando…" → sin Nota, vuelve a la lista;
  - grabar con silencio → mensaje de "sin coincidencia", sin Nota.
- Smoke manual **iOS** (simulador iPhone o dispositivo):
  - aparecen los prompts de micrófono y reconocimiento;
  - dictado → Nota con transcripción en la lista;
  - negar permiso y cancelar → comportamiento de Escenarios 2 y 4.
- `./init.sh` → exit 0; ejecuta el gate no bloqueante actual, **no** inicia dev
  servers ni simuladores. No se agrega un check de voz al script (requiere
  hardware/motor de voz).
- **E2E persistente: no aplica** — no existe harness de UI/E2E y `init.sh` no
  levanta simuladores; el motor de STT nativo no se puede simular de forma
  determinista. El path de datos (crear Nota) está cubierto por los tests de
  `:core` en dos plataformas; la integración nativa se verifica con smoke manual,
  igual que las features de UI previas.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest`, `:core:iosSimulatorArm64Test`,
  `:composeApp:assembleDebug` y `:composeApp:linkDebugFrameworkIosSimulatorArm64`
  (y `xcodebuild` si se corre).
- Salida de `./init.sh` (exit 0) sin procesos de larga duración.
- Evidencia manual Android e iOS: permiso concedido → dictado → Nota con
  transcripción; permiso denegado; cancelar; sin habla. Capturas/UI dumps o
  transcripciones observadas.
- Confirmación de que no hubo cambios en `core/**`, `Note.sq`,
  `libs.versions.toml`, `composeApp/build.gradle.kts` ni `init.sh`; `schema.version`
  sigue en 1, sin `.sqm`.
- Limitaciones de entorno registradas de forma honesta (STT no disponible en
  emulador/simulador, etc.) si aparecen.

## Validator Checklist

- [ ] Alcance respetado: dictado → Nota con transcripción; **sin** guardar el
      audio original, sin `Adjunto`, sin cámara/OCR/IA/tags/GDD.
- [ ] Escenarios de aceptación 1–6 pasan (o la limitación de entorno queda
      declarada honestamente sin fabricar evidencia).
- [ ] La Nota se crea con `track = null`/`status = capturada` vía
      `noteRepository.create(...)`; la lista la muestra sin reiniciar.
- [ ] Permiso denegado/no disponible/sin habla muestran mensaje explícito en texto
      y **no** crean Nota ni crashean.
- [ ] Cancelar detiene el reconocimiento y libera el micrófono.
- [ ] Puerto `VoiceTranscriber` + adaptadores por plataforma; sin DI/`ViewModel`/
      Flow/coroutines nuevas ni librería de navegación.
- [ ] Sin cambios en `core/**`, `Note.sq` ni esquema (`schema.version = 1`, sin
      `.sqm`) ni dependencias nuevas.
- [ ] `Info.plist` y `AndroidManifest.xml` declaran micrófono/reconocimiento.
- [ ] Evidencia en `feature_list.json`/`PROGRESS.md`; `ARCHITECTURE.md`,
      `docs/technical-discovery.md`, `docs/risks-and-open-questions.md` y
      `AGENTS.md` actualizados.
- [ ] No hay E2E persistente y la spec lo justifica.
