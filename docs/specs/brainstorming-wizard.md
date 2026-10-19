# Feature Implementation Spec: Wizard de Brainstorming (9 pasos)

## Source Feature

- `id`: `brainstorming-wizard`
- `area`: `ui`
- `depends_on`: `["game-project-persistence", "genres-catalog-ui"]` (ambas
  `accepted` al planificar)
- `status`: `not_started` (al planificar)
- `source`: `feature_list.json`

> **Alcance.** Wizard de 9 pasos que crea o itera **únicamente la ficha del
> Proyecto** (`GameProject`) usando `GameProjectRepository`. Todos los pasos son
> opcionales, hay progreso `Paso X/9`, navegación atrás/siguiente, guardado
> explícito y reapertura con precarga. El paso Género reutiliza `GenrePicker`
> (de `genres-catalog-ui`). La creación de una **Nota por paso** se separó a
> `brainstorming-note-per-step` (decisión del autor, 2026-10-19) y **no** forma
> parte de esta feature.

## Goal

Que el usuario complete o itere la ficha del Proyecto (Título, Género, Core
Loop, Audiencia, Concepto, Inspiraciones ≤3, Fantasía, Mecánicas clave ≤5 con
categoría y Fuera de alcance ≤3) con un asistente guiado, sin perder el
contexto de progreso. El wizard mantiene un borrador local, guarda la ficha
completa al tocar "Guardar", descarta al cancelar y precarga los valores
persistidos al reabrirse.

La feature es **sólo UI + wiring**: no cambia `:core`, no agrega esquema, no
crea Notas, no usa IA y no toca `init.sh`, `iosApp` ni build files. Reutiliza
`GameProjectRepository` y `GenrePicker` ya aceptados.

## Non-Goals

- **Nota por paso** y pre-clasificación contextual: feature separada
  `brainstorming-note-per-step`.
- **Sugerencias IA** (2 por paso) y cualquier llamada a `AiClient`: feature
  `brainstorming-ai-suggestions`.
- **Onboarding del primer arranque** (ofrecer/saltar, crear Proyecto al
  arrancar): feature `first-run-project-onboarding`. El wizard no se abre solo.
- **Pantalla principal nueva** (top bar + iconos + FAB): feature
  `main-screen-playbook-shell`; aquí sólo se agrega un botón temporal.
- GDD híbrido, export, edición de Notas, indexado, clasificación de Notas.
- Múltiples Proyectos, Banco de ideas, `delete()`/reset del Proyecto,
  `note.project_id` o FK Nota→Proyecto.
- Cambios en `:core`, SQLDelight/`.sqm`, dependencias, build files, `init.sh` o
  `iosApp/**`; iOS sólo debe seguir compilando.
- Tests de UI/E2E nuevos: no hay harness persistente en el repo.

## Job Story

When quiero arrancar o iterar el diseño de mi juego,
I want completar la ficha del Proyecto con un asistente de 9 pasos que puedo
saltar y retomar,
so I can mantener la visión del juego en un solo lugar sin perder lo ya
persistido.

## Users And Permissions

- **Autor** (único usuario, single-user local-first): crea/itera el Proyecto;
  sin auth ni permisos nuevos.
- **Desarrollador**: corre Gradle/tests y smoke manual Android. Sin red,
  secrets ni claves.

## Scope Decisions

> **Cerradas con el autor (2026-10-19). Vinculantes para el implementer.**

1. **Sólo ficha.** El wizard escribe `game_project` + hijas vía
   `GameProjectRepository.save(draft)`. No crea Notas ni adjuntos.
2. **Guardado explícito.** "Guardar" persiste la ficha **completa** del
   formulario; "Cancelar" descarta sin escribir. No hay autosave por paso.
3. **Todos los pasos opcionales.** Se puede avanzar con campos vacíos y guardar
   un Proyecto vacío. Un Proyecto sin datos es válido (`get() != null`).
4. **Reabrible para iterar.** Al abrir el wizard, el formulario se inicializa
   desde `GameProjectRepository.get()`. Guardar reemplaza los campos y las
   colecciones hijas; `owner`/`createdAt` se preservan (contrato del
   repositorio).
