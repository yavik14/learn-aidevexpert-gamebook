# Feature Implementation Spec: Lista de notas en Compose Multiplatform

## Source Feature

- `id`: `notes-list-ui`
- `area`: `ui`
- `depends_on`: `["note-model-crud-core"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Mostrar en pantalla, por primera vez, la **lista de notas locales** del usuario en
las dos plataformas. La feature cablea el `NoteRepository` (ya implementado en
`:core`) a la UI de Compose Multiplatform y renderiza un estado **con notas** y un
estado **vacío**. Es una pantalla **solo de lectura**: no crea, edita ni borra.

Habilita `create-text-note` (que introducirá las mutaciones y el refresco de la
lista) y `gdd-view`. No agrega IA, etiquetas ni adjuntos.

## Non-Goals

- Crear/editar/borrar notas desde la UI (pertenece a `create-text-note`).
- Etiquetas, adjuntos, enlaces, embeddings, indexado/IA, estado de enriquecimiento.
- Agrupar por `track` o resumen tipo GDD (`gdd-view`), búsqueda, filtros o navegación
  a detalle.
- Framework DI, ViewModel/`Lifecycle`, Flow/observabilidad reactiva o refresco tras
  mutaciones.
- Cambios de esquema/migraciones: `schema.version` sigue en 1, sin `.sqm`.
- Formalizar `DESIGN.md` (sigue `status: provisional`). No hay entrega de UI/UX.

## Job Story

When abro Playbook en mi teléfono,
I want ver de un vistazo las notas que ya capturé (o una indicación clara de que
todavía no hay ninguna),
so I can confirmar que mis ideas quedaron guardadas en el dispositivo.

## Users And Permissions

- Autor/usuario único del MVP (local-first): ve todas las notas del dispositivo.
- Sin auth, permisos ni acciones de escritura en esta feature.

## Acceptance Scenarios

### Scenario 1: Base vacía → estado vacío

Given una instalación nueva (base local sin filas),
When se abre la app en Android o iOS,
Then se muestra el estado vacío ("Todavía no hay notas") sin lista y sin errores.

### Scenario 2: Base con notas → lista ordenada

Given la base local con notas persistidas (sembradas para QA),
When se abre la app,
Then se muestra una lista con el cuerpo de cada nota y su `track`/`status`
etiquetados con texto, ordenada por `updated_at DESC, id DESC` (más reciente
primero), y sin elementos de IA/etiquetas/adjuntos.

### Scenario 3: Wiring del repositorio sin DI

Given la base ya creada al arranque,
When `MainActivity` (Android) y `MainViewController` (iOS) construyen el
`SqlDelightNoteRepository` con los defaults de plataforma y lo pasan a `App(...)`,
Then la app compila y arranca en ambas plataformas sin crashes ni cambios de
esquema.

### Scenario 4: Orden determinista en el core

Given varias notas persistidas,
When se llama `NoteRepository.getAll()`,
Then devuelve las filas en orden `updated_at DESC, id DESC` (determinista y
estable ante empates), cubierto por el test compartido del core.

### Scenario 5: Solo lectura y gate honesto

Given la feature implementada,
When se ejecuta `./init.sh`,
Then sigue terminando en 0 sin dev servers ni simuladores; los tests del core
(Android/JVM e iOS) siguen verdes, `:composeApp:assembleDebug` compila y el
framework iOS enlaza.

## Repository Research

### Files Inspected

- `AGENTS.md` — flujo SDD, WIP=1, gate no bloqueante; la línea "Próxima feature en
  cola" quedó apuntando a `note-model-crud-core` (ya `accepted`).
- `PROGRESS.md` — `note-model-crud-core` `accepted` (Session 008); próximo paso
  esta spec.
- `feature_list.json` — metadata de `notes-list-ui`; verificación provisional y
  nota "DESIGN.md diferido" (hoy desactualizada: `DESIGN.md` existe provisional).
- `ARCHITECTURE.md` — `:composeApp → :core`; dominio/repositorio en `:core`; UI y
  wiring de plataforma en `:composeApp`; "Deferred: UI de lista y wiring".
- `CONTEXT.md`, `docs/domain-model.md` — `Track` (`mecánicas`/`personajes`/
  `historia`) vs `Etiqueta`; estados `capturada`/`pendiente`/`indexada`/`fallida`;
  "Nota sin `track` no debe romper la vista".
- `docs/build-brief.md` — slice de UI de lista; captura y IA en fases posteriores.
- `docs/technical-discovery.md` — SQLDelight única fuente local; `createDatabase`
  con `executeQuery`; tests in-memory por plataforma.
- `docs/risks-and-open-questions.md` — tokens visuales provisionales; riesgo de
  drift visual; `DESIGN.md` provisional.
- `DESIGN.md` — dirección visual provisional (Material 3, cuaderno cálido, pantalla
  "Lista de Notas: por track; con estado vacío"); **no** es fuente de verdad.
- `docs/specs/note-model-crud-core.md` — contrato del repositorio y de `getAll()`.
- `docs/specs/local-persistence-sqldelight.md` — esquema v1, drivers y regla
  "evitar `expect/actual` en tests".
- `composeApp/src/commonMain/.../App.kt` — `App()` placeholder, único punto de UI;
  sin parámetros.
- `composeApp/src/androidMain/.../MainActivity.kt` — crea la DB; `App()` sin args.
- `composeApp/src/iosMain/.../MainViewController.kt` — crea la DB a nivel módulo;
  `App()` sin args.
- `composeApp/build.gradle.kts` — Compose MP (`runtime`, `foundation`, `material3`,
  `ui`) + `:core`; sin deps de test.
- `core/.../repository/{NoteRepository,SqlDelightNoteRepository}.kt` — CRUD
  **síncrono** (`getAll(): List<Note>`); sin Flow.
- `core/.../platform/PlatformDefaults.*.kt` — `randomNoteId()`/`currentTimeMillis()`
  `expect`/`actual`.
- `core/.../db/Note.sq` — `selectAllNotes` **sin `ORDER BY`** (hueco señalado por el
  validador de la feature previa).
- `core/.../commonTest/.../NoteRepositoryCheck.kt` + tests por plataforma — patrón
  de test compartido sobre un `SqlDriver`.
- `iosApp/iosApp/ContentView.swift` — `ComposeView` instancia `MainViewController()`.
- `init.sh` — gate: `assembleDebug` + tests del core (Android/JVM e iOS) + link iOS.

### Existing Patterns To Follow

- Dirección `UI → core`: la UI consume `NoteRepository`; no se mueve lógica de
  dominio a `:composeApp`.
- Wiring de plataforma en `MainActivity`/`MainViewController`; `commonMain` sólo UI.
- Sin DI: las dependencias se pasan como parámetros (como ya se hace con la DB).
- Tests compartidos del core en `commonTest` + `@Test` por plataforma con drivers
  in-memory.
- `init.sh` no bloqueante, sin dev servers ni simuladores.
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- `App()` no recibe dependencias; la UI placeholder no lee datos.
- El `NoteRepository` no está instanciado en la app (sólo existe la DB).
- No hay pantalla de lista, estado vacío, ni `LazyColumn`.
- `selectAllNotes` no define orden: la lista sería no determinista.
- `:composeApp` no tiene source set de test ni harness de UI tests.
- La nota de `feature_list.json` sobre `DESIGN.md` está desactualizada.

## Technical Approach

### Wiring del repositorio (sin DI)

En los entry points de plataforma, tras `createDatabase(...)`:

```kotlin
// MainActivity (androidMain) y MainViewController (iosMain)
val database = createDatabase(AndroidDatabaseDriverFactory(applicationContext)) // / IosDatabaseDriverFactory()
val noteRepository: NoteRepository = SqlDelightNoteRepository(
    database = database,
    idFactory = ::randomNoteId,
    clock = ::currentTimeMillis,
)
setContent { App(noteRepository) } // ComposeUIViewController { App(noteRepository) } en iOS
```

- `App(noteRepository: NoteRepository)` en `commonMain` recibe la dependencia; no se
  introduce DI ni se expone el `PlaybookDatabase` a la UI.
- Se pasa el `NoteRepository` completo (no una interfaz *read-only* extra): ya es la
  abstracción del core y `create-text-note` reutilizará el mismo wiring.

### Lectura de la lista (sin ViewModel/Flow)

El repositorio es **síncrono** y no hay mutaciones ni reactividad todavía. La
lectura se hace una vez por composición con `remember`:

```kotlin
@Composable
fun App(noteRepository: NoteRepository) {
    MaterialTheme {
        val notes = remember(noteRepository) { noteRepository.getAll() }
        NotesListScreen(notes = notes)
    }
}
```

- **No** se agrega `ViewModel`/`Lifecycle`/coroutines/Flow: sería infraestructura y
  complejidad sin consumidor real. La lectura en el hilo de composición es aceptable
  para una tabla local pequeña en esta primera rebanada.
- Se documenta explícitamente que **no hay refresco tras mutaciones**: cuando llegue
  `create-text-note` habrá que re-leer o introducir un estado observable. Fuera de
  alcance aquí.

### Pantalla y estados (`NotesListScreen.kt`, `commonMain`)

- `Scaffold`/`Column` con título "Notas" (sin `TopAppBar` experimental: un `Text`
  de encabezado basta) y `LazyColumn` para el contenido.
- **Estado vacío** (`notes.isEmpty()`): mensaje centrado "Todavía no hay notas"
  (y una línea de apoyo neutra). Sin botón de creación.
- **Estado con notas**: un `Card`/ítem por nota con el `body` (máx. 2 líneas,
  ellipsis) y una fila de etiquetas de texto: `track.code` (o "Sin track" si
  `track == null`) y `status.code`. Se usan **texto**, no sólo color (accesibilidad;
  `DESIGN.md` prohíbe comunicar track/estado sólo por color).
- Lista **plana** (sin agrupar por `track`): el agrupado es `gdd-view`.

### Orden determinista (soporte acotado en `:core`)

- `selectAllNotes` pasa a `SELECT * FROM note ORDER BY updated_at DESC, id DESC;`
  (`id` desempata para ser estable). Es un cambio de **query**, no de esquema
  (`schema.version` sigue en 1, sin `.sqm`).
- Se extiende `verifyNoteCrud` (mismo harness existente, sin infra nueva) para
  constatar que `getAll()` respeta `updated_at DESC, id DESC`, comparando contra la
  lista ordenada en Kotlin con el mismo criterio.
- Se elige SQL (no ordenar en la UI) para que el orden sea una garantía del
  repositorio, reutilizable por futuras vistas; es una línea y no rompe el contrato
  previo (sólo agrega determinismo).

### Diseño visual

- `DESIGN.md` existe y es `status: provisional` (el equipo UI/UX aún no entregó
  tokens). Se sigue su **dirección estructural** (mobile-first, una columna, calidez
  tipo cuaderno) usando `MaterialTheme` de Material 3; no se formalizan colores,
  tipografías ni un theme propio en esta feature.
- No se requiere artefacto de diseño nuevo. La nota "DESIGN.md diferido" de
  `feature_list.json` es obsoleta y debe corregirse al cerrar.

### Dependencias y `init.sh`

- **No se agregan dependencias**: `compose.foundation` (LazyColumn) y
  `compose.material3` ya están en `composeApp/build.gradle.kts`.
- **`init.sh` no cambia**: ya corre `assembleDebug`, los tests del core (Android/JVM
  e iOS) y el link del framework; sigue no bloqueante y sin procesos de larga
  duración. Puede imprimir los comandos manuales (ya lo hace).

### Verificación realista

- No existe harness de UI tests ni comando E2E persistente, y `init.sh` no levanta
  simuladores por diseño. Por eso la verificación es: **compilación** + **tests del
  core** (incluye el orden) + **smoke manual en Android e iOS**.
- Para el estado **no vacío** en dispositivo no hay UI de creación: se siembra la
  base local **sólo para QA** (p. ej. `sqlite3` sobre el archivo `playbook.db` del
  emulador/simulador, o un cambio local temporal). **Queda prohibido commitear
  código de seed**; el diff final sólo contiene la lectura.

## Expected File Changes

- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — modificar; recibe
  `NoteRepository`, lee la lista con `remember` y delega en la pantalla.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` — crear;
  estados vacío/con notas y el ítem de nota.
