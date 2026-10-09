# Feature Implementation Spec: Etiquetas libres (tags) editables en la Nota

> **Reconciliación de terminología (obligatoria).** El `id` de la feature se
> mantiene (`note-category-and-tags`) para no romper `depends_on` de
> `gdd-view` y `ai-enrichment-classify-tags-type`, pero **su título y
> comportamiento en `feature_list.json` son obsoletos y deben corregirse**.
> Ver la sección "Terminology Reconciliation". El trabajo real de esta feature
> son **Etiquetas**; la "Categoría fija" ya existe como `Track`.

## Source Feature

- `id`: `note-category-and-tags`
- `area`: `core` (la entrega real cubre `:core` + `:composeApp`; ver Durable Docs)
- `depends_on`: `["create-text-note"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`
- `title` actual (obsoleto): "Categoría fija y etiquetas editables en la nota"
- `user_visible_behavior` actual (obsoleto): "El usuario asigna una categoría
  (mecánica/nivel/narrativa/arte/otro) y agrega/quita etiquetas libres a una
  nota."

## Goal

Agregar a la Nota la capacidad de tener **Etiquetas (tags) libres, múltiples y
editables manualmente**, según `CONTEXT.md` y `docs/domain-model.md`
(`Nota N—N Etiqueta`). El usuario agrega y quita etiquetas al crear/editar una
nota desde la UI, y las ve listadas en la tarjeta de la lista. La etiqueta es
vocabulario libre del usuario; **no** hay clasificación automática por IA ni
categoría/tipo/nivel.

La clasificación fija de la Nota ya existe y está cubierta por `Track`
(`mecánicas`/`personajes`/`historia`), ya seleccionable y editable desde
`create-text-note`. Esta feature **no** la toca: sólo agrega etiquetas. El
cambio de mayor riesgo es la **persistencia**: la tabla `note_tag` (N—N) exige
una **migración de esquema de v1 a v2** (primer `.sqm` del repo).

## Non-Goals

- **`Track`/Categoría/Tipo/Nivel**: no se agrega ni se modifica el `track`
  (ya existe, enum fijo y único). No hay campo "Categoría", "Tipo de nota" ni
  "Nivel".
- Clasificación o etiquetado automático por IA (`ai-enrichment-classify-tags-type`),
  corrección de enriquecimiento (`note-enrichment-status-ui`) y GDD
  (`gdd-view`).
- Adjuntos, enlaces, embeddings, indexado, RAG, búsqueda/filtros por etiqueta.
- Renombrar/borrar etiquetas globalmente, autocompletar, vocabulario curado,
  jerarquía o colores por etiqueta. Las etiquetas se gestionan por nota.
- Cambiar el esquema de `note` o el resto de la capa de persistencia aceptada
  (`createDatabase`, drivers, orden de `selectAllNotes`).
- Framework DI, `ViewModel`/`Lifecycle`, Flow o librería de navegación; se sigue
  el patrón de estado de Compose ya vigente.
- Manejo del back físico Android/iOS (sigue sólo "Cancelar").
- Formalizar `DESIGN.md`/tokens (sigue `provisional`).

## Job Story

When estoy capturando una idea y quiero agruparla con otras por un término mío,
I want agregar/quitar etiquetas libres a la nota (además de su track),
so I can organizar y reencontrar mis notas con mi propio vocabulario.

## Users And Permissions

- Autor/usuario único del MVP (local-first): todas las notas y etiquetas del
  dispositivo.
- Sin auth ni permisos. `owner` sigue siendo único y lo provee la UI
  (`LOCAL_OWNER_ID`); no cambia.

## Terminology Reconciliation

Fuentes de verdad: `CONTEXT.md` ("Glossary" y "Rejected / Ambiguous Terms") y
`docs/domain-model.md`.

| Término obsoleto | Estado | Reemplazo canónico |
| --- | --- | --- |
| `Categoría` / `Tipo de nota` | **Rechazado** | El enum fijo y único `Track` |
| `Nivel` | **Ambiguo / rechazado como clasificación** | `Level` (tilemap) es Fase B; para una nota de diseño de nivel usar `track` `mecánicas` o `historia` |
| `mecánica/nivel/narrativa/arte/otro` (valores) | **Obsoleto** | `Track` = `mecánicas`/`personajes`/`historia` |
| `Etiqueta (Tag)` | **Vigente y núcleo de esta feature** | Descriptor libre y múltiple de una Nota |

