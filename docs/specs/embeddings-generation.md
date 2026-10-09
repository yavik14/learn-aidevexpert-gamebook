# Feature Implementation Spec: Generación y persistencia de embeddings

## Source Feature

- `id`: `embeddings-generation`
- `area`: `ai`
- `depends_on`: `["ai-client-interface", "note-model-crud-core"]` (ambas `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Generar el **embedding de una Nota** a través del puerto `AiClient` y persistirlo
localmente como **BLOB en SQLDelight**, de modo que cada Nota indexada tenga un
vector recuperable intacto. Es el **primer consumidor real de `AiClient`**: se
introduce `NoteIndexingService` (genera y guarda) y un `EmbeddingRepository`
(upsert / lectura / borrado) sobre una nueva tabla `embedding` (1:0..1 con
`note`). La feature agrega la **primera migración de esquema** (`1.sqm`,
v1 → v2), porque la persistencia pasa de v1 sin tablas vecinas a v2 con el
storage de vectores.

Habilita `semantic-linking` (similitud coseno en memoria sobre los vectores
persistidos) y `rag-query`. **No incluye UI ni wiring en la app**, y **no**
incluye las transiciones de `NoteStatus` (`pendiente`/`indexada`/`fallida`), que
se deciden más abajo.

### Decisión de alcance sobre `NoteStatus` (documentada)

El `user_visible_behavior` de esta feature es "cada nota enriquecida tiene un
embedding persistido como BLOB"; la conducta observable es que existe un
`embedding` recuperable para la Nota. **No se transiciona `NoteStatus` en esta
feature**:

- En `docs/domain-model.md`, `indexada` = "el embedding **y sus Enlaces** se
  generaron"; los Enlaces llegan en `semantic-linking`.
- `pendiente` vs `fallida` exige clasificar el fallo en transitorio/persistente,
  algo que el puerto `AiClient` **no** expone hoy (`AiClientException` es
  genérico); la política de reintento es `offline-pending-retry`.
- La fuente de verdad de "tiene embedding" pasa a ser la fila en `embedding`
  (`getByNoteId(...) != null`); el estado de la Nota y su UI son las features
  `note-enrichment-status-ui` / `offline-pending-retry`.

El estado de la Nota **no se modifica** al indexar: un fallo de IA no la degrada
(la captura nunca se bloquea por IA). Esta es la decisión de scope del planner.

## Non-Goals

- Transiciones de `NoteStatus` (`capturada → indexada/pendiente/fallida`),
  reintentos, colas, detección de conectividad o scheduling (`offline-pending-retry`,
  `note-enrichment-status-ui`).
- Enlaces semánticos / similitud coseno, umbrales o grafos (`semantic-linking`,
  `note-links-ui`) y RAG (`rag-query`).
- Elegir el runtime/proveedor real o fijar dimensión/modelo del embedding
  (`ai-runtime-decision`); se consume el `FakeAiClient` por defecto.
- UI, Compose, `DESIGN.md`, navegación o wiring de `AiClient`/`EmbeddingRepository`
  en `App`/`MainActivity`/`MainViewController` (sin superficie observable todavía;
  sería una dependencia muerta). La regla durable "el dominio consume sólo el
  puerto `AiClient`" ya vive en `ARCHITECTURE.md`.
- Etiquetas, adjuntos, categorías, clasificación IA y GDD.
- Vector DB / extensiones vectoriales (`sqlite-vec`): la estrategia es BLOB +
  similitud en memoria (`docs/technical-discovery.md`).
- Índice ANN, búsqueda por rango o cuantización.

## Job Story

When capturo una Nota y el runtime de IA está disponible,
I want que su cuerpo se convierta en un embedding y quede guardado localmente,
so I can recuperarlo intacto después para enlazar Notas por similitud y responder
consultas (RAG) sin depender de la red.

## Users And Permissions

- Desarrollador (autor): único actor; ejecuta Gradle/tests e inyecta
  `AiClient`/`EmbeddingRepository`. Sin usuarios finales ni permisos.
- MVP single-user local-first; `owner` lo provee el llamador. El embedding se
  vincula a la Nota por `note.id` (`note_id`), sin auth.

## Acceptance Scenarios

### Scenario 1: Persistir y recuperar el vector intacto (BLOB)

Given una `PlaybookDatabase` con el esquema v2 y un `SqlDelightEmbeddingRepository`
con `clock` determinista, y una Nota persistida,
When se llama `upsert(noteId, values)` y luego `getByNoteId(noteId)`,
Then el `NoteEmbedding` recuperado tiene exactamente los mismos `values`
(bit-exactos), la `dimension` almacenada coincide con `values.size` y `indexedAt`
es el `clock()` usado; otro `noteId` no lo ve.

### Scenario 2: Generar vía `AiClient` (primer consumidor real)

Given un `NoteIndexingService(FakeAiClient(), embeddingRepository)` y una Nota
persistida,
When se corre `index(note)`,
Then el resultado es `IndexingResult.Indexed`, el vector guardado es idéntico a
`FakeAiClient().embed(listOf(note.body)).single().values` y existe una fila en
`embedding` para `note.id`. El servicio llama `embed` con un único texto (el
`body` de la Nota).

### Scenario 3: Re-generación idempotente (overwrite)

Given una Nota ya indexada,
When se corre `index(note)` de nuevo con el `body` cambiado (vector distinto),
Then sigue habiendo **una sola** fila para `note.id`, los `values` son los nuevos,
`indexedAt` avanzó al `clock()` actual y no se duplican filas.

### Scenario 4: Fallo del runtime no corrompe el estado

Given un `AiClient` de test que lanza `AiClientException` y una Nota que ya tenía
un embedding persistido,
When se corre `index(note)`,
Then el resultado es `IndexingResult.Failed(cause)` y el embedding previo queda
**intacto** (misma fila/valores); si la Nota no tenía embedding, no se crea
ninguno.

### Scenario 5: Respuesta inválida del runtime (control de contrato)

Given un `AiClient` de test que devuelve `0` vectores, `>1` vectores o un vector
de dimensión `0`,
When se corre `index(note)`,
Then el resultado es `IndexingResult.Failed` con `AiClientException` y **no** se
persiste ni se pisa ninguna fila.

### Scenario 6: Codec BLOB round-trip y entrada inválida (control negativo)

Given `EmbeddingBlobCodec`,
When se codifica y decodifica una lista de `Float` (incluye `0f`, negativos,
valores grandes y `-0.0f`),
Then el round-trip es bit-exacto y `encode` produce `values.size * 4` bytes
(big-endian explícito). `decode(ByteArray(3))` lanza `IllegalArgumentException`
(la longitud no es múltiplo de 4). Un cambio de endianness en el codec debe
romper el round-trip (control negativo del validador).

### Scenario 7: Migración v1 → v2 y bootstrap en ambos caminos

Given un driver in-memory con el esquema **v1** aplicado a mano (`note` + una fila
legacy),
When se llama `PlaybookDatabase.Schema.migrate(driver, oldVersion = 1L,
newVersion = 2L)`,
Then `PlaybookDatabase.Schema.version == 2L`, la fila de `note` sobrevive, y la
tabla `embedding` existe y es usable. Un `Schema.create(driver)` desde cero
produce el mismo esquema final (v2) y la app arranca sin errores.

### Scenario 8: Ciclo de vida por FK (borrado de la Nota)

Given `PRAGMA foreign_keys = ON` y una Nota con embedding,
When se borra la fila de `note` (o el embedding vía `deleteByNoteId`),
Then el embedding se elimina (cascade) / `deleteByNoteId` devuelve `true` y
`getByNoteId` devuelve `null`; no quedan filas huérfanas.

### Scenario 9: Ambas plataformas + gate honesto

Given los tests compartidos de los escenarios 1–8,
When se corren `:core:testDebugUnitTest`, `:core:iosSimulatorArm64Test`,
`:composeApp:assembleDebug`, `:composeApp:linkDebugFrameworkIosSimulatorArm64` y
`./init.sh`,
Then todo pasa en Android/JVM e iOS, `./init.sh` termina en 0 sin dev servers ni
simuladores, y `PlaybookDatabase.Schema.version == 2` con `1.sqm` presente.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `ai-client-interface` y
  `note-model-crud-core` son las últimas `accepted` (Sessions 008/014); no hay
  bloqueos.
- `feature_list.json` — metadata de `embeddings-generation`; `depends_on`
  satisfecho; nota "BLOB + similitud en memoria".
- `docs/domain-model.md`, `CONTEXT.md` — `indexado` = embedding **y** enlaces;
  `embedding` opcional en la Nota; estados `capturada/pendiente/indexada/fallida`;
  edge case "indexado fallido no participa del RAG".
- `docs/build-brief.md` — slice 4: embeddings por Nota detrás de `AiClient` con
  estados; "la captura nunca debe bloquearse por la IA".
- `docs/technical-discovery.md` — "Vectores como BLOB en SQLDelight; similitud
  coseno en memoria"; esquema v1; regla `PRAGMA user_version` con
  `executeQuery`.
- `docs/risks-and-open-questions.md` — runtime de IA y dimensión del embedding
  siguen abiertos (no bloquean esta feature: el `FakeAiClient` es el default).
- `ARCHITECTURE.md` — dirección `:composeApp → :core`; dominio/persistencia en
  `:core`; puerto `AiClient` + `FakeAiClient`; sin DI (dependencias por
  constructor); "Deferred: embeddings".
- `docs/specs/ai-client-interface.md` — patrón de puerto, contrato y tests
  compartidos; el primer consumidor sería `embeddings-generation`.
- `docs/specs/note-model-crud-core.md` — patrón de repositorio + mapper + tests
  por plataforma; `update`/`delete` devuelven `rowsAffected > 0`.
- `core/build.gradle.kts`, `gradle/libs.versions.toml` — SQLDelight 2.1.0 y
  coroutines 1.10.1 ya presentes; no se requieren dependencias nuevas.
- `core/src/commonMain/sqldelight/com/playbook/core/db/Note.sq` — tabla `note`
  (v1) + queries; sin embeddings.
- `core/src/commonMain/kotlin/com/playbook/core/{ai,model,repository,db,platform}/**`
  — `AiClient`/`FakeAiClient`/`Embedding`; `Note`/`NoteDraft`; `NoteRepository` +
  `SqlDelightNoteRepository`; mapper con alias `Note as NoteRow`; `expect/actual`
  de id/tiempo.
- `core/src/{androidUnitTest,iosTest}/**` — patrón `JdbcSqliteDriver.IN_MEMORY` /
  `inMemoryDriver(Schema)` + helper compartido; `runTest` para `suspend`.
- `core/src/androidMain|iosMain/.../db/*DatabaseDriverFactory.kt` —
  `AndroidSqliteDriver(PlaybookDatabase.Schema, ...)` y
  `NativeSqliteDriver(PlaybookDatabase.Schema, ...)`: el driver aplica
  create/migrate según `Schema.version` y `user_version` automáticamente.
- `core/src/commonMain/kotlin/com/playbook/core/db/DatabaseDriverFactory.kt` —
  `createDatabase(...)` fuerza la apertura con `PRAGMA user_version`.
- `init.sh` — gate: `assembleDebug` + `testDebugUnitTest` + link iOS +
  `iosSimulatorArm64Test`; sin procesos de larga duración.

### Existing Patterns To Follow

- Dominio/persistencia/servicios en `:core`; UI y wiring en `:composeApp`. El
  `:core` no depende de UI.
- Sin DI: dependencias por constructor (`AiClient`, repos, `clock`).
- Mapper fila ↔ dominio con alias del import generado (`import ...Embedding as
  EmbeddingRow`).
- Tests compartidos en `commonTest` que reciben un `SqlDriver` + `@Test` por
  plataforma; `runTest` para `suspend`.
- SQLDelight: `INTEGER` → `Long`, `BLOB` → `ByteArray`; el esquema lo describe
  `.sq` y las migraciones van en `.sqm` (`<version a actualizar>.sqm`).
- Versiones centralizadas en `gradle/libs.versions.toml` (sin cambios).
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- No existe storage de embeddings ni tabla `embedding`; `schema.version` es 1 y
  no hay `.sqm`.
- No hay repositorio/servicio que consuma `AiClient` (`FakeAiClient` no tiene
  consumidor).
- No hay serialización de `List<Float>` a BLOB portable KMP.
- `createDatabase` no habilita `PRAGMA foreign_keys`, así que un `ON DELETE
  CASCADE` no se aplicaría por defecto (SQLite lo trae OFF).
- No hay verificación de migraciones (el repo nunca migró).

## Technical Approach

### 1. Esquema y migración (decisión y justificación)

**Decisión: tabla separada `embedding` 1:0..1 + `.sqm` v1 → v2.**

- **Tabla separada, no columnas en `note`.** Poner `embedding BLOB` en `note`
  obligaría a tocar la tabla y el CRUD aceptados, y `selectAllNotes()` cargaría
  todos los BLOBs al pintar la lista (el `NoteRow.toDomain()` actual no los
  necesita). Con tabla separada, las lecturas de Notas quedan livianas y
  `semantic-linking` puede pedir sólo `embedding.getAll()`.
- **`.sqm` obligatorio.** Cambia el esquema (nueva tabla). Con `1.sqm`,
  `PlaybookDatabase.Schema.version` pasa a **2**; los drivers (Android/Native) ya
  construidos con `Schema` migran **automáticamente** por `user_version`, y un
  `Schema.create` en instalación nueva crea el esquema final. Se descarta cambiar
  v1 "en el lugar" porque rompería instalaciones existentes (verificado:
  `playbook.db` con `user_version=1` en Android/iOS).
- **Sin `schemaOutputDirectory` ni `.db`.** El repo no usa `verifySqlDelightMigration`;
  se evita un binario versionado y la migración se verifica con un test portable
  (Scenario 7) que corre en Android/JVM e iOS. Se documenta la limitación: el
  `CREATE TABLE` de `1.sqm` debe replicar el de `.sq`.
- **FK + cascade.** `note_id ... REFERENCES note(id) ON DELETE CASCADE` para no
  dejar huérfanos al borrar Notas. Como SQLite trae FKs **OFF**, `createDatabase`
  agrega `PRAGMA foreign_keys = ON` (statement sin filas → `execute`, no
  `executeQuery`), después del `PRAGMA user_version` existente.

```sql
-- core/src/commonMain/sqldelight/com/playbook/core/db/embedding.sq
CREATE TABLE embedding (
  note_id TEXT NOT NULL PRIMARY KEY REFERENCES note(id) ON DELETE CASCADE,
  dimension INTEGER NOT NULL,
  vector BLOB NOT NULL,
  indexed_at INTEGER NOT NULL
);

upsertEmbedding:
INSERT OR REPLACE INTO embedding (note_id, dimension, vector, indexed_at)
VALUES (?, ?, ?, ?);

selectEmbeddingByNoteId:
SELECT * FROM embedding WHERE note_id = ?;

selectAllEmbeddings:
SELECT * FROM embedding ORDER BY note_id;

deleteEmbeddingByNoteId:
DELETE FROM embedding WHERE note_id = ?;
```

```sql
-- core/src/commonMain/sqldelight/com/playbook/core/db/1.sqm (v1 -> v2)
CREATE TABLE embedding (
  note_id TEXT NOT NULL PRIMARY KEY REFERENCES note(id) ON DELETE CASCADE,
  dimension INTEGER NOT NULL,
  vector BLOB NOT NULL,
  indexed_at INTEGER NOT NULL
);
```

`INSERT OR REPLACE` en vez de `ON CONFLICT DO UPDATE`: el UPSERT de SQLite
requiere 3.24 (Android 11+), y `minSdk = 24` (SQLite 3.9). `INSERT OR REPLACE` es
seguro en todas las versiones soportadas. Se guarda un único `indexed_at`
(re-indexar lo actualiza); no hace falta `created_at` para datos derivados.

### 2. Modelo y codec

- `com.playbook.core.model.NoteEmbedding(noteId: String, values: List<Float>,
  indexedAt: Long)`: entidad persistida (el `dimension` de la tabla es
  `values.size`).
- `com.playbook.core.repository.EmbeddingBlobCodec` (`internal object`):
  serializa `List<Float>` a `ByteArray` con **big-endian explícito** usando
  `Float.toBits()`/`Float.fromBits()` y 4 bytes por valor (puro Kotlin common,
  sin dependencias y estable entre plataformas). `decode` exige longitud múltiplo
  de 4 (`IllegalArgumentException` si no).
- `NoteEmbeddingMappers.kt`: `import com.playbook.core.db.Embedding as
  EmbeddingRow`; `EmbeddingRow.toDomain()` decodifica el BLOB y hace `check`
  de que `dimension == values.size` (fail-fast ante corrupción). Se confirma el
  nombre exacto que genere SQLDelight (la tabla de una palabra `embedding`
  produce `Embedding`, como `note` → `Note`).

### 3. Repositorio

```kotlin
interface EmbeddingRepository {
    /** Inserta o reemplaza el embedding de [noteId]; sella `indexedAt` con clock(). */
    fun upsert(noteId: String, values: List<Float>): NoteEmbedding
    fun getByNoteId(noteId: String): NoteEmbedding?
    fun getAll(): List<NoteEmbedding>
    fun deleteByNoteId(noteId: String): Boolean
}
```

`SqlDelightEmbeddingRepository(database, clock)`: `upsert` `require(values.isNotEmpty())`,
encodea y escribe; `getAll`/`getByNoteId` mapean; `deleteByNoteId` devuelve
`rowsAffected > 0`.

### 4. Servicio de indexado (consumidor de `AiClient`)

```kotlin
sealed interface IndexingResult {
    data class Indexed(val embedding: NoteEmbedding) : IndexingResult
    data class Failed(val cause: AiClientException) : IndexingResult
}

