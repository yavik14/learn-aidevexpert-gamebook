# Validación independiente — main-screen-playbook-shell

- Fecha: 2026-10-20
- Rol: `feature-validator` (independiente del implementer)
- Feature: `main-screen-playbook-shell`
- Spec: `docs/specs/main-screen-playbook-shell.md`
- Worktree validado: `worktree/main-screen-playbook-shell`, rama
  `feature/main-screen-playbook-shell`, **working tree sin commitear** sobre
  `origin/develop` `763a357`.
- Estado reclamado al iniciar: `passing`. Recomendación: **`accepted`**.
- Veredicto: **accept** (sin hallazgos Critical/High/Medium).

Nota de coordinación: durante la validación `origin/develop` avanzó a `6a89317`
(`save-answer-as-note`). El diff validado es el working tree sobre `763a357`; la
rama todavía no está rebasada. Hay solapamiento de archivos (`App.kt`, docs y
`feature_list.json`) pero no de las líneas del shell. Antes de mergear
corresponde rebase y re-verificación (ver “Próximos pasos”).

## Alcance revisado

- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt`:
  `Scaffold` con `TopAppBar` “Playbook”, tres acciones, speed-dial `+`/`×`,
  scrim, estado vacío y lista/tarjetas conservadas.
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt`: wiring
  `onOpenImageSource`/`onCaptureCamera` y retiro de `onDictate`; sin cambios en
  diálogo de imagen, persistencia ni otros destinos.
- `PlaybookBackHandler.kt` + actuals Android/iOS (`expect`/`actual`).
- `ARCHITECTURE.md`, `DESIGN.md`, `docs/technical-discovery.md`, `AGENTS.md`,
  `PROGRESS.md` y `feature_list.json` como documentación/estado durable.
- Paths protegidos: `git status --porcelain` no muestra cambios en `:core`,
  `sqldelight`/`.sqm`, build files, `gradle/libs.versions.toml`, `init.sh`,
  `iosApp/**`, `ImageCapture*`, `NoteEditorScreen.kt`, `NoteThumbnail.kt` ni
  `ImageGenerationDialog.kt`. No se importa `Icons.*`.

## Checks re-ejecutados

Todos con `ANDROID_HOME=$HOME/Library/Android/sdk` e IA fake
(`-Pplaybook.ai.mode=fake`), sin red ni API key.

| Check | Resultado |
|---|---|
| `CI=true ./init.sh` | **exit 0**; gate Android-only (`assembleDebug` + `:core:testDebugUnitTest`), sin dev servers. |
| `./gradlew :composeApp:assembleDebug -Pplaybook.ai.mode=fake` | **exit 0**, BUILD SUCCESSFUL; con `--rerun-tasks`: 56/56 tareas ejecutadas. |
| `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 -Pplaybook.ai.mode=fake` | **exit 0**, BUILD SUCCESSFUL; con `--rerun-tasks`: 4/4 tareas ejecutadas (iOS congelado, sólo compila). |
| `./gradlew :core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` | BUILD SUCCESSFUL; **34 suites / 99 tests / 0 failures / 0 errors / 0 skipped**. |
| `git diff --check` | OK (sin errores de whitespace). |
| Revisión de secrets/deps/esquema | Sin secrets en el diff, sin deps nuevas y sin cambios de esquema/`.sqm`. |

## Smoke Android

Emulador **dedicado** `Pixel_XL_API_31` en `emulator-5560` (API 31,
1440x2560/560dpi), arrancado para esta validación. No se tocaron los emuladores
`emulator-5556`/`emulator-5558` de otras sesiones ni se borraron datos
compartidos; el AVD se apagó al terminar. APK debug instalado con
`adb install -r`, `pm clear` y onboarding con “Saltar”.

Verificado con `uiautomator` (dumps del validador en
`composeApp/build/smoke-evidence/main-screen-playbook-shell/validator-ui/`) y
capturas del implementer (`01`–`14`):

1. **Top bar y navegación (Escenario 1):** título “Playbook”; acciones con
   `contentDescription` “Ver GDD”, “Brainstorming” y “Preguntar”, sin texto
   duplicado. GDD abre el documento y “Volver” regresa a la lista; Brainstorming
   abre el wizard (Paso 1/9) y “Cancelar” vuelve; Preguntar abre la pantalla de
   Q&A y “Volver” vuelve. No aparecen “Notas”, “Dictar nota”, la botonera vieja
   ni los FAB extendidos “Nueva nota”/“Foto”.
2. **Speed-dial (Escenario 2):** el FAB `+` (“Nueva nota”) expande Nota de
   texto → Galería (“Galería y generar imagen con IA”) → Cámara, de arriba a
   abajo, y cambia a `×` (“Cerrar menú de captura”). Cierra con el FAB, con Back
   y con tap en el scrim; el scrim no aparece en el árbol de accesibilidad.
3. **Nota de texto (Escenario 3):** abre “Nueva nota” vacía, **sin** dictado
   iniciado (no hay estado Listening); Guardar/Cancelar vigentes. Se creó una
   Nota de texto real, apareció en la lista y el tap abrió la Nota correcta.
