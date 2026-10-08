# Feature Implementation Spec: CRUD de nota de texto en la UI

## Source Feature

- `id`: `create-text-note`
- `area`: `ui`
- `depends_on`: `["notes-list-ui"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Habilitar el **CRUD de notas de texto desde la UI** sobre la lista ya existente:
crear una nota nueva, editarla (cuerpo y `track`) y borrarla, con la lista
reflejando los cambios **sin reiniciar la app**. Es el primer modo de captura del
MVP (`docs/build-brief.md`, slice 1) y cierra la deuda aceptada de
`notes-list-ui`: la lista se leía en el hilo de composición y no se refrescaba
tras mutaciones.

La feature conserva la arquitectura vigente: `NoteRepository` síncrono pasado a
`App(...)` sin DI, sin `ViewModel` y sin Flow. Agrega estado de UI con Compose
(`mutableStateOf`) y una relectura explícita tras cada mutación. No agrega
etiquetas, adjuntos, IA ni GDD.

## Non-Goals

- Etiquetas (`note-category-and-tags`), adjuntos, enlaces, embeddings, indexado o
  estado de enriquecimiento. Sin cambios al `status` desde la UI (una nota creada
  queda `capturada`).
- Captura por voz o imagen (`voice-capture-stt`, `image-capture-camera`).
- GDD/agrupado por `track` (`gdd-view`), pantalla de detalle de sólo lectura,
  búsqueda o filtros.
- Framework DI, `ViewModel`/`Lifecycle`, coroutines/Flow o librería de
  navegación. La navegación lista ↔ editor se resuelve con estado local de
  Compose.
- Cambios de esquema o migraciones: `schema.version` sigue en 1, sin `.sqm`; sólo
  se reutilizan `create`/`update`/`delete` del repositorio.
- Manejo del back del sistema Android/iOS (se ofrece acción "Cancelar" en el
  editor; ver Riesgos).
- Formalizar `DESIGN.md` (sigue `status: provisional`) ni introducir tokens.

## Job Story

When tengo una idea de diseño en la cabeza,
I want capturarla como nota de texto y poder corregirla o descartarla después,
so I can mantener mis notas reales y ordenadas sin perder el ritmo de captura.

## Users And Permissions

- Autor/usuario único del MVP (local-first): todas las notas del dispositivo.
- Sin auth ni permisos. `owner` es el único del MVP y lo provee la UI con una
  constante local (`LOCAL_OWNER_ID`).

## Acceptance Scenarios

### Scenario 1: Crear desde el estado vacío

Given una instalación nueva (base local sin filas),
When se abre la app y se toca la acción de creación,
Then se abre el editor; al guardar con un cuerpo no vacío se persiste la nota,
vuelve a la lista y la nota aparece (la lista deja de mostrar el estado vacío).

### Scenario 2: Editar una nota existente

Given una nota en la lista,
When se toca la tarjeta, se modifica el `body` (y opcionalmente el `track`) y se
guarda,
Then la nota se actualiza en la base, `updated_at` avanza al reloj, la lista
refleja los nuevos valores y la nota reordenada aparece primero
(`updated_at DESC, id DESC`).

### Scenario 3: Borrar con confirmación

Given una nota en el editor,
When se toca "Borrar" y se confirma el diálogo,
Then la nota se elimina de la base, se vuelve a la lista y la nota ya no aparece;
si era la última, se muestra el estado vacío. Cancelar el diálogo no borra nada.

### Scenario 4: Validación del cuerpo

Given el editor abierto (crear o editar),
When el cuerpo está vacío o sólo contiene espacios,
Then la acción de guardar está deshabilitada y no se persiste ninguna fila.

### Scenario 5: Refresco sin reinicio

Given la app abierta en la lista,
When se crea, edita o borra una nota (Scenario 1–3),
Then la lista se actualiza en pantalla sin relanzar la app (relectura tras la
mutación); no se lee la base dentro del cuerpo de composición.

### Scenario 6: Persistencia entre reinicios

Given una nota creada/editada desde la UI,
When se cierra y reabre la app,
Then la nota persiste con los últimos valores guardados.

### Scenario 7: Gate honesto

Given la feature implementada,
When se ejecuta `./init.sh`,
Then sigue terminando en 0 sin dev servers ni simuladores; los tests del core
siguen verdes, `:composeApp:assembleDebug` compila y el framework iOS enlaza.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `notes-list-ui` `accepted`
  (Session 010); deuda aceptada "lectura en el hilo de composición sin refresco
  tras mutaciones, la aborda `create-text-note`".
- `feature_list.json` — metadata de `create-text-note` (verificación provisional:
  flujo manual + "test del use case de nota"); siguiente `note-category-and-tags`.
- `ARCHITECTURE.md` — `:composeApp → :core`; repositorio síncrono cableado sin DI;
  UI sólo lectura y "Sin refresco tras mutaciones" (Deferred).
- `docs/technical-discovery.md` — decisiones de persistencia y UI; SQLDelight
  única fuente local; tests in-memory por plataforma.
- `docs/build-brief.md` — slice 1: "CRUD de notas de texto eligiendo `track`";
  la captura no debe bloquearse.
- `docs/domain-model.md`, `CONTEXT.md` — `Track` (`mecánicas`/`personajes`/
  `historia`) vs `Etiqueta`; estados `capturada`/`pendiente`/`indexada`/`fallida`;
  edge case "Nota sin `track` no rompe la vista"; editar cuerpo no revierte estado.
- `DESIGN.md` — dirección provisional; pantallas "Lista de Notas" y "Editor de
  Nota de Texto: cuerpo + track + etiquetas"; regla "estados no sólo por color".
- `docs/specs/notes-list-ui.md` — contrato precedente: `App(noteRepository)`,
  `NotesListScreen`, estado vacío sin CTA, sin refresco (fuera de alcance allá).
- `composeApp/src/commonMain/.../App.kt` — `App(noteRepository)` lee con
  `remember(noteRepository) { noteRepository.getAll() }`; sin estado de refresco.
- `composeApp/src/commonMain/.../NotesListScreen.kt` — sólo lectura: estado vacío
  ("Todavía no hay notas", **sin CTA**) y `LazyColumn` de `Card`s no clickeables.
- `composeApp/src/androidMain/.../MainActivity.kt` y
  `composeApp/src/iosMain/.../MainViewController.kt` — construyen
  `SqlDelightNoteRepository(database, ::randomNoteId, ::currentTimeMillis)` y lo
  pasan a `App(...)`; **no** requieren cambios.
- `core/.../repository/{NoteRepository,SqlDelightNoteRepository}.kt` — CRUD
  síncrono ya implementado: `create(NoteDraft)`, `getAll()`, `getById`, `update`
  (re-sella `updatedAt`, devuelve `Boolean`), `delete` (devuelve `Boolean`).
- `core/.../model/{Note,Track,NoteStatus}.kt` — `NoteDraft(owner, body, track,
  status = CAPTURED)`; `Track` nullable; `NoteStatus` no nulo.
- `core/.../commonTest/.../NoteRepositoryCheck.kt` — `verifyNoteCrud(driver)` ya
  cubre create/update/delete y el orden `updated_at DESC, id DESC`.
- `composeApp/build.gradle.kts` — `compose.runtime`, `compose.foundation`,
  `compose.material3`, `compose.ui`, `:core`; sin source set de test.
- `init.sh` — gate no bloqueante: `assembleDebug`, tests del core (Android/JVM e
  iOS), link del framework iOS.

### Existing Patterns To Follow

- `UI → core`: la UI consume `NoteRepository`; la lógica de dominio no se mueve a
  `:composeApp`.
- Sin DI: las dependencias se pasan como parámetros (`App(noteRepository)`).
- Tests de datos en `:core` (`commonTest` + `@Test` por plataforma con drivers
  in-memory); la UI se verifica con smoke manual (no hay harness de UI tests).
- `init.sh` no bloqueante, sin dev servers ni simuladores.
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- `App` no tiene estado de lista mutable ni relectura; `remember` sólo carga una
  vez.
- `NotesListScreen` no ofrece acción de creación y sus tarjetas no abren detalle.
- No hay editor de texto, selector de `track`, validación ni diálogo de borrado.
- No existe estado de navegación lista ↔ editor (no hay librería de navegación).
- No hay `:composeApp` test source set: la lógica de UI se cubre manualmente.

## Technical Approach

### Estado de UI y refresco (sin DI/ViewModel/Flow)

En `App(noteRepository: NoteRepository)` se hoistea el estado de la lista y el
destino de navegación con Compose; **no** se lee la base en el cuerpo de
composición:

```kotlin
sealed interface NotesDestination {
    data object List : NotesDestination
    data object Create : NotesDestination
    data class Edit(val noteId: String) : NotesDestination
}

