# Feature Implementation Spec: Enlazado por similitud semántica

## Source Feature

- `id`: `semantic-linking`
- `area`: `linking`
- `depends_on`: `["embeddings-generation"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Calcular la **similitud coseno** entre los embeddings ya persistidos de las
Notas y persistir **Enlaces Nota↔Nota con un score** en una nueva tabla
`note_link`. Es el segundo consumidor del storage de embeddings introducido por
`embeddings-generation` (`EmbeddingRepository.getAll()`); no genera embeddings
(eso ya existe) ni los muestra (eso es `note-links-ui`).

Se introduce la tercera tabla vecina del esquema (`note_link`), por lo que el
esquema sube a **v5** con la migración `4.sqm` (v4 → v5). La feature es **core
puro** (dominio + persistencia + servicio): sin UI ni wiring en la app.

### Decisiones de alcance (documentadas)

- **Direccionalidad: enlaces bidireccionales, guardados una sola vez.** La
  similitud coseno es **simétrica** (`sim(a,b) == sim(b,a)`), así que persistir
  dos filas por par duplicaría el dato y podría desincronizarse. Se guarda **una
  fila por par** con orden canónico `note_a_id < note_b_id` (orden lexicográfico
  de `id`); un enlace es, por contrato, no dirigido. La lectura "enlaces de una
  Nota" consulta ambos lados (`note_a_id = :id OR note_b_id = :id`).
- **Umbral: `score >= 0.8` (constante, configurable).** Coseno vive en `[-1, 1]`;
  `≥ 0.8` es una heurística razonable de "alta similitud". Se define
  `SemanticLinkingService.DEFAULT_SIMILARITY_THRESHOLD = 0.8` inyectable por
  constructor para poder afinarlo cuando llegue el runtime real
  (`docs/risks-and-open-questions.md`). El umbral es inclusivo (`>=`),
  determinista y testeable con vectores controlados.
- **Sin transición de `NoteStatus`.** `docs/domain-model.md` define `indexada` =
  "el embedding **y sus Enlaces** se generaron"; sin embargo, la transición de
  estado y su trigger/scheduling siguen siendo de `note-enrichment-status-ui` /
  `offline-pending-retry`. Además, hoy no hay wiring que dispare el recomputo, así
  que el servicio expone una operación explícita (`recomputeAll`) y **no** toca la
  Nota. Esta feature no cambia `embeddings-generation` ni `NoteStatus`.
- **"Cuándo se recalcula": operación explícita e idempotente.** El único punto de
  entrada es `SemanticLinkingService.recomputeAll()` (borra todos los enlaces y
  los reconstruye desde los embeddings actuales). El **trigger** (al indexar, al
  editar, offline) se difiere y se documenta como follow-up.

## Non-Goals

- UI, navegación o "ver notas relacionadas" (`note-links-ui`); wiring de
  `SemanticLinkingService`/`NoteLinkRepository` en `App`/`MainActivity`/
  `MainViewController` (sería una dependencia muerta sin superficie).
- Transiciones de `NoteStatus` (`capturada → indexada`), reintentos, colas,
  conectividad o scheduling (`note-enrichment-status-ui`, `offline-pending-retry`).
- Generar/modificar embeddings o tocar `AiClient`/`NoteIndexingService`
  (embeddings-generation ya está `accepted`); RAG (`rag-query`).
- Recomputo incremental por Nota, límite de enlaces por Nota (`top-K`), búsqueda
  por rango, índice ANN, vector DB o `sqlite-vec`; recalcular en background.
- Elegir el runtime/proveedor real, dimensión o afinar el umbral con datos reales
  (follow-ups del ADR 0001).

## Job Story

When ya existe un embedding para mis Notas,
I want que las Notas con contenido semánticamente cercano queden enlazadas entre
sí con un score,
so I can descubrir conexiones entre ideas sin buscarlas a mano (y habilitar
luego la vista de notas relacionadas).

## Users And Permissions

- Desarrollador (autor): único actor; ejecuta Gradle/tests e inyecta
  `EmbeddingRepository`/`NoteLinkRepository`. Sin usuarios finales ni permisos.
- MVP single-user local-first; `owner` lo provee el llamador. El enlace se
  vincula por `note.id`, sin auth.

## Acceptance Scenarios

### Scenario 1: Cálculo de similitud coseno (puro)

Given `CosineSimilarity`,
When se calcula `score(a, b)`,
Then `a == b` no nulo → `1.0`; vectores ortogonales → `0.0`; opuestos → `-1.0`
(comparación con epsilon); dimensiones distintas → `null`; algún vector de norma
`0` (p. ej. todo ceros) → `null`. No lanza para entradas inválidas.

### Scenario 2: Generar enlaces por encima del umbral

Given un `SqlDelightEmbeddingRepository` con 3 Notas y embeddings controlados, y
un `SemanticLinkingService(embeddingRepository, linkRepository, threshold = 0.8)`,
When se corre `recomputeAll()`,
Then sólo los pares con `score >= 0.8` quedan como filas en `note_link`, cada uno
con el score correcto (comparación con epsilon) y `updatedAt = clock()`; el
resumen `LinkingSummary.notesConsidered` es `3` y `linksWritten` cuenta las filas
persistidas.

### Scenario 3: Frontera del umbral (inclusivo)

Given un par con coseno exactamente igual al umbral,
When se corre `recomputeAll()`,
Then el enlace se crea (`>=`). Un par con coseno justo por debajo del umbral no
crea fila. El control negativo (cambiar `>=` por `>`) debe hacer fallar este
escenario.

### Scenario 4: Similitud indefinida no crea enlace

Given dos embeddings de dimensión distinta, o uno de norma `0`,
When se corre `recomputeAll()`,
Then la pareja se omite (no lanza, no persiste fila) y el resto de los pares se
procesa con normalidad.

### Scenario 5: Almacenamiento bidireccional canónico

Given `SqlDelightNoteLinkRepository` con `clock` determinista,
When se hace `upsert(noteB, noteA, score)` con `noteB.id > noteA.id`,
Then la fila queda canónica (`note_a_id < note_b_id`), hay **una sola** fila para
el par (PK `(note_a_id, note_b_id)`) y `getLinksForNote(noteA)` **y**
`getLinksForNote(noteB)` devuelven ese único enlace (resolviendo el "otro" id).
`upsert` con `noteAId == noteBId` lanza `IllegalArgumentException`; `score`
no finito lanza `IllegalArgumentException`.

### Scenario 6: Recomputo idempotente y recalculo tras re-indexar

Given un conjunto de enlaces ya persistidos,
When se corre `recomputeAll()` dos veces sin cambiar embeddings,
Then el conjunto de filas y sus scores son idénticos (idempotencia). Cuando el
embedding de una Nota cambia (re-indexado) y ya no supera el umbral con un par,
`recomputeAll()` **elimina** el enlace obsoleto (borra y reconstruye), sin dejar
filas viejas ni duplicados.

### Scenario 7: Sin embedding no participa; borrado cascadea

Given una Nota sin fila en `embedding` (indexado fallido) y una Nota con embedding
enlazada a otra,
When se corre `recomputeAll()`,
Then la Nota sin embedding no aparece en ningún enlace. Con
`PRAGMA foreign_keys = ON`, al borrar una Nota (`NoteRepository.delete`) sus filas
`note_link` se eliminan por FK `ON DELETE CASCADE` (sin huérfanos).

### Scenario 8: Migración v4 → v5 y bootstrap en ambos caminos

Given un driver in-memory con el esquema **v4** (`note` + `embedding` + `note_tag`
+ `attachment`) y una fila legacy,
When se llama `PlaybookDatabase.Schema.migrate(driver, oldVersion = 4L,
newVersion = 5L)`,
Then `PlaybookDatabase.Schema.version == 5L`, la fila legacy sobrevive, y la tabla
`note_link` existe y es usable. Un `Schema.create(driver)` desde cero produce el
mismo esquema final (columnas y FKs de `note_link` equivalentes).

### Scenario 9: Ambas plataformas + gate honesto

Given los tests compartidos de los escenarios 1–8,
When se corren `:core:testDebugUnitTest`, `:core:iosSimulatorArm64Test`,
`:composeApp:assembleDebug`, `:composeApp:linkDebugFrameworkIosSimulatorArm64` y
`./init.sh`,
Then todo pasa en Android/JVM e iOS, `./init.sh` termina en 0 sin dev servers ni
simuladores, y `PlaybookDatabase.Schema.version == 5` con `4.sqm` presente.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `embeddings-generation` es la
  dependencia `accepted` (Session 020); `semantic-linking` es la próxima ready.
- `feature_list.json` — metadata de `semantic-linking` (`depends_on`
  `embeddings-generation`, notas "definir umbral y direccionalidad").
- `docs/domain-model.md` — `Enlace / Relación`: conexión Nota↔Nota por similitud
  con `score`; `indexada` = embedding **y** enlaces; edge case "indexado fallido
  no genera Enlaces".
- `docs/risks-and-open-questions.md` — preguntas abiertas resueltas por esta spec:
  "¿los enlaces son bidireccionales? ¿cuándo se recalculan y con qué umbral?".
- `docs/technical-discovery.md` — estrategia BLOB + similitud coseno en memoria;
  "Embedding Decisions (embeddings-generation)".
- `ARCHITECTURE.md` — esquema v4, `embedding` (FK cascade), `EmbeddingRepository`,
  `PRAGMA foreign_keys = ON`, patrón repositorio/mapper, "Deferred: enlaces".
- `docs/specs/embeddings-generation.md` — formato de spec a espejar; `Embedding`/
  `NoteEmbedding`/`EmbeddingRepository`/`NoteIndexingService`.
- `core/src/commonMain/sqldelight/.../embedding.sq`, `Note.sq`, `1.sqm`, `3.sqm`
  — patrón `.sq` + `.sqm`, `INSERT OR REPLACE`, FK cascade.
- `core/src/commonMain/kotlin/.../repository/{EmbeddingRepository,
  SqlDelightEmbeddingRepository,EmbeddingBlobCodec}.kt`, `model/{NoteEmbedding,
  NoteEmbeddingMappers}.kt` — patrón repositorio + mapper + `clock` inyectado.
- `core/src/commonTest/.../{repository/EmbeddingRepositoryCheck,ai/NoteIndexingCheck,
  db/DatabaseMigrationCheck,db/MigrationCheck,repository/NoteTagCheck}.kt` — patrones
  de test compartido (`verifyX(driver)`) + `@Test` por plataforma.
- `core/src/{androidUnitTest,iosTest}/kotlin/com/playbook/core/**` — runners
  `JdbcSqliteDriver.IN_MEMORY` + `Schema.create` / `inMemoryDriver(Schema)`.
- `core/src/commonMain/kotlin/.../ai/FakeAiClient.kt` — vectors deterministas en
  `[0,1)` (no representativos de coseno; los tests usan vectores controlados).
- `core/src/commonMain/kotlin/.../db/DatabaseDriverFactory.kt` — `createDatabase`
  habilita `PRAGMA foreign_keys = ON`.

### Existing Patterns To Follow

- Dominio/persistencia/servicios en `:core`; el `:core` no depende de UI.
- Sin DI: dependencias por constructor (`EmbeddingRepository`,
  `NoteLinkRepository`, `clock`, `threshold`).
- Mapper fila ↔ dominio con alias (`import ...NoteLink as NoteLinkRow`).
- `.sq` describe el esquema más reciente; `.sqm` migra; `Schema.version` se
  bumpera y los drivers migran por `user_version`.
- `INSERT OR REPLACE` en lugar de `ON CONFLICT DO UPDATE` (minSdk 24 / SQLite 3.9).
- FK `REFERENCES note(id) ON DELETE CASCADE` para datos derivados; `PRAGMA
  foreign_keys = ON` ya está activo.
- Tests: helper `verifyX(driver)` en `commonTest` + `@Test` por plataforma; tests
  puramente puros pueden vivir como `@Test` directo en `commonTest` (p. ej.
  `GreetingTest`).
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- No existe tabla de enlaces, ni modelo `NoteLink`, ni repositorio/servicio de
  linking; no hay cálculo de similitud coseno.
- El esquema está en **v4**; al agregar `note_link` sube a **v5** con `4.sqm`.
- Los tests de migración hardcodean `version = 4` y `migrate(1, 4)`
  (`DatabaseMigrationCheck`, `MigrationCheck.verifyMigrationV1ToV4`,
  `NoteTagCheck.verifyNoteTagMigration`): **deben actualizarse a v5** o fallarán.

## Technical Approach

### 1. Esquema y migración

**Decisión: tabla `note_link` (par canónico, 1 fila por par) + `4.sqm` v4 → v5.**

- Una fila por **par no ordenado** con PK `(note_a_id, note_b_id)` y
  `CHECK (note_a_id < note_b_id)`: garantiza simetría sin duplicar y sin riesgo
  de scores divergentes entre "ida" y "vuelta".
- FK `note_a_id`/`note_b_id` → `note(id) ON DELETE CASCADE` para que borrar una
  Nota no deje enlaces huérfanos (coherente con `embedding`; `PRAGMA foreign_keys
  = ON` ya está activo). No hace falta tocar `SqlDelightNoteRepository.delete`.
- Índice sobre `note_b_id` para que la consulta por cualquiera de los dos lados
  sea eficiente (mirroring `attachment_note_id`).

```sql
-- core/src/commonMain/sqldelight/com/playbook/core/db/note_link.sq
CREATE TABLE note_link (
  note_a_id TEXT NOT NULL REFERENCES note(id) ON DELETE CASCADE,
  note_b_id TEXT NOT NULL REFERENCES note(id) ON DELETE CASCADE,
  score REAL NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (note_a_id, note_b_id),
  CHECK (note_a_id < note_b_id)
);

CREATE INDEX note_link_note_b_id ON note_link (note_b_id);

upsertNoteLink:
INSERT OR REPLACE INTO note_link (note_a_id, note_b_id, score, updated_at)
VALUES (?, ?, ?, ?);

selectLinkByPair:
SELECT * FROM note_link WHERE note_a_id = ? AND note_b_id = ?;

selectLinksForNote:
SELECT * FROM note_link
WHERE note_a_id = ? OR note_b_id = ?
ORDER BY note_a_id, note_b_id;

selectAllLinks:
SELECT * FROM note_link ORDER BY note_a_id, note_b_id;

deleteAllLinks:
DELETE FROM note_link;
```

```sql
-- core/src/commonMain/sqldelight/com/playbook/core/db/4.sqm (v4 -> v5)
CREATE TABLE note_link (
  note_a_id TEXT NOT NULL REFERENCES note(id) ON DELETE CASCADE,
  note_b_id TEXT NOT NULL REFERENCES note(id) ON DELETE CASCADE,
  score REAL NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (note_a_id, note_b_id),
  CHECK (note_a_id < note_b_id)
);

CREATE INDEX note_link_note_b_id ON note_link (note_b_id);
```

El `CREATE TABLE`/`CREATE INDEX` de `4.sqm` debe replicar exactamente el de
`.sq` (limitación ya documentada en el repo, cubierta por tests portables).

### 2. Modelo, mapper y similitud

- `com.playbook.core.model.NoteLink(noteAId: String, noteBId: String,
  score: Double, updatedAt: Long)`: entidad persistida, siempre canónica
  (`noteAId < noteBId`). Extensión `fun NoteLink.otherNoteId(of: String): String`
  (fail-fast si `of` no es ninguno de los dos) para que los consumidores resuelvan
  "la otra Nota".
- `NoteLinkMappers.kt`: `import com.playbook.core.db.NoteLink as NoteLinkRow`;
  `NoteLinkRow.toDomain()`.
- `com.playbook.core.linking.CosineSimilarity` (`internal object`, puro Kotlin
  common): `fun score(a: List<Float>, b: List<Float>): Double?` → `null` si
  `a.size != b.size` o si alguna norma es `0.0`; si no, `dot / (norm(a)*norm(b))`
  (Double, `kotlin.math.sqrt`). Determinista y sin dependencias.

### 3. Repositorio

```kotlin
interface NoteLinkRepository {
    /** Inserta/reemplaza el enlace del par; canoniza el orden y sella updatedAt con clock(). */
    fun upsert(noteAId: String, noteBId: String, score: Double): NoteLink
    fun getLinksForNote(noteId: String): List<NoteLink>
    fun getAll(): List<NoteLink>
    fun deleteAll()
}
```

`SqlDelightNoteLinkRepository(database, clock)`: `upsert` valida
`require(noteAId != noteBId)` y `require(score.isFinite())`, ordena los ids y hace
`INSERT OR REPLACE`; lecturas mapean; `deleteAll` borra todo.

### 4. Servicio

```kotlin
data class LinkingSummary(val notesConsidered: Int, val linksWritten: Int)