4. **Galería (Escenario 4):** abre “Agregar imagen” con Cámara/Galería/“Generar
   con IA”; elegir Galería lanza el picker (`DocumentsUI`) y una imagen elegida
   navega a “Nueva nota” con la previsualización del Adjunto.
5. **Cámara (Escenario 5):** lanza la cámara nativa directo (con permiso
   concedido, `com.android.camera2/.CaptureActivity`); cancelar vuelve a la
   lista sin Nota. Con permiso denegado se muestra el diálogo existente
   “No se pudo agregar la imagen” con el mensaje de permiso.
6. **Estado vacío y lista (Escenario 6):** vacío con “Todavía no hay notas”,
   “Crear primera nota”, “Agregar imagen” y FAB visible. Lista con tarjetas,
   miniatura, chip “Imagen”, chips de track/estado y badge “capturada/indexada”;
   sin recortes por top bar/insets (captura `12`).
7. **Accesibilidad (Escenario 7):** nombres únicos en top bar y speed-dial,
   glifos internos limpiados (sin anuncios duplicados), targets de 48dp en las
   acciones (168 px / 3.5 = 48dp) y 56dp en FAB/acciones, cierre por Back y FAB
   sin depender del scrim.
8. **Gate (Escenario 8):** `CI=true ./init.sh` exit 0; sin cambios de
   esquema/deps/`init.sh` ni otros destinos.

Límites honestos: no hay harness E2E (la spec lo excluye); la captura exitosa
desde cámara no se repitió (sí se verificó el lanzamiento directo, la
cancelación y el error de permiso); la generación con IA ya estaba aceptada y
sólo se verificó que el diálogo la conserva.

## Hallazgos

- **Critical:** ninguno.
- **High:** ninguno.
- **Medium:** ninguno.
- **Low:**
  1. **Checkboxes de la spec sin marcar.** `docs/specs/main-screen-playbook-shell.md`
     conserva `[ ]` en “Implementation Tasks” (líneas 209–217) aunque el trabajo
     está hecho. Es bookkeeping, no comportamiento. Sugerencia: marcarlos al
     persistir `accepted` o en el commit de cierre.
  2. **Scrim y foco accesible del contenido de fondo.** El scrim está oculto
     para accesibilidad (correcto según la spec), pero el contenido subyacente
     sigue en el árbol accesible mientras el menú está abierto. Es el
     comportamiento especificado; si a futuro se quiere semántica modal, habría
     que ocultar el contenido mientras `expanded`. No bloquea.
  3. **Glifos de fallback dependientes del font.** `GDD`/`✦`/`?` se renderizaron
     bien en API 31; en otro device/font podrían requerir el reemplazo por `*`
     que prevé la spec. No hay test automatizado de eso.
  4. **Rama no rebasada sobre `develop` actual.** El working tree se validó sobre
     `763a357`; `origin/develop` ya está en `6a89317` y solapa `App.kt`/docs/
     `feature_list.json`. No afecta este veredicto, pero exige rebase y
     re-verificación post-rebase antes del merge.

## Seguridad

Sin hallazgos relevantes. El cambio es UI/navegación: no agrega red, secrets,
persistencia, permisos nuevos ni dependencias. Cámara/galería conservan los
permisos y el manejo de errores existentes; no se exponen datos de usuario en
logs ni en el diff. `local.properties` no forma parte del cambio.

## Documentación durable y handoff

- `ARCHITECTURE.md`, `DESIGN.md` y `docs/technical-discovery.md` describen el
  shell, el fallback textual, el speed-dial, Back y la decisión de Galería de
  forma coherente con el código.
- `AGENTS.md`, `PROGRESS.md` y `feature_list.json` reflejan `passing` y la
  evidencia; `CONSTRAINTS.md` no requería cambios.
- Por instrucción de la corrida, este validador **no** modificó
  `feature_list.json` ni `PROGRESS.md`; la persistencia de `accepted`
  corresponde al orquestador. Este artefacto es el registro de la validación.

## Veredicto

**accept.** La implementación cumple la spec y los escenarios 1–8, con gate,
builds Android/iOS, tests del core y smoke Android reproducidos de forma
independiente. Sin hallazgos bloqueantes. Estado recomendado: `passing` →
**`accepted`**.

## Próximos pasos

1. El orquestador puede persistir `accepted` en `feature_list.json` y
   `PROGRESS.md`, y actualizar `AGENTS.md` (la cola ya no debe decir “pendiente
   de validación”).
2. Rebasear `feature/main-screen-playbook-shell` sobre el `origin/develop`
   actual (`6a89317` o posterior), resolver los solapamientos de docs/`App.kt` y
   re-ejecutar `CI=true ./init.sh`, `assembleDebug`, link iOS y un smoke corto
   post-rebase.
3. Opcional: marcar los “Implementation Tasks” de la spec.
4. No mergear desde el validador; la cola de merge sigue siendo de la sesión
   integradora.