- `Track` es clasificación **fija, única y elegida por el usuario**; ya está
  implementado (enum `Track`, columna `note.track`, selector en
  `NoteEditorScreen`) y **no** forma parte del trabajo nuevo.
- `Track` ≠ `Etiqueta`: no son sinónimos (`CONTEXT.md`).
- El título/comportamiento de `feature_list.json` todavía dice "Categoría fija
  (mecánica/nivel/narrativa/arte/otro)". Reemplazo sugerido al cerrar:
  - `title`: "Etiquetas libres editables en la nota"
  - `user_visible_behavior`: "El usuario agrega y quita etiquetas libres
    (múltiples) a una nota desde la UI; la clasificación por `Track` ya existe."
  - `verification`: "Tests del core sobre etiquetas (múltiples, libres,
    normalizadas) y migración v1→v2; la UI permite agregar/quitar etiquetas y
    las muestra en la lista."
- Otros entries con terminología obsoleta (`ai-enrichment-classify-tags-type`,
  `note-enrichment-status-ui`, `gdd-view`) están **fuera de alcance**: sólo se
  dejan señalados para su re-derivación.

## Acceptance Scenarios

### Scenario 1: Agregar etiquetas al crear

Given el editor abierto en modo creación,
When se escribe un cuerpo, se agregan una o más etiquetas no vacías y se guarda,
Then la nota se persiste con esas etiquetas, la lista la muestra con sus chips de
etiqueta y no se creó ninguna "categoría" adicional.

### Scenario 2: Editar etiquetas de una nota existente

Given una nota con etiquetas en la lista,
When se abre, se quita una etiqueta y se agrega otra, y se guarda,
Then la base refleja exactamente el nuevo conjunto (las quitadas desaparecen, las
nuevas aparecen), `updatedAt` avanza y la lista muestra los chips actualizados.

### Scenario 3: Normalización de etiquetas

Given el editor con etiquetas,
When se agrega una etiqueta con espacios sobrantes, vacía/sólo espacios, o
duplicada ignorando mayúsculas/minúsculas,
Then se recorta, se descartan las vacías y no se duplica (se conserva la primera
grafía ingresada); no se persiste ninguna etiqueta vacía.

### Scenario 4: Las etiquetas son independientes del Track

Given una nota con `track` asignado,
When se agregan/quitan etiquetas,
Then el `track` (único y fijo) no cambia; la UI no ofrece ningún control de
Categoría/Tipo/Nivel.

### Scenario 5: Múltiples notas comparten etiquetas (N—N)

Given dos notas que incluyen la etiqueta "jefe",
When se consultan,
Then ambas exponen la misma etiqueta sin duplicar filas por nota, y quitar la
etiqueta de una no afecta a la otra.

### Scenario 6: Borrar una nota limpia sus etiquetas

Given una nota con etiquetas,
When se borra la nota,
Then sus filas de etiquetas desaparecen (sin huérfanas) y la lista no las
muestra.

### Scenario 7: Migración v1 → v2 preserva datos

Given una base local existente en v1 (tabla `note` con filas, sin `note_tag`),
When se abre la app con el esquema v2,
Then la migración corre sin errores, **todas las notas v1 sobreviven** con su
`track`/`status`, `user_version` pasa a 2 y las etiquetas (vacías para datos
viejos) quedan editables.

### Scenario 8: Persistencia entre reinicios

Given una nota creada/editada con etiquetas desde la UI,
When se cierra y reabre la app,
Then las etiquetas persisten junto con el resto de la nota.

### Scenario 9: Gate honesto

Given la feature implementada,
When se ejecuta `./init.sh`,
Then sigue terminando en 0 sin dev servers ni simuladores; los tests del core
(Android/JVM e iOS) siguen verdes y `:composeApp` compila/enlaza.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `create-text-note` `accepted`
  (Session 013); próxima feature `note-category-and-tags` (requiere spec).
