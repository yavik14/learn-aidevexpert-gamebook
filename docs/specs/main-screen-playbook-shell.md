# Feature Implementation Spec: Pantalla principal Playbook (top bar, iconos, FAB)

## Source Feature

- `id`: `main-screen-playbook-shell`
- `area`: `ui`
- `depends_on`: `notes-list-ui`, `gdd-view`, `rag-query`, `brainstorming-wizard`
- `status`: `not_started` (al planificar)
- `source`: `feature_list.json`

## Goal

Reemplazar el encabezado “Notas” y la botonera temporal por el shell de
`DESIGN.md`: `TopAppBar` con “Playbook” y acciones **GDD**, **Brainstorming** y
**Preguntar**, más un **FAB desplegable** con **Nota de texto**, **Galería** y
**Cámara**. Se conservan lista, estado vacío, tarjetas, miniaturas, chips,
badges, el diálogo “Agregar imagen” y los destinos de navegación actuales.

## Non-Goals

- Nuevas funcionalidades de captura, IA, clasificación ni transcripción.
- Cambiar `NotesDestination`, repositorios, esquema, build flags, dependencias,
  `init.sh`, `ImageCapture*` ni `saveTextNote`/`saveNoteWithImage`.
- Rediseñar editor, GDD, Preguntar o Brainstorming; agregar navegación/DI/
  `ViewModel`/Flow ni animaciones.
- Tests E2E persistentes (no hay harness) ni smoke iOS; iOS sólo debe compilar.

## Job Story

When abro la app con un Proyecto, I want ver mis notas y acceder a GDD,
Brainstorming, Preguntar y las capturas desde un solo lugar, so I can trabajar sin
perder funciones existentes.

## Users And Permissions

Autor único local-first, sin auth. Cámara/galería conservan sus permisos nativos.

## Scope Decisions

1. **Top bar:** `TopAppBar` con “Playbook” y tres acciones a la derecha. Sin
   `material-icons` en `commonMain`, se usa el **fallback textual accesible**:
   `Text` dentro de `IconButton` con glifos `GDD`, `✦`, `?`. Cada acción expone un
   único `contentDescription` (“Ver GDD”, “Brainstorming”, “Preguntar”), rol de
   botón y target 48dp. Prohibido importar `Icons.*`. Si un glifo no renderiza en
   el font del sistema, se reemplaza por `*` conservando el `contentDescription`.
2. **Se eliminan** “Notas”, “Dictar nota”, “Ver GDD”, “Preguntar”,
   “Brainstorming” y los FAB “Nueva nota”/“Foto”; sus acciones pasan a top bar o
   speed-dial.
3. **Speed-dial (abajo-derecha):** colapsado, FAB `+` con `contentDescription
   "Nueva nota"`; expandido, FAB `×` (“Cerrar menú de captura”) y tres
   `ExtendedFloatingActionButton` apiladas de arriba a abajo: **Nota de texto**,
   **Galería** (`contentDescription = "Galería y generar imagen con IA"`) y
   **Cámara**. Tocar una acción colapsa y ejecuta su callback; el FAB
   alterna; **Back** cierra si está abierto (Android; iOS congelado es no-op).
   Scrim semitransparente sobre el
   contenido (no top bar/FABs) que cierra al tocar y queda oculto para
   accesibilidad: los cierres accesibles son el FAB y Back. Targets ≥48dp.
4. **Destinos:** Nota de texto → `Create` (sin dictado); Galería → diálogo
   existente “Agregar imagen” (conserva Cámara/Galería/“Generar con IA”); Cámara →
   `imagePicker.launch(ImageSource.CAMERA)` directo.
5. **Dictado:** sale de la principal; queda sólo en el editor de Nueva nota
   (`startDictation`/`stopDictation` vigentes). Se elimina `onDictate`.
6. **Estado vacío:** conserva “Todavía no hay notas”, “Crear primera nota” (texto)
   y “Agregar imagen” (diálogo); el FAB sigue visible.
7. **Insets:** `Scaffold`/`TopAppBar` con insets por defecto; el contenido consume
   `innerPadding`; sin paddings manuales de barras. Ni título bajo la status bar
   ni FAB bajo la barra de gestos.

## Acceptance Scenarios

### Scenario 1: top bar, navegación y ausencia de botones viejos

Given un Proyecto cargado, When se muestra la principal, Then dice “Playbook” y
tiene tres acciones con `contentDescription` “Ver GDD”, “Brainstorming” y
“Preguntar”; cada una abre su destino actual y volver regresa a la lista.
Además no aparecen “Notas”, “Dictar nota”, “Ver GDD”, “Preguntar”,
“Brainstorming” ni los FAB “Nueva nota”/“Foto”.

### Scenario 2: abrir/cerrar el speed-dial

