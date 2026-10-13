# Feature Implementation Spec: Guardar el audio original como Adjunto

## Source Feature

- `id`: `voice-attachment-storage`
- `area`: `capture-voice`
- `depends_on`: `["voice-capture-stt"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Completar la captura por voz del MVP: además de crear una Nota con la
transcripción (lo que ya hace `voice-capture-stt`), la app **graba el audio
original del dictado y lo conserva como `Adjunto` de esa Nota**, separado del
texto derivado (transcripción). Es la segunda mitad del slice de voz de
`docs/build-brief.md` y la primera vez que el producto guarda un Adjunto de
`AUDIO` (hoy sólo existe `IMAGE`).

La feature **no cambia el esquema**: la tabla `attachment` (esquema v4) ya tiene
la columna `kind TEXT NOT NULL` y `AttachmentRepository` acepta cualquier
`AttachmentKind`; sólo hay que agregar el valor `AUDIO` y grabar el archivo en
almacenamiento privado con ruta relativa, reutilizando el patrón de
`image-capture-camera`. La captura de voz sigue viviendo en `:composeApp`
(puerto + adaptadores nativos); `:core` sólo suma el valor de enum y su test.

## Non-Goals

- **Reproducir el audio** (player/`MediaPlayer`/`AVAudioPlayer`, controles
  play/pausa/seek, waveform): la reproducción queda para una feature posterior.
  Refina el texto provisional "se puede reproducir" de `feature_list.json`: esta
  feature garantiza que el Adjunto **persiste y es visible**; el `user_visible_behavior`
  ("conserva el audio original como Adjunto, separado de la transcripción") se
  cumple sin playback.
- Editar/quitar/reemplazar Adjuntos de una nota existente desde la UI.
- Formatos de audio adicionales o conversión/compresión configurable; sólo AAC
  en contenedor MPEG-4 (`.m4a`).
- OCR (`image-ocr-optional`), IA/embeddings/enlaces/GDD/RAG/enriquecimiento.
- Cambios de esquema o migraciones (`schema.version` sigue en **4**, sin `.sqm`).
- Render de audio/reproductor en la tarjeta o pantalla de detalle.
- Framework DI, `ViewModel`/`Lifecycle`, Flow, coroutines nuevas o librería de
  navegación; la navegación sigue con `NotesDestination`.
- Publicación/distribución (`android-release-pipeline`/`ios-release-pipeline`).

## Job Story

When dicté una idea y la app la transcribió,
I want que el audio original quede guardado junto a la Nota,
so I can volver a escuchar la grabación (tono, matices, detalles que la
transcripción perdió) y no depender sólo del texto reconocido.

## Users And Permissions

- Autor/usuario único del MVP (local-first, sin auth). Todas las Notas y sus
  archivos son suyos.
- **Micrófono:** ya se declara y solicita por `voice-capture-stt`
  (`RECORD_AUDIO` runtime en Android; `NSMicrophoneUsageDescription` en iOS).
  **No se agregan permisos nuevos**: grabar usa la misma concesión que el STT.
- Igual que hoy, sin permiso no se graba, no se transcribe y no se crea Nota ni
  archivo (mensaje explícito, sin crash).

## Acceptance Scenarios

### Scenario 1: Dictado con audio (happy path)

Given la app abierta y el permiso de micrófono concedido,
When el usuario toca "Dictar nota", "Grabar", dicta una frase y toca "Detener"
(o el motor entrega un resultado final),
Then se crea una `Note` con la transcripción como `body` (`track = null`,
`status = capturada`), se guarda el audio original en almacenamiento privado y se
persiste una fila `attachment` con `kind = "audio"`, `mime_type = "audio/mp4"` y
ruta **relativa** (`audio/<uuid>.m4a`); la lista muestra la Nota con un chip
textual "Audio".

### Scenario 2: Transcripción vacía/no match con audio grabado

Given un dictado donde se grabó audio pero el motor devuelve vacío/`NO_MATCH`,
When termina la sesión,
Then **no** se persiste Nota, se muestra el mensaje explícito actual y el archivo
de audio grabado se elimina (no quedan huérfanos).

### Scenario 3: Grabación no disponible, transcripción OK

Given que la grabación de audio no puede iniciar o falla (p. ej. el micrófono no
admite dos clientes simultáneos, o el dispositivo no puede escribir el archivo),
When el motor igual entrega una transcripción final,
Then se crea la Nota con la transcripción **sin Adjunto**, sin crash, y la lista
refleja la Nota normalmente (degradación tolerante, mismo camino que hoy).

### Scenario 4: Cancelar

Given el dictado en curso ("Escuchando…"),
When el usuario toca "Cancelar",
Then se detiene el reconocimiento y la grabación, se elimina cualquier archivo de
audio parcial, no se crea Nota y se vuelve a la lista.

### Scenario 5: Visibilidad en la lista

Given Notas con distintos Adjuntos,
When se muestra la lista,
Then una Nota con audio muestra el chip "Audio", una con imagen muestra "Imagen",
una con ambos muestra los dos, y una sin Adjuntos no muestra ninguno; los chips
son texto (no sólo color).

### Scenario 6: Borrar la Nota borra el Adjunto de audio

Given una Nota con Adjunto de audio,
When se borra la Nota desde la UI (con confirmación),
Then se elimina la fila `attachment` (cascade transaccional ya existente) y el
archivo de audio del almacenamiento privado; no quedan huérfanos.

### Scenario 7: Esquema estable y gate honesto

Given la feature implementada,
When se inspecciona el esquema y se ejecuta `./init.sh`,
Then `PlaybookDatabase.Schema.version` sigue siendo **4**, no hay `.sqm` nuevos,
`Attachment.sq` no cambió, y `./init.sh` termina en 0 sin dev servers ni
simuladores (tests de `:core` verdes en Android/JVM e iOS, `assembleDebug` compila
y el framework iOS enlaza).

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `voice-capture-stt` `accepted`
  (Sessions 015+); estado actual: esquema **v4**, `image-capture-camera`
  `accepted`. Próximas listas: `semantic-linking` y esta feature.
- `feature_list.json` — metadata de `voice-attachment-storage` (`depends_on`:
  `voice-capture-stt`, `accepted`); nota "Distinción Nota vs Adjunto en
  CONTEXT.md"; verificación provisional a refinar.
- `docs/specs/voice-capture-stt.md` — contrato precedente: puerto
  `VoiceTranscriber` + adaptadores `AndroidVoiceTranscriber`/`IosVoiceTranscriber`;
  `App(noteRepository, attachmentRepository, voiceTranscriber)`; la Nota se crea en
  `onFinalResult`; el audio original **no** se persiste (es esta feature).
- `docs/specs/image-capture-camera.md` — precedente del Adjunto: archivo en
  almacenamiento privado + ruta relativa, `Attachment`/`AttachmentKind`,
  `AttachmentRepository`, `AttachmentFileStore`, chip "Imagen", cascade al borrar.
- `core/src/commonMain/sqldelight/.../Attachment.sq` — tabla `attachment` con
  `kind TEXT NOT NULL` (comentario: `AUDIO` llegará sin cambio de esquema);
  queries `insertAttachment`/`selectAttachmentsByNoteId`/…/`deleteAttachmentsByNoteId`.
- `core/src/commonMain/sqldelight/.../{1,2,3}.sqm` — v1→v2 embedding, v2→v3
  `note_tag`, v3→v4 `attachment`; esquema actual = **v4**.
- `core/src/commonMain/kotlin/com/playbook/core/model/Attachment.kt` —
  `Attachment` + `AttachmentKind` (`IMAGE("imagen")`, `fromCode` fail-fast).
- `core/.../model/AttachmentMappers.kt` — `AttachmentRow.toDomain()` usa
  `AttachmentKind.fromCode(kind)` (agregar un valor no requiere tocar el mapper).
- `core/.../repository/{AttachmentRepository,SqlDelightAttachmentRepository}.kt` —
  `create(noteId, kind, mimeType, filePath, byteSize)` ya acepta cualquier `kind`.
- `composeApp/src/commonMain/.../voice/VoiceTranscriber.kt` — puerto +
  `VoiceCaptureError`; `start(onPartialResult, onFinalResult: (String) -> Unit,
  onError)`; `stop()`/`cancel()`.
- `composeApp/src/androidMain/.../voice/AndroidVoiceTranscriber.kt` —
  `SpeechRecognizer` + `RECORD_AUDIO` runtime; callbacks en el hilo principal.
- `composeApp/src/iosMain/.../voice/IosVoiceTranscriber.kt` — `SFSpeechRecognizer`
  + `AVAudioEngine`; el tap de `inputNode` ya recibe los buffers PCM.
- `composeApp/src/commonMain/.../App.kt` — `onFinalResult` crea la Nota con
  `create(NoteDraft(LOCAL_OWNER_ID, trimmed, track = null))`; borrado de Nota
  recorre `attachmentRepository.getByNoteId` y borra archivos con `fileStore.delete`.
- `composeApp/src/commonMain/.../{NotesListScreen,VoiceCaptureScreen}.kt` —
  `attachmentCounts: Map<String, Int>` y chip único "Imagen"; estado de voz
  `Idle`/`Listening`/`Processing`/`Error`.
- `composeApp/src/commonMain/.../ImageCapture.kt` + actuals Android/iOS —
  `AttachmentFileStore.saveImage`/`delete` con ruta relativa (`filesDir/images/`
  en Android, `Application Support/images/` en iOS); punto de comparación para
  audio.
- `composeApp/src/androidMain/AndroidManifest.xml` — `CAMERA`, `RECORD_AUDIO`,
  `FileProvider`; `iosApp/iosApp/Info.plist` — `NSMicrophoneUsageDescription`,
  `NSSpeechRecognitionUsageDescription`, `NSCameraUsageDescription`.
- `composeApp/src/{androidMain,iosMain}/.../MainActivity.kt`/`MainViewController.kt`
  — construyen base + repos + adaptador de voz y llaman `App(...)`.
- `CONTEXT.md`, `docs/domain-model.md` — `Adjunto` = archivo original (audio de
  voz o imagen); el texto derivado vive en la Nota; edge case "Adjunto sin texto".
- `ARCHITECTURE.md` — capa de Adjuntos, esquema v4, `AttachmentKind` y "Deferred:
  guardar el audio original del dictado como Adjunto".
- `docs/risks-and-open-questions.md` — `¿Los Adjuntos sin texto entran al RAG?`;
  limitación de audio real no inyectable en emulador/simulador.
- `init.sh` — gate no bloqueante (assembleDebug + tests core Android/JVM e iOS +
  link framework iOS).

### Existing Patterns To Follow

- **Adjunto = archivo + metadatos**: nunca BLOB; archivo en almacenamiento privado
  con **ruta relativa** al contenedor (`audio/<uuid>.m4a`), sobrevive a
  reinstalaciones de iOS.
- **Puerto + adaptadores por plataforma** (patrón `DatabaseDriverFactory`/
  `VoiceTranscriber`/`ImageCapture`), sin `expect/actual` cuando hay contexto de
  plataforma en el constructor.
- **Sin DI**: dependencias por parámetro; `App(...)` sigue dueña del estado.
- **Persistencia sólo en `:core`**; la UI/nativo guarda archivos; `:core` no
  conoce filesystem.
- **Tests de datos en `:core`** (`commonTest` + `@Test` por plataforma con drivers
  in-memory); la integración nativa se verifica con smoke manual.
- `init.sh` no bloqueante, sin dev servers ni simuladores.
- Códigos canónicos en español para valores persistidos; identificadores Kotlin en
  inglés. Documentos en español, términos técnicos en inglés.

### Current Gaps

- `AttachmentKind` sólo tiene `IMAGE`; no existe el valor `AUDIO`.
- El dictado no graba archivos: `SpeechRecognizer` (Android) y el tap de
  `AVAudioEngine` (iOS) sólo alimentan al reconocedor.
- No hay escritura de audio a almacenamiento privado ni formato/mime definidos.
- `App` no persiste un Adjunto en el flujo de voz.
- La lista sólo distingue "tiene adjunto" (chip único "Imagen").
- No hay forma determinista de inyectar audio de voz en emulador/simulador
  (limitación heredada de `voice-capture-stt`).

## Technical Approach

### `:core` — valor de enum, sin cambio de esquema

- `AttachmentKind`: agregar `AUDIO("audio")`. `fromCode` sigue fail-fast; el
  mapper no cambia. **No** se toca `Attachment.sq` ni se crean `.sqm`; el esquema
  sigue en v4.
- `AttachmentRepository.create(note.id, AttachmentKind.AUDIO, mimeType, path,
  size)` ya sirve; **no** requiere cambios.
- Tests: extender `verifyAttachmentCrud(driver)` (o agregar un caso) para hacer
  round-trip de un Adjunto `AUDIO` y comprobar `AttachmentKind.fromCode("audio")`
  y el fail-fast de un código desconocido. Se ejecutan en `:core:testDebugUnitTest`
  (Android/JVM) y `:core:iosSimulatorArm64Test` (iOS).

### `:composeApp` — grabar el audio original en la misma sesión de dictado

El audio debe capturarse de la **misma sesión de micrófono** que la
transcripción (no se agrega un segundo puerto que abra otro cliente de micrófono,
porque compite por el recurso). Se extiende el puerto de voz para que la sesión
devuelva también el archivo grabado:

```kotlin
/** Audio original grabado en almacenamiento privado (ruta relativa). */
data class RecordedAudio(
    val relativePath: String,   // p.ej. "audio/<uuid>.m4a"
    val byteSize: Long,
    val mimeType: String,       // "audio/mp4"
)