- `feature_list.json` — entry `note-category-and-tags` con terminología obsoleta;
  `gdd-view` y `ai-enrichment-classify-tags-type` dependen de esta feature.
- `CONTEXT.md`, `docs/domain-model.md` — `Track` (fijo/único) vs `Etiqueta`
  (libre/múltiple); `Nota N—N Etiqueta`; términos rechazados.
- `docs/build-brief.md` — Goal "Etiquetar las notas manualmente con vocabulario
  libre"; clasificación IA descartada.
- `docs/design/ui-ux-brief.md`, `DESIGN.md` — "Editor de Nota de Texto: cuerpo +
  selector de track + etiquetas"; componente "Chip de Etiqueta"; regla "no sólo
  color"; tokens provisionales.
- `docs/technical-discovery.md` — SQLDelight única fuente local; esquema v1; sin
  `.sqm`; tests in-memory por plataforma; UI sin DI/ViewModel/Flow.
- `ARCHITECTURE.md` — `:composeApp → :core`; `Note`/`NoteDraft` sin `tags`;
  `NoteRepository` síncrono; `App`/`NoteEditorScreen`/`NotesListScreen`.
- `core/src/commonMain/sqldelight/com/playbook/core/db/Note.sq` — esquema v1
  (`note`) + `insertNote`/`selectAllNotes`/`selectNoteById`/`updateNote`/
  `deleteNoteById`. No hay tablas de etiquetas ni `.sqm`.
- `core/src/commonMain/kotlin/com/playbook/core/model/{Note,Track,NoteMappers}.kt`
  — `Note`/`NoteDraft` sin `tags`; mapper `NoteRow.toDomain()`.
- `core/.../repository/{NoteRepository,SqlDelightNoteRepository}.kt` — CRUD
  síncrono; `create` genera id/timestamps, `update` re-sella `updatedAt`.
- `core/src/commonTest/.../NoteRepositoryCheck.kt` — `verifyNoteCrud(driver)`;
  patrón de test compartido + `@Test` por plataforma.
- `composeApp/src/commonMain/.../{App,NotesListScreen,NoteEditorScreen}.kt` —
  estado de lista con `LaunchedEffect` + `refreshKey`; editor con `body` +
  selector de `track` (`onSave(body, track)`); tarjeta con chips `track`/`status`.
- `core/build.gradle.kts`, `gradle/libs.versions.toml` — SQLDelight 2.1.0 con
  `PlaybookDatabase` (`com.playbook.core.db`), sin `schemaOutputDirectory` ni
  migraciones; `compose.material3`/`compose.foundation` disponibles.
- `init.sh` — gate no bloqueante: `assembleDebug`, `testDebugUnitTest`, link del
  framework iOS y `iosSimulatorArm64Test` (macOS).

### Existing Patterns To Follow

- Dominio y persistencia en `:core`; UI/wiring en `:composeApp`; sin DI.
- Tests de datos compartidos en `commonTest` sobre un `SqlDriver`; `@Test` por
  plataforma (`androidUnitTest` JVM in-memory; `iosTest` `inMemoryDriver`).
- Migraciones SQLDelight: `.sq` describe el esquema más reciente; `.sqm` hacen
  upgrade; archivo `<versión desde>.sqm` (v1→v2 ⇒ `1.sqm`).
- Versiones centralizadas en `gradle/libs.versions.toml`; sin dependencias nuevas.
- Documentos en español, identificadores Kotlin en inglés, valores de dominio en
  español.

### Current Gaps

- `Note`/`NoteDraft` no tienen `tags`; no hay tipo ni normalización de etiquetas.
- No existe la tabla `note_tag` ni queries de etiquetas; no hay ningún `.sqm`.
- El repositorio no lee/escribe etiquetas.
- El editor no ofrece campo de etiquetas; la tarjeta no muestra etiquetas.
- `feature_list.json` conserva "categoría/nivel" obsoletos.

## Technical Approach

### Modelo de dominio

