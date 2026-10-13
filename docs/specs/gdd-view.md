# Feature Implementation Spec: Vista de GDD que agrupa y resume las notas por track

> **Reconciliación de terminología (obligatoria).** `CONTEXT.md`,
> `docs/domain-model.md`, `docs/build-brief.md` y `docs/design/ui-ux-brief.md`
> fijan el GDD como una **vista agregada de las Notas agrupada por `track`**
> (`mecánicas`/`personajes`/`historia`), sin tabla propia. El entry de
> `feature_list.json` todavía dice "por categoría/etiqueta" (terminología
> obsoleta ya señalada por `docs/specs/note-category-and-tags.md`). Esta feature
> agrupa **por `track`** y muestra las Etiquetas como chips; no introduce
> "Categoría"/"Tipo"/"Nivel" ni agrupación por etiqueta. Al cerrar se corrige el
> `title`/`user_visible_behavior`/`notes` del entry.

## Source Feature

- `id`: `gdd-view`
- `area`: `gdd`
- `depends_on`: `["note-category-and-tags", "notes-list-ui"]` (ambas `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`
- `title` actual (obsoleto): "Vista de GDD que agrupa y resume las notas"
- `user_visible_behavior` actual (obsoleto): "...agrupa y resume sus notas por
  categoría/etiqueta."

## Goal

Entregar la primera **vista de GDD** de Playbook: una pantalla que proyecta las
Notas locales ya persistidas en **secciones por `track`** (arriba), cada una con
su conteo y sus notas resumidas (cuerpo truncado + `status`, Etiquetas y chip de
imagen), con un encabezado de resumen global y la posibilidad de abrir una nota.
El GDD **no es una entidad persistida**: es una proyección pura sobre las Notas
(`docs/domain-model.md`), sin tabla ni query nueva. Es la pantalla "Inicio / GDD"
del brief de producto.

La agregación se implementa como una **función pura en `:core`**
(`buildGdd(notes): List<GddSection>`) para que sea determinista y esté cubierta
por tests cross-platform; la UI (`GddScreen`) es un render fino que además ofrece
navegación a la nota. Se conserva la arquitectura vigente: sin DI, sin
`ViewModel`, sin Flow ni librería de navegación.

## Non-Goals

- **Agrupación por Etiqueta**: no se agrupa por tag (las Etiquetas se muestran
  como chips). No hay filtros, búsqueda ni "gestión global de etiquetas".
- **Categoría/Tipo/Nivel**: descartados (`CONTEXT.md`); el `track` es la
  estructura del GDD.
- IA (embeddings/RAG/enlaces/estado de indexado), adjuntos distintos de mostrar
  el chip "Imagen" existente, audio, OCR.
- Pantalla de **detalle de sólo lectura** y edición de adjuntos (`note-links-ui`
  y features posteriores); abrir una nota del GDD reutiliza el editor existente.
- Cambios de **esquema o migraciones** (`Schema.version` sigue en v4, sin `.sqm`),
  ni del repositorio/servicios existentes más allá de la nueva proyección pura.
- Framework DI, `ViewModel`/`Lifecycle`, coroutines/Flow o librería de
  navegación; manejo del back físico Android/iOS (se ofrece "Volver").
- Formalizar `DESIGN.md`/tokens (sigue `provisional`).

## Job Story

When tengo ya varias notas capturadas y quiero ver mi diseño como un documento,
I want un GDD que agrupe y resuma mis notas por pilar de diseño,
so I can recorrer mi juego de un vistazo y entrar a la nota que quiero ajustar.

## Users And Permissions

- Autor/usuario único del MVP (local-first): ve todas las Notas del dispositivo.
- Sin auth ni permisos. `owner` sigue siendo único (`LOCAL_OWNER_ID` en la UI);
  no cambia.

## Terminology Reconciliation

Fuentes de verdad: `CONTEXT.md`, `docs/domain-model.md`, `docs/build-brief.md`,
`docs/design/ui-ux-brief.md`.

| Término obsoleto | Estado | Reemplazo canónico |
| --- | --- | --- |
| "GDD por categoría/etiqueta" | **Obsoleto** | GDD agrupado por `Track` |
| `Categoría` / `Tipo de nota` | **Rechazado** | El enum fijo y único `Track` |
| `Etiqueta (Tag)` | **Vigente** (se muestra como chip, no agrupa) | Descriptor libre y múltiple |
| "tabla de GDD" | **Rechazado** | Proyección sobre las Notas (sin tabla) |

Reemplazo sugerido de `feature_list.json` al cerrar:
- `title`: "Vista de GDD que agrupa y resume las notas por track"
- `user_visible_behavior`: "El usuario ve un GDD navegable que agrupa y resume
  sus notas por track (mecánicas/personajes/historia) y muestra sus etiquetas."
- `notes`: "El GDD es una vista sobre las notas, sin tabla propia
  (docs/domain-model.md); agrupa por track y no por etiqueta."

## Acceptance Scenarios

### Scenario 1: GDD agrupa por track

Given la base local con notas de distintos `track` (y al menos una sin `track`),
When se abre el GDD desde la lista,
Then se muestran las secciones `mecánicas`, `personajes` e `historia` en ese
orden, cada una con sus notas, y una sección "Sin track" con las notas sin track;
ninguna nota queda fuera ni duplicada.

### Scenario 2: Resumen por sección y global

Given el GDD abierto,
When se recorren las secciones,
Then el encabezado global resume el total de notas, y cada sección muestra su
conteo ("N notas") con texto (no sólo color); cada nota muestra su cuerpo
truncado, su `status`, sus Etiquetas (si tiene) y el chip "Imagen" (si tiene
adjunto).

### Scenario 3: Nota sin track no rompe la vista

Given una base con notas sin `track` (edge case de `docs/domain-model.md`),
When se abre el GDD,
Then esas notas aparecen en la sección "Sin track" y el resto de las secciones se
renderiza normalmente, sin excepción.

### Scenario 4: Navegación

Given el GDD abierto,
When se toca una nota y luego se vuelve,
Then se abre esa nota en el editor existente (mismo comportamiento que desde la
lista) y se puede volver al listado de notas; "Volver" desde el GDD retorna a la
lista.

### Scenario 5: Estado vacío

Given una instalación nueva (base local sin filas),
When se abre el GDD,
Then se muestra un estado vacío explícito ("Todavía no hay notas") sin secciones
ni errores.

### Scenario 6: Proyección determinista (core)

Given una lista de Notas en el orden de `getAll()` (`updated_at DESC, id DESC`),
When se llama `buildGdd(notes)`,
Then devuelve las secciones en orden fijo (`mecánicas`, `personajes`, `historia`,
`Sin track`) y, dentro de cada sección, las notas conservan el orden de entrada;
la proyección no muta la lista ni duplica notas.

### Scenario 7: Refresco y persistencia

Given el GDD visitado y una nota creada/editada/borrada desde otra pantalla,
When se vuelve a abrir el GDD,
Then refleja el estado vigente de las Notas (misma fuente `notes` recargada por
`refreshKey`); no se lee la base dentro del cuerpo de composición.

### Scenario 8: Gate honesto

Given la feature implementada,
When se ejecuta `./init.sh`,
Then sigue terminando en 0 sin dev servers ni simuladores; los tests del core
(Android/JVM e iOS) siguen verdes y `:composeApp` compila/enlaza.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `note-category-and-tags` e
  `image-capture-camera` `accepted`; `gdd-view` en cola con spec pendiente.
- `feature_list.json` — entry `gdd-view` con terminología obsoleta
  ("categoría/etiqueta") y `depends_on` ya satisfechos.
- `CONTEXT.md`, `docs/domain-model.md` — GDD = vista agregada por `track`, sin
  tabla; edge case "Nota sin `track` no rompe la vista".
- `docs/build-brief.md`, `docs/design/ui-ux-brief.md` — "Inicio / GDD: notas
  agrupadas y resumidas por track"; `DESIGN.md` lista "Inicio / GDD" como
  pantalla core.
- `DESIGN.md` — provisional; jerarquía por agrupación (track → nota), regla "no
  sólo color", targets ≥ 48dp; sin tokens definitivos.
- `docs/technical-discovery.md` — SQLDelight única fuente local; esquema v4;
  UI sin DI/ViewModel/Flow; `gdd-view` diferido.
- `ARCHITECTURE.md` — `:composeApp → :core`; `NoteRepository` síncrono; `App`
  hoistea `notes`/`attachments` y refresca con `refreshKey`; `GddScreen` en
  "Deferred".
- `core/.../model/{Note,Track,NoteStatus}.kt` — `Note` con `track` nullable,
  `status`, `tags`; `Track.entries` en orden `MECHANICS`/`CHARACTERS`/`STORY`.
- `core/.../repository/NoteRepository.kt` — `getAll(): List<Note>` ya ordenado
  (`updated_at DESC, id DESC`) y con etiquetas; no hace falta query nueva.
- `composeApp/.../{App,NotesListScreen,NoteEditorScreen}.kt` — `App` con
  `NotesDestination` (`List`/`Create`/`Edit`/`VoiceCapture`/`CreateWithImage`),
  `LaunchedEffect(refreshKey)`; `NotesListScreen` con `Scaffold`, FABs y
  `LabelChip` privado; `NoteCard` privado; `NoteEditorScreen` reutilizable para
  abrir una nota.
- `core/src/commonTest/.../NoteRepositoryCheck.kt` — patrón de helper compartido
  (`verifyX`) + `@Test` por plataforma.
- `composeApp/build.gradle.kts` — `compose.runtime`/`foundation`/`material3`/`ui`
  + `:core`; sin source set de test.
- `init.sh` — gate no bloqueante sin dev servers ni simuladores.

### Existing Patterns To Follow

- Dirección `UI → core`: la agregación de dominio vive en `:core`; `:composeApp`
  sólo renderiza y cablea.
- Sin DI: dependencias/estado por parámetro (`App(...)`, `GddScreen(...)`).
- Tests de lógica en `:core` (`commonTest` + `@Test` por plataforma, sin
  `expect`/`actual` en test).
- Navegación local con `sealed interface NotesDestination`; sin librería.
- `init.sh` no bloqueante, sin procesos de larga duración.
- Documentos en español, identificadores Kotlin en inglés, valores de dominio en
  español.

### Current Gaps

- No existe ninguna vista de GDD ni proyección agrupada por `track`.
- `NotesListScreen` no ofrece entrada al GDD.
- No hay función pura de agrupación/resumen ni sus tests.
- No hay harness de UI/E2E: la lógica de agrupación debe testearse en `:core`.

## Technical Approach

### Proyección pura en `:core` (sin esquema ni repositorio nuevos)

Nuevo `com.playbook.core.gdd.GddProjection` (commonMain), dependiente sólo del
modelo, sin SQLDelight ni `AiClient`:

```kotlin
package com.playbook.core.gdd

import com.playbook.core.model.Note
import com.playbook.core.model.Track

/** Sección del GDD: un [track] (null = "Sin track") con sus notas. */
data class GddSection(
    val track: Track?,        // null = notas sin track
    val notes: List<Note>,    // conserva el orden de entrada (getAll())
)

/**
 * Proyecta las Notas en secciones por track, en orden fijo:
 * Track.entries (mecánicas, personajes, historia) y luego "Sin track".
 * Las tres secciones de track siempre están presentes; "Sin track" sólo si hay
 * notas sin track. No muta la lista ni duplica notas.
 */
fun buildGdd(notes: List<Note>): List<GddSection>
```

- Se **reutiliza** `NoteRepository.getAll()` (ya ordenado y con etiquetas): no se
  agrega tabla, columna, query ni migración (`Schema.version` sigue en v4).
- El resumen derivado (conteos, total) se calcula a partir de las secciones en la
  UI o con helpers triviales; no se persiste.

### Pantalla `GddScreen` (`:composeApp/commonMain`, nueva)

Firma sugerida:

```kotlin
@Composable
fun GddScreen(
    notes: List<Note>,
    attachmentCounts: Map<String, Int>,
    onOpenNote: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- **Estado vacío**: si `notes.isEmpty()`, mensaje explícito ("Todavía no hay
  notas") y acción "Volver". Sin secciones.
- **Encabezado**: título "GDD" + línea de resumen con el total de notas (texto).
- **Secciones**: un bloque por `GddSection`. Encabezado con `track?.code` (o
  "Sin track") + conteo ("N notas") y, si está vacía, un texto "Sin notas en este
  track". Las notas dentro de la sección se renderizan en `LazyColumn` con
  `key = { it.id }`, cuerpo truncado (máx. 2 líneas) y chips textuales de
  `status` + Etiquetas + "Imagen" (si `attachmentCounts[note.id] > 0`).
- **Navegación**: cada nota es clickeable (`clickable(onClickLabel = "Abrir
  nota", role = Role.Button)`) → `onOpenNote(note.id)`; botón "Volver" →
  `onBack()`. Todo se comunica con texto, no sólo color (`DESIGN.md`).
- Componentes: `Scaffold` con botón "Volver" en la barra/encabezado (o un
  `TextButton`); sin `TopAppBar` experimental si se puede evitar (basta un
  encabezado `Row`). Se puede reutilizar/extaer el `LabelChip` (hoy privado en
  `NotesListScreen`) a un archivo común; no es obligatorio.

### Wiring y navegación (`App.kt`)

- Nuevo `data object Gdd : NotesDestination`.
- `NotesListScreen` suma una acción "Ver GDD" (p. ej. `FilledTonalButton` junto a
  "Dictar nota") con `onOpenGdd: () -> Unit`.
- En `App`:
  - `NotesDestination.List → onOpenGdd = { destination = NotesDestination.Gdd }`.
  - `NotesDestination.Gdd → GddScreen(notes, attachmentCounts, onOpenNote =
    { id -> destination = NotesDestination.Edit(id) }, onBack = ::goToList)`.
- **Decisión**: abrir una nota desde el GDD reutiliza el editor (`Edit`) y, al
  guardar/cancelar, vuelve a `List` (comportamiento vigente). Volver al GDD tras
  editar queda fuera de alcance (documentado como limitación). No se agrega
  estado de "destino previo".
- Sin DI, sin `ViewModel`, sin Flow, sin coroutines propias, sin librería de
  navegación.

### Dependencias, esquema y `init.sh`

- **Sin dependencias nuevas**: `Scaffold`, `LazyColumn`, `Card`/`Surface`,
  `Button`/`TextButton` y `mutableStateOf` ya están disponibles.
- **Sin cambios de esquema** (`Schema.version = 4`, sin `.sqm`), ni en
  `NoteRepository`/drivers/`MainActivity`/`MainViewController`.
- **`init.sh` no cambia**: sigue no bloqueante y sin procesos de larga duración.

### Riesgos y decisiones abiertas

- **Agrupación en `:core` vs UI**: se elige `:core` para tener tests
  cross-platform deterministas (no hay harness de UI). Es una función pura sin
  persistencia; no crea deuda arquitectónica.
- **Secciones vacías**: las tres secciones de `track` se muestran siempre
  (estructura estable del GDD); "Sin track" sólo si hay notas sin track. Si el
  producto prefiere omitir secciones vacías, se ajusta el helper sin cambiar el
  contrato de la UI.
- **Retorno tras editar desde el GDD**: vuelve a la lista (no al GDD). Aceptado
  como limitación de esta rebanada; un `previousDestination` llega con una
  feature de navegación/detalle.
- **Sin detalle de sólo lectura**: tocar una nota abre el editor; el detalle de
  lectura es `note-links-ui`/posterior.

## Expected File Changes

- `core/src/commonMain/kotlin/com/playbook/core/gdd/GddProjection.kt` — crear;
  `GddSection` + `buildGdd(notes)` (proyección pura por track).
- `core/src/commonTest/kotlin/com/playbook/core/gdd/GddProjectionCheck.kt` —
  crear; `verifyGddProjection()` (agrupación, orden, sin track, sin duplicados).
- `core/src/androidUnitTest/kotlin/com/playbook/core/gdd/GddProjectionAndroidTest.kt`
  — crear; `@Test` que corre el helper en JVM.
- `core/src/iosTest/kotlin/com/playbook/core/gdd/GddProjectionIosTest.kt` —
  crear; idem iOS.
- `composeApp/src/commonMain/kotlin/com/playbook/app/GddScreen.kt` — crear;
  estado vacío, resumen, secciones por track y navegación.
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — modificar;
  `NotesDestination.Gdd` y wiring de la pantalla.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` —
  modificar; acción "Ver GDD" (y, opcional, extraer `LabelChip` a común).
- `ARCHITECTURE.md` — modificar; superficie UI del GDD y proyección en `:core`.
- `docs/technical-discovery.md` — modificar; decisión de GDD como proyección.
- `AGENTS.md` — modificar al cerrar; línea "Próxima feature en cola".
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; terminología
  reconciliada, estado y evidencia.

No se esperan cambios en `core/src/commonMain/sqldelight/**` (esquema),
`core/build.gradle.kts`, `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`,
`init.sh`, `iosApp/**`, `MainActivity`/`MainViewController` ni el resto del
repositorio de notas.

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` (provisional); `docs/design/ui-ux-brief.md` define la
  pantalla "Inicio / GDD: notas agrupadas y resumidas por track". Se sigue su
  dirección estructural (jerarquía por agrupación track → nota) y la regla "no
  sólo color". No se fijan tokens ni se crea artefacto nuevo.
- Screens or states affected: "Inicio / GDD" (nueva: resumen + secciones por
  track + estado vacío), "Lista de Notas" (acción "Ver GDD").
- New design artifact required: no — la entrega de UI/UX sigue pendiente.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — nueva pantalla de UI (GDD), proyección pura en
  `:core` y la navegación `List ↔ Gdd ↔ Edit`; quitar el GDD de "Deferred".
- `CONSTRAINTS.md`: not needed — no surge una regla MUST/MUST NOT durable nueva.
- `AGENTS.md`: **update** — cambia "Próxima feature en cola"; no cambian workflow
  ni gate.
- Other docs: `docs/technical-discovery.md` — update (decisión de GDD como
  proyección sin tabla); `feature_list.json`/`PROGRESS.md` — update al cerrar
  (terminología y evidencia). `CONTEXT.md`/`docs/domain-model.md` — not needed
  (ya definen el GDD y esta spec se alinea). `docs/adr/` — not needed.

## Implementation Plan

1. Crear `GddProjection.kt` en `:core` (`GddSection` + `buildGdd`).
2. Escribir `verifyGddProjection()` y los `@Test` por plataforma; correr `:core`
   en Android/JVM e iOS.
3. Crear `GddScreen.kt` (estado vacío, resumen, secciones y navegación).
4. Agregar `NotesDestination.Gdd` y cablear `App`; agregar "Ver GDD" en
   `NotesListScreen`.
5. Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
6. Correr verificación completa, smoke manual Android/iOS y registrar evidencia;
   actualizar `feature_list.json` (terminología), `PROGRESS.md` y `AGENTS.md`.

## Implementation Tasks

- [x] Crear `GddSection`/`buildGdd(notes)` en `:core` con orden fijo y sin
  duplicados.
- [x] Escribir `verifyGddProjection()` + `@Test` por plataforma.
- [x] Crear `GddScreen` con estado vacío, resumen, secciones por track y chips.
- [x] Agregar `NotesDestination.Gdd` y el wiring en `App`.
- [x] Agregar la acción "Ver GDD" en `NotesListScreen`.
- [x] Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
- [x] Correr `./init.sh` y los comandos de verificación; registrar evidencia.
- [x] Smoke manual Android/iOS (secciones, sin track, navegación, estado vacío).
- [x] Actualizar `feature_list.json` (terminología), `PROGRESS.md` y `AGENTS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest` → BUILD SUCCESSFUL; `GddProjectionAndroidTest`
  verde (agrupación, orden, sin track, sin duplicados) y los tests previos siguen
  verdes.
- `./gradlew :core:iosSimulatorArm64Test` (macOS) → el mismo helper verde en iOS.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (UI + wiring Android).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL;
  `xcodebuild` del scheme `iosApp` → BUILD SUCCEEDED.
- Esquema sin cambios: `PlaybookDatabase.Schema.version == 4`, sin `.sqm` nuevos;
  `getAll()` sin cambios.
- Smoke manual Android (emulador) e iOS (simulador): abrir el GDD →
  secciones por track ordenadas con conteos; nota sin track en "Sin track"; chip
  "Imagen"/Etiquetas/`status` visibles; abrir una nota; "Volver"; base vacía →
  estado vacío.
- `./init.sh` → exit 0; ejecuta el gate no bloqueante actual, **no** inicia dev
  servers ni simuladores, y puede imprimir los comandos manuales.
- E2E persistente: **no aplica** — no existe harness de UI/E2E y `init.sh` no
  levanta simuladores. La lógica de agrupación se cubre con tests del core en dos
  plataformas y la UI con smoke manual, igual que en las features de UI previas.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` con
  `GddProjection` verde y los tests previos verdes (nombres y conteos).
- Salida de `:composeApp:assembleDebug`, link del framework iOS y `xcodebuild`.
- Confirmación de que `Schema.version == 4` sin `.sqm` nuevos, sin dependencias
  nuevas y sin cambios en `init.sh`.
- Smoke manual Android/iOS: capturas/UI dumps con secciones por track, conteos,
  "Sin track", chips y navegación; estado vacío.
- Salida de `./init.sh` (exit 0) sin procesos de larga duración.

## Validator Checklist

- [ ] Alcance respetado: GDD = vista agrupada **por `track`**, sin tabla, sin
  Categoría/Tipo/Nivel, sin agrupación por Etiqueta, sin filtros, sin IA, sin
  detalle de sólo lectura ni edición de adjuntos.
- [ ] Escenarios de aceptación 1–8 pasan.
- [ ] `buildGdd` es determinista (orden fijo de secciones, orden de notas
  preservado, sin duplicados, sin mutar la entrada) y está cubierto por tests en
  Android/JVM e iOS.
- [ ] Notas sin `track` no rompen la vista (sección "Sin track" o estado válido).
- [ ] La UI se comunica con texto (no sólo color) y ofrece navegación/volver.
- [ ] Sin esquema/migraciones nuevos (`Schema.version == 4`, sin `.sqm`), sin
  dependencias nuevas, sin DI/`ViewModel`/Flow/librería de navegación; `init.sh`
  sin cambios y no bloqueante.
- [ ] `feature_list.json` corregido a la terminología vigente (por track) y
  evidencia en `feature_list.json`/`PROGRESS.md`; `ARCHITECTURE.md` y
  `docs/technical-discovery.md` actualizados.
- [ ] No hay E2E persistente y la spec lo justifica.
