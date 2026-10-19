# Feature Implementation Spec: UI de Géneros (chips con catálogo + libre)

## Source Feature

- `id`: `genres-catalog-ui`
- `area`: `ui`
- `depends_on`: `["genres-catalog-persistence", "create-text-note"]` (ambos `accepted`)
- `status`: `not_started` (al planificar)
- `source`: `feature_list.json`

> **Revisión de alcance (2026-10-19).** `genres-catalog-persistence` dejó dominio y
> persistencia (esquema v7) listos con UI de **texto libre** y una deuda visual
> explícita: el chip de Género del catálogo llega con esta feature. Aquí se agrega el
> picker de catálogo y se unifica el chip de Género en las superficies existentes.
> No cambia el modelo, el esquema ni la IA.

## Goal

Que el usuario elija **Géneros del catálogo fijo** (`GenreCatalog.GENRES`, 12) o
escriba un **Género personalizado** desde el editor, y que todos se vean como chips
(catálogo vs personalizado) en editor, tarjetas, GDD y Notas relacionadas. Toda alta
pasa por `normalizeGenres` de `:core` (trim, canonización case-insensitive, dedupe,
orden). Entregable: **sólo `:composeApp`** + docs/evidencia; sin cambios en `:core`,
esquema, `App.kt`, build files ni dependencias.

## Non-Goals

- `:core`, dominio, `NoteRepository`, SQLDelight y esquema: esta feature **no agrega
  `.sqm`**. En la base rebasada el esquema global es **v8** (`7.sqm` de
  `game-project-persistence`, ya integrado).
- `App.kt`, navegación, estado y wiring (el contrato `initialGenres`/`onSave` alcanza).
- IA/sugerencia de clasificación, autocompletado y clasificación automática.
- Gestión global de Géneros, tabla `genre`, favoritos, filtros o búsqueda.
- Límites de cantidad o largo para personalizados en el MVP (decisión 2026-10-19).
- Proyecto/Brainstorming/GDD híbrido; sólo se reutiliza el chip en superficies actuales.
- Rediseño del editor o formalización de tema/paleta; se usan colores semánticos de M3.
- Tests de UI/E2E nuevos (no hay harness y no se incorpora uno).

## Job Story

When capturo o edito una Nota y quiero clasificarla, I want elegir Géneros del
catálogo de un toque o escribir uno propio, so I can describir el tipo de juego sin
recordar la grafía ni terminar con duplicados o inconsistencias.

## Users And Permissions

- Autor único local-first: crea/edita Notas y sus Géneros; sin auth ni permisos nuevos.
- Desarrollador: corre Gradle/tests y smoke manual en emulador Android.

## Scope Decisions

> **Resueltas con el autor (2026-10-19). Vinculantes para el implementer.**

1. **Toggle:** tocar un chip del catálogo lo agrega si no está y lo quita si está; los
   elegidos también se quitan con `×`.
2. **Layout:** los 12 chips del catálogo siempre visibles en un `FlowRow` (sin sección
   colapsable).
3. **Visual:** catálogo = `primaryContainer` (proxy de `chip-genre` sin tema propio);
   personalizado = `secondaryContainer`; ambos siempre con texto. Se aplica en editor,
   tarjetas, GDD y relacionadas.
4. **Campo libre:** rótulo **"Género personalizado"**, placeholder "Escribí uno nuevo…",
   botón **"Agregar"**.
5. **Sin límites** de cantidad/largo. Los chips truncan la etiqueta a una línea con
   ellipsis; el dato persistido no se recorta.

## Acceptance Scenarios

### Scenario 1: el picker muestra el catálogo
Given el editor en modo creación, When se renderiza "Géneros", Then se ven los 12
chips en orden de catálogo, ninguno seleccionado y sin sección "Seleccionados".