```kotlin
data class Note(
    val id: String, val owner: String, val body: String,
    val track: Track?, val status: NoteStatus,
    val createdAt: Long, val updatedAt: Long,
    val tags: List<String> = emptyList(),        // nuevo, al final con default
)

data class NoteDraft(
    val owner: String, val body: String,
    val track: Track?, val status: NoteStatus = NoteStatus.CAPTURED,
    val tags: List<String> = emptyList(),        // nuevo, al final con default
)
```

- Los defaults `emptyList()` evitan romper el código existente y los tests.
- Se agrega `normalizeTags(raw: List<String>): List<String>` en
  `com.playbook.core.model` (archivo `Tags.kt`): `trim`, descarta vacías,
  deduplica sin distinguir mayúsculas/minúsculas conservando la **primera**
  grafía, y ordena por `label` (case-insensitive) para una salida determinista.
  La UI reutiliza este helper para no mostrar duplicados.

### Persistencia (esquema v2 + migración)

Nuevo `NoteTag.sq` (esquema más reciente, para instalaciones nuevas):

```sql
CREATE TABLE note_tag (
  note_id TEXT NOT NULL,
  label TEXT NOT NULL,
  PRIMARY KEY (note_id, label)
);

insertNoteTag:
INSERT OR IGNORE INTO note_tag (note_id, label) VALUES (?, ?);

selectTagsByNoteId:
SELECT label FROM note_tag WHERE note_id = ? ORDER BY label;

selectAllNoteTags:
SELECT note_id, label FROM note_tag ORDER BY note_id, label;

deleteTagsByNoteId:
DELETE FROM note_tag WHERE note_id = ?;
```

Migración `core/src/commonMain/sqldelight/migrations/1.sqm` (v1→v2):

```sql
CREATE TABLE note_tag (
  note_id TEXT NOT NULL,
  label TEXT NOT NULL,
  PRIMARY KEY (note_id, label)
);
```

- `note_tag` con PK `(note_id, label)` modela `Nota N—N Etiqueta` con la etiqueta
  identificada por su `label` (sin tabla `tag` ni id sintético: suficiente para
  el MVP y sin gestión global de etiquetas; ver Riesgos).
- **Sin FK ni `PRAGMA foreign_keys`**: la limpieza de etiquetas se hace explícita
  en el repositorio, no por `ON DELETE CASCADE`.
- Como se agrega una tabla, hay cambio de esquema: **`Schema.version` pasa de 1 a
  2** y aparece el primer `.sqm`. `AndroidSqliteDriver`/`NativeSqliteDriver`
  ejecutan `Schema.migrate` sobre bases existentes; `createDatabase(...)` no
  cambia (sigue forzando la apertura con el `PRAGMA`).

### Repositorio

`SqlDelightNoteRepository` persiste etiquetas en la misma operación que la nota,
dentro de `database.transaction { ... }`:

- `create`: `val tags = normalizeTags(draft.tags)`; transacción `insertNote` +
  `insertNoteTag` por etiqueta; devuelve `Note(..., tags = tags)`.
- `getById(id)`: `selectNoteById` + `selectTagsByNoteId(id)` →
  `row.toDomain(tags)`.
- `getAll()`: `selectAllNotes` + `selectAllNoteTags`; agrupa en
  `Map<noteId, List<String>>` y mapea (evita N+1).
- `update(note)`: `val tags = normalizeTags(note.tags)`; transacción `updateNote`
  + `deleteTagsByNoteId` + `insertNoteTag` (reemplazo total del conjunto);
  devuelve `rowsAffected > 0`.
- `delete(id)`: transacción `deleteTagsByNoteId(id)` + `deleteNoteById(id)`;
  devuelve `rowsAffected > 0` de la nota.
- Mapper: `fun NoteRow.toDomain(tags: List<String> = emptyList()): Note`.

### UI (Compose, sin DI/ViewModel/Flow)