5. **Géneros compartidos.** El paso Género usa el `GenrePicker` de
   `genres-catalog-ui` (`internal fun GenrePicker(selected, onChange, modifier)`),
   con catálogo fijo + personalizados y `normalizeGenres`.
6. **Entrada temporal.** `NotesListScreen` suma un botón "Brainstorming" en una
   fila propia, debajo de Dictar/Ver GDD/Preguntar. `main-screen-playbook-shell`
   lo reemplazará por el icono de top bar.
7. **Límites 3/3/5.** Inspiraciones ≤3, Fuera de alcance ≤3, Mecánicas ≤5. La UI
   deshabilita "Agregar" al llegar al tope; el repositorio sigue siendo la
   fuente de verdad y valida después de normalizar. Las filas vacías se
   descartan al guardar.
8. **Mecánica incompleta.** Una fila con descripción no vacía y sin categoría
   muestra un error y bloquea el guardado; una fila completamente vacía se
   descarta. Cada mecánica completa lleva categoría
   (`Movimiento`/`Interacción`/`Conflicto`) + descripción.
9. **Progreso mínimo.** `LinearProgressIndicator` + texto
   `Paso X/9 · <paso>`. Sin sugerencias IA, sin artefacto de diseño nuevo.
10. **Errores de guardado.** Si el repositorio rechaza el draft, se muestra un
    `AlertDialog` y se permanece en el wizard; no se navega ni se escribe a
    medias.
11. **Owner.** Se usa `initialProject.owner` si existe; si no, el
    `LOCAL_OWNER_ID` de `App` (`"local"`). El wizard no inventa otro owner.
12. **Sin IA.** No se muestra ningún control de sugerencias; la estructura de
    cada paso queda lista para que `brainstorming-ai-suggestions` las agregue
    después.

## Acceptance Scenarios

### Scenario 1: entrada sin Proyecto

Given una instalación sin Proyecto y la lista de Notas,
When el usuario toca "Brainstorming",
Then se abre el wizard en `Paso 1/9 · Título` con campos vacíos, `Atrás`
deshabilitado y sin ninguna fila escrita en `game_project`.

### Scenario 2: navegación por los 9 pasos

Given el wizard abierto,
When el usuario toca "Siguiente" y "Atrás" repetidamente,
Then recorre Título → Género → Core Loop → Audiencia → Concepto →
Inspiraciones → Fantasía → Mecánicas clave → Fuera de alcance, el texto
`Paso X/9` y la barra avanzan/retroceden y los valores ingresados se conservan.

### Scenario 3: guardar un Proyecto vacío

Given el wizard en el paso 9 sin ningún dato,
When el usuario toca "Guardar",
Then se crea el singleton (`id = "singleton"`, `owner = "local"`), todos los
escalares quedan `null`, las listas vacías, no hay hijas y se vuelve a la
lista.

### Scenario 4: guardado parcial en cualquier paso

Given el wizard en un paso 1–8 con el Título escrito,
When el usuario toca "Guardar" en la barra superior,
Then se persiste la ficha completa con ese Título, el resto vacío y se vuelve
a la lista; al reabrir, el Título aparece precargado.

### Scenario 5: ficha completa

Given el wizard con Título, Géneros, Core Loop, Audiencia, Concepto,
Inspiraciones, Fantasía, Mecánicas y Fuera de alcance,
When el usuario toca "Guardar",
Then `get()` devuelve exactamente esos valores, las listas conservan el orden
ingresado, cada mecánica conserva categoría + descripción y se vuelve a la
lista.

### Scenario 6: Géneros de catálogo + personalizados

Given el paso Género,
When el usuario toca chips del catálogo, agrega un Género personalizado y
quita uno seleccionado,
Then `GenrePicker` aplica `normalizeGenres` (trim, canonización
case-insensitive, dedupe y orden) y el conjunto persiste tal como se muestra.

### Scenario 7: límite de Inspiraciones