### Scenario 2: toggle de catálogo
Given el editor sin Géneros, When se toca "Platformer" y "JRPG", Then ambos aparecen
seleccionados (con `✓` y borde de 2.dp en el catálogo) y en "Seleccionados" como chips
removibles en orden alfabético; When se vuelve a tocar "JRPG", Then desaparece de
ambas áreas y "Platformer" sigue seleccionado.

### Scenario 3: personalizado y normalización
Given "Platformer" seleccionado, When se escribe `"  zelda-like  "` y "Agregar", Then
se agrega `"zelda-like"` como personalizado y el campo se limpia. When se escribe
`"jrpg"` y "Agregar", Then queda `"JRPG"` (canonización), el chip del catálogo se
marca y no hay duplicado. When el texto es `"   "`, Then "Agregar" está deshabilitado.

### Scenario 4: dedupe case-insensitive
Given "JRPG" ya seleccionado, When se agrega `"jrpg"` o `" JRPG "`, Then el conjunto no
cambia, no hay chip duplicado y el campo se limpia.

### Scenario 5: editar una Nota existente
Given una Nota con Géneros persistidos, When se abre el editor, Then el estado inicial
pasa por `normalizeGenres` (un `"jrpg"` migrado verbatim se muestra "JRPG" y marca el
chip) y "Seleccionados" refleja el conjunto. When se quita uno, se agrega otro y se
guarda, Then se persiste el conjunto exacto, `updatedAt` avanza y la tarjeta lo refleja.

### Scenario 6: chips consistentes
Given una Nota con un Género de catálogo y uno personalizado, When se muestra en la
tarjeta, el GDD y Notas relacionadas, Then ambos son chips con texto y estilo de
catálogo vs personalizado, sin depender sólo del color.

### Scenario 7: persistencia tras relanzar
Given una Nota guardada con Géneros, When se cierra y reabre la app, Then lista y
editor conservan los mismos Géneros y estados de selección.

### Scenario 8: accesibilidad y tamaño táctil
Given el picker renderizado, When se inspecciona la semántica, Then cada chip del
catálogo expone "Agregar género X"/"Quitar género X" y su estado `selected`, comunica
la selección con `✓` + borde y los controles nuevos apuntan a ≥ 48.dp.

### Scenario 9: gate y disciplina de alcance
Given la implementación, When corren verificación y gate, Then
`:composeApp:assembleDebug` compila, `:core:testDebugUnitTest` sigue en verde
(suite actual 55/55 en la base rebasada; 53 pre-integración; 47 + `AiPortsAndroidTest` 6),
`./init.sh` sale 0, no hay `.sqm` nuevos de esta feature (el global es v8 por
`game-project-persistence`) y no hay cambios en `:core`, `App.kt`, build files,
dependencias ni secrets.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `docs/mvp-execution-plan.md` —
  Android-first, iOS congelado, gate Android-only, WIP=1 y paradas por rol.
- `docs/specs/genres-catalog-persistence.md` — dominio/persistencia v7 y deuda visual
  `chip-genre` diferida a esta feature; `docs/specs/note-category-and-tags.md` —
  precedente de UI de chips.
- `CONTEXT.md`, `docs/domain-model.md`, `CONSTRAINTS.md` — Género, catálogo,
  canonización y `Track` vs Género.
- `DESIGN.md` — componentes `chip-genre`/`chip-label-alt`, regla "no sólo color".
- `core/.../model/Genres.kt` — `GenreCatalog.GENRES` (12) y `normalizeGenres`.
- `composeApp/.../NoteEditorScreen.kt` — sección actual de texto libre, estado
  `genres` y `GenreChip` privado.
- `composeApp/.../App.kt` — `initialGenres`/`onSave(..., genres)` ya cableados para
  crear, editar y crear con imagen.
- `composeApp/.../LabelChip.kt` — chip textual de lectura reutilizable.
- `composeApp/.../{NotesListScreen,GddScreen,RelatedNotesScreen}.kt` — ya renderizan
  `note.genres` con `LabelChip`.
