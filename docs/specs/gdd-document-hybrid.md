# Feature Implementation Spec: GDD como documento del Proyecto (híbrido)

## Source Feature

- `id`: `gdd-document-hybrid` · `area`: `gdd`
- `depends_on`: `["gdd-view", "game-project-persistence"]` (ambas `accepted`)
- `status`: `not_started` (al planificar) · `source`: `feature_list.json`

## Goal

Que el GDD sea el **documento del Proyecto**: cabecera con el título, arriba la **ficha estructurada** del Proyecto (campos actuales de `GameProject`) y debajo las **Notas agrupadas por `Track`** (`mecánicas`, `personajes`, `historia`, `Sin track`), con los conteos, chips, miniaturas y apertura de Notas ya existentes. La proyección vive en `:core` como función pura y determinista (`buildGddDocument(project, notes)`), cubierta por tests Android/JVM e iOS; la UI (`GddScreen`) sólo renderiza. **No** se agregan tablas, columnas, campos nuevos, migraciones, IA ni export híbrido.

## Non-Goals

- `gdd-export-hybrid`: el Markdown compartido **no** incluye la ficha todavía; `buildGddMarkdown(notes, now)` y su “Format Contract” quedan intactos.
- Esquema/persistencia (`GameProject`, `GameProject.sq`, `Note.sq`, `.sqm`), repositorios o migraciones; nuevos campos de Historia/Lore o Personajes.
- IA, nuevas tablas/entidades o adjuntos; filtros, búsqueda, edición del Proyecto desde el GDD, PDF o detalle de sólo lectura.
- Rediseño del wizard, onboarding o pantalla principal; no se agrega CTA de onboarding al GDD. iOS sólo debe seguir compilando (Android-first, congelado).

## Job Story

When quiero revisar el diseño completo de mi juego en un solo documento,
I want ver la ficha del Proyecto junto a mis Notas ordenadas por Track,
so I can entender el estado del GDD sin saltar entre pantallas ni duplicar datos.

## Users And Permissions

- **Autor** (single-user local-first): lee la ficha y las Notas del dispositivo. Sin auth ni permisos nuevos; `LOCAL_OWNER_ID` y el singleton no cambian.

## Scope Decisions

1. **Ficha = campos actuales de `GameProject`.** El título del Proyecto se muestra en la cabecera fija `GDD — <título>` (o `GDD` si no hay) y **no** se repite como campo. Orden fijo de la ficha: `Géneros`, `Core Loop`, `Audiencia`, `Concepto`, `Inspiraciones`, `Fantasía`, `Mecánicas clave`, `Fuera de alcance`. Sólo campos no vacíos; las listas conservan su orden; cada mecánica se muestra como `"<Categoría> · <description>"` en una línea, con categoría capitalizada (`Movimiento`/`Interacción`/`Conflicto`).
2. **Historia/Lore y Personajes.** El `GameProject` actual **no tiene** campos dedicados de lore ni personajes. Esas secciones se representan con las **Notas del `Track`** correspondiente (`historia` y `personajes`), no con campos fabricados. `concept` y `fantasy` permanecen en la ficha `Proyecto` y no se copian (evita duplicación).

| Sección del documento | Origen |
| --- | --- |
| Proyecto | campos de `GameProject` (sin `Título`, que va en la cabecera) |
| `historia` | Notas con `track = STORY` |
| `personajes` | Notas con `track = CHARACTERS` |
| `mecánicas` | Notas con `track = MECHANICS` |
| `Sin track` | Notas con `track = null` |
3. **Las Notas se proyectan una sola vez.** La ficha sólo contiene campos del Proyecto; cuerpos, `status`, Géneros y miniaturas viven únicamente en las secciones de Notas. No hay tabla ni listado duplicado.
4. **Se reutiliza `buildGdd(notes)`.** Las secciones conservan el orden, la agrupación y los títulos actuales (`mecánicas`, `personajes`, `historia`, `Sin track`), de modo que `buildGddMarkdown` sigue siendo compatible. La ficha se agrega **arriba**; no se reordena el export.
5. **Estados válidos.** `project == null` → `GddDocument.project == null`; la UI muestra un placeholder y sigue mostrando Notas. `project != null` sin campos → `GddProjectBlock(title, emptyList())` y “Proyecto sin datos adicionales” (el título, si existe, vive en la cabecera). `notes.isEmpty()` → ficha + “Todavía no hay notas”.
6. **El export no cambia.** “Compartir” sigue usando `buildGddMarkdown` (sólo Notas) y queda deshabilitado sin Notas; la ficha en el export es `gdd-export-hybrid`.

