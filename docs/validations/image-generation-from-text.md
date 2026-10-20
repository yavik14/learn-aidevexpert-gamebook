# Validación: image-generation-from-text

- **Feature:** `image-generation-from-text`
- **Spec:** `docs/specs/image-generation-from-text.md`
- **Worktree/rama:** `worktree/image-generation-from-text` / `feature/image-generation-from-text`
- **Base de validación:** `dc74959` (`docs: acepta transcribe-audio-attachment`).
  En el cierre posterior se rebasó sobre `origin/develop` `42e6c60`
  (`ai-suggest-classification`) y se re-ejecutó la verificación; el veredicto
  `accept` sigue vigente.
- **Estado previo:** `passing` (implementada y auto-verificada en Session 081)
- **Fecha:** 2026-10-20
- **Veredicto:** **`accept`** (validación independiente)

## Alcance revisado

Se revisaron la spec completa, el diff actual contra la base, los archivos
nuevos/modificados, `feature_list.json`, `PROGRESS.md` (Session 081), `AGENTS.md`,
`ARCHITECTURE.md`, `DESIGN.md`, `docs/technical-discovery.md`,
`docs/risks-and-open-questions.md` y la evidencia de smoke declarada en
`composeApp/build/smoke-evidence/image-generation-from-text/`. El validador **no
implementó, no commiteó, no rebaseó y no pusheó** nada. El único artefacto nuevo
de esta corrida es este documento.

Archivos del diff (11 modificados + 6 nuevos/untracked):

- `:core`: `ai/TextImageGenerationService.kt` (nuevo), `ai/FakeAiPorts.kt`
  (PNG fake decodificable).
- `:core` tests: `TextImageGenerationCheck.kt` (common),
  `TextImageGenerationAndroidTest.kt`, `TextImageGenerationIosTest.kt`.
- `:composeApp`: `ImageGenerationDialog.kt` (nuevo), `App.kt`, `MainActivity.kt`,
  `MainViewController.kt`.
- Docs/estado: `docs/specs/image-generation-from-text.md` (nuevo),
  `ARCHITECTURE.md`, `DESIGN.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md`, `AGENTS.md`, `feature_list.json`,
  `PROGRESS.md`.

## Verificación re-ejecutada

| Check | Resultado |
| --- | --- |
| `ANDROID_HOME="$HOME/Library/Android/sdk" CI=true ./init.sh` | **exit 0**; `:composeApp:assembleDebug` + `:core:testDebugUnitTest`, `BUILD SUCCESSFUL` (IA fake, sin red ni key). |
| `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks` | **BUILD SUCCESSFUL** (20 tasks ejecutadas). Android/JVM: **32 suites / 92 tests / 0 failures / 0 errors / 0 skipped**. iOS: **32 / 92 / 0 / 0 / 0**. Incluye `TextImageGeneration{Android,Ios}Test` 1/1 y `AiPorts{Android,Ios}Test` 6/6. |
| `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` | **BUILD SUCCESSFUL** (59 tasks ejecutadas), APK debug y framework iOS Simulator generados. |
| Post-rebase sobre `origin/develop` `42e6c60` | `CI=true ./init.sh` exit 0; `:core` 34 suites/99 tests/0 failures en Android/JVM e iOS; `assembleDebug` + link iOS OK; smoke Android re-ejecutado (2 Notas/2 Adjuntos; `kind=imagen`, `mime_type=image/png`, ruta relativa). |
| Controles negativos (a) prompt vacío y (b) validación de imagen | **Reproducidos de forma independiente**: romper el guard de prompt vacío y luego la validación de `GeneratedImage` hace fallar `TextImageGenerationAndroidTest`; ambos archivos se restauraron (`diff -q` idéntico). Tras restaurar, la suite completa volvió a 32/92/0 en Android e iOS. |
| Controles negativos (c) fake de texto y (d) fake no determinista | No se re-ejecutaron (el (c) exige smoke y el (d) ya está registrado por el implementer); se inspeccionó la evidencia de (c) y se revisó el código de `FakeImageClient`, que es puramente determinista. |
| PNG fake | Verificado de forma independiente con un parser propio y `zlib`: firma PNG, chunks `IHDR`/`IDAT`/`tEXt`/`IEND`, **CRC32 válido** en todos, `IHDR` 1×1 RGBA, pixel rojo opaco, `tEXt` con `Comment\0fake image: <Base64 UTF-8>` y longitud de datos correcta antes de `IEND`. `sips` decodifica el resultado como PNG 1×1. Para el prompt `un jefe final` el archivo mide **122 bytes**, el mismo `byte_size` reportado en el smoke. |
| Evidencia visual | Inspeccionada: `preview.png` muestra el editor "Nueva nota" con la imagen roja 1×1 escalada y "Quitar imagen"; `list-thumbnail.png` muestra la tarjeta con chip **"Imagen"**, **"Sin track"**, estado `capturada` y miniatura roja; `negative-control-text-fake.png` muestra "No se pudo previsualizar la imagen." con el fake de texto. Las marcas de tiempo (12:27/12:29) y el contenido son consistentes con el flujo declarado. |
| Smoke Android | **No re-ejecutado por el validador** para no interferir con las otras sesiones/worktrees que usan emuladores. Se inspeccionó la evidencia, se validó el PNG fake de forma independiente y se revisaron las rutas de UI/persistencia; la evidencia es consistente con el código. |