Given el paso Inspiraciones con 3 filas,
When el usuario intenta agregar una cuarta,
Then "Agregar inspiración" está deshabilitado; al guardar, las filas vacías no
se persisten y se permiten menos de 3.

### Scenario 8: límite de Mecánicas clave

Given el paso Mecánicas con 5 filas,
When el usuario intenta agregar una sexta,
Then "Agregar mecánica" está deshabilitado; al menos una fila puede quedar
vacía y se descarta sin error.

### Scenario 9: límite de Fuera de alcance

Given el paso Fuera de alcance con 3 filas,
When el usuario intenta agregar una cuarta,
Then "Agregar fuera de alcance" está deshabilitado; las filas vacías se
descartan al guardar.

### Scenario 10: mecánica incompleta

Given una fila de Mecánicas con descripción no vacía y sin categoría,
When el usuario toca "Guardar",
Then se muestra un mensaje que pide elegir una categoría, no se escribe nada y
el usuario permanece en el wizard.

### Scenario 11: cancelar descarta

Given un Proyecto persistido y cambios locales en el wizard,
When el usuario toca "Cancelar",
Then vuelve a la lista sin modificar `game_project` ni sus hijas; al reabrir se
ven los valores persistidos anteriores.

### Scenario 12: iterar y reemplazar

Given un Proyecto persistido con Título, Inspiraciones y Mecánicas,
When el usuario reabre el wizard, cambia el Título, quita una Inspiración y una
Mecánica y toca "Guardar",
Then la ficha refleja exactamente el nuevo conjunto, `owner`/`createdAt` se
preservan, `updatedAt` avanza y no quedan filas hijas huérfanas.

### Scenario 13: error de guardado

Given un draft que el repositorio rechaza (p. ej. un límite inválido o un owner
distinto),
When el usuario toca "Guardar",
Then se muestra un `AlertDialog` de error, se permanece en el wizard y no se
navega.

### Scenario 14: gate honesto

Given los escenarios 1–13,
When corren `:composeApp:assembleDebug`, `:core:testDebugUnitTest` y
`CI=true ./init.sh`,
Then todo pasa en Android/JVM, sin red ni dependencias nuevas, el esquema sigue
en v8 sin `.sqm` nuevo y `./init.sh` termina en `0`.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`,
  `docs/mvp-execution-plan.md` — Android-first, iOS congelado, gate
  Android-only, WIP=1 y orden de olas.
- `docs/specs/game-project-persistence.md` — contrato de `GameProject`/
  `GameProjectDraft`, normalización, límites 3/3/5, singleton, owner y upsert.
- `docs/specs/genres-catalog-ui.md` y `composeApp/.../GenrePicker.kt` — picker
  compartido, `FlowRow`, toggle, normalización y accesibilidad.
- `CONTEXT.md`, `docs/domain-model.md`, `docs/build-brief.md`, `DESIGN.md`,
  `docs/risks-and-open-questions.md`, `docs/technical-discovery.md`,
  `CONSTRAINTS.md`.
- `composeApp/.../App.kt` — `App(...)` recibe repositorios/servicios por
  constructor, `LOCAL_OWNER_ID = "local"`, `NotesDestination`,
  `LaunchedEffect(refreshKey)`, `reload()` y navegación local.
- `composeApp/.../NotesListScreen.kt` — fila de acciones actual
  (`Dictar nota`/`Ver GDD`/`Preguntar`), estado vacío y tarjetas.
- `composeApp/.../NoteEditorScreen.kt` — patrón de pantalla "tonta" con estado
  local, chips, `AlertDialog` y botones Atrás/Cancelar/Guardar.
- `composeApp/.../LabelChip.kt` — chip textual; no se necesita para el wizard.
- `core/.../model/GameProject.kt` — modelo, límites `internal`, normalización y
  `MechanicCategory`.
- `core/.../repository/GameProjectRepository.kt` y
  `SqlDelightGameProjectRepository.kt` — `get()`/`save()`, preservación de
  owner/createdAt, reemplazo de hijas y validaciones.
- `core/.../sqldelight/.../GameProject.sq` — esquema v8 del Proyecto.
- `core/.../repository/GameProjectCheck.kt` — escenarios 1–9 del repositorio.
- `composeApp/.../MainActivity.kt` y `MainViewController.kt` — composition roots
  que construyen repositorios con `::currentTimeMillis` y llaman a `App(...)`.
- `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `init.sh` — sin
  source set de tests de UI; gate Android-only.