interface VoiceTranscriber {
    fun start(
        onPartialResult: (String) -> Unit,
        onFinalResult: (String, RecordedAudio?) -> Unit,  // audio null si no se pudo grabar
        onError: (VoiceCaptureError) -> Unit,
    )
    fun stop()
    fun cancel()
}
```

- Cambio de firma de `onFinalResult` (de `(String) -> Unit` a `(String,
  RecordedAudio?) -> Unit`): la sesión entrega el texto **y** el Adjunto ya
  cerrado en un único callback terminal, evitando una carrera entre el resultado
  del STT y el cierre del archivo.
- **Ubicación y formato:** AAC en contenedor MPEG-4 (`.m4a`), `mimeType =
  "audio/mp4"`, bajo el mismo root privado que las imágenes (`filesDir/audio/` en
  Android, `Application Support/audio/` en iOS); ruta relativa `audio/<uuid>.m4a`.
  El root coincide con el que ya resuelve `AttachmentFileStore.delete(relativePath)`,
  así que el borrado de archivos al borrar la Nota sigue funcionando sin cambios.
- **Android (`AndroidVoiceTranscriber`)**: al iniciar la sesión arranca también un
  `MediaRecorder` (`AudioSource.MIC`, `OutputFormat.MPEG_4`, `AudioEncoder.AAC`)
  con salida a `filesDir/audio/<uuid>.m4a`, en paralelo con el `SpeechRecognizer`.
  Al finalizar (o en `stop()`) se cierra el `MediaRecorder` y se devuelve
  `RecordedAudio(path, bytes, "audio/mp4")`; si no puede iniciar/escribir, se
  devuelve `null` y se sigue con la transcripción. `cancel()` cierra y **borra** el
  archivo parcial.
- **iOS (`IosVoiceTranscriber`)**: el tap de `AVAudioEngine.inputNode` ya recibe
  cada `AVAudioPCMBuffer`; además de `appendAudioPCMBuffer`, se escribe el buffer a
  un `AVAudioFile` en `Application Support/audio/<uuid>.m4a`. Al finalizar se cierra
  el archivo y se devuelve `RecordedAudio(...)`; `cancel()` cierra y borra el
  parcial. El detalle exacto de conversión a AAC (settings de `AVAudioFile` y, si
  hace falta, `AVAudioConverter`) se confirma en el build; si la conversión a AAC
  resulta frágil, el fallback documentado es grabar PCM en `.wav`/`.caf` y ajustar
  `mimeType` (ver "Open Questions"/Risks). No se agrega un segundo cliente de
  audio.
- **Permisos:** sin cambios (mismo micrófono que `voice-capture-stt`).

### `:composeApp` — UI y persistencia del Adjunto

- `App` (destino `VoiceCapture`): en `onFinalResult(text, audio)`:
  1. si `text.trim()` es blank → estado de error `NO_MATCH` y, si `audio != null`,
     `fileStore.delete(audio.relativePath)` (no crear Nota ni huérfanos);
  2. si hay texto → `noteRepository.create(NoteDraft(...))`; si `audio != null`,
     `attachmentRepository.create(note.id, AttachmentKind.AUDIO, audio.mimeType,
     audio.relativePath, audio.byteSize)`; si la persistencia del Adjunto falla,
     se borra el archivo y se conserva la Nota (degradación tolerante); luego
     `goToList()` + `reload()`.
  - `cancelVoiceCapture()` sigue llamando `voiceTranscriber.cancel()`, que ahora
    también descarta el audio parcial.
- `NotesListScreen`: reemplazar `attachmentCounts: Map<String, Int>` por un mapa
  por tipo (p. ej. `attachmentKinds: Map<String, Set<AttachmentKind>>`) y renderizar
  chips por tipo ("Imagen" y/o "Audio") con la regla de accesibilidad "no sólo
  color". `App` calcula el mapa desde `attachments` en `LaunchedEffect(refreshKey)`.
- `VoiceCaptureScreen`: sin cambios de contrato; opcionalmente se agrega una línea
  informativa ("Se guarda también el audio original"). No se agrega playback.
- **Borrado**: sin cambios; el cascade de filas ya existe en el repositorio y el
  borrado de archivos (`fileStore.delete`) cubre `audio/...`.

### Dependencias, esquema e `init.sh`

- **Sin dependencias Gradle nuevas esperadas**: `MediaRecorder` es framework
  Android; `AVAudioFile`/`AVAudioEngine`/`AVAudioSession` son platform libs de
  Kotlin/Native. Sin coroutines/Flow nuevos.
- **Sin cambio de esquema:** `schema.version` sigue en **4**; no se agregan `.sqm`
  ni se toca `Attachment.sq`.
- **`init.sh` no cambia**: sigue no bloqueante, sin procesos de larga duración.

## Expected File Changes

### `:core`

- `core/src/commonMain/kotlin/com/playbook/core/model/Attachment.kt` — modificar;
  agregar `AUDIO("audio")`.
- `core/src/commonTest/kotlin/com/playbook/core/repository/AttachmentRepositoryCheck.kt`
  — modificar; round-trip de `AUDIO` + `fromCode("audio")`.
- `core/src/androidUnitTest/.../AttachmentRepositoryAndroidTest.kt` y
  `core/src/iosTest/.../AttachmentRepositoryIosTest.kt` — modificar sólo si hace
  falta ajustar aserciones (usan el helper compartido).

### `:composeApp`

- `composeApp/src/commonMain/kotlin/com/playbook/app/voice/VoiceTranscriber.kt` —
  modificar; `RecordedAudio` + nueva firma de `onFinalResult`.
- `composeApp/src/androidMain/kotlin/com/playbook/app/voice/AndroidVoiceTranscriber.kt`
  — modificar; `MediaRecorder` + cierre/borrado del archivo.
- `composeApp/src/iosMain/kotlin/com/playbook/app/voice/IosVoiceTranscriber.kt` —
  modificar; escribir el tap a `AVAudioFile` + cierre/borrado.
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — modificar;
  persistir el Adjunto `AUDIO` en `onFinalResult`, limpieza y mapa por tipo.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` —
  modificar; chips por tipo ("Imagen"/"Audio").