- `composeApp/build.gradle.kts`, `gradle/libs.versions.toml` — Compose MP 1.8.2; sin
  source set de tests en `:composeApp`; `init.sh` corre assembleDebug + tests del core.
- Fuentes locales de `foundation-layout` 1.8.2 — el `FlowRow` público es estable (sin
  `@OptIn(ExperimentalLayoutApi::class)`).
- Entorno: `emulator-5556` conectado; `adb` existe en
  `~/Library/Android/sdk/platform-tools/adb` pero no está en `PATH`.

### Existing Patterns To Follow

- Estado de Compose sin DI/ViewModel/Flow; la pantalla emite intenciones.
- Selección con texto + forma + color (`TrackOption`: `✓`, borde 2.dp).
- `GenreChip` actual: `Surface` no clickeable + `Modifier.clickable(onClickLabel,
  role = Role.Button)`.
- Layout: `Column` con `padding(24.dp)`, títulos `titleSmall`, espaciado 8/16.dp.
- Chips de lectura con `LabelChip` (`secondaryContainer`, `labelMedium`).
- iOS congelado: código puramente común de Compose; sin `expect/actual` nuevos.

### Current Gaps

- No existe picker de catálogo: sólo texto libre.
- No hay distinción visual catálogo vs personalizado.
- No hay helper de UI "¿es del catálogo?"; `canonicalize`/`normalizeGenres` alcanzan
  sin tocar `:core` (comparación case-insensitive local).
- Sin tests de UI/E2E: verificación por compilación + smoke manual.

## Technical Approach

### 1. `GenrePicker.kt` (nuevo, sólo `:composeApp`)

```kotlin
@Composable
internal fun GenrePicker(
    selected: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
)
```

- Estado local `newGenre` con `remember`.
- Orden: título "Géneros"; si hay elegidos, "Seleccionados" + `FlowRow` de chips
  removibles; "Catálogo" + `FlowRow` con `GenreCatalog.GENRES` en orden; fila
  `OutlinedTextField` "Género personalizado" + `Button` "Agregar" (`enabled =
  newGenre.isNotBlank()`).
- Toggle: `isSelected = selected.any { it.equals(label, true) }`; si estaba,
  `onChange(selected.filterNot { it.equals(label, true) })`; si no,
  `onChange(normalizeGenres(selected + label))`.
- Agregar: `onChange(normalizeGenres(selected + newGenre)); newGenre = ""`.
- Quitar elegido: `onChange(selected.filterNot { it.equals(genre, true) })`.
- Chips interactivos: `Modifier.clickable(onClickLabel = …, role = Role.Button)`,
  `defaultMinSize(minHeight = 48.dp)`; el seleccionado muestra `"✓ $label"`, borde de
  2.dp y `Modifier.semantics { selected = … }`.
- Etiquetas: `maxLines = 1`, `overflow = TextOverflow.Ellipsis` (sólo presentación).

### 2. Editor

En `NoteEditorScreen.kt`, reemplazar el bloque actual de "Géneros" (título, campo,
botón y chips) por `GenrePicker(selected = genres, onChange = { genres = it })` y
eliminar el `GenreChip` privado. Se mantiene `genres = normalizeGenres(initialGenres)`
y `onSave(..., genres)`. **`App.kt` no cambia.**

### 3. Chip de lectura con variante

En `LabelChip.kt`, agregar `GenreLabelChip(text, modifier)`:
`isCatalog = GenreCatalog.GENRES.any { it.equals(text, ignoreCase = true) }`; catálogo
`primaryContainer`/`onPrimaryContainer`, personalizado
`secondaryContainer`/`onSecondaryContainer`; texto `labelMedium`, una línea con
ellipsis. `LabelChip` queda igual para `track`, `status` y adjuntos. Reemplazar
`LabelChip(text = genre)` por `GenreLabelChip(text = genre)` en `NotesListScreen.kt`,
`GddScreen.kt` y `RelatedNotesScreen.kt`.

