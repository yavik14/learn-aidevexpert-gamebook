# Feature Implementation Spec: Generación de imagen desde texto
## Source Feature
- `id`: `image-generation-from-text`; `area`: `ai`
- `depends_on`: `["ai-fake-real-switch", "image-capture-camera"]` (ambas `accepted`)
- `status`: `not_started` (al planificar); `source`: `feature_list.json`
> **Alcance.** El usuario escribe un prompt en la lista, genera una imagen con
> `ImageClient.generateImage(prompt)`, la previsualiza y, al guardar, la persiste como
> Adjunto `IMAGE` de una **Nota nueva**, reutilizando `PickedImage`, `CreateWithImage`,
> `saveNoteWithImage` y las miniaturas existentes. Se agrega un caso de uso en `:core`
> que valida prompt/`GeneratedImage` y se ajusta el fake para devolver bytes
> **decodificables**.
## Goal
Convertir una descripción de texto en un boceto visual adjunto al GDD sin salir de la
app, con IA fake determinista en gate/tests y el runtime real existente sin cambios.
Entregable: `:core` (caso de uso + fake decodificable + tests Android/JVM e iOS) y
`:composeApp` (diálogo prompt/estado + wiring + smoke Android). Sin esquema nuevo (v8),
deps ni red en el gate.

## Non-Goals
- Editar/retocar/regenerar imágenes o reemplazar/quitar Adjuntos de una Nota guardada.
- OCR/visión, historial de prompts, múltiples imágenes por Nota, caché/redimensionado.
- Cambiar proveedor/build flag/adaptadores reales/`AiRuntime`/`AiRuntimeConfig`/tamaño.
- Rediseño de editor/lista, generación desde el editor de una Nota existente, PDF,
  tts/chat/jev; paridad o smoke iOS (congelado, ADR 0003; sólo debe compilar con fake).

## Job Story
When tengo una idea visual descrita en palabras, I want generar una imagen desde ese
texto y dejarla adjunta a una Nota, so I can conservar el concepto visual junto al
resto del GDD sin buscar referencias.

## Users And Permissions
Autor único local-first, sin auth. Fake por defecto en gate/tests; real sólo si el build
lo habilita (la key nunca se muestra ni se versiona).

## Scope Decisions
1. **Entrada única desde la lista.** El diálogo "Agregar imagen" (FAB "Foto" y CTA del
   estado vacío) suma **"Generar con IA"**. No se agrega en el editor de Nueva nota
   para no perder `body`/`track`; generar siempre crea Nota nueva, sin reemplazar
   Adjuntos de una Nota existente (fuera de alcance).
2. **Prompt no persistido.** Sólo es entrada del modelo: no va al `body` ni hay
   historial; el `body` lo decide el usuario (puede quedar vacío).
3. **Persistencia reutilizada.** `saveNoteWithImage` guarda el archivo, crea la Nota y
   la fila `attachment` (ruta relativa/mime/tamaño) y limpia ante fallo.
4. **Fake decodificable.** `FakeImageClient` devuelve un PNG mínimo válido con un
   chunk `tEXt` (CRC32) que incluye el prompt en Base64 ASCII, antes de `IEND`;
   conserva determinismo, `image/png` y bytes distintos por prompt.

## Acceptance Scenarios

### Scenario 1: generar y previsualizar (fake)
Given la lista, When el usuario toca "Foto" → "Generar con IA", escribe un prompt no
vacío y toca "Generar", Then ve carga, se llama una vez al `ImageClient` y se abre el
editor con preview decodificable, "Quitar imagen" y "Guardar". Nada se persiste aún.

### Scenario 2: guardar la Nota con el Adjunto
Given la preview, When el usuario guarda (con o sin `body`), Then se crea el archivo,
la Nota y una fila `attachment` `IMAGE` con `file_path` relativa (`images/<uuid>.jpg`,
contrato vigente del store), `mime_type` del `GeneratedImage` validado y `byte_size`
real; la lista muestra chip "Imagen" y **miniatura**; al reabrir, todo persiste.

### Scenario 3: prompt vacío, carga y cancelación
Given el diálogo, When el prompt está vacío o sólo espacios, Then "Generar" está
deshabilitado y no se llama al cliente. Durante la carga se muestra "Generando…" sin
doble envío; cancelar/cerrar cancela el job y no deja fila, archivo ni preview.