## Acceptance Scenarios

### Scenario 1: documento completo (ficha + Notas)

Given un `GameProject` con título y todos los campos, y Notas de los tres Tracks (y una sin Track), When se llama `buildGddDocument(project, notes)`, Then `GddDocument.project.title` expone el título y `fields` devuelve los campos en el orden fijado (sin `Título`), con las mecánicas como `Movimiento`/`Interacción`/`Conflicto · descripción`; las secciones conservan el orden/agrupación de `buildGdd` y ninguna Nota se pierde ni se duplica.

### Scenario 2: ficha parcial o vacía

Given un Proyecto con título y campos en blanco o listas con entradas vacías, When se proyecta, Then `title` conserva el título normalizado (`trim`; en blanco → `null`) y sólo aparecen los campos no vacíos, en orden; un Proyecto sin datos devuelve `GddProjectBlock(title, emptyList())` y `project == null` devuelve `null`. La proyección no muta el Proyecto ni sus listas.

### Scenario 3: sin Proyecto, con Notas

Given `project == null` y Notas existentes, When se abre el GDD, Then se muestra “Todavía no hay Proyecto” y las secciones de Notas se renderizan normalmente (conteos, chips y apertura), sin excepción.

### Scenario 4: con Proyecto, sin Notas

Given un Proyecto visible (con o sin campos) y `notes.isEmpty()`, When se abre el GDD, Then se muestra la ficha (campos si existen o “Proyecto sin datos adicionales” si no) y “Todavía no hay notas”; no se renderizan secciones de Track vacías. La proyección sigue devolviendo las tres secciones vacías para tests.

### Scenario 5: sin Proyecto y sin Notas

Given `project == null` y `notes.isEmpty()`, When se abre el GDD, Then se muestra el placeholder de Proyecto y “Todavía no hay notas”, sin crash, sin secciones y con “Compartir” deshabilitado.

### Scenario 6: Notas sin Track

Given Notas con `track == null`, When se proyecta/abre el GDD, Then aparecen en “Sin track” al final, sólo si existen; las tres secciones de Track siguen presentes y no se duplica ninguna Nota.

### Scenario 7: determinismo y pureza

Given la misma entrada, When se llama dos veces `buildGddDocument`, Then las salidas son iguales; la lista de Notas y el Proyecto no se mutan; el orden de las listas del Proyecto y de las Notas se conserva.

### Scenario 8: UI híbrida y navegación

Given el GDD abierto con ficha y Notas, When se recorre, Then la cabecera fija muestra `GDD — <título>` (o `GDD`) y el total de Notas; la ficha aparece arriba de las secciones; cada sección muestra su título (`mecánicas`, `personajes`, `historia`, `Sin track`) y su conteo (`N notas`/`1 nota`); las tarjetas conservan `status`, Géneros, chip “Imagen”, miniatura y apertura de la Nota; “Volver” regresa a la lista.

### Scenario 9: compatibilidad del export y gate

Given la feature implementada, When corren los tests de `:core` y `CI=true ./init.sh`, Then `buildGddMarkdown` y sus tests siguen verdes (sin ficha), el esquema sigue en v8 sin `.sqm` nuevos, no hay dependencias nuevas y el gate termina en 0 sin dev servers.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `docs/mvp-execution-plan.md` — Android-first, iOS congelado, WIP/olas y estado de la feature.
- `CONTEXT.md`, `docs/domain-model.md` — GDD = documento híbrido (ficha + Notas por Track); “Historia / Lore” y “Personajes” son secciones del documento.
- `DESIGN.md`, `docs/build-brief.md`, `docs/design/ui-ux-brief.md` — GDD como documento; jerarquía por agrupación, texto además de color, targets ≥ 48dp.
- `CONSTRAINTS.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md` — regla de GDD híbrido, decisiones previas de `buildGdd`/`buildGddMarkdown` y esquema v8.
- `docs/specs/gdd-view.md`, `gdd-export.md`, `game-project-persistence.md`, `brainstorming-wizard.md` — contratos de vista, export, ficha y wizard.
- `core/.../gdd/GddProjection.kt` (`GddSection`, `buildGdd`), `core/.../gdd/GddMarkdown.kt` (`buildGddMarkdown`), `core/.../model/GameProject.kt` (`GameProject`, `KeyMechanic`, `MechanicCategory`) y `core/.../model/Note.kt` (`Note`, `Track`).
- `composeApp/.../GddScreen.kt` (render, chips, miniatura, “Compartir”), `App.kt` (`project` ya cargado por `refreshKey`), `NotesListScreen.kt` / `LabelChip.kt` (`LabelChip`, `GenreLabelChip`) e `init.sh`.