- `NoteEditorScreen`: firma nueva
  `NoteEditorScreen(initialBody, initialTrack, initialTags, isEditing,
  onSave: (body, track, tags) -> Unit, onDelete, onCancel)`.
  - Estado local `tags` (init con `normalizeTags(initialTags)`) y `newTag`.
  - Campo "Nueva etiqueta" + botón "Agregar" (habilitado si `newTag.isNotBlank()`);
    al agregar se aplica `normalizeTags` y se limpia el campo. Cada etiqueta se
    muestra como chip con control de quitar "×" (`clickable` con
    `onClickLabel = "Quitar etiqueta <label>"`, `role = Role.Button`), sin
    depender de íconos ni de `FlowRow` experimental.
  - El `body` sigue obligatorio; guardar deshabilitado si `body.isBlank()`.
- `NotesListScreen`: `NoteCard` muestra una fila adicional de chips de etiqueta
  (misma `LabelChip`, en `Row` con `horizontalScroll`) sólo si `tags` no está
  vacía; se mantiene `body` (2 líneas) + chips `track`/`status`. Sin dependencia
  de color para distinguir (texto).
- `App`: `onSave` pasa `tags`; creación
  `NoteDraft(owner = LOCAL_OWNER_ID, body = body.trim(), track = track, tags = tags)`;
  edición `note.copy(body = body.trim(), track = track, tags = tags)`.
  `LaunchedEffect`/`refreshKey` (`reload()`) sin cambios.

### Dependencias, esquema y `init.sh`

- **Sin dependencias nuevas**: `Surface`, `OutlinedTextField`, botones y
  `AlertDialog` ya vienen de `compose.material3`/`compose.foundation`; para el
  "×" se usa texto, sin `material-icons`.
- **`init.sh` no cambia**: sigue no bloqueante y sin procesos de larga duración.

### Riesgos y decisiones abiertas

- **Migración v1→v2 (riesgo principal):** es el primer `.sqm`; un error deja la
  base sin migrar. Se mitiga con un test que simula una base v1, corre
  `Schema.migrate(driver, 1, 2)` y verifica que la fila v1 sobrevive y
  `note_tag` queda operable, más un smoke real sobre una base v1.
- **`Etiqueta` sin tabla propia:** PK `(note_id, label)` es más simple; si una
  feature futura necesita renombrar/autocompletar etiquetas globalmente, se
  normaliza a `tag` + `note_tag` sin cambiar el contrato `Note.tags`.
- **Normalización case-insensitive:** regla de producto explícita y testeable;
  conserva la primera grafía. Si se prefiere case-sensitive, se ajusta el helper.
- **Sin tope de cantidad/largo de etiquetas:** no se impone límite en esta
  feature (documentado como decisión, no como olvido).
- **`order by label`:** las etiquetas se devuelven ordenadas (no en orden de
  ingreso); no se agrega columna `position`.

## Expected File Changes

- `core/src/commonMain/sqldelight/com/playbook/core/db/NoteTag.sq` — crear;
  tabla `note_tag` (esquema v2) + queries.
- `core/src/commonMain/sqldelight/migrations/1.sqm` — crear; migración v1→v2
  (crea `note_tag`).
- `core/src/commonMain/kotlin/com/playbook/core/model/Tags.kt` — crear;
  `normalizeTags`.
- `core/src/commonMain/kotlin/com/playbook/core/model/Note.kt` — modificar;
  `tags` en `Note`/`NoteDraft`.
- `core/src/commonMain/kotlin/com/playbook/core/model/NoteMappers.kt` —
  modificar; `toDomain(tags)`.
- `core/src/commonMain/kotlin/com/playbook/core/repository/SqlDelightNoteRepository.kt`
  — modificar; transacciones y lecturas de etiquetas.
- `core/src/commonMain/kotlin/com/playbook/core/repository/NoteRepository.kt` —
  modificar sólo KDoc (contrato de `tags`); firma sin cambios.
- `core/src/commonTest/kotlin/com/playbook/core/repository/NoteTagCheck.kt` —
  crear; `verifyNoteTags(driver)` + `verifyNoteTagMigration(driver)`.
- `core/src/androidUnitTest/kotlin/com/playbook/core/repository/NoteRepositoryAndroidTest.kt`
  — modificar; `@Test` de etiquetas y migración.