### Scenario 4: error o descarte antes de guardar
Given el cliente lanza `AiClientException` o devuelve bytes vacíos/`mimeType` vacío o
no `image/*`, When termina la llamada, Then hay error textual accionable, no se navega
ni persiste, se puede reintentar y se registra un diagnóstico sin prompt/bytes/rutas/ids.
Given la preview, When se toca "Cancelar" o "Quitar imagen", Then se descarta en
memoria sin archivos ni filas; si falla el guardado, aplica la limpieza existente.

### Scenario 5: Adjuntos existentes y clasificación
Given una Nota con Adjuntos, When se genera otra imagen desde la lista, Then la Nota
original no se modifica. Generar **no** dispara indexado/re-indexado: una Nota con
`body` vacío sigue sin indexarse y una con texto se indexa por el flujo existente.

### Scenario 6: fake determinista y decodificable
Given `FakeImageClient`, When se genera dos veces el mismo prompt, Then los bytes son
idénticos y empiezan con la firma PNG; prompts distintos difieren. Conserva el contrato
de `ai-client-ports` y permite smoke sin red.

### Scenario 7: sin esquema, sin red, gate honesto
Given la feature, When se inspecciona el diff y corre `CI=true ./init.sh`, Then no hay
`.sqm`/esquema v8 nuevo, deps, cambios de `init.sh`, adaptadores reales ni red; el gate
usa IA fake y termina en `0`.

## Repository Research

### Files Inspected
- `docs/specs/{ai-client-ports,ai-fake-real-switch,image-capture-camera,sketch-thumbnails}.md`
  y `core/.../ai/{AiPorts,FakeAiPorts}.kt`/`openai/{AiRuntime,OpenAiAdapters}.kt` —
  puerto, runtime fake/real, captura/guardado y miniaturas (`imagePaths` +
  `loadThumbnail` ya consumen cualquier `IMAGE`; el fake actual son bytes de texto).
- `core/.../ai/AudioAttachmentTranscription.kt`; `AttachmentFileStore`/repositorio de
  Adjuntos; `composeApp/.../App.kt` (`CreateWithImage`, `saveNoteWithImage`, preview,
  miniaturas, limpieza); pantallas de captura/editor/lista; roots; `DESIGN.md`/`risks`.

### Patterns And Gaps
Casos de uso `suspend` en `:core` con resultado sellado, `AiClientException` y
`Diagnostics` sin datos del usuario; fake determinista + contrato en `commonTest` con
runners Android/iOS; UI "tonta" con estado en `App`, sin DI/`ViewModel`/Flow; gate
Android-only. Gaps: el fake no produce imagen decodificable; falta validación de
prompt/`GeneratedImage`; la lista no ofrece generación y `App` no recibe `ImageClient`.

## Technical Approach

### 1. Caso de uso `:core` (`core/.../ai/TextImageGenerationService.kt`, crear)
```kotlin
sealed interface ImageGenerationResult {
    data class Generated(val image: GeneratedImage) : ImageGenerationResult
    data object EmptyPrompt : ImageGenerationResult
    data object InvalidImage : ImageGenerationResult
    data class Failed(val cause: AiClientException) : ImageGenerationResult
}
class TextImageGenerationService(private val imageClient: ImageClient,
    private val diagnostics: Diagnostics = NoOpDiagnostics) {
    suspend fun generate(prompt: String): ImageGenerationResult
    companion object { const val SUMMARY_FAILED = "image_generation_failed"
        const val SUMMARY_INVALID = "image_generation_invalid" }
}
```
- `prompt.trim()` vacío → `EmptyPrompt` **sin** llamar al cliente; una llamada a
  `generateImage(trimmed)`; `AiClientException` → `DiagnosticSource.IMAGE`/
  `SUMMARY_FAILED` + `Failed`.
- Valida `bytes.isNotEmpty()`, `mimeType.trim().isNotEmpty()` y `image/*` (ignoreCase);
  si falla → `SUMMARY_INVALID` + `InvalidImage`. Devuelve `GeneratedImage(bytes,
  mimeType.trim())`; no recodifica ni re-detecta mime (el adapter real ya lo detecta).
- No persiste, no indexa, no conoce repositorios ni historial.