## Cumplimiento de la spec

| Escenario | Estado |
| --- | --- |
| 1. Generar y previsualizar (fake) | Cumplido. Entrada "Foto" → "Generar con IA" en la lista; `ImageGenerationDialog` con prompt; "Generar" deshabilitado con prompt vacío; una llamada a `ImageClient.generateImage`; éxito → `CreateWithImage(PickedImage)` sin persistir. Test del core + evidencia de preview. |
| 2. Guardar la Nota con el Adjunto | Cumplido. `onSave` de `CreateWithImage` llama a `saveNoteWithImage`; `AttachmentFileStore.saveImage` genera ruta relativa `images/<uuid>.jpg`, `byte_size` real y la fila `attachment` usa `image.mimeType` (`image/png`). Evidencia: chip "Imagen", miniatura, persistencia tras relanzar y `attachment`/`note.body=''`/`status=capturada`. |
| 3. Prompt vacío, carga y cancelación | Cumplido. Test: prompt en blanco → `EmptyPrompt` y `calls == 0`; UI deshabilita "Generar" con `isNotBlank`; durante `Generating` el botón queda deshabilitado y se muestra "Generando…"; `onCancel`/`onDismissRequest` cancelan el job y limpian estado. El fake resuelve al instante, así que la carga/cancelación en vuelo se cubren por código y no visualmente. |
| 4. Error o descarte antes de guardar | Cumplido. Tests: bytes vacíos, mime vacío y mime no `image/*` → `InvalidImage` con diagnóstico `IMAGE`/`image_generation_invalid`; `AiClientException` → `Failed` con `image_generation_failed` y `detail == null`. La UI muestra mensajes textuales distintos, no navega ni persiste y permite reintentar; `Throwable` inesperado → `Failed` + diagnóstico sin datos. "Cancelar"/"Quitar imagen" descartan en memoria; el guardado reutiliza la limpieza existente. |
| 5. Adjuntos existentes y clasificación | Cumplido. Generar siempre abre `CreateWithImage` de Nota nueva; no modifica Adjuntos existentes. `indexBestEffort` sigue cortando con `body.isBlank()`, por lo que una Nota con `body` vacío no se indexa; con texto se indexa por el flujo existente. No hay indexado ni re-indexado por la imagen. |
| 6. Fake determinista y decodificable | Cumplido. Mismo prompt → bytes idénticos; prompts distintos → bytes distintos; firma PNG; `image/png`; contrato `ImageClient`/`ImagePorts` intacto. Verificado además por decodificación independiente del PNG. |
| 7. Sin esquema, sin red, gate honesto | Cumplido. `git diff` sin `.sqm`, esquema v8, dependencias, `init.sh`, `AiRuntime`, `OpenAiAdapters`/`OpenAiImageClient` ni `iosApp/**`; `CI=true ./init.sh` exit 0 con IA fake y sin red. |

## Hallazgos

### Critical / High

Ninguno.

### Medium

Ninguno.

### Low (no bloqueantes)

1. **Identidad de `generationJob` en cancelación seguida de una nueva
   generación.** En `App.kt`, el `finally` del job hace `generationJob = null`
   sin comprobar que siga siendo el job actual. Si se cancela un job y se abre
   otra generación antes de que corra su `finally` (ventana muy estrecha; el fake
   resuelve al instante), el `finally` viejo podría limpiar la referencia del job
   nuevo y una cancelación posterior no lo cortaría. No se observó en el smoke ni
   afecta al flujo normal. Mejora opcional: capturar el job lanzado y limpiar
   sólo si sigue siendo el actual (o usar un token de generación).
2. **Sin smoke independiente del validador.** Se inspeccionó y validó la
   evidencia, pero no se reinstaló el APK ni se repitió el flujo en emulador para
   evitar interferir con otras sesiones. El riesgo residual es bajo porque el
   camino de UI está cubierto por revisión, los tests del core pasan y el PNG
   fake se decodificó de forma independiente.
3. **Rama 1 commit detrás de `origin/develop`.** `origin/develop` avanzó con
   `42e6c60` (`ai-suggest-classification`). El rebase se ejecutó en el cierre;
   los conflictos en `App.kt`/docs se resolvieron conservando ambas features y
   el contrato de esta feature no cambió.

## Seguridad y privacidad

