# Feature Implementation Spec: Miniaturas de bocetos en la UI

## Source Feature

- `id`: `sketch-thumbnails`
- `area`: `ui`
- `depends_on`: `["image-capture-camera", "notes-list-ui", "gdd-view"]` (todas `accepted`)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`

> **Alcance.** Los bocetos (Notas con Adjunto `IMAGE`) hoy se muestran con el
> cuerpo y un chip textual "Imagen", pero **sin la imagen**. Esta feature hace
> visible la **miniatura** en las tarjetas de la **lista** y del **GDD**. Para poder
> **leer** los bytes del archivo desde el core (y testearlo), se **reubica** el
> almacén de adjuntos `AttachmentFileStore` de `:composeApp` a `:core` (patrón
> `expect`/`actual` basado en `baseDir`, como `DiagnosticStorage`) y se le agrega
> `read(relativePath): ByteArray?`. El cacheo de miniaturas queda **diferido**.
>
> **Estado del código al planificar (2026-10-19).** La UI ya usa **Géneros**
> (`GenreLabelChip`) y no Etiquetas. `NotesListScreen` recibe
> `attachmentKinds: Map<noteId, Set<AttachmentKind>>`; `GddScreen` recibe
> `attachmentCounts: Map<noteId, Int>` y muestra `status`, Géneros y el chip
> "Imagen". Las props nuevas de miniatura son **aditivas** y no cambian esos
> contratos. `AttachmentRepository.getAll()` devuelve los Adjuntos ordenados por
> `created_at ASC, id ASC`.

## Goal

Que el usuario vea la **miniatura del boceto** en las tarjetas de la lista y del GDD,
de modo que una Nota capturada por cámara/galería se reconozca de un vistazo. La
lectura de bytes vive en un puerto de `:core` testeable cross-platform; el render es
tonto (App provee el mapa de rutas + un loader) y degrada sin romper si el archivo
no está o no decodifica. Entregable: `:core` (store + test) + `:composeApp` (UI +
reubicación del store). Sin dependencias nuevas, sin cambio de esquema, sin cacheo.

## Non-Goals

- **Cacheo / redimensionado de miniaturas** (memoria o disco): diferido
  (`docs/risks-and-open-questions.md`). Se decodifica a tamaño completo y se
  escala en pantalla.
- **OCR** del boceto: descartado del MVP (`docs/adr/0002-ocr-decision.md`).
- Miniaturas en el **editor** (ya previsualiza la imagen recién elegida) ni en
  otras superficies (relacionadas, Q&A).
- Cambiar la **captura/persistencia** de imágenes (formato, path, mime); sólo se
  reubica el store y se agrega `read`.
- Corregir el comportamiento preexistente del chip "Imagen" del GDD (hoy se
  muestra ante cualquier Adjunto, incluido `AUDIO`); esta feature sólo agrega la
  miniatura cuando hay `IMAGE` y no altera los chips.
- Audio (`AttachmentKind.AUDIO`) no tiene miniatura.
- Deps nuevas, esquema, `init.sh`, IA, GDD híbrido/Proyecto.

## Job Story

When abro la lista o el GDD, I want ver la miniatura de cada boceto en su tarjeta,
so I can reconocer mis ideas visuales de un vistazo sin abrir cada Nota.

## Users And Permissions

- Autor (único usuario, single-user local-first): captura bocetos y los ve.
- Desarrollador: corre Gradle/tests. Sin auth, permisos ni secrets nuevos.

## Scope Decisions

> **Validada con el autor (2026-10-16)**.

1. **El store de adjuntos se reubica en `:core`.** `AttachmentFileStore`/
   `StoredImage` pasan de `:composeApp` a `com.playbook.core.storage` como
   `interface` + `expect fun attachmentFileStore(baseDir: String)` con `actual`
   Android/JVM (`java.io.File`) e iOS (`NSFileManager`), siguiendo el precedente de
   `DiagnosticStorage`. Así el `read()` es **testeable cross-platform** en el gate
   Android-only (la nota de `risks` decía "extender el puerto"; se extiende **y** se
   reubica por testabilidad). `:composeApp` conserva `rememberAttachmentFileStore()`
   (ahora sólo calcula el `baseDir` y delega) y `decodeImageBitmap`.
2. **Render tonto y perezoso.** `App` calcula `imagePaths: Map<noteId, relativePath>`
   (sólo `IMAGE`, primer Adjunto en el orden determinista del repositorio) y un
   `loadThumbnail: suspend (String) -> ImageBitmap?`; las pantallas reciben esos
   props **además** de las props actuales (`attachmentKinds`/`attachmentCounts`) y
   cada tarjeta carga la miniatura con `produceState` (sólo al componerse, es
   decir, visible). Sin `ViewModel`/Flow/DI/ImageLoader.
3. **Degradación tolerante.** Si el archivo no existe / no decodifica, no se muestra
   imagen y se conservan los chips existentes (incluido "Imagen"); nunca crashea.

## Acceptance Scenarios

### Scenario 1: `save` → `read` round-trip en `:core`
Given un `AttachmentFileStore` sobre un `baseDir` vacío, When
`saveImage(bytes, "image/jpeg")` y luego `read(stored.relativePath)`, Then
`stored.relativePath` empieza con `images/`, `stored.byteSize == bytes.size` y
`read` devuelve **exactamente** `bytes`.

### Scenario 2: `read` de una ruta inexistente
Given un store sin el archivo, When `read("images/no-existe.jpg")`, Then devuelve
`null` (sin lanzar).

### Scenario 3: `delete`
Given un Adjunto guardado, When `delete(relativePath)`, Then `read` devuelve `null`
y un segundo `delete` devuelve `false`.

### Scenario 4: ruta insegura (path traversal)
Given un store, When `read("../secreto")` / `delete("../../x")`, Then devuelve
`null`/`false` (no escapa del `baseDir`).

### Scenario 5: la reubicación preserva rutas y comportamiento
Given rutas relativas ya persistidas (`images/<uuid>.jpg`), When `:composeApp` usa
el store de `:core` con el `baseDir` del contenedor, Then `saveImage`/`delete`
siguen con el mismo contrato y las rutas guardadas en la base siguen leyéndose
(sin migración, sin tocar la tabla `attachment`).

### Scenario 6: miniatura en la lista
Given una Nota con Adjunto `IMAGE`, When se renderiza su tarjeta en `NotesListScreen`,
Then muestra la miniatura decodificada (recorte `rounded.md`) además del cuerpo y
los chips.

### Scenario 7: miniatura en el GDD
Given una Nota con Adjunto `IMAGE` en una sección, When se renderiza `GddScreen`,
Then su tarjeta muestra la miniatura además de `status`/Géneros (`GenreLabelChip`)
y el chip "Imagen".

### Scenario 8: sin imagen no hay miniatura
Given una Nota sin Adjunto `IMAGE` (o sólo `AUDIO`), When se renderiza su tarjeta,
Then no aparece miniatura (no hay hueco vacío) y el resto de la tarjeta no cambia.

### Scenario 9: degradación sin archivo/decodificación
Given un Adjunto `IMAGE` cuyo archivo falta o cuyos bytes no decodifican, When se
renderiza la tarjeta, Then no aparece miniatura, se conservan los chips existentes
(incluido "Imagen") y la pantalla **no** crashea.

### Scenario 10: gate honesto
Given los escenarios 1–9, When corren `:core:testDebugUnitTest`,
`:composeApp:assembleDebug` y `./init.sh`, Then todo pasa en Android/JVM, sin red ni
deps nuevas, y `./init.sh` termina en `0`. En macOS, adicionalmente,
`:composeApp:linkDebugFrameworkIosSimulatorArm64` compila (no bloqueante para el
gate Android-only).

## Repository Research

### Files Inspected

- `feature_list.json` (`sketch-thumbnails`), `docs/risks-and-open-questions.md`
  (§"Miniaturas de imagen": extender `AttachmentFileStore` con `read` +
  `decodeImageBitmap`; cacheo diferido), `DESIGN.md` (§tarjeta de Nota con miniatura,
  recorte `rounded.md`).
- `composeApp/.../ImageCapture.kt` — `PickedImage`, `StoredImage`,
  `AttachmentFileStore` (`saveImage`/`delete`), `rememberAttachmentFileStore()`
  (`expect`), `decodeImageBitmap()` (`expect`).
- `composeApp/.../ImageCapture.android.kt` (`AndroidAttachmentFileStore` sobre
  `context.filesDir`) e `ImageCapture.ios.kt` (`IosAttachmentFileStore` sobre
  Application Support; helpers `NSData`↔`ByteArray`).
- `core/.../diagnostics/{FileDiagnostics,Diagnostics}.kt`,
  `core/.../diagnostics/Diagnostics.{android,ios}.kt` — **precedente** de storage de
  archivos en `:core` (`interface` + `expect fun diagnosticStorage(path)` + actuals
  `java.io.File`/`NSFileManager`) y su test `verifyDiagnosticStorage` +
  `DiagnosticsStorage{Android,Ios}Test`.
- `composeApp/.../App.kt` — carga `attachments`, calcula
  `attachmentKinds: Map<noteId, Set<AttachmentKind>>` y
  `attachmentCounts: Map<noteId, Int>`, y crea `fileStore` con
  `rememberAttachmentFileStore()`.
- `composeApp/.../NotesListScreen.kt` — `NotesListScreen(notes, attachmentKinds, …)`;
  `NoteCard(note, attachmentKinds, onClick)` renderiza cuerpo + chips de Adjunto,
  `track`, `status` y Géneros.
- `composeApp/.../GddScreen.kt` — `GddScreen(notes, attachmentCounts, …)`;
  `GddNoteCard(note, hasAttachment, onClick)` renderiza cuerpo + `status`, chip
  "Imagen" y Géneros (`GenreLabelChip`).
- `core/src/commonTest/.../repository/AttachmentRepositoryCheck.kt` — metadatos del
  Adjunto (ruta relativa `images/…`), sin bytes. `selectAllAttachments` ordena por
  `created_at ASC, id ASC`.
- `DESIGN.md` — tarjeta de Nota con miniatura y recorte `rounded.md` (ya vigente).

### Existing Patterns To Follow

- Storage de archivos en `:core` con `interface` + `expect fun` basada en path/`baseDir`
  y `actual` por plataforma (como `DiagnosticStorage`).
- Tests compartidos en `commonTest` (`verify…(storage)`) corridos por runners
  Android/JVM (temp dir) e iOS; el gate es **Android-only** pero iOS debe compilar.
- UI Compose "tonta": estado con `remember`/`produceState`, coroutines con
  `rememberCoroutineScope`; sin `ViewModel`/Flow/DI/ImageLoader.
- `LabelChip`/chips textuales (no sólo color) y `rounded.md` de `DESIGN.md`.

### Current Gaps

- `AttachmentFileStore` no puede **leer** bytes (sólo `saveImage`/`delete`) y vive en
  `:composeApp` (sin infra de tests).
- La UI no tiene acceso a la ruta del Adjunto `IMAGE` por Nota (sólo `attachmentKinds`).
- No hay componente de miniatura.

## Technical Approach

### 1. Store en `:core` (`core/.../storage/`, crear)

```kotlin
package com.playbook.core.storage