### 4. Sin cambios fuera de UI

No se toca `:core`/SQLDelight (el global es v8 por `game-project-persistence`),
`App.kt`, `MainActivity`, `MainViewController`,
`init.sh`, `iosApp/**`, build files ni `gradle/libs.versions.toml`.

## Expected File Changes

- `docs/specs/genres-catalog-ui.md` — **create** (este spec).
- `composeApp/.../GenrePicker.kt` — **create**; picker completo.
- `composeApp/.../NoteEditorScreen.kt` — **modify**; usar `GenrePicker`, quitar chip
  privado y actualizar KDoc.
- `composeApp/.../LabelChip.kt` — **modify**; `GenreLabelChip`.
- `composeApp/.../{NotesListScreen,GddScreen,RelatedNotesScreen}.kt` — **modify**; usar
  `GenreLabelChip` sólo en la fila de Géneros.
- `docs/technical-discovery.md` — **update**; decisión de UI.
- `AGENTS.md` — **update acotado al cierre**; sólo próxima feature.
- `feature_list.json`, `PROGRESS.md` — **update al cierre** con evidencia.

No se esperan cambios en `:core/**`, `composeApp/build.gradle.kts`,
`gradle/libs.versions.toml`, `init.sh`, `iosApp/**`, `App.kt` ni
`MainActivity`/`MainViewController`.

## Visual Design Impact

- UI involved: **yes**. Fuente: `DESIGN.md` (provisional), `chip-genre`,
  `chip-label-alt` y "no sólo color"; sin formalizar paleta.
- Afecta: `NoteEditorScreen` (picker vacío/seleccionado/personalizado/toggle),
  `NotesListScreen`/`GddScreen`/`RelatedNotesScreen` (chip catálogo vs personalizado).
- Nuevo artefacto de diseño: **no**.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **not needed** — no cambian capas ni boundaries.
- `CONSTRAINTS.md`: **not needed** — la regla de Géneros ya existe.
- `AGENTS.md`: **update acotado** — sólo la línea de próxima feature al cerrar.
- `docs/technical-discovery.md`: **update** — decisión de UI y ausencia de cambios de
  `:core`/esquema.
- `DESIGN.md`, `CONTEXT.md`, `docs/domain-model.md`: **not needed**.
- `PROGRESS.md`, `feature_list.json`: **update al cierre** con evidencia.

## Implementation Plan

1. Crear `GenrePicker.kt` (seleccionados, catálogo y personalizado).
2. Integrarlo en `NoteEditorScreen.kt` y eliminar el chip de texto libre.
3. Agregar `GenreLabelChip` y usarlo en las tres superficies de lectura.
4. Correr build Android + regresión de `:core`.
5. Smoke manual (catálogo, libre, dedupe, toggle, tarjeta, reapertura, relanzamiento).
6. Actualizar docs y, al cerrar, `feature_list.json`/`PROGRESS.md`.

## Implementation Tasks

- [x] `GenrePicker.kt`: `FlowRow` de catálogo con toggle, "Seleccionados" con `×`,
      campo + "Agregar", normalización en altas/remociones, accesibilidad y ≥ 48.dp.
- [x] `NoteEditorScreen.kt`: usar `GenrePicker`; eliminar `GenreChip` privado; KDoc.
- [x] `LabelChip.kt`: `GenreLabelChip` con detección case-insensitive y variantes.
- [x] `NotesListScreen.kt`, `GddScreen.kt`, `RelatedNotesScreen.kt`: usarlo en Géneros.
- [x] Verificar build, tests del core, `./init.sh` y ausencia de `.sqm` nuevos de
      esta feature (el global es v8 por `game-project-persistence`).
- [x] Smoke manual Android y evidencia.
- [x] Docs durables y cierre de `feature_list.json`/`PROGRESS.md`.

## Verification Plan