Given la principal con el FAB colapsado, When se toca el FAB, Then aparecen “Nota
de texto”, “Galería” y “Cámara” en ese orden, cambia a `×` y se muestra el scrim;
tocar el FAB, el scrim o Back vuelve a `+`.

### Scenario 3: Nota de texto

Given el menú abierto, When se toca “Nota de texto”, Then se cierra y abre el
editor de Nueva nota vacío, sin dictado iniciado, con Guardar/Cancelar vigentes.

### Scenario 4: Galería y “Generar con IA”

Given el menú abierto, When se toca “Galería”, Then se abre “Agregar imagen” con
“Cámara”, “Galería” y “Generar con IA”; elegir Galería lanza el picker y una
imagen elegida navega a `CreateWithImage` como hoy.

### Scenario 5: Cámara

Given el menú abierto, When se toca “Cámara”, Then se cierra y lanza la captura
nativa; cancelar es no-op y un error de permiso/cámara muestra el mensaje actual.

### Scenario 6: estado vacío y lista sin regresiones

Given una instalación vacía, Then se ve el vacío con ambas CTAs y FAB. Given Notas
con track/géneros/estado/Adjuntos, Then tarjetas, miniaturas, chips y badges se
conservan, el tap abre la Nota correcta y no hay recortes por top bar/insets.

### Scenario 7: accesibilidad

Given TalkBack/switch access, When se recorre top bar y speed-dial, Then cada
acción tiene un nombre único, no se anuncian glifos duplicados, los targets son
≥48dp y el menú se cierra sin depender del scrim.

### Scenario 8: gate honesto

Given la feature implementada, When corre `CI=true ./init.sh`, Then termina en 0
con IA fake, sin red, sin cambios de esquema/deps/`init.sh` ni otros destinos.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `CONTEXT.md`, `DESIGN.md`.
- Specs: `notes-list-ui`, `create-text-note`, `gdd-view`, `rag-query`,
  `brainstorming-wizard`, `image-capture-camera`, `inline-dictation-30s`,
  `image-generation-from-text`; `docs/technical-discovery.md` y
  `docs/risks-and-open-questions.md`.