- No hay secrets, API keys, keystores ni URLs privadas en el diff.
- Los diagnósticos usan `DiagnosticSource.IMAGE` con resúmenes estables
  (`image_generation_failed`/`image_generation_invalid`) y **sin `detail`**; no
  registran prompt, bytes, rutas ni ids.
- El prompt no se persiste en `body`, historial ni metadatos. El fake lo incluye
  en el chunk `tEXt` por contrato de la spec (sólo fake, para determinismo); no
  se muestra en logs ni en la UI.
- La persistencia reutiliza `saveNoteWithImage`: ruta relativa generada por la
  app (`images/<uuid>.jpg`), tamaño real y mime del `GeneratedImage` validado.
  No se agregan permisos, dependencias, red en el gate ni cambios de esquema.
- El adaptador real y `AiRuntime` no se tocaron; el gate/tests usan IA fake.

## Documentación y estado durable

- `feature_list.json`: `passing` con evidencia y notas alineadas con la spec;
  los conteos (40 `accepted`, 7 `not_started`, 1 `passing`, 1 `blocked`, 49
  total) coinciden con `PROGRESS.md`.
- `PROGRESS.md`: Session 081 registra implementación, verificación, smoke,
  controles negativos y riesgos.
- `ARCHITECTURE.md`, `DESIGN.md`, `docs/technical-discovery.md` y
  `docs/risks-and-open-questions.md` describen el caso de uso, el fake, la UI, la
  persistencia y los límites sin contradecir el código.
- `AGENTS.md` se mantiene corto y actualizado con la línea de cola.
- La evidencia de smoke declarada existe y es consistente con lo verificado.

## Recomendación de estado

`passing` → **`accepted`**. La sesión integradora debe persistir el estado y la
evidencia en `feature_list.json`/`PROGRESS.md`, rebasar sobre `origin/develop`
(`42e6c60`); los conflictos en `App.kt`/docs se resolvieron conservando ambas
features. No se requiere reparación de la feature para aceptarla.

## Reverificación (2026-10-20, base `0cffd1e`)

Reverificación independiente sobre `origin/develop` = `0cffd1e` (feature
squasheada sobre `42e6c60`; árbol **idéntico** a
`origin/feature/image-generation-from-text` `d9d8aec`, sin drift). Veredicto
confirmado: **`accept`**.

| Check re-ejecutado | Resultado |
| --- | --- |
| `ANDROID_HOME=… CI=true ./init.sh` | **exit 0**; `:composeApp:assembleDebug` + `:core:testDebugUnitTest` OK (IA fake, sin red). |
| `:core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks` | **BUILD SUCCESSFUL**; Android/JVM e iOS **34 suites/99 tests/0 failures/0 errores/0 skipped**; `TextImageGeneration{Android,Ios}Test` 1/1 y `AiPorts{Android,Ios}Test` 6/6. |
| `:composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` | **BUILD SUCCESSFUL** (iOS no bloqueante). |
| Controles negativos (a) prompt vacío y (b) validación de imagen | Reproducidos de forma independiente: ambos rompen `TextImageGenerationAndroidTest` (exit 1). El archivo se restauró byte-idéntico (sha256 `b2b92c5f…`) y la suite filtrada volvió a verde. |
| PNG fake | Parseado con `zlib`/CRC32 propio: `IHDR`/`IDAT`/`tEXt`/`IEND` con CRC válido, 1×1 RGBA rojo opaco, 122 bytes para `un jefe final`; `sips` decodifica 1×1 PNG. |
| Smoke Android | **Re-ejecutado** en `emulator-5556` (API 35, headless, fake): "Foto" → "Generar con IA" → prompt → preview → Guardar con `body` vacío → tercera Nota con chip "Imagen" + miniatura; tras force-stop/relanzar persisten **3 Notas/3 Adjuntos/3 miniaturas**. Adjunto nuevo: `kind=imagen`, `mime_type=image/png`, `file_path=images/<uuid>.jpg` relativa, `byte_size=130` = archivo real; `note.body=''`, `status=capturada`. "Quitar imagen" no creó fila ni archivo (3/3 antes y después). Con prompt vacío el botón "Generar" queda `enabled=false`. |
| Alcance | `git diff 0cffd1e^..0cffd1e` sin `.sqm`/esquema v8, deps, `init.sh`, `AiRuntime`/`OpenAiAdapters` reales ni `iosApp/**`. |

`composeApp/build/smoke-evidence/image-generation-from-text/` no existe en este
checkout (artefactos de `build/` efímeros); se re-ejecutó el smoke en su lugar.
No se detectaron cambios materiales respecto de la validación previa: los
conteos 34/99 y el comportamiento coinciden. El hallazgo low de identidad de
`generationJob` sigue vigente como mejora no bloqueante; la ausencia de smoke
independiente quedó cubierta por esta corrida.