class SemanticLinkingService(
    private val embeddingRepository: EmbeddingRepository,
    private val noteLinkRepository: NoteLinkRepository,
    private val threshold: Double = DEFAULT_SIMILARITY_THRESHOLD,
) {
    fun recomputeAll(): LinkingSummary
    companion object { const val DEFAULT_SIMILARITY_THRESHOLD: Double = 0.8 }
}
```

`recomputeAll()`: `require(threshold in -1.0..1.0)`; lee
`embeddingRepository.getAll()` (ordenado por `note_id`), `deleteAll()` de enlaces,
y para cada par `i < j` calcula `CosineSimilarity.score`; si no es `null` y
`score >= threshold`, hace `upsert`. Devuelve `LinkingSummary`. Determinista e
idempotente; un borrado+reconstrucción es aceptable para una biblioteca personal
(si el proceso muere a mitad, los enlaces se regeneran en el próximo recomputo).

## Expected File Changes

- `core/src/commonMain/sqldelight/com/playbook/core/db/NoteLink.sq` — crear;
  tabla + índice + queries. _(El nombre planeado era `note_link.sq`; se usó
  `NoteLink.sq` por la convención de SQLDelight del accessor desde el nombre del
  archivo — ver Implementation Tasks.)_
- `core/src/commonMain/sqldelight/com/playbook/core/db/4.sqm` — crear; v4 → v5.
- `core/src/commonMain/kotlin/com/playbook/core/model/NoteLink.kt` — crear.
- `core/src/commonMain/kotlin/com/playbook/core/model/NoteLinkMappers.kt` — crear.
- `core/src/commonMain/kotlin/com/playbook/core/linking/CosineSimilarity.kt` —
  crear.
- `core/src/commonMain/kotlin/com/playbook/core/linking/SemanticLinkingService.kt`
  — crear; servicio + `LinkingSummary`.
- `core/src/commonMain/kotlin/com/playbook/core/repository/NoteLinkRepository.kt`
  — crear; interfaz.
- `core/src/commonMain/kotlin/com/playbook/core/repository/SqlDelightNoteLinkRepository.kt`
  — crear; implementación.
- `core/src/commonTest/kotlin/com/playbook/core/linking/CosineSimilarityTest.kt`
  — crear; `@Test` puro (corre en Android/JVM e iOS vía `commonTest`).
- `core/src/commonTest/kotlin/com/playbook/core/repository/NoteLinkCheck.kt` —
  crear; `verifyNoteLinkRepository(driver)`.
- `core/src/commonTest/kotlin/com/playbook/core/linking/SemanticLinkingCheck.kt`
  — crear; `verifySemanticLinking(driver)`.
- `core/src/androidUnitTest/kotlin/com/playbook/core/repository/NoteLinkRepositoryAndroidTest.kt`
  y `core/src/androidUnitTest/kotlin/com/playbook/core/linking/SemanticLinkingAndroidTest.kt`
  — crear; `@Test` JVM (`JdbcSqliteDriver.IN_MEMORY` + `Schema.create`).
- `core/src/iosTest/kotlin/com/playbook/core/repository/NoteLinkRepositoryIosTest.kt`
  y `core/src/iosTest/kotlin/com/playbook/core/linking/SemanticLinkingIosTest.kt`
  — crear; `@Test` iOS (`inMemoryDriver(Schema)`).
- `core/src/commonTest/kotlin/com/playbook/core/db/DatabaseMigrationCheck.kt` —
  modificar; `version == 5L`, `migrate(1, 5)` y chequeos de columnas/FKs de
  `note_link`.
- `core/src/commonTest/kotlin/com/playbook/core/db/MigrationCheck.kt` —
  modificar; renombrar a `verifyMigrationV1ToV5`, `version == 5L`,
  `migrate(1, 5)` + aserción de `note_link`.
- `core/src/commonTest/kotlin/com/playbook/core/repository/NoteTagCheck.kt` —
  modificar; `migrate(1, 5)` y comentario.
- `core/src/androidUnitTest/.../db/DatabaseMigrationAndroidTest.kt`,
  `.../db/MigrationAndroidTest.kt` y los equivalentes iOS — modificar; nombres de
  `@Test` a `...V5...`.
- `ARCHITECTURE.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md` — modificar; decisiones y estado.
- `AGENTS.md` — modificar sólo la línea "Próxima feature en cola" al cierre.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado y evidencia.

No se esperan cambios en `:composeApp`, `iosApp/**`, `init.sh`, `Note.sq`,
`embedding.sq`/`1.sqm`/`2.sqm`/`3.sqm`, `NoteRepository`/`EmbeddingRepository` ni
en `gradle/libs.versions.toml`.

## Visual Design Impact

- UI involved: no.
- Design source: not applicable.
- Screens or states affected: ninguna.
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — tabla `note_link` (esquema **v5** con `4.sqm`),
  `NoteLinkRepository`, `SemanticLinkingService` + `CosineSimilarity`, umbral
  `0.8` y regla de enlaces bidireccionales canónicos.
- `CONSTRAINTS.md`: **not needed** — no surge un MUST/MUST NOT nuevo para agentes;
  la regla de simetría es una decisión de dominio que vive en `ARCHITECTURE.md`.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola".
- Other docs: `docs/technical-discovery.md` — update ("Linking Decisions");
  `docs/risks-and-open-questions.md` — update (direccionalidad/umbral resueltos; el
  **trigger** del recomputo queda como follow-up); `docs/domain-model.md` /
  `CONTEXT.md` — not needed (la spec se alinea); `PROGRESS.md` /
  `feature_list.json` — update al cerrar.

## Implementation Plan

1. Crear `note_link.sq` + `4.sqm`; confirmar `Schema.version == 5` y que
   `Schema.create` sigue creando el esquema completo.
2. Escribir `NoteLink` + mapper (alias `NoteLinkRow`) y `CosineSimilarity`.
3. Implementar `NoteLinkRepository` + `SqlDelightNoteLinkRepository(clock)`.
4. Implementar `SemanticLinkingService` + `LinkingSummary` (umbral configurable).
5. Actualizar los tests de migración hardcodeados a v4 → v5.
6. Escribir `CosineSimilarityTest`, `verifyNoteLinkRepository` y
   `verifySemanticLinking` + runners por plataforma.
7. Actualizar docs durables; correr verificación, smoke real de migración y
   registrar evidencia.

## Implementation Tasks

- [x] Crear `note_link.sq` (tabla + índice + upsert/select/deleteAll) y `4.sqm`.
      _Desvío menor: el archivo se llamó `NoteLink.sq` (no `note_link.sq`) porque
      SQLDelight deriva el accessor del nombre del archivo: `NoteLink.sq` →
      `database.noteLinkQueries` (camelCase, igual que `NoteTag.sq` → `noteTagQueries`).
      La tabla sigue siendo `note_link` y la data class generada es `Note_link`._
- [x] Confirmar bump a `Schema.version = 5` y actualizar los tests de migración a v5.
- [x] Crear `NoteLink`, `NoteLinkMappers` (canónico) y `NoteLink.otherNoteId`.
- [x] Crear `CosineSimilarity` (puro common).
- [x] Crear `NoteLinkRepository` + `SqlDelightNoteLinkRepository(clock)`.
- [x] Crear `SemanticLinkingService` + `LinkingSummary` con `DEFAULT_SIMILARITY_THRESHOLD`.
- [x] Escribir `CosineSimilarityTest` (puro, `@Test` en `commonTest`).
- [x] Escribir `verifyNoteLinkRepository` (canónico/upsert/getLinksForNote/cascade) y runners.
- [x] Escribir `verifySemanticLinking` (umbral, frontera, indefinido, idempotencia, re-index, sin embedding) y runners.
- [x] Actualizar `DatabaseMigrationCheck`, `MigrationCheck`, `NoteTagCheck` a v5 y sus runners.
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`.
- [x] Correr la verificación + smoke real de migración y registrar evidencia.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → nuevos tests (similitud,
  repositorio de enlaces, servicio, migración v5) en verde + los previos
  (`NotePersistence` 2/2, `NoteRepository` 3/3, `AiClient` 3/3, `EmbeddingRepository`
  1/1, `NoteIndexing` 1/1, `Attachment` 1/1, `Greeting` 1/1), 0 failures.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` (macOS) → mismos tests en
  verde en iOS, 0 failures.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (el `:core` v5 compila
  en Android).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (el `:core` v5 enlaza en iOS).
- Confirmar `PlaybookDatabase.Schema.version == 5` y `4.sqm` presente.
- `./init.sh` → exit 0; gate no bloqueante, sin dev servers ni simuladores, sin
  cambios en `init.sh`.
- **Control negativo 1**: invertir la comparación del umbral (`>=` → `>`) debe
  hacer fallar el Scenario 3; restaurar.
- **Control negativo 2**: romper `4.sqm` (p. ej. renombrar `note_link`) debe hacer
  fallar el test de migración con `SQLiteException ... no such table: note_link`;
  restaurar byte-idéntico.
- **Smoke real de migración v4 → v5** (manual, no en `init.sh`): en Android
  (emulador) e iOS (simulador), partir de un `playbook.db` v4 (de una build
  previa) con Notas, lanzar la app y verificar `user_version = 5`, la tabla
  `note_link` creada y la preservación de las Notas, sin crashes. (Los emuladores
  Android pueden estar compartidos entre sesiones; declarar la limitación sin
  fabricar evidencia.)
- E2E persistente: **no aplica**. No hay flujo de usuario/API observable (sin UI
  ni wiring) ni harness E2E; `init.sh` no levanta simuladores. La cobertura es de
  tests del core en dos plataformas + compilación de la app + smoke manual de
  migración. Se justifica explícitamente.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` con los
  nuevos tests en verde (nombres y conteos por plataforma).
- Salida de `:composeApp:assembleDebug` y `:composeApp:linkDebugFrameworkIosSimulatorArm64`.
- `./init.sh` → exit 0, sin procesos de larga duración.
- `PlaybookDatabase.Schema.version == 5`; `4.sqm` presente; `Note.sq` sin cambios.
- Control negativo 1 (umbral) y 2 (`4.sqm`) reproducidos y restaurados.
- Smoke de migración real v4 → v5 en Android e iOS: `user_version = 5`, tabla
  `note_link`, Notas preservadas, sin crashes.
- Confirmación de que no se tocaron `:composeApp`/`iosApp`/`init.sh`/
  `libs.versions.toml`, `Note.sq` ni `embedding.sq`/`.sqm` previos.

## Validator Checklist

- [ ] Alcance respetado: sólo dominio + persistencia + servicio de enlaces; sin
      UI, wiring, `NoteStatus`, RAG ni runtime real.
- [ ] Escenarios de aceptación 1–9 pasan.
- [ ] `Schema.version == 5` con `4.sqm`; la migración v4 → v5 preserva los datos y
      `Schema.create` produce el mismo esquema (`note_link`).
- [ ] Los enlaces son **bidireccionales y canónicos** (una fila por par,
      `note_a_id < note_b_id`); `getLinksForNote` ve el enlace de ambos lados.
- [ ] El umbral es inclusivo (`>= 0.8`), configurable y el control negativo lo
      confirma.
- [ ] `CosineSimilarity` devuelve `null` para dimensiones distintas o norma 0 y no
      crea enlaces en esos casos.
- [ ] El recomputo es idempotente y elimina enlaces obsoletos tras re-indexar.
- [ ] Sin embedding no participa; borrar una Nota cascadea sus `note_link`.
- [ ] Evidencia en `feature_list.json` / `PROGRESS.md`; smoke real de migración
      declarado honestamente.
- [ ] No hay E2E persistente y la spec justifica por qué.
- [ ] `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados;
      `init.sh` sin cambios, no bloqueante y sin procesos de larga duración.