- `composeApp/src/commonMain/kotlin/com/playbook/app/VoiceCaptureScreen.kt` —
  modificar (opcional, sólo texto informativo).

No se esperan cambios en `core/src/**/Attachment.sq`, `.sqm`, `Note.sq`,
`gradle/libs.versions.toml`, `composeApp/build.gradle.kts`, `init.sh`,
`AndroidManifest.xml`, `Info.plist` ni `iosApp/**`.

### Docs y cierre

- `ARCHITECTURE.md` — modificar; `AttachmentKind.AUDIO`, archivos de audio y la
  captura de audio en la sesión de voz.
- `docs/technical-discovery.md` — modificar; decisiones de grabación (formato,
  ruta, MediaRecorder/AVAudioFile, concurrencia de micrófono).
- `docs/risks-and-open-questions.md` — modificar; riesgo de doble cliente de
  micrófono y límite de audio no inyectable.
- `AGENTS.md` — modificar al cerrar; línea "Próxima feature en cola".
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado y evidencia.

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` (provisional). Se respeta "estados no sólo por color"
  (chips de texto) y la pantalla "Captura de Voz"; no se fijan tokens.
- Screens or states affected: "Lista de Notas" (chip "Audio", coexistencia con
  "Imagen") y "Captura de Voz" (línea informativa opcional; estados sin cambios).
- New design artifact required: no — la entrega de UI/UX sigue pendiente.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — aparece el valor `AUDIO`, el almacenamiento de
  audio y la grabación dentro de la sesión de voz.
- `CONSTRAINTS.md`: **not needed** — no surge una regla MUST/MUST NOT durable
  nueva (las reglas de `:core` vs `:composeApp` y "no BLOB" ya están).
- `AGENTS.md`: **update** — cambia la línea "Próxima feature en cola"; no cambian
  workflow ni gate.
- Other docs: `docs/technical-discovery.md` y `docs/risks-and-open-questions.md` —
  update; `feature_list.json`/`PROGRESS.md` — update al cerrar;
  `CONTEXT.md`/`docs/domain-model.md` — not needed (la spec se alinea con ellos).

## Implementation Plan

1. **Core:** agregar `AttachmentKind.AUDIO("audio")` y extender
   `verifyAttachmentCrud` con el round-trip de `AUDIO` + `fromCode`.
2. **Puerto de voz:** `RecordedAudio` + nueva firma de `onFinalResult` en
   `VoiceTranscriber`.
3. **Android:** `MediaRecorder` en `AndroidVoiceTranscriber` (ruta/cierre/borrado).
4. **iOS:** escribir el tap de `AVAudioEngine` a `AVAudioFile` en
   `IosVoiceTranscriber` (ruta/cierre/borrado).
5. **App/UI:** persistir el Adjunto `AUDIO` en `onFinalResult`, limpieza y mapa por
   tipo; chips "Imagen"/"Audio" en `NotesListScreen`.
6. **Docs durables** y verificación (`:core` Android/JVM e iOS, `assembleDebug`,
   link framework, `xcodebuild`, `./init.sh`), smoke manual Android/iOS y
   evidencia; cierre en `feature_list.json`/`PROGRESS.md`/`AGENTS.md`.

## Implementation Tasks

- [x] Agregar `AttachmentKind.AUDIO("audio")` en `:core`.
- [x] Extender `verifyAttachmentCrud` con el round-trip de `AUDIO` y `fromCode`.
- [x] Definir `RecordedAudio` y cambiar `onFinalResult` a `(String, RecordedAudio?)`.
- [x] Implementar la grabación Android (`MediaRecorder` → `audio/<uuid>.m4a`) y el
      cierre/borrado del parcial.
- [x] Implementar la grabación iOS (tap → `AVAudioFile` AAC en `audio/`) y el
      cierre/borrado del parcial.
- [x] Persistir el Adjunto `AUDIO` en `App.onFinalResult` con limpieza ante fallo.
- [x] Cambiar `NotesListScreen` a chips por tipo ("Imagen"/"Audio").
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y
      `docs/risks-and-open-questions.md`.
- [x] Correr la verificación y el smoke manual Android/iOS; registrar evidencia y
      limitaciones honestas.
- [x] Actualizar `feature_list.json`, `PROGRESS.md` y `AGENTS.md` al cerrar.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL;
  `AttachmentRepositoryAndroidTest` (round-trip `AUDIO`) + tests previos en verde.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → los mismos helpers verdes
  en iOS.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (grabación Android +
  UI).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (grabación iOS + UI); `xcodebuild` del scheme `iosApp` → BUILD SUCCEEDED.
- Confirmar `PlaybookDatabase.Schema.version == 4`, `find core/src -name '*.sqm'`
  sin nuevos y `Attachment.sq` sin cambios.
- `./init.sh` → exit 0; gate no bloqueante actual, **no** inicia dev servers ni
  simuladores.
- Smoke manual **Android** (emulador o dispositivo con micrófono): "Dictar nota" →
  "Grabar" → dictar → "Detener" → Nota con chip "Audio"; verificar con `sqlite3`
  la fila `attachment` (`kind=audio`, `mime_type=audio/mp4`, `file_path` relativa)
  y el archivo en `files/audio/`; cancelar → sin Nota y sin archivo; borrar la Nota
  → fila y archivo eliminados.
- Smoke manual **iOS** (simulador iPhone o dispositivo): build/install/launch;
  dictado → Nota con chip "Audio"; `Library/Application Support/audio/<uuid>.m4a`
  (ruta relativa) y `playbook.db` con `user_version=4` y fila `attachment`
  `kind=audio`;
  cancelar y borrar verifican limpieza de archivo.
- E2E persistente: **no aplica** — no hay harness de UI/nativo y `init.sh` no
  levanta simuladores; el micrófono real no se puede inyectar de forma
  determinista. El path de datos (Adjunto `AUDIO`) queda cubierto por tests de
  `:core` en dos plataformas; la grabación nativa se verifica con smoke manual,
  igual que las features previas.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` con el
  round-trip de `AUDIO` en verde (nombres y conteos).