- `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt` — modificar;
  construir `SqlDelightNoteRepository` y pasarlo a `App(...)`.
- `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt` — modificar;
  idem en iOS.
- `core/src/commonMain/sqldelight/com/playbook/core/db/Note.sq` — modificar;
  `selectAllNotes` con `ORDER BY updated_at DESC, id DESC`.
- `core/src/commonTest/kotlin/com/playbook/core/repository/NoteRepositoryCheck.kt` —
  modificar; assert de orden determinista.
- `ARCHITECTURE.md` — modificar; nueva superficie de UI y wiring del repositorio.
- `docs/technical-discovery.md` — modificar; registrar decisiones de UI/lectura/orden.
- `AGENTS.md` — modificar; corregir la línea obsoleta "Próxima feature en cola".
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado, evidencia y nota
  de `DESIGN.md`.

No se esperan cambios en `composeApp/build.gradle.kts`, `core/build.gradle.kts`,
`gradle/libs.versions.toml`, `init.sh`, `iosApp/**` ni `Note.sq` más allá del
`ORDER BY`.

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` (provisional; no es fuente de verdad). Se siguen su
  dirección estructural y la regla de accesibilidad "no sólo color"; no se fijan
  tokens.
- Screens or states affected: "Lista de Notas" — estado vacío y estado con notas.
- New design artifact required: no — la entrega de UI/UX sigue pendiente y esta
  feature no la bloquea.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — aparece la primera superficie de UI real y el
  wiring `MainActivity`/`MainViewController → NoteRepository → App(...)`.
- `CONSTRAINTS.md`: not needed — no surge una regla MUST/MUST NOT durable nueva.
- `AGENTS.md`: **update** — la línea "Próxima feature en cola" apunta a una feature
  ya aceptada; corregirla a `notes-list-ui`/siguiente. No cambian workflow ni gate.
- Other docs: `docs/technical-discovery.md` — update (decisiones de UI y orden);
  `feature_list.json`/`PROGRESS.md` — update al cerrar; `DESIGN.md` — not needed
  (provisional, sin entrega de UI/UX); `CONTEXT.md`/`docs/domain-model.md` — not
  needed.

## Implementation Plan

1. Agregar `ORDER BY updated_at DESC, id DESC` a `selectAllNotes` y extender el test
   compartido del core para el orden.
2. Cambiar `App()` a `App(noteRepository: NoteRepository)` y crear
   `NotesListScreen` con estado vacío y con notas.
3. Instanciar `SqlDelightNoteRepository` en `MainActivity` y `MainViewController`
   con `::randomNoteId`/`::currentTimeMillis` y pasar el repositorio a `App(...)`.
4. Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y `AGENTS.md`.
5. Correr la verificación (build + tests + `./init.sh`), hacer smoke manual en
   Android e iOS (vacío + sembrado) y registrar evidencia.

## Implementation Tasks

- [x] Agregar `ORDER BY updated_at DESC, id DESC` a `selectAllNotes` en `Note.sq`.
- [x] Extender `verifyNoteCrud` con la aserción de orden determinista.
- [x] Crear `NotesListScreen.kt` con el estado vacío y el estado con notas.
- [x] Cambiar la firma de `App()` para recibir `NoteRepository` y leer con `remember`.
- [x] Construir el repositorio y pasarlo a `App(...)` en `MainActivity`.
- [x] Construir el repositorio y pasarlo a `App(...)` en `MainViewController`.
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y `AGENTS.md`.
- [x] Correr `./init.sh` y los comandos de verificación; registrar evidencia.
- [x] Smoke manual: estado vacío (instalación nueva) y estado con notas (sembrado)
      en emulador Android y simulador iOS; capturar pantallas.

## Verification Plan

- `./gradlew :core:testDebugUnitTest` → tests del core verdes, incluida la
  aserción de orden de `getAll()`.
- `./gradlew :core:iosSimulatorArm64Test` (macOS) → el mismo helper verde en iOS.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (UI + wiring Android).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (UI + wiring iOS).
- Smoke manual Android (emulador): instalación nueva → estado vacío; base sembrada
  → lista ordenada. Captura de pantalla.
- Smoke manual iOS (simulador): idem. Captura de pantalla.
- `./init.sh` → exit 0; corre el gate no bloqueante actual, **no** inicia dev
  servers ni simuladores, y puede imprimir los comandos manuales.
- E2E persistente: **no aplica**; no existe harness de UI tests/comando E2E y
  `init.sh` no levanta simuladores. Se justifica: el path de datos (`getAll()`
  ordenado) está cubierto por los tests del core en dos plataformas y la UI es un
  render fino verificado manualmente en Android e iOS.
- Para el estado no vacío: sembrar la base local sólo para QA; **no** commitear
  código de seed.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` (tests verdes,
  con nombre del test de orden).