- `core/src/iosTest/kotlin/com/playbook/core/repository/NoteRepositoryIosTest.kt`
  — modificar; idem iOS.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NoteEditorScreen.kt` —
  modificar; campo/edición de etiquetas y `onSave(body, track, tags)`.
- `composeApp/src/commonMain/kotlin/com/playbook/app/NotesListScreen.kt` —
  modificar; chips de etiqueta en la tarjeta.
- `composeApp/src/commonMain/kotlin/com/playbook/app/App.kt` — modificar;
  pasar `tags` a `NoteDraft`/`copy`.
- `ARCHITECTURE.md`, `docs/technical-discovery.md` — modificar; esquema v2,
  etiquetas, migración y UI.
- `AGENTS.md` — modificar al cerrar; línea "Próxima feature en cola".
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; terminología
  reconciliada, estado y evidencia.

No se esperan cambios en `composeApp/build.gradle.kts`,
`gradle/libs.versions.toml`, `init.sh`, `iosApp/**`, `Note.sq` (la tabla `note`
no cambia) ni `MainActivity`/`MainViewController`.

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` (provisional); se sigue su dirección y el
  componente "Chip de Etiqueta" y la regla "no sólo color" (las etiquetas se
  comunican con texto). No se fijan tokens ni se crea artefacto nuevo.
- Screens or states affected: "Editor de Nota de Texto" (campo y chips de
  etiqueta) y "Lista de Notas" (fila de chips de etiqueta).
- New design artifact required: no (la entrega de UI/UX sigue pendiente).

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — primera migración (esquema v2 `note_tag`),
  `tags` en modelo/repositorio y edición de etiquetas en la UI.
- `CONSTRAINTS.md`: not needed — no surge una regla MUST/MUST NOT nueva; no
  existe el archivo.
- `AGENTS.md`: **update** — cambia "Próxima feature en cola"; no cambian
  workflow ni gate.
- Other docs: `docs/technical-discovery.md` — update (decisión de migración v2 y
  modelo de etiquetas); `feature_list.json`/`PROGRESS.md` — update al cerrar
  (título/comportamiento reconciliados y evidencia); `CONTEXT.md` y
  `docs/domain-model.md` — not needed (ya son la fuente de verdad y esta spec se
  alinea con ellos); `docs/adr/` — not needed.

## Implementation Plan

1. Agregar `tags` a `Note`/`NoteDraft` y crear `normalizeTags`.
2. Crear `NoteTag.sq` y `migrations/1.sqm`; confirmar codegen y
   `Schema.version == 2`.
3. Extender `SqlDelightNoteRepository` con transacciones y lecturas de etiquetas;
   adaptar el mapper.
4. Escribir `verifyNoteTags` y `verifyNoteTagMigration` en `commonTest` + tests
   por plataforma; correr `:core` en Android/JVM e iOS.
5. Actualizar `NoteEditorScreen`, `NotesListScreen` y `App`.
6. Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
7. Correr verificación completa, smoke manual en Android/iOS (incluida la
   migración real) y registrar evidencia; actualizar `feature_list.json`,
   `PROGRESS.md` y `AGENTS.md` (reconciliando terminología).

## Implementation Tasks

- [x] Agregar `tags: List<String> = emptyList()` a `Note` y `NoteDraft`.
- [x] Crear `normalizeTags` (trim, sin vacías, dedupe case-insensitive, orden).
- [x] Crear `NoteTag.sq` con la tabla y las queries `insertNoteTag`,
  `selectTagsByNoteId`, `selectAllNoteTags`, `deleteTagsByNoteId`.
- [x] Crear `core/src/commonMain/sqldelight/migrations/1.sqm` (v1→v2).
- [x] Confirmar que `PlaybookDatabaseImpl.Schema.version == 2` y que no hay
  cambios en la tabla `note`.
- [x] Adaptar `NoteMappers` (`toDomain(tags)`) y `SqlDelightNoteRepository`
  (create/getById/getAll/update/delete con etiquetas y transacciones).
- [x] Escribir `verifyNoteTags(driver)` (múltiples, normalización, N—N,
  reemplazo en update, limpieza en delete).
- [x] Escribir `verifyNoteTagMigration(driver)` y un `@Test` por plataforma para
  la migración v1→v2.
- [x] Actualizar `NoteEditorScreen` (campo/chips de etiqueta y `onSave` con
  `tags`).
- [x] Actualizar `NotesListScreen` (chips de etiqueta) y `App` (pasar `tags`).
- [x] Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
- [x] Correr `./init.sh` y los comandos de verificación; registrar evidencia.
- [x] Smoke manual Android/iOS (agregar/quitar etiquetas, chips en lista,
  `track` intacto, migración real v1→v2).
- [x] Actualizar `feature_list.json` (título/comportamiento reconciliados) y
  `PROGRESS.md`/`AGENTS.md` al cerrar.

## Verification Plan

- `./gradlew :core:testDebugUnitTest` → BUILD SUCCESSFUL; `NoteTagCheck` verde
  (etiquetas + migración) y `verifyNoteCrud`/tests previos siguen verdes.
- `./gradlew :core:iosSimulatorArm64Test` (macOS) → los mismos helpers verdes.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (UI + wiring Android).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL;
  `xcodebuild` del scheme `iosApp` → BUILD SUCCEEDED.
- Esquema: `Schema.version == 2`; existe `migrations/1.sqm`; la tabla `note` no
  cambió.
- Smoke manual Android (emulador) e iOS (simulador):
  - crear nota con 2 etiquetas → chips en la lista;
  - editar (quitar una, agregar otra) → chips actualizados y reordenamiento al
    tope; `track` sin cambios;
  - agregar etiqueta vacía/duplicada → no se agrega;
  - borrar la nota → sus etiquetas desaparecen;
  - migración real: partir de una base v1 con notas y abrir el build nuevo →
    notas intactas, `user_version=2`, etiquetas editables.
- `./init.sh` → exit 0; ejecuta el gate no bloqueante actual, **no** inicia dev
  servers ni simuladores.
- E2E persistente: **no aplica** — no existe harness de UI tests/comando E2E y
  `init.sh` no levanta simuladores. El path de datos (etiquetas + migración) está
  cubierto por tests del core en dos plataformas y la UI por smoke manual, igual
  que en `create-text-note`.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` con
  `verifyNoteTags`/`verifyNoteTagMigration` y los tests previos en verde
  (nombres y conteos).
- Salida de `:composeApp:assembleDebug`, link del framework iOS y `xcodebuild`.
- Confirmación de `Schema.version == 2`, presencia de `migrations/1.sqm` y
  ausencia de cambios en la tabla `note`/dependencias/`init.sh`.
- Smoke manual Android/iOS: capturas o UI dumps con chips de etiqueta, alta/baja,
  normalización y `track` intacto.
- Evidencia de la migración real v1→v2 (nota v1 que sobrevive y
  `PRAGMA user_version` = 2 sobre `playbook.db`).
- Salida de `./init.sh` (exit 0) sin procesos de larga duración.

## Validator Checklist

- [ ] Alcance respetado: **sólo etiquetas**; sin Categoría/Tipo/Nivel, IA, GDD,
  adjuntos, filtros ni gestión global de etiquetas.
- [ ] `track` no se modificó (sigue enum fijo y único, selector existente).
- [ ] Escenarios de aceptación 1–9 pasan.
- [ ] `Schema.version == 2` con el primer `.sqm`; la migración v1→v2 preserva
  las notas existentes (test + smoke).
- [ ] Etiquetas: múltiples, libres, normalizadas (trim/dedupe case-insensitive),
  N—N entre notas, reemplazadas en `update` y limpiadas al borrar la nota.
- [ ] La UI permite agregar/quitar etiquetas y las muestra en la lista, con
  texto (no sólo color) y sin librería de íconos nueva.
- [ ] Sin DI/`ViewModel`/Flow ni librería de navegación; sin dependencias nuevas;
  `init.sh` sin cambios y no bloqueante.
- [ ] `feature_list.json` corregido a la terminología vigente y evidencia en
  `feature_list.json`/`PROGRESS.md`; `ARCHITECTURE.md` y
  `docs/technical-discovery.md` actualizados.
- [ ] No hay E2E persistente y la spec lo justifica.