- Salida de `:composeApp:assembleDebug`,
  `:composeApp:linkDebugFrameworkIosSimulatorArm64` y `xcodebuild` del scheme.
- `PlaybookDatabase.Schema.version == 4`, ausencia de `.sqm` nuevos y
  `Attachment.sq` sin cambios (`git diff` vacío).
- Salida de `./init.sh` (exit 0) sin procesos de larga duración.
- Smoke Android: dictado → Nota con chip "Audio", fila `attachment` `kind=audio`
  con ruta relativa y tamaño de archivo coincidente; cancelar sin huérfanos;
  borrado con limpieza de fila y archivo (capturas/UI dumps + `sqlite3`).
- Smoke iOS: dictado → chip "Audio", `user_version=4` y fila `attachment`
  `kind=audio`; limpieza al cancelar/borrar (capturas/OCR cuando aplique).
- Limitaciones de entorno registradas honestamente si el happy path de audio real
  no es ejecutable (no fabricar evidencia), igual que en `voice-capture-stt`.

## Validator Checklist

- [ ] Alcance respetado: se guarda el audio original como Adjunto `AUDIO`; **sin**
      playback, sin editar adjuntos existentes, sin OCR/IA/GDD.
- [ ] Escenarios de aceptación 1–7 pasan (nativos con smoke honesto).
- [ ] `AttachmentKind.AUDIO("audio")` agregado; sin cambio de esquema
      (`Schema.version == 4`, sin `.sqm` nuevos, `Attachment.sq` intacto).