### Existing Patterns To Follow

- Proyección pura en `:core` (sin SQLDelight/IA) + tests `commonTest` con runners Android/JVM e iOS (`verifyX` + `@Test` por plataforma).
- UI sin DI/`ViewModel`/Flow: estado hoisteado en `App`, refresco por `refreshKey`, dependencias por parámetro. `GddScreen` reutiliza `LabelChip`/`GenreLabelChip`/`NoteThumbnail` y `notesSummary`.
- Documentos en español; identificadores Kotlin en inglés; valores de dominio (`track.code`, `status.code`, `MechanicCategory.code`) en español.
- `./init.sh` es el gate Android-only no bloqueante; tests/gate siempre con IA fake.

### Current Gaps

- No existe `GddDocument` ni una proyección que reciba `GameProject?`; `GddScreen` no recibe el Proyecto y sólo renderiza `buildGdd(notes)`.
- No hay UI de ficha ni estados “sin Proyecto”/“Proyecto vacío” en el GDD, ni tests de la proyección híbrida.

## Technical Approach

### Proyección pura en `:core`

Nuevo `core/src/commonMain/kotlin/com/playbook/core/gdd/GddDocument.kt`:

```kotlin
data class GddProjectField(val label: String, val value: String)
data class GddProjectBlock(val title: String?, val fields: List<GddProjectField>)
data class GddDocumentSection(val track: Track?, val title: String, val notes: List<Note>)
data class GddDocument(val project: GddProjectBlock?, val sections: List<GddDocumentSection>)

fun buildGddDocument(project: GameProject?, notes: List<Note>): GddDocument
```

- `project == null` → `GddDocument.project = null`; si no, `GddProjectBlock(title = project.title?.trim()?.takeIf { it.isNotEmpty() }, fields = …)` con los campos no vacíos en el orden de Scope Decisions (`Título` excluido). Escalares: `trim`; vacío → se omite. Listas: `trim` por elemento, se descartan vacíos y se conserva el orden; lista vacía → se omite el campo. `Géneros`, `Inspiraciones` y `Fuera de alcance` se unen con `, `. Mecánicas: `"${mechanicCategoryLabel(category)} · ${description.trim()}"` por línea, unidas con `\n`; `mechanicCategoryLabel` es un helper privado del mismo archivo que mapea `MOVEMENT`→`Movimiento`, `INTERACTION`→`Interacción`, `CONFLICT`→`Conflicto` (el `code` persistido no cambia).
- `sections` = `buildGdd(notes)` mapeado a `GddDocumentSection`, con `title`: `MECHANICS` → `"mecánicas"`, `CHARACTERS` → `"personajes"`, `STORY` → `"historia"`, `null` → `"Sin track"`. Conserva orden y Notas de `buildGdd` (sin mutar ni duplicar). Sin fechas, SQLDelight, `AiClient` ni re-normalización de Géneros (la persistencia ya normalizó).

### UI (`GddScreen.kt`)

- Firma: agregar `project: GameProject?` (el resto no cambia); `val document = remember(project, notes) { buildGddDocument(project, notes) }`.
- Mantener `Scaffold`/snackbar y la fila superior “Volver”/“Compartir”. Cabecera **fija** con `GDD — <título>` (o `GDD` si no hay título) + resumen de Notas; debajo, un `LazyColumn` con la ficha y `document.sections` (ficha y Notas scrollean juntas).
- Ficha: `Card`/`Surface` destacada, separada de las tarjetas de Notas, con encabezado `Proyecto`; cada `GddProjectField` con label arriba (`labelMedium`) y valor debajo (`bodyMedium`), permitiendo valores multilínea. `fields.isEmpty()` → “Proyecto sin datos adicionales”; `project == null` → “Todavía no hay Proyecto” + “Completalo desde Brainstorming.” (sin CTA).
- Secciones: `section.title`, conteo con `notesSummary`, texto “Sin notas en este track” si está vacía; tarjetas con el render actual (cuerpo 2 líneas, `status`, Géneros, “Imagen”, miniatura, `onOpenNote`). `attachmentCounts`, `imagePaths` y `loadThumbnail` no cambian.
- `notes.isEmpty()`: mostrar ficha (si existe) y “Todavía no hay notas”; no renderizar secciones vacías. “Compartir” sigue deshabilitado sin Notas y no cambia: `buildGddMarkdown(notes, currentTimestampLabel())` + `GddSharer`.