### 2. Fake decodificable (`core/.../ai/FakeAiPorts.kt`, modificar)
`FakeImageClient` devuelve una constante PNG 1×1 válida más un chunk `tEXt`
(`Comment\0fake image: <Base64 UTF-8 del prompt>`) con CRC32, insertado antes de
`IEND`. Es ancillary estándar y Latin-1 seguro: Android/Skia lo ignoran y la imagen
decodifica. Conserva `image/png`, determinismo y bytes distintos por prompt. No cambiar
`AiPorts`, `AiRuntime`, `OpenAiImageClient` ni el contrato.

### 3. UI y wiring (`:composeApp`)
- `App(...)` recibe `imageClient`; los roots pasan `aiRuntime.imageClient` (iOS fake).
- `remember(imageClient, diagnostics) { TextImageGenerationService(...) }`.
- Estado: `showGenerateImageDialog`, `imagePrompt`, `imageGeneration`
  (`Idle`/`Generating`/`Error(message)`) y `generationJob`.
- "Agregar imagen" suma "Generar con IA" → `ImageGenerationDialog` (nuevo archivo
  `commonMain`): `OutlinedTextField`, "Generando…", error textual, "Generar"
  deshabilitado con prompt vacío o en curso, "Cancelar" cancela el job.
- Éxito: cerrar/limpiar y `destination = CreateWithImage(PickedImage(bytes, mime))`;
  el editor existente muestra preview y permite quitar/guardar sin `body`.
- `EmptyPrompt`/`InvalidImage`/`Failed` → mensajes distintos, sin navegar ni persistir.
  `Throwable` inesperado = `Failed` + diagnóstico. Cancelar/cerrar durante la carga
  cancela `generationJob`; `DisposableEffect` también lo cancela. No se usan
  `ImageCaptureError` ni el diálogo de errores nativos.

### 4. Persistencia, Adjuntos y clasificación
Sin cambios: `saveNoteWithImage` guarda el archivo, crea Nota/Adjunto y limpia ante
fallo. `mime_type` es el del `GeneratedImage` validado; la ruta sigue siendo relativa
(extensión `.jpg` fija aunque el mime sea PNG, comportamiento preexistente aceptado).
Generar no reemplaza Adjuntos ni dispara indexado; `status`/miniatura siguen el flujo
existente. Sin migraciones (v8), deps, `init.sh`, red ni cambios en runtime
real/`iosApp/**`.

## Expected File Changes
- `core/.../ai/TextImageGenerationService.kt` — crear (resultado + validaciones +
  diagnósticos); `FakeAiPorts.kt` — modificar (fake con PNG válido + marcador).
- `core/src/commonTest/.../ai/TextImageGenerationCheck.kt` — crear; runners
  `TextImageGeneration{Android,Ios}Test.kt` en `androidUnitTest`/`iosTest`.
- `composeApp/.../ImageGenerationDialog.kt` — crear (prompt/carga/error);
  `App.kt` — modificar (`imageClient`, estado/job, opción, navegación, limpieza);
  `MainActivity.kt`/`MainViewController.kt` — pasar `aiRuntime.imageClient`.
- `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`,
  `DESIGN.md`, `feature_list.json`, `PROGRESS.md` — actualizar al cerrar.
No se esperan cambios en `AiPorts.kt`, `AiRuntime.kt`, `OpenAiAdapters.kt`,
`AiRuntimeConfig.kt`, esquema/`.sqm`, build files, `init.sh` ni `iosApp/**`.

## Visual Design Impact
UI involved: yes. Fuente `DESIGN.md` (§"Superficies de IA": acción "Generar imagen",
provisional). Afecta lista ("Generar con IA"), diálogo (vacío/carga/error) y editor
Nueva nota (preview existente); estados con texto, no sólo color. Sin artefacto nuevo.

## Durable Documentation Impact
- `ARCHITECTURE.md`: update (caso de uso y consumo de `ImageClient`);
  `CONSTRAINTS.md`: not needed (fake/gate/key ya documentados); `AGENTS.md`: update
  acotado al cerrar (línea de cola).
- Otros: `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`,
  `DESIGN.md`, `feature_list.json`, `PROGRESS.md` al cerrar.

## Implementation Plan
1. Caso de uso + fake; contrato y runners Android/iOS.
2. Cablear `imageClient` en `App`/roots, diálogo, opción y navegación a `CreateWithImage`;
   verificar, correr controles negativos y smoke Android, y actualizar docs/cierre.

## Implementation Tasks
- [x] Implementar `TextImageGenerationService` (prompt, validación, diagnósticos) y
      hacer `FakeImageClient` decodificable sin romper el contrato.