class NoteIndexingService(
    private val aiClient: AiClient,
    private val embeddingRepository: EmbeddingRepository,
) {
    suspend fun index(note: Note): IndexingResult
}
```

`index`: `aiClient.embed(listOf(note.body))`; si lanza `AiClientException` →
`Failed`. Si la respuesta no tiene exactamente 1 vector con `values` no vacíos →
`Failed(AiClientException(...))` **sin escribir**. Si es válida →
`embeddingRepository.upsert(note.id, values)` → `Indexed`. No toca `NoteStatus`.
Al ser `suspend`, el llamador puede correrlo fuera del camino de captura.

### 5. Bootstrap

`createDatabase(...)` agrega `driver.execute(null, "PRAGMA foreign_keys = ON", 0)`
tras el `PRAGMA user_version` (sin filas → `execute`). Es idempotente por
conexión y no afecta la tabla `note`. Sin cambios en `init.sh`.

## Expected File Changes

- `core/src/commonMain/sqldelight/com/playbook/core/db/embedding.sq` — crear;
  tabla + queries.
- `core/src/commonMain/sqldelight/com/playbook/core/db/1.sqm` — crear; migración
  v1 → v2.
- `core/src/commonMain/kotlin/com/playbook/core/model/NoteEmbedding.kt` — crear.
- `core/src/commonMain/kotlin/com/playbook/core/model/NoteEmbeddingMappers.kt` —
  crear; fila ↔ dominio + validación de dimensión.
- `core/src/commonMain/kotlin/com/playbook/core/repository/EmbeddingBlobCodec.kt`
  — crear; codec big-endian.
- `core/src/commonMain/kotlin/com/playbook/core/repository/EmbeddingRepository.kt`
  — crear; interfaz.
- `core/src/commonMain/kotlin/com/playbook/core/repository/SqlDelightEmbeddingRepository.kt`
  — crear; implementación.
- `core/src/commonMain/kotlin/com/playbook/core/ai/NoteIndexingService.kt` — crear;
  servicio + `IndexingResult`.
- `core/src/commonMain/kotlin/com/playbook/core/db/DatabaseDriverFactory.kt` —
  modificar; `PRAGMA foreign_keys = ON`.
- `core/src/commonTest/kotlin/com/playbook/core/repository/EmbeddingRepositoryCheck.kt`
  — crear; helpers de repositorio y codec.
- `core/src/commonTest/kotlin/com/playbook/core/ai/NoteIndexingCheck.kt` — crear;
  helpers del servicio + `AiClient` stubs (fallo/respuesta inválida).
- `core/src/commonTest/kotlin/com/playbook/core/db/DatabaseMigrationCheck.kt` —
  crear; helper v1 → v2.
- `core/src/androidUnitTest/kotlin/com/playbook/core/{repository,ai,db}/*AndroidTest.kt`
  — crear; `@Test` JVM (`JdbcSqliteDriver.IN_MEMORY`, `runTest`).
- `core/src/iosTest/kotlin/com/playbook/core/{repository,ai,db}/*IosTest.kt` —
  crear; `@Test` iOS (`inMemoryDriver(Schema)`, `runTest`).
- `ARCHITECTURE.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md` — modificar; decisiones y estado.
- `AGENTS.md` — modificar sólo la línea "Próxima feature en cola" al cierre.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado y evidencia.

No se esperan cambios en `:composeApp`, `iosApp/**`, `init.sh`, `Note.sq` ni en
`gradle/libs.versions.toml`.

## Visual Design Impact

- UI involved: no.
- Design source: not applicable.
- Screens or states affected: ninguna.
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — nueva tabla `embedding`, esquema **v2** con
  `1.sqm`, `PRAGMA foreign_keys`, `EmbeddingRepository` + `NoteIndexingService`
  como primer consumidor de `AiClient`.
- `CONSTRAINTS.md`: **not needed** — la regla durable "el dominio consume sólo el
  puerto `AiClient`" ya está en `ARCHITECTURE.md`; no surge un MUST/MUST NOT nuevo.
- `AGENTS.md`: **update (acotado)** — sólo la línea "Próxima feature en cola"
  (práctica del repo); no cambian workflow, arranque ni gate.
- Other docs: `docs/technical-discovery.md` — update ("Embedding Decisions");
  `docs/risks-and-open-questions.md` — update ligero (embeddings ya persisten;
  dimensión/runtime y `sqlite-vec` siguen abiertos); `docs/domain-model.md` /
  `CONTEXT.md` — not needed (la spec se alinea); `PROGRESS.md` /
  `feature_list.json` — update al cerrar.

## Implementation Plan

1. Crear `embedding.sq` + `1.sqm`; confirmar `Schema.version == 2` y que
   `Schema.create` sigue creando el esquema completo.
2. Escribir `EmbeddingBlobCodec` + `NoteEmbedding` + mapper (alias `EmbeddingRow`).
3. Implementar `EmbeddingRepository` + `SqlDelightEmbeddingRepository`.
4. Implementar `NoteIndexingService` + `IndexingResult` (consumidor de `AiClient`).
5. Habilitar `PRAGMA foreign_keys = ON` en `createDatabase`.
6. Escribir helpers en `commonTest` (repositorio/codec, servicio, migración) y los
   `@Test` por plataforma con `runTest`.
7. Actualizar docs durables; correr verificación, smoke real de migración y
   registrar evidencia.

## Implementation Tasks

- [x] Crear `embedding.sq` (tabla 1:0..1 + upsert/select/delete) y `1.sqm`.
- [x] Confirmar bump a `Schema.version = 2` y que `NotePersistence`/`NoteRepository` siguen en verde.
- [x] Crear `NoteEmbedding`, `NoteEmbeddingMappers` y `EmbeddingBlobCodec` (big-endian).
- [x] Crear `EmbeddingRepository` + `SqlDelightEmbeddingRepository(clock)`.
- [x] Crear `NoteIndexingService` + `IndexingResult` sobre `AiClient`.
- [x] Agregar `PRAGMA foreign_keys = ON` a `createDatabase`.
- [x] Escribir `EmbeddingRepositoryCheck` (round-trip/codec/cascade) y sus `@Test` Android/iOS.
- [x] Escribir `NoteIndexingCheck` (fake, overwrite, fallo, respuesta inválida) y sus `@Test`.
- [x] Escribir `DatabaseMigrationCheck` (v1 → v2) y sus `@Test`.
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`.
- [x] Correr la verificación + smoke real de migración y registrar evidencia en `feature_list.json`/`PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → nuevos tests (repositorio,
  codec, servicio, migración) en verde + los previos
  (`NotePersistence` 2/2, `NoteRepository` 1/1, `AiClient` 3/3, `Greeting` 1/1),
  0 failures.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` (macOS) → mismos tests en
  verde en iOS, 0 failures.
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (el `:core` v2 compila
  en Android).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL
  (el `:core` v2 enlaza en iOS).
- Confirmar `PlaybookDatabase.Schema.version == 2` y `1.sqm` presente (no queda
  "v1 sin `.sqm`").
- `./init.sh` → exit 0; gate no bloqueante, sin dev servers ni simuladores, sin
  cambios en `init.sh`.
- **Control negativo**: romper el codec (p. ej. endianness invertido) o hacer que
  `index` persista antes de validar debe hacer fallar los tests de Scenarios 5/6;
  restaurar.
- **Smoke real de migración** (manual, no en `init.sh`): en Android
  (`Pixel_3A_API_34`) e iOS (iPhone 15 / iOS 17.2), partir de un `playbook.db` v1
  (de una build previa), lanzar la app y verificar `user_version = 2`, la tabla
  `embedding` creada y la preservación de las Notas, sin crashes.
- E2E persistente: **no aplica**. No hay flujo de usuario/API observable (sin UI
  ni wiring) ni harness E2E; `init.sh` no levanta simuladores. La cobertura es de
  tests del core en dos plataformas + compilación de la app + smoke manual de
  migración. Se justifica explícitamente.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` con los
  nuevos tests en verde (nombres y conteos por plataforma).
- Salida de `:composeApp:assembleDebug` y `:composeApp:linkDebugFrameworkIosSimulatorArm64`.
- `./init.sh` → exit 0, sin procesos de larga duración.
- `PlaybookDatabase.Schema.version == 2`; `1.sqm` presente; `Note.sq` sin cambios.
- Control negativo reproducido y restaurado (codec o persistencia prematura).
- Smoke de migración real en Android e iOS: `user_version = 2`, tabla `embedding`,
  Notas preservadas, sin crashes.
- Confirmación de que no se tocaron `:composeApp`/`iosApp`/`init.sh`/
  `libs.versions.toml` ni `Note.sq`.

## Validator Checklist

- [ ] Alcance respetado: sólo generación + persistencia BLOB + migración v2; sin
      UI, wiring, `NoteStatus`, enlaces, RAG ni runtime real.
- [ ] Escenarios de aceptación 1–9 pasan.
- [ ] `Schema.version == 2` con `1.sqm`; la migración v1 → v2 preserva los datos y
      `Schema.create` produce el mismo esquema.
- [ ] El BLOB se recupera bit-exacto (codec big-endian) y `dimension` consistente.
- [ ] `NoteIndexingService` es el primer consumidor de `AiClient`; un fallo o una
      respuesta inválida no persiste ni pisa datos.
- [ ] `PRAGMA foreign_keys = ON` habilita el cascade de `embedding`; sin huérfanos.
- [ ] El estado de la Nota no cambia al indexar (decisión de scope) y el repo lo
      refleja.
- [ ] Evidencia en `feature_list.json` / `PROGRESS.md`; smoke real de migración
      declarado honestamente.
- [ ] No hay E2E persistente y la spec justifica por qué.
- [ ] `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados;
      `init.sh` sin cambios, no bloqueante y sin procesos de larga duración.