- Salida de `:composeApp:assembleDebug` y `:composeApp:linkDebugFrameworkIosSimulatorArm64`.
- Salida de `./init.sh` (exit 0) sin procesos de larga duración.
- Capturas del estado vacío y del estado con notas en Android e iOS.
- Confirmación de que `schema.version` sigue en 1, sin `.sqm`, y de que no se
  agregaron dependencias ni código de seed.

## Validator Checklist

- [ ] Alcance respetado: sólo lectura de la lista; sin crear/editar/borrar, tags,
      adjuntos, IA ni GDD.
- [ ] Escenarios de aceptación 1–5 pasan.
- [ ] El wiring usa `NoteRepository` pasado a `App(...)` sin DI ni ViewModel.
- [ ] `getAll()` es determinista (`updated_at DESC, id DESC`) y está cubierto por tests.
- [ ] El estado vacío se maneja y el estado con notas muestra `body`/`track`/`status`
      con texto (no sólo color).
- [ ] Evidencia en `feature_list.json` / `PROGRESS.md`.
- [ ] No hay E2E persistente y la spec lo justifica; no se commiteó código de seed.
- [ ] `ARCHITECTURE.md`, `docs/technical-discovery.md` y `AGENTS.md` actualizados.
- [ ] `init.sh` sin cambios, no bloqueante y sin procesos de larga duración.