- [x] Escribir `TextImageGenerationCheck` + runners Android/JVM e iOS; agregar
      `imageClient` a `App`/roots.
- [x] Crear `ImageGenerationDialog` y la opción "Generar con IA"; navegar a
      `CreateWithImage` al éxito y manejar carga/cancelación/errores.
- [x] Verificar persistencia/miniatura, limpieza y ausencia de reindexado por imagen;
      correr `CI=true ./init.sh`, controles negativos y smoke Android; registrar evidencia.
- [x] Actualizar docs durables, `feature_list.json`, `PROGRESS.md` y `AGENTS.md` (cierre).

## Verification Plan
- `./gradlew :core:testDebugUnitTest --rerun-tasks` → tests nuevos + suite previa, 0
  failures; `:core:iosSimulatorArm64Test --rerun-tasks` (macOS, no bloqueante) → mismo
  contrato. `:composeApp:assembleDebug` y `linkDebugFrameworkIosSimulatorArm64` → OK.
- `CI=true ./init.sh` → exit 0, gate Android-only, sin red ni procesos de larga
  duración; no requiere cambios. `git diff --stat` → sin esquema/`.sqm`, deps,
  `init.sh`, adaptadores reales ni `iosApp/**`.
- **Controles negativos** (restaurar y re-verificar): (a) permitir prompt vacío → falla
  el test que verifica que el cliente no se invoca; (b) aceptar bytes vacíos/mime no
  imagen → falla `InvalidImage`; (c) fake con bytes de texto → el smoke no muestra
  preview/miniatura; (d) fake no determinista → falla el contrato.
- **Smoke Android (fake, sin red):** lista → "Foto" → "Generar con IA" → prompt →
  "Generando…" → preview → guardar con `body` vacío → tarjeta con chip "Imagen" y
  miniatura; relanzar y verificar persistencia; inspeccionar `playbook.db`
  (`attachment.kind='imagen'`, `mime_type='image/png'`, `file_path` relativa) y el
  archivo privado. Probar prompt vacío, cancelar durante carga y "Quitar
  imagen"/"Cancelar" sin filas ni archivos.
- **E2E persistente:** no aplica (sin harness E2E y `init.sh` no levanta simuladores);
  se cubre con tests de `:core` + smoke Android manual honesto.

## Evidence To Capture
- Tests nuevos (nombres/conteos), `assembleDebug`, link iOS y `CI=true ./init.sh` exit
  0 (fake, sin red); controles negativos reproducidos/restaurados.
- Smoke Android: capturas/UI dumps de prompt, carga, preview, tarjeta con miniatura y
  persistencia; inspección de `attachment`/archivo; prompt vacío, cancelación y
  descarte; `git diff` acotado sin esquema/deps/`init.sh`/runtime real/`iosApp/**`.

## Validator Checklist
- [ ] Alcance: texto → preview → Nota nueva con `IMAGE`; sin edición/reemplazo/OCR/
      historial/multi-imagen/caché/UI extra. Escenarios 1–7 pasan (smoke honesto; iOS
      sólo compila).
- [ ] `GeneratedImage` validado (bytes/mime no vacíos e `image/*`) antes de
      previsualizar/persistir; mime persistido real; persistencia con
      `AttachmentFileStore` + `AttachmentRepository` (ruta relativa/tamaño) y sin
      huérfanos al cancelar/fallar.
- [ ] Generar no reemplaza Adjuntos ni reindexa por imagen; fake determinista/
      decodificable y gate fake sin red.
- [ ] Sin esquema/`.sqm` (v8), deps, `init.sh`, runtime real ni cambios innecesarios en
      `iosApp/**`; docs y `feature_list.json`/`PROGRESS.md` con evidencia; `AGENTS.md`
      sólo la línea de cola; no hay E2E persistente y la spec lo justifica.

## Known Risks
- **PNG fake:** el `tEXt` antes de `IEND` debe llevar CRC32 correcto; el smoke Android
  lo verifica. **Mime vs extensión:** el store usa `.jpg` fijo; el mime real va en la
  columna y la UI decodifica por bytes; no se cambia el store.
- **Proveedor real/iOS:** puede devolver JPEG aunque se pida PNG y no garantiza
  transparencia ni tamaño distinto de `1024x1024`; el adapter detecta mime. Sin smoke
  real (fuera del gate); iOS congelado sólo compila con fake.