- [ ] El audio se guarda como archivo en almacenamiento privado con **ruta
      relativa** (`audio/<uuid>.m4a`), mime `audio/mp4`; sin BLOBs.
- [ ] El Adjunto se persiste vía `AttachmentRepository.create(..., AUDIO, ...)` y la
      transcripción sigue en el `body` de la Nota (separación Nota vs Adjunto).
- [ ] Transcripción vacía/cancelar no dejan Nota ni archivo huérfano; fallo de
      grabación degrada a Nota sin Adjunto sin crash.
- [ ] Borrar la Nota elimina la fila `attachment` y el archivo de audio.
- [ ] La lista distingue "Imagen" y "Audio" con chips de texto (no sólo color).
- [ ] Sin DI/`ViewModel`/Flow/coroutines nuevas ni librería de navegación; sin
      dependencias Gradle nuevas; sin permisos nuevos.
- [ ] Evidencia en `feature_list.json`/`PROGRESS.md`; `ARCHITECTURE.md`,
      `docs/technical-discovery.md`, `docs/risks-and-open-questions.md` y
      `AGENTS.md` actualizados.
- [ ] `init.sh` sin cambios, no bloqueante y sin procesos de larga duración.

## Open Questions / Bloqueos

- **Playback diferido:** el texto provisional de `feature_list.json` menciona "se
  puede reproducir"; esta spec lo acota a "persiste y es visible" y difiere el
  reproductor a una feature futura. Si el producto exige playback ahora, la
  feature debe re-scoparse/splitearse.