- `./gradlew :composeApp:assembleDebug --rerun-tasks` → **BUILD SUCCESSFUL**.
- `./gradlew :core:testDebugUnitTest --rerun-tasks` → **55/55, 0 failures**
  en la base rebasada (53 pre-integración; el spec original citaba 47 +
  `AiPortsAndroidTest` 6).
- `./init.sh` → **exit 0**; Android-only, IA fake, sin dev servers ni red.
- iOS no bloqueante: `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` →
  BUILD SUCCESSFUL.
- `PlaybookDatabase.Schema.version == 7`; sin `.sqm` nuevos; `git diff --stat` vacío
  en `core`, build files, `init.sh`, `iosApp` y `App.kt`.
- **Smoke manual Android** (`emulator-5556`; `adb` en
  `~/Library/Android/sdk/platform-tools/adb`):
  1. `./gradlew :composeApp:installDebug` + lanzar `MainActivity`.
  2. Verificar los 12 chips en orden; tocar "Platformer" y "JRPG" (selección con
     `✓`/borde y chips elegidos); tocar "JRPG" de nuevo (se quita).
  3. Escribir `" jrpg "` + "Agregar" → "JRPG" sin duplicado; `"Zelda-like"` → chip
     personalizado; guardar.
  4. Tarjeta con estilos distintos; reabrir (selección correcta), quitar/agregar,
     guardar; relanzar y confirmar persistencia. Capturar UI dumps/capturas.
  5. Editar una Nota con 3+ Géneros y verificar que el cuerpo conserva altura
     usable y que `Borrar/Cancelar/Guardar` quedan visibles; repetir con los 12
     del catálogo + un personalizado. Capturar UI dumps/capturas.
- **E2E persistente: no aplica** — no hay harness de UI/E2E e `init.sh` no levanta
  simuladores; la normalización ya está cubierta por tests del core y la UI se valida
  con compilación + smoke manual.
- **Control de alcance:** `rg -n "Nuevo género" composeApp/src` sin resultados;
  `git status --short` sólo con archivos de la feature y cierre.

## Evidence To Capture

- Salida de `assembleDebug`, `:core:testDebugUnitTest` (55/55 en la base rebasada;
  53 pre-integración) y `./init.sh` (exit 0).
- `Schema.version == 7`, ausencia de `.sqm` y diff vacío en `:core`, build files,
  `init.sh`, `iosApp` y `App.kt`.
- Pasos y resultado del smoke (catálogo, toggle, libre, canonización, dedupe, tarjeta,
  reapertura y relanzamiento), con capturas/UI dumps.
- Sin deps nuevas, secrets ni cambios de esquema de esta feature; `feature_list.json` en `accepted`
  con `verification`/`evidence` y entrada de sesión en `PROGRESS.md`.

## Validator Checklist

- [ ] Sólo `:composeApp` + docs; sin cambios en `:core`/esquema (global v8), `App.kt`, build files,
      deps, `init.sh` ni `iosApp`.
- [ ] 12 chips de catálogo siempre visibles en `FlowRow`, en orden.
- [ ] Toggle correcto; elegidos con `×`; toda alta/remoción usa `normalizeGenres`;
      `jrpg` → `JRPG`; sin duplicados; vacíos no se agregan; sin límites de cantidad.
- [ ] Catálogo (`primaryContainer`) vs personalizado (`secondaryContainer`) en editor,
      tarjetas, GDD y relacionadas, con texto y truncado sólo visual.
- [ ] Accesibilidad: `onClickLabel`, estado `selected`, `✓` + borde, ≥ 48.dp.
- [ ] Escenarios 1–9 pasan; smoke registrado honestamente.
- [ ] `feature_list.json`/`PROGRESS.md` actualizados; `AGENTS.md` sólo la cola;
      `docs/technical-discovery.md` con la decisión.
- [ ] No hay E2E persistente y el spec lo justifica; `init.sh` sin cambios.