- Entorno: `develop` en `e5ff8b9` con `genres-catalog-ui` mergeada; la rama
  `feature/brainstorming-wizard` fue rebaseada sobre ese commit.

### Existing Patterns To Follow

- **Estado en `App`:** sin DI, `ViewModel`, Flow ni librería de navegación; la
  pantalla es "tonta" y emite callbacks.
- **Lectura:** `LaunchedEffect(refreshKey)`; **mutación:** `reload()`.
- **Repositorios:** se construyen en los composition roots y se pasan por
  constructor.
- **Ficha:** `GameProjectRepository.save` normaliza, valida límites/owner y
  reemplaza las hijas en una transacción; no duplicar esa lógica.
- **Géneros:** `GenrePicker` + `normalizeGenres`; el catálogo es
  `GenreCatalog.GENRES`.
- **Mecánicas:** `MechanicCategory.entries`; el `code` persistido es español y
  la etiqueta visible se deriva capitalizando el `code`.
- **Accesibilidad:** texto/forma, no sólo color; Material 3 y objetivos
  táctiles estándar.
- **Verificación:** `:core:testDebugUnitTest` + `:composeApp:assembleDebug` +
  `CI=true ./init.sh`; smoke manual Android para UI.

### Current Gaps

- No existe `BrainstormingWizardScreen`.
- `App` no recibe `GameProjectRepository`, no carga el Proyecto y no tiene
  `NotesDestination.Brainstorming`.
- `NotesListScreen` no tiene entrada al wizard.
- Los composition roots no construyen/pasan `SqlDelightGameProjectRepository`.
- `GameProjectRepository` ya existe y está cubierto por tests, pero no está
  cableado a la UI.
- No hay tests de UI/E2E persistentes.

## Technical Approach

### 1. `BrainstormingWizardScreen.kt` (crear, `:composeApp`)