- Código: `NotesListScreen.kt`, `App.kt`, `NoteEditorScreen.kt`,
  `NoteThumbnail.kt`, `ImageCapture.kt`, `ImageCapture.android.kt`,
  `ImageCapture.ios.kt`, `MainActivity.kt`, `themes.xml`,
  `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `init.sh`.

### Existing Patterns To Follow

- `App` es dueño de navegación/estado; la pantalla sólo emite callbacks.
  `NotesDestination` es local, sin navegación/DI.
- El dictado inline ya vive en el editor; la entrada desde la lista es la que se
  retira.
- “Agregar imagen” centraliza Cámara/Galería/“Generar con IA” y el mapeo de
  `ImageCaptureError`.

### Current Gaps

- `material-icons-core` sólo llega al classpath Android vía `material3`; no está
  en `commonMain`. Por eso se usa fallback textual.
- No hay harness de UI/E2E; la verificación es build + smoke manual.

## Technical Approach

- `NotesListScreen`: nueva firma con `onCreate`, `onEdit`, `onOpenGdd`, `onAsk`,
  `onOpenBrainstorming`, `onOpenImageSource`, `onCaptureCamera`; sin `onDictate`.
  `Scaffold(topBar = { PlaybookTopBar(...) }, floatingActionButton = {
  CaptureSpeedDial(...) })`.
- `PlaybookTopBar`: `TopAppBar` (requiere `@OptIn(ExperimentalMaterial3Api::class)`)
  con `Text`/`IconButton` y semántica explícita; el texto interno se limpia con
  `clearAndSetSemantics {}` para no duplicar el nombre.
- `CaptureSpeedDial`: `var expanded by remember { mutableStateOf(false) }`;
  render condicional (sin animaciones) de acciones + scrim. Hallazgo de
  implementación: `androidx.compose.ui.backhandler` no está en el classpath
  Android (sólo metadata/iOS), así que se agrega el wrapper `PlaybookBackHandler`
  (`expect`/`actual`): Android usa `androidx.activity.compose.BackHandler`; iOS
  congela un no-op. El scrim vive dentro del contenido del `Scaffold` y usa
  `detectTapGestures` para cerrar sin semántica.
- `App`: en `NotesDestination.List`, cablea `onOpenImageSource = {
  showImageSourceDialog = true }` y `onCaptureCamera = { imagePicker.launch(
  ImageSource.CAMERA) }`; elimina `onDictate`. Diálogo y persistencia no cambian.
- Insets: `TopAppBarDefaults.windowInsets` + `ScaffoldDefaults.contentWindowInsets`,
  sin `enableEdgeToEdge` ni paddings manuales nuevos.

## Expected File Changes

- `composeApp/.../NotesListScreen.kt` — modificar; top bar, speed-dial, scrim,
  callbacks y estados.
- `composeApp/.../App.kt` — modificar; wiring de lista (`onCaptureCamera`, quitar
  `onDictate`).
- `composeApp/.../PlaybookBackHandler.kt` — crear; `expect` de Back.
- `composeApp/.../PlaybookBackHandler.android.kt`/`.ios.kt` — crear; actuales
  (Android intercepta, iOS no-op).
- `ARCHITECTURE.md`, `docs/technical-discovery.md`, `DESIGN.md` — actualizar al
  cerrar (shell, fallback de iconos, speed-dial).
- `feature_list.json`, `PROGRESS.md`, `AGENTS.md` — estado/evidencia/cola al
  cerrar; no antes.
- Sin cambios en `NoteEditorScreen.kt`, `NoteThumbnail.kt`, `ImageCapture*`,
  `ImageGenerationDialog.kt`, `:core`, esquema, build files, `init.sh` ni
  `iosApp/**`.

## Visual Design Impact

- UI involved: yes. Fuente `DESIGN.md` (Layout, Components, Core Screen 1,
  Accessibility Baseline). Afecta top bar, acciones, speed-dial, scrim y vacío.
- Sin artefacto nuevo; el fallback textual se documenta en `DESIGN.md` al cerrar.

## Durable Documentation Impact

- `ARCHITECTURE.md`: update — composición del shell y contrato de callbacks.
- `CONSTRAINTS.md`: not needed — no surge una regla MUST/MUST NOT nueva.
- `AGENTS.md`: update acotado al cerrar — mover la cola.
- `docs/technical-discovery.md`: update — decisiones del shell/iconos.
- `DESIGN.md`: update — resolver la pregunta del FAB y registrar el fallback.

## Implementation Plan

1. Reescribir el chrome de `NotesListScreen` y ajustar su firma.
2. Cablear callbacks en `App.kt`; retirar la entrada de dictado de la lista.
3. Compilar, correr gate, smoke Android y verificar iOS compile-only.
4. Actualizar docs durables, `feature_list.json` y `PROGRESS.md` con evidencia.

## Implementation Tasks

- [x] Agregar `PlaybookTopBar` con tres acciones y `contentDescription` único.
- [x] Implementar `CaptureSpeedDial` con orden, FAB `+`/`×`, scrim, cierre por
      tap/Back, labels y targets ≥48dp.
- [x] Reemplazar botonera/FABs viejos; conservar lista, tarjetas, miniaturas,
      chips y badges.
- [x] Ajustar firma/wiring; quitar `onDictate`; conservar “Agregar imagen” y
      “Generar con IA”.
- [x] Verificar insets/edge-to-edge y accesibilidad básica.
- [x] Actualizar docs durables y evidencia; no tocar esquema/deps/`init.sh`.

## Verification Plan

- `CI=true ./init.sh` → exit 0 (Android build + tests core, IA fake, sin red).
- `./gradlew :composeApp:assembleDebug -Pplaybook.ai.mode=fake` → BUILD SUCCESSFUL.
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (iOS congelado: sólo compilar, sin smoke).
- Smoke Android: top bar navega y vuelve; FAB abre/cierra con tap/scrim/Back;
  texto abre editor; Galería abre diálogo y “Generar con IA” sigue accesible;
  Cámara lanza picker; vacío y lista con miniaturas/chips/badges sin regresiones.
- TalkBack: nombres únicos, sin glifos duplicados, targets ≥48dp y cierre sin
  scrim.
- `git diff --stat` → sólo archivos esperados; sin esquema, deps, `init.sh`,
  `ImageCapture*`, `:core` ni otros destinos.
- E2E persistente: **no aplica**; no hay harness y `init.sh` no inicia emuladores.

## Evidence To Capture

- Salida de `CI=true ./init.sh`, `assembleDebug` y link iOS.
- Capturas: principal con top bar, speed-dial cerrado/abierto, vacío y lista con
  miniatura/chips/badges.
- Confirmación manual de las tres navegaciones, Galería/Cámara/“Generar con IA”,
  cierre por Back/scrim e insets.
- `git diff --stat` y nota de que no se tocaron esquema, deps, `init.sh`, `:core`
  ni iOS más allá del compile.

## Validator Checklist

- [ ] Sólo se implementó el shell; sin lógica de captura/navegación/IA nueva.
- [ ] Escenarios 1–8 pasan.
- [ ] Evidencia de gate, build, link iOS y smoke Android registrada.
- [ ] Top bar/speed-dial con orden, labels, scrim, Back y accesibilidad definidos;
      dictado sólo en editor.
- [ ] Lista, vacío, tarjetas, miniaturas, chips, badges y destinos sin regresiones.
- [ ] Sin cambios de esquema, deps, `init.sh` ni otros features.
- [ ] Docs durables, `feature_list.json` y `PROGRESS.md` actualizados al cerrar.