- **Doble cliente de micrófono en Android:** `MediaRecorder` + `SpeechRecognizer`
  en simultáneo puede fallar en algunos dispositivos (el micrófono se comparte).
  Mitigación: degradación tolerante (Nota sin Adjunto). Si resulta poco confiable,
  la alternativa es un único camino de audio (p. ej. `AudioRecord`/`EXTRA_AUDIO_SOURCE`
  o registrar en una sesión y transcribir después); documentar el hallazgo real.
- **Convertir a AAC en iOS:** escribir el tap como AAC puede requerir
  `AVAudioConverter`; fallback a PCM `.wav`/`.caf` con `mimeType` ajustado, sin
  cambiar el contrato de `RecordedAudio`.
- **Verificación de audio real:** no hay inyección determinista de voz en
  emulador/simulador; el happy path puede requerir dispositivo físico. Se
  documenta sin fabricar evidencia.
- **Root de archivos compartido:** confirmar que la ruta relativa `audio/...`
  resuelve con el mismo root que usa `AttachmentFileStore.delete` en ambas
  plataformas (evitar archivos huérfanos al borrar).
- **Orden de persistencia:** Nota primero, Adjunto después; si el Adjunto falla se
  borra el archivo y se conserva la Nota. Confirmar que este comportamiento es el
  deseado.