```kotlin
@Composable
fun BrainstormingWizardScreen(
    initialProject: GameProject?,
    owner: String,
    onSave: (GameProjectDraft) -> Boolean,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- Constantes locales: `TOTAL_STEPS = 9`, `MAX_INSPIRATIONS = 3`,
  `MAX_OUT_OF_SCOPE = 3`, `MAX_KEY_MECHANICS = 5`.
- `STEP_TITLES` en orden: Título, Género, Core Loop, Audiencia, Concepto,
  Inspiraciones, Fantasía, Mecánicas clave, Fuera de alcance.
- Estado local con `remember(initialProject?.updatedAt)` para que el wizard se
  precargue cuando `App` termina de leer el Proyecto:
  - `step` (`0..8`), `title`, `genres`, `coreLoop`, `audience`, `concept`,
    `inspirations`, `fantasy`, `keyMechanics`, `outOfScope`, `saveError`.
  - `inspirations`/`outOfScope` como listas mutables de `String`;
    `keyMechanics` como lista mutable de un `MechanicForm(category:
    MechanicCategory?, description: String)`.
  - Si una lista viene vacía, arranca con **una fila vacía** para permitir
    escribir; el usuario puede quitarla. Los topes cuentan filas.
- Layout:
  - Título "Brainstorming".
  - Fila superior: "Cancelar" (izquierda), `Paso X/9 · <paso>` (centro),
    "Guardar" (derecha, visible en pasos 1–8).
  - `LinearProgressIndicator(progress = { (step + 1) / 9f })`.
  - Texto de apoyo "Todos los pasos son opcionales."
  - Contenido en `Column` con `weight(1f)`, `verticalScroll` e `imePadding()`.
  - Fila inferior fija: "Atrás" (`enabled = step > 0`) y "Siguiente" (pasos
    1–8) o "Guardar" (paso 9).
- Contenido por paso:
  - **Título:** `OutlinedTextField` de una línea.
  - **Género:** `GenrePicker(selected = genres, onChange = { genres = it })`.
  - **Core Loop / Audiencia / Concepto / Fantasía:** `OutlinedTextField`
    multilínea (`minLines` 2–4).
  - **Inspiraciones / Fuera de alcance:** una fila por valor con
    `OutlinedTextField` de una línea + `TextButton("Quitar")`;
    `Button("Agregar inspiración"/"Agregar fuera de alcance")` con
    `enabled = rows.size < max`.
  - **Mecánicas clave:** una fila por mecánica con tres chips de categoría
    (`Movimiento`/`Interacción`/`Conflicto`, selección textual "✓" + borde) y
    `OutlinedTextField` de descripción; `TextButton("Quitar")`;
    `Button("Agregar mecánica")` con `enabled = rows.size < 5`.
- Guardado:
  1. Validar mecánicas: si alguna fila tiene `description.isNotBlank()` y
     `category == null`, `saveError = "Elegí una categoría para cada mecánica
     con descripción."` y no llamar a `onSave`.
  2. Construir el draft **completo**:
     ```kotlin
     GameProjectDraft(
         owner = owner,
         title = title.trim().ifEmpty { null },
         genres = normalizeGenres(genres),
         coreLoop = coreLoop.trim().ifEmpty { null },
         audience = audience.trim().ifEmpty { null },
         concept = concept.trim().ifEmpty { null },
         inspirations = inspirations.map { it.trim() }.filter { it.isNotEmpty() },
         fantasy = fantasy.trim().ifEmpty { null },
         keyMechanics = keyMechanics
             .filter { it.category != null && it.description.isNotBlank() }
             .map { KeyMechanic(it.category!!, it.description.trim()) },
         outOfScope = outOfScope.map { it.trim() }.filter { it.isNotEmpty() },
     )
     ```
  3. `if (!onSave(draft)) saveError = "No se pudo guardar el Proyecto. Intentá
     de nuevo."`. Si `onSave` devuelve `true`, `App` ya navegó a la lista.
- **Nunca** guardar sólo el paso actual: `save` reemplaza la ficha completa y
  un draft parcial borraría campos.
- "Cancelar" llama `onCancel()` sin escribir. "Atrás" no guarda.
- `AlertDialog` para `saveError` con "Aceptar"; no se cierra el wizard.
- Sin `AiClient`, sin creación de Notas, sin `ViewModel`/Flow/DI.

### 2. `App.kt` (modificar)

- Agregar el parámetro `gameProjectRepository: GameProjectRepository` después
  de `noteRepository`.
- Agregar estado `var project by remember { mutableStateOf<GameProject?>(null) }`
  y cargarlo en el mismo `LaunchedEffect(refreshKey)` que `notes`/`attachments`:
  `project = gameProjectRepository.get()`.
- Agregar `data object Brainstorming : NotesDestination`.
- Pasar `onOpenBrainstorming = { destination = NotesDestination.Brainstorming }`
  a `NotesListScreen`.
- Renderizar:
  ```kotlin
  NotesDestination.Brainstorming -> BrainstormingWizardScreen(
      initialProject = project,
      owner = project?.owner ?: LOCAL_OWNER_ID,
      onSave = { draft ->
          val saved = runCatching { gameProjectRepository.save(draft) }.isSuccess
          if (saved) {
              reload()
              goToList()
          }
          saved
      },
      onCancel = ::goToList,
  )
  ```
- `LOCAL_OWNER_ID` sigue siendo privado de `App.kt`; no se mueve a `:core`.

### 3. `NotesListScreen.kt` (modificar)

- Agregar `onOpenBrainstorming: () -> Unit` a la firma.
- Debajo de la fila de acciones existente, agregar una fila propia con un
  `FilledTonalButton(onClick = onOpenBrainstorming, modifier =
  Modifier.fillMaxWidth()) { Text("Brainstorming") }`.