/** Imagen persistida: ruta relativa al contenedor + tamaño. */
data class StoredImage(val relativePath: String, val byteSize: Long)

interface AttachmentFileStore {
    fun saveImage(bytes: ByteArray, mimeType: String): StoredImage
    /** Bytes del archivo en [relativePath], o `null` si no existe / no se puede leer. */
    fun read(relativePath: String): ByteArray?
    fun delete(relativePath: String): Boolean
}

/** Store anclado a [baseDir] (contenedor privado de la app). */
expect fun attachmentFileStore(baseDir: String): AttachmentFileStore
```

- `androidMain/.../storage/AttachmentFileStore.android.kt`: `java.io.File(baseDir)`;
  `saveImage` escribe `images/<uuid>.jpg` (conserva el contrato actual),
  `read` = `readBytes()` si `isFile`, `delete` = `File.delete()`.
- `iosMain/.../storage/AttachmentFileStore.ios.kt`: `NSFileManager`/`NSUUID`/
  `NSData` (helpers `ByteArray`↔`NSData` movidos desde `ImageCapture.ios.kt`);
  `read` = `NSData.dataWithContentsOfFile`; `delete` =
  `removeItemAtPath(_:error:)` devolviendo `true/false`.
- **Path traversal**: `read`/`delete` normalizan la ruta y verifican que quede
  dentro de `baseDir` (prefijo con separador final); si no, `null`/`false`. La
  implementación iOS evita APIs de canonicalización exóticas: normaliza `..` y
  separadores antes del chequeo de prefijo.

### 2. `:composeApp` (reubicación + loader)

- `ImageCapture.kt`: eliminar `StoredImage`/`AttachmentFileStore` (ahora en `:core`);
  `rememberAttachmentFileStore()` (**expect**) sigue existiendo pero devuelve el store
  de `:core`.
- `ImageCapture.android.kt`: `actual` → `attachmentFileStore(context.filesDir.absolutePath)`
  (borra `AndroidAttachmentFileStore`).
- `ImageCapture.ios.kt`: `actual` →
  `attachmentFileStore(applicationSupportDir())` (borra `IosAttachmentFileStore`;
  mueve `toNSData` a `:core`; mantiene el `toByteArray` del picker).
- `App.kt`: calcula `imagePaths` sólo con Adjuntos `IMAGE`, quedándose con el
  **primero** por Nota en el orden determinista del repositorio:
  ```kotlin
  val imagePaths = attachments
      .filter { it.kind == AttachmentKind.IMAGE }
      .distinctBy { it.noteId }
      .associate { it.noteId to it.filePath }
  val loadThumbnail: suspend (String) -> ImageBitmap? =
      { path -> fileStore.read(path)?.let(::decodeImageBitmap) }
  ```
  Los pasa a `NotesListScreen` y `GddScreen` sin quitar las props actuales.

### 3. UI (miniaturas)

- Nuevo composable `NoteThumbnail(path, loadThumbnail)`:
  `val bitmap by produceState<ImageBitmap?>(null, key1 = path) { value =
  withContext(Dispatchers.Default) { loadThumbnail(path) } }`; si no es `null`,
  `Column { Image(bitmap, contentDescription = "Miniatura del boceto",
  modifier = Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)),
  contentScale = ContentScale.Crop); Spacer(8.dp) }`. El espaciado vive dentro
  del composable: no hay hueco si la imagen falta o no decodifica.
- `NotesListScreen`/`GddScreen`: props nuevas `imagePaths` + `loadThumbnail`; la
  tarjeta renderiza `NoteThumbnail` sólo si `imagePaths[note.id] != null`. En la
  lista se conserva `attachmentKinds`; en el GDD se conserva `attachmentCounts`.
  Chips, Géneros y cuerpo sin cambios. Si la miniatura no decodifica, el
  composable no emite `Image` (no deja hueco).

## Expected File Changes

- `core/src/commonMain/kotlin/com/playbook/core/storage/AttachmentFileStore.kt` —
  **crear**; interface + `StoredImage` + `expect fun`.
- `core/src/androidMain/kotlin/com/playbook/core/storage/AttachmentFileStore.android.kt`
  — **crear**; actual `java.io.File`.
- `core/src/iosMain/kotlin/com/playbook/core/storage/AttachmentFileStore.ios.kt` —
  **crear**; actual `NSFileManager` + helpers `NSData`.
- `core/src/commonTest/kotlin/com/playbook/core/storage/AttachmentFileStoreCheck.kt`
  — **crear**; escenarios 1–4.
- `core/src/androidUnitTest/.../storage/AttachmentFileStoreAndroidTest.kt` y
  `core/src/iosTest/.../storage/AttachmentFileStoreIosTest.kt` — **crear**; runners.
- `composeApp/.../ImageCapture.kt`, `ImageCapture.android.kt`, `ImageCapture.ios.kt`
  — modificar; usan el store de `:core` (`read` disponible).
- `composeApp/.../NoteThumbnail.kt` — **crear**; composable perezoso + degradación.
- `composeApp/.../{App,NotesListScreen,GddScreen}.kt` — modificar; `imagePaths` +
  `loadThumbnail` + render de miniatura.
- `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`
  — **update** (ver abajo).
- `AGENTS.md` — update **acotado** (sólo "próxima feature en cola").
- `feature_list.json`, `PROGRESS.md` — update al cerrar.

No se esperan cambios en `Attachment.kt`/`AttachmentRepository`, el esquema/`.sqm`,
`core/build.gradle.kts`, `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`,
`init.sh` ni `iosApp/**`.

## Visual Design Impact

- UI involved: yes. Design source: `DESIGN.md` (provisional).
- Screens or states affected: `NotesListScreen` (tarjeta con miniatura),
  `GddScreen` (tarjeta con miniatura).
- New design artifact required: no — `DESIGN.md` ya define "tarjeta con miniatura"
  y "miniaturas con recorte `rounded.md`". El tamaño (72dp) es una concreción menor.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — el almacén de adjuntos pasa a `:core`
  (`AttachmentFileStore` + `read`) y la UI de miniatura (loader + degradación).
- `CONSTRAINTS.md`: **not needed** — no surge una regla MUST/MUST NOT durable nueva.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` **update** ("Thumbnail Decisions
  (sketch-thumbnails)": store en core, `read`, render perezoso, cacheo diferido);
  `docs/risks-and-open-questions.md` **update** (§"Miniaturas de imagen": el store
  queda en `:core` con `read`; el cacheo diferido sigue abierto). `DESIGN.md`
  **not needed** (ya cubre la miniatura). `feature_list.json`/`PROGRESS.md`
  **update** al cerrar.

## Implementation Plan

1. Crear el store en `:core` (`AttachmentFileStore` + actuals Android/iOS).
2. Escribir `AttachmentFileStoreCheck` + runners Android/iOS (escenarios 1–4).
3. Reubicar el uso en `:composeApp` (`ImageCapture.*`, `App`) al store de `:core`.
4. Agregar `imagePaths`/`loadThumbnail` y `NoteThumbnail` en lista y GDD.
5. Correr verificación + controles negativos, actualizar docs y cerrar.

## Implementation Tasks

- [ ] `core/.../storage/AttachmentFileStore.kt` (interface + `StoredImage` + `expect`).
- [ ] Actuals `AttachmentFileStore.{android,ios}.kt` (con guard de path traversal).
- [ ] `AttachmentFileStoreCheck` + runners Android/iOS.
- [ ] `:composeApp`: `ImageCapture.{kt,android,ios}` usan el store de `:core`.
- [ ] `App`: `imagePaths` + `loadThumbnail`.
- [ ] `NoteThumbnail` + integración en `NotesListScreen` y `GddScreen`.
- [ ] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md`,
      `docs/risks-and-open-questions.md`; `AGENTS.md` (cola).
- [ ] Correr verificación + controles negativos y registrar evidencia.
- [ ] Actualizar `feature_list.json` y `PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → `AttachmentFileStoreAndroidTest`
  + `AttachmentRepositoryAndroidTest` + resto del core en verde, 0 failures.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (UI + store de `:core`).
- `./init.sh` → exit 0; gate Android-only, sin dev servers ni red.
- `git status`/`git diff` → `attachment` (esquema) sin cambios; sin `.sqm` ni deps.
- **iOS (no bloqueante, macOS):** `./gradlew :core:iosSimulatorArm64Test` y
  `:composeApp:linkDebugFrameworkIosSimulatorArm64` compilan. El `actual` iOS del
  store debe existir aunque el gate no lo ejecute.
- **Smoke manual Android** (emulador): crear una Nota con imagen (cámara/galería) →
  la **lista** y el **GDD** muestran la miniatura; una Nota sólo-texto/sólo-audio no
  muestra miniatura; borrar el archivo del Adjunto → la tarjeta sigue sin crashear.
- **Controles negativos** (restaurar byte-idéntico y re-verificar en verde):
  (a) `read` devolviendo siempre `null` → falla el Scenario 1;
  (b) `delete` devolviendo `true` sin borrar → falla el Scenario 3;
  (c) quitar el guard de path traversal → falla el Scenario 4.
- E2E persistente: **no aplica** (sin harness E2E; `init.sh` no levanta simuladores).
  Cobertura: test del store en `:core` + compilación de la app + smoke manual Android.

## Risks and Limits

- Se decodifica la imagen a tamaño completo y se escala en pantalla; con bocetos
  grandes puede haber picos de memoria. El cacheo/redimensionado queda diferido
  (non-goal), así que se declara como límite, no se optimiza aquí.
- No hay test de UI/E2E automatizado: la miniatura visible se verifica por
  compilación + smoke manual Android; si no hay `adb`/emulador, se registra
  honestamente como no ejecutado.
- Varios Adjuntos `IMAGE` por Nota son posibles en el modelo pero no en el flujo
  de producto; se muestra el primero según `created_at ASC, id ASC`.
- El `actual` iOS no se ejecuta en el gate (iOS congelado); sólo se exige que
  compile/linkee.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` con `AttachmentFileStoreAndroidTest` + resto en
  verde (nombres y conteos) y de `:composeApp:assembleDebug`.
- `git diff` → esquema/`attachment` sin cambios; sin `.sqm` ni deps nuevas.
- Controles negativos (a)/(b)/(c) reproducidos y restaurados, con el test que falla.
- Smoke Android: capturas/UI dumps de la lista y del GDD con miniatura visible, y de
  una Nota sin imagen (sin miniatura).
- `./init.sh` → exit 0, sin procesos de larga duración ni red.

## Validator Checklist

- [ ] Alcance respetado: miniatura en lista y GDD + `read()` en `:core`; **sin**
      cacheo/redimensionado, OCR, editor ni cambios de captura/esquema/deps.
- [ ] Escenarios de aceptación 1–10 pasan.
- [ ] `AttachmentFileStore`/`StoredImage` reubicados en `:core` con actuals
      Android/iOS; `read` con guard de path traversal.
- [ ] `saveImage`/`delete` preservan el contrato previo y las **rutas relativas**
      guardadas (`images/<uuid>.jpg`); la tabla `attachment` no cambia.
- [ ] La UI degrada sin imagen/decodificación y no cambia el resto de la tarjeta.
- [ ] `:composeApp` compila; `init.sh` sin cambios; iOS sigue compilando.
- [ ] `ARCHITECTURE.md`, `docs/technical-discovery.md` y
      `docs/risks-and-open-questions.md` actualizados; `AGENTS.md` sólo la cola.
- [ ] `feature_list.json` y `PROGRESS.md` actualizados con la evidencia.
- [ ] No hay E2E persistente y la spec lo justifica.