@Composable
fun App(noteRepository: NoteRepository) {
    MaterialTheme {
        var notes by remember { mutableStateOf(emptyList<Note>()) }
        var destination by remember { mutableStateOf<NotesDestination>(NotesDestination.List) }
        var refreshKey by remember { mutableStateOf(0) }

        // La lectura ocurre en un efecto, no durante la composición.
        LaunchedEffect(refreshKey) { notes = noteRepository.getAll() }

        fun reload() { refreshKey++ }
        // ...
    }
}
```

- **Refresco tras mutaciones**: crear/editar/borrar llaman al repositorio y luego
  `reload()`, que incrementa `refreshKey` y dispara la relectura. Así la lista
  refleja los cambios sin reiniciar la app (Scenario 5).
- **Fuera del hilo de composición**: la lectura queda en `LaunchedEffect`, no en
  el cuerpo del composable (deuda de `notes-list-ui`). Se mantiene el repositorio
  síncrono y la tabla local pequeña; no se introduce `Flow` ni coroutines fuera de
  Compose.
- **Sin DI**: `App` recibe el `NoteRepository` ya cableado; `MainActivity`/
  `MainViewController` no cambian.

### Owner del MVP

- `LOCAL_OWNER_ID = "local"` como `private const val` en `App.kt`. El usuario
  único del MVP lo provee la UI al construir `NoteDraft`; no se toca `:core`.

### Pantalla de lista (cambios en `NotesListScreen.kt`)

- **Acción de creación**: `Scaffold` con un `FloatingActionButton`/`ExtendedFloatingActionButton`
  "Nueva nota" que dispara `onCreate`.
- **Estado vacío**: se agrega un CTA ("Crear primera nota") que dispara la misma
  acción (hoy no había CTA).
- **Editar**: cada `Card` se vuelve clickeable (`Modifier.clickable {
  onEdit(note.id) }`), con `contentDescription`/semántica clara. Se sigue
  mostrando `body` (máx. 2 líneas) y chips de texto `track.code`/`status.code`.
- Lista plana y orden determinista existente; sin agrupar por `track`.

### Widget de edición/captura (`NoteEditorScreen.kt`, nuevo)

- Firma sugerida:
  `NoteEditorScreen(initialBody, initialTrack, isEditing: Boolean, onSave: (body, track) -> Unit, onDelete: (() -> Unit)?, onCancel: () -> Unit)`.
- Campos: `OutlinedTextField` multilínea para `body` y un selector de `track`
  (chips o botones estables con texto para `Track.entries` + "Sin track"). La
  selección se comunica con **más que color** (estado de selección textual/
  borde), según `DESIGN.md`.
- **Track opcional**: por defecto `null` ("Sin track") para no bloquear la
  captura (edge case "Nota sin `track`"); el usuario puede asignar uno.
- Acciones: guardar (deshabilitado si `body.isNotBlank()` es `false`), cancelar y,
  en modo edición, borrar.
- **Borrado con confirmación**: `AlertDialog` "¿Borrar esta nota?" con
  "Borrar"/"Cancelar"; sólo en modo edición.

### Flujo de datos

- **Crear**: `noteRepository.create(NoteDraft(owner = LOCAL_OWNER_ID, body =
  body.trim(), track = selectedTrack))` (status default `CAPTURED`) → `reload()` →
  lista.
- **Editar**: se busca la nota por `id` en `notes`; `noteRepository.update(note.copy(
  body = body.trim(), track = selectedTrack))` → `reload()` → lista. `update`
  re-sella `updatedAt` y preserva `owner`/`createdAt`/`status`.
- **Borrar**: `noteRepository.delete(id)` → `reload()` → lista.
- Navegación por `NotesDestination`; volver al editor desde `Edit(noteId)` usa la
  nota vigente de `notes` (si ya no existe, se vuelve a la lista).
- El `status` no se edita: una nota nueva queda `capturada` y editar el cuerpo no
  lo revierte (consistente con `docs/domain-model.md`).

### Diseño visual

- Sigue la dirección provisional de `DESIGN.md` (Material 3, cálido) sin fijar
  tokens. Editor: `cuerpo + track`; las etiquetas quedan fuera.
- No se requiere artefacto de diseño nuevo.

### Dependencias, esquema y `init.sh`

- **Sin dependencias nuevas**: `Scaffold`, FAB, `OutlinedTextField`, `AlertDialog`
  y chips vienen de `compose.material3`; `mutableStateOf`/`LaunchedEffect` de
  `compose.runtime`; `clickable` de `compose.foundation`.
- **Sin cambios de esquema** (`schema.version = 1`, sin `.sqm`) ni de
  `MainActivity`/`MainViewController`.
- **`init.sh` no cambia**: sigue no bloqueante y sin procesos de larga duración.

### Riesgos y decisiones abiertas

- **Back del sistema**: el editor ofrece "Cancelar" (botón en UI). El back físico
  Android/iOS queda fuera de alcance (CMP no expone un `BackHandler` común
  estable en esta versión); se acepta como limitación conocida.
- **Lectura en el main thread**: el `LaunchedEffect` saca la lectura del cuerpo
  de composición, pero sigue en el main thread. Para una tabla local pequeña es
  aceptable; moverla a un dispatcher de background requeriría declarar
  `kotlinx-coroutines-core`, que se evita aquí.
- **`LOCAL_OWNER_ID` en la UI**: el usuario único del MVP lo provee la UI. Si una
  feature futura necesita el owner fuera de `:composeApp`, se puede promover a
  `:core` sin cambiar el contrato funcional.
- **`track` opcional por defecto `null`**: mantiene la captura sin fricción y el
  edge case "Nota sin `track`"; si el producto lo requiere obligatorio, se ajusta
  en `note-category-and-tags`.

## Expected File Changes

- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — modificar; estado
  de lista (`mutableStateOf` + `LaunchedEffect`), navegación `NotesDestination`,
  `LOCAL_OWNER_ID` y callbacks de crear/editar/borrar con `reload()`.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` —
  modificar; acción de creación (FAB), CTA en estado vacío y tarjetas clickeables.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NoteEditorScreen.kt` — crear;
  cuerpo + selector de `track` + guardar/cancelar/borrar con `AlertDialog`.
- `ARCHITECTURE.md` — modificar; superficie de mutación de UI, estado/refresco y
  navegación local.
- `docs/technical-discovery.md` — modificar; decisión de estado/refresco sin
  DI/ViewModel/Flow.
- `AGENTS.md` — modificar al cerrar; corregir la línea "Próxima feature en cola".
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado, evidencia y
  retiro de la deuda de refresco.

No se esperan cambios en `core/**`, `composeApp/build.gradle.kts`,
`gradle/libs.versions.toml`, `init.sh`, `iosApp/**` ni `Note.sq`.

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` (provisional). Se sigue su dirección estructural
  ("Lista de Notas", "Editor de Nota de Texto") y las reglas de accesibilidad
  ("no sólo color", targets ≥ 48dp); no se fijan tokens.
- Screens or states affected: "Lista de Notas" (acción de creación, estado vacío
  con CTA, tarjetas clickeables) y "Editor de Nota de Texto" (crear/editar,
  validación, borrado con confirmación).
- New design artifact required: no — la entrega de UI/UX sigue pendiente.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — aparece la mutación desde la UI, el estado de
  lista con refresco y la navegación local sin librería.
- `CONSTRAINTS.md`: not needed — no surge una regla MUST/MUST NOT durable nueva.
- `AGENTS.md`: **update** — la línea "Próxima feature en cola" cambia; no cambian
  workflow ni gate.
- Other docs: `docs/technical-discovery.md` — update (estado/refresco);
  `feature_list.json`/`PROGRESS.md` — update al cerrar; `DESIGN.md`/`CONTEXT.md`/
  `docs/domain-model.md` — not needed.

## Implementation Plan

1. Reescribir `App.kt`: estado de lista con `mutableStateOf`, carga en
   `LaunchedEffect`, navegación `NotesDestination`, `LOCAL_OWNER_ID` y callbacks
   de CRUD con `reload()`.
2. Actualizar `NotesListScreen.kt`: FAB/acción de creación, CTA en el estado
   vacío y tarjetas clickeables.
3. Crear `NoteEditorScreen.kt`: campos, selector de `track`, validación de
   `body`, guardar/cancelar y borrado con `AlertDialog`.
4. Cablear el flujo en `App`: crear/editar/borrar → repositorio → `reload()` →
   volver a la lista.
5. Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
6. Correr la verificación, hacer smoke manual en Android e iOS y registrar
   evidencia; actualizar `feature_list.json`/`PROGRESS.md`/`AGENTS.md` al cerrar.

## Implementation Tasks

- [x] Hoistear `notes`/`destination`/`refreshKey` en `App` y cargar con
      `LaunchedEffect` (sin lectura en el cuerpo de composición).
- [x] Definir `NotesDestination` y `LOCAL_OWNER_ID` en `App.kt`.
- [x] Agregar la acción de creación (FAB) y el CTA del estado vacío.
- [x] Hacer clickeables las tarjetas para abrir el editor en modo edición.
- [x] Crear `NoteEditorScreen` con cuerpo, selector de `track` y validación de
      `body` no vacío.
- [x] Implementar guardar (crear/editar) con `NoteDraft`/`update` y `reload()`.
- [x] Implementar borrado con `AlertDialog` de confirmación y `reload()`.
- [x] Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
- [x] Correr `./init.sh` y los comandos de verificación; registrar evidencia.
- [x] Smoke manual Android e iOS: crear/editar/borrar, validación, orden y
      persistencia entre reinicios. (iOS limitado a build + launch + render; sin
      input automation disponible, ver evidencia.)
- [x] Actualizar `feature_list.json`, `PROGRESS.md` y `AGENTS.md` al cerrar.

## Verification Plan

- `./gradlew :core:testDebugUnitTest` → BUILD SUCCESSFUL; `verifyNoteCrud`
  (create/update/delete y orden) sigue verde. El path de datos del CRUD ya está
  cubierto aquí.
- `./gradlew :core:iosSimulatorArm64Test` (macOS) → el mismo helper verde en iOS.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (UI + wiring Android).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (UI + wiring iOS); `xcodebuild` del scheme `iosApp` → BUILD SUCCEEDED.
- Smoke manual Android (emulador) e iOS (simulador):
  - estado vacío → crear nota → aparece y desaparece el estado vacío;
  - editar `body`/`track` → la lista muestra los cambios y la nota sube al tope;
  - borrar con confirmación → desaparece; borrar la última → estado vacío;
  - guardar con `body` vacío → acción deshabilitada, sin fila;
  - cerrar y reabrir → la nota persiste.
- `./init.sh` → exit 0; ejecuta el gate no bloqueante actual, **no** inicia dev
  servers ni simuladores y puede imprimir los comandos manuales.
- E2E persistente: **no aplica** — no existe harness de UI tests/comando E2E y
  `init.sh` no levanta simuladores. Se justifica: el path de datos
  (create/update/delete + orden) está cubierto por los tests del core en dos
  plataformas; el estado/refresco/validación de UI es fino y se verifica con
  smoke manual en Android e iOS, igual que en `notes-list-ui`.
- No hace falta sembrar la base para QA: la creación desde la UI ya permite
  generar los datos del smoke.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` (verdes).
- Salida de `:composeApp:assembleDebug` y `:composeApp:linkDebugFrameworkIosSimulatorArm64`
  (y `xcodebuild` si se corre).
- Salida de `./init.sh` (exit 0) sin procesos de larga duración.
- Capturas/registros del smoke en Android e iOS: crear, editar (reordenamiento),
  borrar con confirmación, validación de cuerpo vacío y persistencia tras
  reinicio.
- Confirmación de que no hubo cambios de esquema (`schema.version = 1`, sin
  `.sqm`), ni dependencias nuevas, ni cambios en `MainActivity`/
  `MainViewController`.

## Validator Checklist

- [ ] Alcance respetado: CRUD de texto en la UI (crear/editar/borrar + refresco);
      sin etiquetas, adjuntos, IA, GDD, búsqueda ni captura de voz/imagen.
- [ ] Escenarios de aceptación 1–7 pasan.
- [ ] La lista se refresca tras cada mutación sin reiniciar la app y **no** se lee
      la base dentro del cuerpo de composición.
- [ ] Validación: no se persiste `body` vacío/blank.
- [ ] Borrado con confirmación; cancelar no borra.
- [ ] `track` es opcional y "Sin track" se refleja en la lista.
- [ ] Sin DI/`ViewModel`/Flow ni librería de navegación; `MainActivity`/
      `MainViewController` sin cambios.
- [ ] Sin cambios de esquema (`schema.version = 1`, sin `.sqm`) ni dependencias.
- [ ] Evidencia en `feature_list.json` / `PROGRESS.md`; `ARCHITECTURE.md`,
      `docs/technical-discovery.md` y `AGENTS.md` actualizados.
- [ ] No hay E2E persistente y la spec lo justifica.