- Es una entrada **temporal**; `main-screen-playbook-shell` la reemplazará por
  el icono de top bar. No rediseñar la lista.

### 4. Composition roots (modificar)

- `MainActivity`: construir
  `val gameProjectRepository: GameProjectRepository =
  SqlDelightGameProjectRepository(database = database, clock =
  ::currentTimeMillis)` y pasarlo a `App(...)` (la llamada ya usa argumentos
  nombrados).
- `MainViewController`: agregar `private var gameProjectRepository:
  GameProjectRepository? = null`; resolverlo con
  `?: SqlDelightGameProjectRepository(database, ::currentTimeMillis).also {
  gameProjectRepository = it }` y pasarlo a `App(...)`. Cambiar la llamada
  posicional actual a **argumentos nombrados** para evitar desorden.
- Actualizar los comentarios de esquema de los roots a v8 si siguen diciendo
  v5 (cambio de comentario acotado).

### 5. Sin cambios fuera de UI

No se toca `:core`, `GameProject.sq`/`.sqm`, `GenrePicker.kt`, build files,
`gradle/libs.versions.toml`, `init.sh` ni `iosApp/**`. No hay dependencias
nuevas.

## Expected File Changes

- `docs/specs/brainstorming-wizard.md` — **create** (este spec).
- `composeApp/src/commonMain/kotlin/com/playbook/app/BrainstormingWizardScreen.kt`
  — **create**; wizard de 9 pasos, estado, guardado/cancelación y error.
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — **modify**;
  repositorio, estado `project`, destino, wiring y entrada.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` —
  **modify**; botón temporal "Brainstorming".
- `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt` —
  **modify**; construir/pasar `SqlDelightGameProjectRepository`.
- `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt` —
  **modify**; construir/pasar el repositorio y usar argumentos nombrados.
- `ARCHITECTURE.md` — **update**; UI del wizard y wiring del Proyecto.
- `docs/technical-discovery.md` — **update**; decisiones del wizard
  (ficha-only, guardado explícito, reutilización de `GenrePicker`).
- `docs/risks-and-open-questions.md` — **update**; cerrar la pregunta de UI
  fina del wizard y registrar que Nota por paso se separó.
- `AGENTS.md` — **update acotado**; sólo la cola de features al cerrar.
- `feature_list.json` y `PROGRESS.md` — **update** al cerrar con evidencia.

Sin cambios: `core/**`, `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`,
`init.sh`, `iosApp/**`, `GenrePicker.kt`, `NoteEditorScreen.kt`, `LabelChip.kt`.

## Visual Design Impact

- UI involved: **yes**. Fuente: `DESIGN.md` (provisional), que ya define
  "Wizard de Brainstorming: stepper 9 pasos con progreso (Paso X/9)" y chips
  con texto/forma, no sólo color.
- Screens or states affected:
  - `NotesListScreen`: nuevo botón temporal "Brainstorming".
  - `BrainstormingWizardScreen`: 9 pasos, progreso, listas dinámicas, chips de
    categoría, estado de error de guardado.
- New design artifact required: **no** — `DESIGN.md` ya describe el wizard; las
  2 sugerencias IA quedan para `brainstorming-ai-suggestions`.
- iOS congelado: no se exige paridad visual ni smoke iOS.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — nueva pantalla/destino, `project` en `App`,
  wiring de `GameProjectRepository` en los roots y separación de Nota por paso.
- `CONSTRAINTS.md`: **not needed** — no surge una regla MUST/MUST NOT nueva; la
  ficha y los límites ya están en `game-project-persistence`.
- `AGENTS.md`: **update acotado** — sólo la línea de próximas features al
  cerrar.
- `docs/technical-discovery.md`: **update** — "Brainstorming Wizard Decisions":
  ficha-only, guardado explícito, `GenrePicker` compartido, límites en UI,
  entrada temporal y sin IA.
- `docs/risks-and-open-questions.md`: **update** — cerrar "UI fina del wizard"
  remitiendo a esta spec; registrar `brainstorming-note-per-step` como feature
  separada y el mapeo paso→Nota pendiente para esa feature.
- `DESIGN.md`, `CONTEXT.md`, `docs/domain-model.md`: **not needed** — no cambia
  el dominio ni la dirección visual.
- `feature_list.json`/`PROGRESS.md`: **update** al implementar/validar.

## Implementation Plan

1. Rebasear la rama sobre `develop` (hecho al planificar: `e5ff8b9`).
2. Crear `BrainstormingWizardScreen.kt` con los 9 pasos, estado, límites,
   `GenrePicker`, mecánicas y guardado/cancelación.
3. Agregar `NotesDestination.Brainstorming`, estado `project` y wiring en
   `App`.
4. Agregar el botón temporal en `NotesListScreen`.
5. Construir/pasar `SqlDelightGameProjectRepository` en `MainActivity` y
   `MainViewController` (argumentos nombrados).
6. Correr verificación + smoke Android + controles negativos.
7. Actualizar docs durables y cerrar `feature_list.json`/`PROGRESS.md`.

## Implementation Tasks

- [x] `BrainstormingWizardScreen.kt` con progreso, 9 pasos y estado inicial
      desde `initialProject`.
- [x] Paso Género con `GenrePicker`; pasos de texto con `OutlinedTextField`.
- [x] Inspiraciones/Fuera de alcance con topes 3 y filas removibles.
- [x] Mecánicas con topes 5, categorías y validación de fila incompleta.
- [x] Guardado completo vía `GameProjectDraft` + error dialog; cancelar sin
      escribir.
- [x] `App`: repositorio, `project`, destino, save/cancel y `reload()`.
- [x] `NotesListScreen`: botón temporal "Brainstorming".
- [x] `MainActivity`/`MainViewController`: wiring del repositorio con argumentos
      nombrados.
- [x] Docs durables, `feature_list.json` y `PROGRESS.md`.
- [x] Verificación + smoke Android + controles negativos + evidencia.

## Verification Plan

- `./gradlew :composeApp:assembleDebug --rerun-tasks` → **BUILD SUCCESSFUL**.
- `./gradlew :core:testDebugUnitTest --rerun-tasks` → **23 suites / 55 tests /
  0 failures** (base `develop` `e5ff8b9`; confirmar el conteo real).
- `CI=true ./init.sh` → **exit 0**; Android-only, IA fake, sin dev servers ni
  red.
- `PlaybookDatabase.Schema.version == 8`; `.sqm` = `1.sqm`..`7.sqm`; ningún
  `.sqm` nuevo; `git diff --stat` vacío en `:core`, build files,
  `gradle/libs.versions.toml`, `init.sh`, `iosApp` y `GenrePicker.kt`.
- **iOS (no bloqueante):**
  `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` compila.
- **Smoke manual Android** (emulador/`adb`; si no hay dispositivo, declararlo
  honestamente):
  1. `./gradlew :composeApp:installDebug`, `pm clear com.playbook.app` y
     lanzar `MainActivity`.
  2. La lista muestra "Brainstorming"; abrirlo → `Paso 1/9 · Título`, `Atrás`
     deshabilitado, barra en 1/9.
  3. Recorrer Siguiente/Atrás y comprobar que los valores se conservan y el
     progreso cambia.
  4. Sin datos, Guardar en el paso 9 → volver a la lista; consultar la DB:
     `game_project` tiene una fila con `id = "singleton"`, `owner = "local"`,
     escalares `NULL` y tablas hijas vacías.
  5. Reabrir → los campos están vacíos (precarga del singleton vacío).
  6. Completar Título, Géneros (catálogo + `Zelda-like`), Core Loop, Audiencia,
     Concepto, 3 Inspiraciones, Fantasía, 5 Mecánicas (Movimiento/Interacción/
     Conflicto) y 3 Fuera de alcance; verificar que "Agregar" se deshabilita en
     3/5/3; Guardar.
  7. Consultar la DB: `game_project` con los escalares; `game_project_genre`,
     `game_project_inspiration`, `game_project_key_mechanic` y
     `game_project_out_of_scope` con los valores y el orden esperados.
  8. Reabrir → todos los valores precargados. Cambiar el Título, quitar una
     Inspiración y una Mecánica, Guardar; la DB refleja el conjunto exacto, sin
     huérfanos, `owner`/`createdAt` preservados y `updatedAt` avanzado.
  9. Hacer cambios locales y Cancelar → la DB no cambia; reabrir muestra lo
     persistido.
  10. Con Título cargado, tocar "Guardar" en el paso 1 → persiste la ficha
      completa y vuelve a la lista.
  11. Agregar una Mecánica con descripción y sin categoría, Guardar → aparece
      el error y no se escribe nada.
- Consultas de evidencia (ejemplo):
  ```bash
  adb shell run-as com.playbook.app sqlite3 databases/playbook.db \
    "SELECT id, owner, title, core_loop, audience, concept, fantasy, created_at, updated_at FROM game_project;"
  adb shell run-as com.playbook.app sqlite3 databases/playbook.db \
    "SELECT * FROM game_project_genre; SELECT * FROM game_project_inspiration; SELECT * FROM game_project_key_mechanic; SELECT * FROM game_project_out_of_scope;"
  ```
- **Controles negativos** (restaurar byte-idéntico y re-verificar):
  (a) "Guardar" sin llamar a `gameProjectRepository.save` → no hay fila en
  `game_project`;
  (b) ignorar `initialProject` en el estado inicial → reabrir no precarga;
  (c) quitar la validación de categoría → una mecánica con descripción y sin
  categoría se descarta sin mostrar el error.
- **E2E persistente: no aplica** — no hay harness de UI/E2E y `init.sh` no
  levanta simuladores. Cobertura: tests de `GameProjectRepository` en `:core`,
  compilación de la app y smoke manual Android. La UI no se testea con
  Compose UI tests porque `:composeApp` no tiene source set de tests.

## Evidence To Capture

- Salida de `:composeApp:assembleDebug`, `:core:testDebugUnitTest` (nombres y
  conteos) y `CI=true ./init.sh` (exit 0).
- `Schema.version == 8`, `.sqm` 1..7 y diff vacío en `:core`/build files/
  `GenrePicker.kt`/`init.sh`/`iosApp`.
- Smoke Android: capturas/UI dumps de `Paso 1/9`, paso Género, paso Mecánicas,
  paso 9, precarga al reabrir y error de mecánica incompleta.
- Salida de las consultas SQL del Proyecto y sus tablas hijas antes/después de
  iterar; prueba de que Cancelar no cambia la DB.
- Controles negativos (a)/(b)/(c) reproducidos y restaurados.
- `git status` sólo con archivos de la feature y docs; sin dependencias,
  secrets, `.sqm` ni cambios de esquema.

## Validator Checklist

- [ ] Alcance respetado: sólo ficha del Proyecto, 9 pasos, guardado explícito y
      entrada temporal; **sin** Nota por paso, IA, onboarding, GDD híbrido,
      rediseño de principal, `:core`, esquema ni deps.
- [ ] Escenarios 1–14 pasan (donde el smoke lo permite, con límites
      declarados).
- [ ] `GameProjectRepository` cableado en `App` y en ambos composition roots;
      `GenrePicker` reutilizado sin duplicar.
- [ ] Guardar envía el draft completo; cancelar no escribe; reabrir precarga;
      owner/createdAt se preservan.
- [ ] Topes 3/3/5 en UI; filas vacías descartadas; mecánica incompleta bloquea
      con error; géneros normalizados.
- [ ] `init.sh` sin cambios y exit 0; iOS sigue compilando; sin `.sqm` nuevo.
- [ ] Docs durables actualizados; `feature_list.json`/`PROGRESS.md` con
      evidencia; `AGENTS.md` sólo la cola.
- [ ] `brainstorming-note-per-step` queda registrada como feature separada y no
      se implementa aquí.
- [ ] No hay E2E persistente y la spec lo justifica.