### Wiring y límites

- `App.kt`: pasar `project = project` a `GddScreen` (ya se carga en `LaunchedEffect(refreshKey)`); no se agregan repositorios, estado ni navegación.
- Sin cambios en `MainActivity`/`MainViewController`, repositorios, `GddProjection.kt`, `GddMarkdown.kt`, esquema (v8), `.sqm`, build files, `init.sh`, `iosApp` ni dependencias.

## Expected File Changes

- `core/.../gdd/GddDocument.kt` — crear; modelos + `buildGddDocument`.
- `core/src/commonTest/.../gdd/GddDocumentCheck.kt` — crear; `verifyGddDocument()` (escenarios 1–7).
- `core/src/androidUnitTest/.../gdd/GddDocumentAndroidTest.kt` y `core/src/iosTest/.../gdd/GddDocumentIosTest.kt` — crear; `@Test` por plataforma.
- `composeApp/.../GddScreen.kt` — modificar; ficha + secciones desde `GddDocument`, estados vacíos.
- `composeApp/.../App.kt` — modificar; pasar `project`.
- `ARCHITECTURE.md`, `docs/technical-discovery.md` — actualizar decisión y render del GDD híbrido.
- `feature_list.json`, `PROGRESS.md`, `AGENTS.md` — actualizar al cerrar (estado, evidencia y cola); **no** en el paso de planning.

No se esperan cambios en `GddProjection.kt`, `GddMarkdown.kt`, `core/.../model/GameProject.kt`, `core/.../repository/**`, `core/src/commonMain/sqldelight/**`, `.sqm`, build files, `MainActivity`, `MainViewController`, `init.sh` ni `iosApp/**`.

## Visual Design Impact

- UI involved: yes. Design source: `DESIGN.md` (GDD como documento; calidez, jerarquía por agrupación, texto además de color, targets ≥ 48dp) y `docs/design/ui-ux-brief.md`.
- Screens/states: GDD (ficha en `Card` destacada, secciones, sin Proyecto, Proyecto vacío, sin Notas); el resto no cambia. New design artifact required: no — se reutilizan `Card`/`Text`/chips, la tipografía `labelMedium`/`bodyMedium` y el lenguaje visual existente.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — `buildGddDocument`, ficha arriba de las secciones y límite temporal del export.
- `CONSTRAINTS.md`: **not needed** — la regla “GDD híbrido sin tabla que duplique Notas” ya existe.
- `AGENTS.md`: **update (acotado)** — sólo la línea de próxima feature en cola.
- `docs/technical-discovery.md`: **update** — decisiones del GDD híbrido (mapeo Historia/Lore y Personajes, export sin ficha).
- `CONTEXT.md`/`docs/domain-model.md`: **not needed** — ya describen el GDD híbrido. `feature_list.json`/`PROGRESS.md`: **update** al implementar/validar.

## Implementation Plan

1. Crear `GddDocument.kt` con modelos, orden de campos y `buildGddDocument`.
2. Escribir `verifyGddDocument()` y los runners Android/JVM e iOS; correr `:core` en ambas plataformas.
3. Actualizar `GddScreen` para renderizar ficha y secciones desde `GddDocument`, conservando chips, miniaturas y “Compartir”; pasar `project` desde `App`.
4. Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
5. Correr el gate, smoke Android y registrar evidencia; actualizar `feature_list.json`, `PROGRESS.md` y la cola de `AGENTS.md`.

## Implementation Tasks

- [ ] Crear `GddProjectField`, `GddProjectBlock`, `GddDocumentSection`, `GddDocument` y `buildGddDocument`.
- [ ] Implementar el orden/format de la ficha, el título en cabecera, las categorías capitalizadas y el mapeo de títulos de sección.
- [ ] Escribir `verifyGddDocument()` + `@Test` Android/JVM e iOS.
- [ ] Renderizar la ficha y los estados vacíos en `GddScreen`; pasar `project` desde `App` y conservar el render actual de Notas.
- [ ] Mantener `buildGddMarkdown` y “Compartir” sin cambios.
- [ ] Actualizar docs durables, `feature_list.json`, `PROGRESS.md` y `AGENTS.md`.
- [ ] Correr verificación, controles negativos y smoke Android; registrar evidencia.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → `GddDocumentAndroidTest` verde (escenarios 1–7) + `GddProjectionAndroidTest`/`GddMarkdownAndroidTest` previos verdes, 0 failures.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` (macOS) → `GddDocumentIosTest` y los tests previos verdes.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (UI + wiring).
- `CI=true ./init.sh` → exit 0; gate Android-only, sin dev servers ni red. En este worktree no hay `local.properties`: exportar `ANDROID_HOME` o crear uno **sin** la API key (el de la raíz contiene un secret y no debe copiarse).
- `:composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (iOS congelado, sólo compilación).
- Esquema sin cambios: `Schema.version == 8`, `.sqm` = `1.sqm`..`7.sqm`, sin `.sqm` nuevo; `git diff` vacío en `GameProject.sq`, `Note.sq` y repositorios.
- Controles negativos (restaurar byte-idéntico y re-verificar): (a) cambiar el orden de un campo → falla `GddDocumentAndroidTest`; (b) incluir un campo vacío → falla el escenario 2; (c) duplicar/omitir una Nota → fallan los escenarios 1/6; (d) cambiar un título de sección → falla el escenario 8; (e) dejar la categoría de mecánica en minúsculas → falla el escenario 1.
- Smoke Android: Proyecto completo + Notas de los tres Tracks y una sin Track → cabecera `GDD — <título>` + total, ficha (campos en orden) y secciones con conteos/chips; abrir Nota y Volver; sin Proyecto con Notas; Proyecto vacío (sólo título); sin Notas; sin Proyecto ni Notas. Evidencia en `composeApp/build/smoke-evidence/gdd-document-hybrid/`. Si no hay `adb`/emulador, el implementer puede dejar la feature `passing` con la salvedad explícita (tests/build/gate verdes) y el validator puede reintentar el smoke; no se fabrica evidencia.
- E2E persistente: **no aplica** — no hay harness UI/E2E y `init.sh` no levanta simuladores; la proyección se cubre con tests cross-platform y la UI con smoke Android.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` con `GddDocument*Test` y los tests de GDD/export previos verdes.
- Salida de `:composeApp:assembleDebug`, `CI=true ./init.sh` exit 0 y link iOS.
- Confirmación de `Schema.version == 8`, `.sqm` 1..7, sin dependencias nuevas y sin cambios en `GddProjection.kt`/`GddMarkdown.kt`/repositorios/build files.
- Smoke Android (capturas/UI dumps) de ficha + secciones, estados sin Proyecto/sin Notas y apertura/Volver; si no hay dispositivo, salvedad honesta del smoke no ejecutado. Controles negativos reproducidos, restaurados y re-verificados.

## Validator Checklist

- [ ] Alcance respetado: sólo proyección híbrida + UI del GDD; **sin** export híbrido, esquema/persistencia, IA, campos nuevos, wizard/onboarding ni rediseño de la pantalla principal.
- [ ] Escenarios 1–9 pasan.
- [ ] `buildGddDocument` es puro/determinista, no muta entradas, no duplica Notas y respeta el orden fijo de ficha, listas y secciones.
- [ ] La ficha muestra los campos actuales en el orden acordado (sin `Título`, que vive en la cabecera), con categorías de mecánica capitalizadas; Historia/Lore y Personajes se representan con sus Notas, sin campos fabricados y sin duplicar el título.
- [ ] Estados sin Proyecto, Proyecto vacío, sin Notas y Notas sin Track son explícitos y no rompen la vista.
- [ ] `buildGddMarkdown` y “Compartir” quedan intactos (sólo Notas); tests previos verdes.
- [ ] Esquema v8 sin `.sqm` nuevos, sin deps nuevas, `init.sh` sin cambios; `feature_list.json`/`PROGRESS.md` con evidencia y `ARCHITECTURE.md`/`docs/technical-discovery.md` actualizados.
- [ ] No hay E2E persistente y la spec lo justifica.
