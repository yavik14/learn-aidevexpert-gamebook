# Feature Implementation Spec: Modelo Nota y CRUD en el core KMP

## Source Feature

- `id`: `note-model-crud-core`
- `area`: `core`
- `depends_on`: `["local-persistence-sqldelight"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Introducir en `:core` el **modelo de dominio `Note`** y su **CRUD** (crear, leer,
actualizar, borrar) sobre la persistencia SQLDelight existente (tabla `note`,
esquema v1). El modelo cubre `id`, `owner`, `body`, `track` (enum fijo y
nullable), `status` (enum del ciclo de vida) y `createdAt`/`updatedAt`. Se agrega
un `NoteRepository` con implementación SQLDelight, se extienden las queries de
`Note.sq` (`updateNote`, `deleteNoteById`) y se cubre el CRUD con tests
compartidos en Android/JVM e iOS.

Habilita `notes-list-ui` y las features de captura/IA. **No incluye UI ni wiring
en la app**: sin consumidor todavía, el repositorio se verifica vía tests.

### Límite de alcance con features vecinas

- **`note-category-and-tags`** queda fuera salvo el enum `Track`. Las
  **etiquetas** son N-N (tablas `tag`/`note_tag`) y no se crean aquí. `Track` sí
  entra: ya es columna de `note`, valor escalar único y `create`/`update` deben
  fijarlo. El vecino se limita a etiquetas (y su UI); su texto actual
  ("Categoría") es terminología obsoleta a normalizar a `Track`.
- Alineación de vocabulario: se usa **`Track`** (no `Categoría`/`Tipo`) y se
  corrigen los ejemplos de la persistencia previa (`"mechanics"`/`"captured"` →
  códigos canónicos en español, ver abajo).

## Non-Goals

- **Etiquetas**: tablas `tag`/`note_tag`, campo `tags`, alta/edición/borrado →
  `note-category-and-tags`.
- Adjuntos (`attachment`), enlaces (`link`), embeddings, indexado/IA,
  clasificación automática, GDD y RAG.
- UI, view models, navegación o wiring del repositorio en `MainActivity`/
  `MainViewController` (lo hará `notes-list-ui`).
- Migraciones o bump de `schema.version`: sólo se agregan *queries*; la tabla no
  cambia (sigue v1, sin `.sqm`).
- Cambiar la capa de persistencia aceptada (drivers, `createDatabase`, esquema).
- Multi-usuario, auth, permisos o validación de contenido.

## Job Story

When trabajo en el core de Playbook sobre la base SQLDelight ya existente,
I want un modelo `Note` y un repositorio CRUD tipados,
so I can crear, leer, actualizar y borrar notas con `track` y `status` de forma
verificable desde Android e iOS, sin depender de UI.

## Users And Permissions

- Desarrollador (autor): único actor; ejecuta Gradle/tests. Sin usuarios finales.
- `owner` es obligatorio en el modelo, pero en el MVP single-user lo provee el
  llamador (sin auth ni permisos).

## Acceptance Scenarios

### Scenario 1: Crear nota

Given una `PlaybookDatabase` con el esquema v1 y un `SqlDelightNoteRepository`
con `idFactory`/`clock` deterministas,
When se llama `create(NoteDraft(owner, body, track, status))`,
Then devuelve una `Note` con `id` generado, `createdAt == updatedAt == clock()`,
los campos del draft y `status` default `CAPTURED`; `getById(id)` y `getAll()` la
recuperan con los mismos valores.

### Scenario 2: Actualizar nota

Given una nota persistida,
When se llama `update(note)` con otro `body`/`track`/`status`,
Then devuelve `true`, `getById` refleja los cambios y `updatedAt` avanzó al
`clock()` actual (el `updatedAt` del argumento se ignora). Si el `id` no existe,
devuelve `false` y no lanza.

### Scenario 3: Borrar nota

Given una nota persistida,
When se llama `delete(id)`,
Then devuelve `true`, `getById` devuelve `null` y `getAll()` ya no la contiene.
`delete(idInexistente)` devuelve `false`.

### Scenario 4: Round-trip de `track` y `status`

Given notas creadas con cada valor de `Track` y de `NoteStatus`,
When se releen del repositorio,
Then los enums coinciden; `track = null` se persiste y relee como `null`; un valor
de `track` desconocido almacenado se mapea a `null` (no rompe la lectura).

### Scenario 5: Mismo test en Android/JVM e iOS

Given el helper compartido de CRUD,
When se corren `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test`,
Then ambos pasan usando drivers in-memory (`JdbcSqliteDriver.IN_MEMORY` e
`inMemoryDriver`).

### Scenario 6: Gate honesto

Given la feature implementada,
When se ejecuta `./init.sh`,
Then sigue terminando en 0, sin dev servers ni simuladores, e incluye los tests
del core en ambas plataformas (Android/JVM e iOS en macOS).

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD, WIP=1, gate no bloqueante; esta es la
  próxima feature y `local-persistence-sqldelight` está `accepted`.
- `feature_list.json` — metadata/dependencia; su `user_visible_behavior` aún
  menciona "etiquetas" (fuera de alcance).
- `ARCHITECTURE.md` — dirección `:composeApp → :core`; persistencia SQLDelight;
  "Deferred: modelo, repositorio y CRUD de `Note`".
- `CONTEXT.md`, `docs/domain-model.md` — `Track` (`mecánicas`/`personajes`/
  `historia`) vs `Etiqueta`; estados `capturada`/`pendiente`/`indexada`/`fallida`;
  edge case "Nota sin `track` no rompe la vista".
- `docs/technical-discovery.md` — SQLDelight única fuente local; esquema v1;
  `createDatabase` con `executeQuery`; tests in-memory por plataforma.
- `docs/risks-and-open-questions.md`, `docs/build-brief.md` — `feature_list.json`
  desactualizado ("categoría/tipo"); slice = modelo + persistencia + CRUD.
- `docs/specs/local-persistence-sqldelight.md` — spec aceptada precedente:
  esquema, drivers, halo de tests y regla "evitar expect/actual en tests".
- `docs/specs/kmp-project-bootstrap.md` — estilo y nivel de detalle de spec.
- `core/build.gradle.kts` — `:core` KMP con `commonTest`/`androidUnitTest`/
  `iosTest`; deps SQLDelight (runtime, android/native/sqlite driver).
- `core/src/commonMain/sqldelight/.../Note.sq` — tabla `note` (`track TEXT`
  nullable) + `insertNote`, `selectAllNotes`, `selectNoteById` (falta update/delete).
- `core/src/commonMain/kotlin/.../db/DatabaseDriverFactory.kt` — factory +
  `createDatabase(...)`.
- `core/src/commonTest/.../db/NotePersistenceCheck.kt`,
  `DatabaseCreationCheck.kt` y tests por plataforma — patrón de test y ejemplos
  `"mechanics"`/`"captured"` a unificar.
- `init.sh` — gate: `assembleDebug`, `testDebugUnitTest`, link iOS y
  `iosSimulatorArm64Test` (macOS), sin procesos de larga duración.

### Existing Patterns To Follow

- Dominio y persistencia en `:core`; UI/wiring en `:composeApp`. El repositorio
  no toca `:composeApp`.
- Lógica de test compartida en `commonTest` sobre un `SqlDriver`; `@Test` por
  plataforma (`androidUnitTest` JVM in-memory; `iosTest` `inMemoryDriver`).
- Versiones centralizadas en `gradle/libs.versions.toml`; sin dependencias nuevas.
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- No existe modelo Kotlin (`Note`, `Track`, `NoteStatus`) ni repositorio/CRUD.
- `Note.sq` no tiene `update` ni `delete`.
- `track`/`status` son `TEXT` libre: sin enum ni mapeo canónico.
- No hay generación de `id` ni timestamps en el core.
- Colisión de nombres: SQLDelight genera `com.playbook.core.db.Note` para la tabla;
  el mapper debe aliasar el import.
- `feature_list.json` conserva términos obsoletos ("etiquetas", "categoría").

## Technical Approach

### Modelo de dominio (`com.playbook.core.model`)

```kotlin
data class Note(
    val id: String, val owner: String, val body: String,
    val track: Track?, val status: NoteStatus,
    val createdAt: Long, val updatedAt: Long,
)

data class NoteDraft(
    val owner: String, val body: String,
    val track: Track?, val status: NoteStatus = NoteStatus.CAPTURED,
)
```

Enums con código de persistencia (alineados con `CONTEXT.md`/`domain-model.md`;
identificadores en inglés, valores de dominio en español):

| Enum | Código persistido |
| --- | --- |
| `Track.MECHANICS` | `"mecánicas"` |
| `Track.CHARACTERS` | `"personajes"` |
| `Track.STORY` | `"historia"` |
| `NoteStatus.CAPTURED` | `"capturada"` |
| `NoteStatus.PENDING` | `"pendiente"` |
| `NoteStatus.INDEXED` | `"indexada"` |
| `NoteStatus.FAILED` | `"fallida"` |

- `Track.fromCode(code: String?): Track?` → `null` si es `null` o desconocido
  (preserva el edge case "Nota sin `track`").
- `NoteStatus.fromCode(code: String): NoteStatus` → lanza
  `IllegalArgumentException` si es desconocido (campo `NOT NULL`, sólo lo escribe
  este código; un valor inválido es corrupción).

### Repositorio (`com.playbook.core.repository`)

```kotlin
interface NoteRepository {
    fun create(draft: NoteDraft): Note
    fun getAll(): List<Note>
    fun getById(id: String): Note?
    fun update(note: Note): Boolean   // re-sella updated_at con clock()
    fun delete(id: String): Boolean
}
```

`SqlDelightNoteRepository(database, idFactory: () -> String, clock: () -> Long)`:

- `create`: `id = idFactory()`, `createdAt = updatedAt = clock()`, inserta, devuelve `Note`.
- `getAll`/`getById`: `selectAllNotes`/`selectNoteById` + mapper de fila.
- `update`: escribe `body`/`track`/`status` y `updated_at = clock()` de la fila;
  preserva `owner`/`created_at`; devuelve `rowsAffected > 0`.
- `delete`: `deleteNoteById`; devuelve `rowsAffected > 0`.

### Mapeo fila ↔ dominio

- `import com.playbook.core.db.Note as NoteRow` para evitar la colisión con el
  modelo de dominio.
- `NoteRow.toDomain()` usa `Track.fromCode(track)` y `NoteStatus.fromCode(status)`.
- La escritura usa `track?.code` y `status.code`.

### IDs y tiempo

- Se inyectan `idFactory: () -> String` y `clock: () -> Long` (tests
  deterministas; sin acoplar el dominio a `kotlinx-datetime`).
- Defaults de producción vía `expect`/`actual` en
  `commonMain`/`androidMain`/`iosMain` (`randomNoteId()`, `currentTimeMillis()`);
  en tests se pasan fakes. No se usa `expect/actual` en source sets de test.

### SQL (`Note.sq`, v1, sin migración)

```sql
updateNote:
UPDATE note SET body = ?, track = ?, status = ?, updated_at = ? WHERE id = ?;

deleteNoteById:
DELETE FROM note WHERE id = ?;
```

No cambia la tabla ni columnas: `schema.version` sigue en 1, sin `.sqm`. Se
reconcilia el ejemplo de `NotePersistenceCheck` a los códigos canónicos
(`"capturada"`, `"mecánicas"`).

### Tests

- `commonTest`: `verifyNoteCrud(driver: SqlDriver)` construye el repositorio con
  `idFactory`/`clock` deterministas y cubre crear + leer (por id y lista),
  actualizar (avance de `updatedAt` y caso `id` inexistente), borrar (y caso
  inexistente), round-trip de todos los `Track`/`NoteStatus` y `track = null`.
  Incluye releer con una segunda instancia sobre el mismo driver.
- `androidUnitTest`: `NoteRepositoryAndroidTest` con `JdbcSqliteDriver.IN_MEMORY`
  + `PlaybookDatabase.Schema.create(driver)`.
- `iosTest`: `NoteRepositoryIosTest` con `inMemoryDriver(PlaybookDatabase.Schema)`.

### `init.sh`

No requiere cambios: ya corre ambas tareas de test del core, no inicia dev
servers y es no bloqueante. El implementer confirma que sigue en 0 y que los
tests nuevos aparecen.

### Decisiones abiertas y riesgos

- **Códigos de `Track` con acento** (`"mecánicas"`): SQLite (JVM y nativo) usa
  UTF-8; riesgo bajo; los tests assertan el string exacto. Usar ASCII
  (`"mechanics"`) requeriría actualizar `CONTEXT.md`/`domain-model.md` (fuente de
  verdad), así que se descarta.
- **`NoteStatus` desconocido**: fail-fast (campo `NOT NULL` escrito sólo aquí).
  Revisar si en el futuro se leen datos externos.
- **Sin wiring del repositorio en la app**: intencional; `notes-list-ui` lo
  instanciará con los defaults de plataforma.
- **Paquetes** `model`/`repository`/`platform`: propuesta ajustable sin cambiar
  el contrato funcional.

## Expected File Changes

- `core/src/commonMain/sqldelight/com/playbook/core/db/Note.sq` — modificar;
  agregar `updateNote` y `deleteNoteById` (sin cambios de esquema).
- `core/src/commonMain/kotlin/com/playbook/core/model/Track.kt` — crear; enum + código.
- `core/src/commonMain/kotlin/com/playbook/core/model/NoteStatus.kt` — crear; enum + código.
- `core/src/commonMain/kotlin/com/playbook/core/model/Note.kt` — crear; `Note` + `NoteDraft`.
- `core/src/commonMain/kotlin/com/playbook/core/model/NoteMappers.kt` — crear; fila ↔ dominio.
- `core/src/commonMain/kotlin/com/playbook/core/repository/NoteRepository.kt` — crear; interfaz.
- `core/src/commonMain/kotlin/com/playbook/core/repository/SqlDelightNoteRepository.kt` — crear; implementación.
- `core/src/commonMain/kotlin/com/playbook/core/platform/PlatformDefaults.kt` — crear; `expect` id/tiempo.
- `core/src/androidMain/kotlin/com/playbook/core/platform/PlatformDefaults.android.kt` — crear; `actual` (`UUID`/`System`).
- `core/src/iosMain/kotlin/com/playbook/core/platform/PlatformDefaults.ios.kt` — crear; `actual` (`NSUUID`/`NSDate`).
- `core/src/commonTest/kotlin/com/playbook/core/repository/NoteRepositoryCheck.kt` — crear; helper CRUD compartido.
- `core/src/androidUnitTest/kotlin/com/playbook/core/repository/NoteRepositoryAndroidTest.kt` — crear; `@Test` JVM.
- `core/src/iosTest/kotlin/com/playbook/core/repository/NoteRepositoryIosTest.kt` — crear; `@Test` iOS.
- `core/src/commonTest/kotlin/com/playbook/core/db/NotePersistenceCheck.kt` — modificar; unificar vocabulario de ejemplo.
- `ARCHITECTURE.md` — modificar; documentar modelo/repositorio en `:core`.
- `docs/technical-discovery.md` — modificar; registrar modelo, códigos de enum y defaults.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado y evidencia.

## Visual Design Impact

- UI involved: no. Design source: not applicable. Screens or states affected:
  ninguna. New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — se establece la capa de dominio (`model/`) y
  repositorio (`repository/`) en `:core`, con el mapeo a SQLDelight.
- `CONSTRAINTS.md`: not needed — no surge una regla MUST/MUST NOT nueva.
- `AGENTS.md`: not needed — no cambia workflow, arranque ni gate.
- Other docs: `docs/technical-discovery.md` — update; `docs/domain-model.md`/
  `CONTEXT.md` — not needed (la spec se alinea con ellos); `PROGRESS.md` y
  `feature_list.json` — update al cerrar.

## Implementation Plan

1. Crear `Track`/`NoteStatus` con `code`/`fromCode`.
2. Crear `Note`/`NoteDraft` y el mapper (alias `NoteRow`).
3. Agregar `updateNote`/`deleteNoteById` a `Note.sq`; confirmar codegen sin cambios de esquema.
4. Implementar `NoteRepository` + `SqlDelightNoteRepository` con `idFactory`/`clock` y los `expect/actual` de plataforma.
5. Escribir `verifyNoteCrud` + tests por plataforma; unificar el vocabulario de `NotePersistenceCheck`.
6. Actualizar docs durables, correr la verificación y registrar evidencia.

## Implementation Tasks

- [x] Crear `Track` (`mecánicas`/`personajes`/`historia`) y `NoteStatus` (`capturada`/`pendiente`/`indexada`/`fallida`) con `code`/`fromCode`.
- [x] Crear `Note` y `NoteDraft` en `com.playbook.core.model`.
- [x] Crear el mapper fila↔dominio usando `import ...Note as NoteRow`.
- [x] Agregar `updateNote` y `deleteNoteById` a `Note.sq` (sin tocar el esquema).
- [x] Definir `NoteRepository` y `SqlDelightNoteRepository` con `idFactory`/`clock`.
- [x] Agregar `randomNoteId()`/`currentTimeMillis()` con `expect`/`actual` Android/iOS.
- [x] Escribir `verifyNoteCrud(driver)` en `commonTest` (create/read/update/delete, round-trip, `track = null`, ids inexistentes).
- [x] Escribir `NoteRepositoryAndroidTest` (`JdbcSqliteDriver.IN_MEMORY`) e `NoteRepositoryIosTest` (`inMemoryDriver`).
- [x] Unificar los valores de ejemplo de `NotePersistenceCheck` a los códigos canónicos.
- [x] Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
- [x] Correr la verificación y registrar evidencia en `feature_list.json`/`PROGRESS.md`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest` → `NoteRepositoryAndroidTest` en verde +
  tests previos.
- `./gradlew :core:iosSimulatorArm64Test` → el mismo helper en verde en iOS (macOS).
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL (`:core` compila Android).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (`:core` compila iOS).
- `./init.sh` → exit 0; corre el gate no bloqueante actual (tests del core en
  Android/JVM e iOS), **no** inicia dev servers ni simuladores y puede imprimir
  los comandos manuales.
- E2E persistente: no aplica. No hay flujo de usuario/API observable (la UI de
  notas llega en `notes-list-ui`); la cobertura es de tests del core en dos
  plataformas más la compilación de la app. Se justifica explícitamente.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` con los
  tests de CRUD en verde (nombres).
- Salida de `:composeApp:assembleDebug` y `:composeApp:linkDebugFrameworkIosSimulatorArm64`.
- Salida de `./init.sh` (exit 0) sin procesos de larga duración.
- Confirmación de que `schema.version` sigue en 1, sin `.sqm`.

## Validator Checklist

- [ ] Alcance respetado: modelo `Note` + CRUD en `:core`; sin UI ni
      etiquetas/adjuntos/enlaces/embeddings/IA.
- [ ] Escenarios de aceptación 1–6 pasan.
- [ ] El esquema sigue en v1 (sólo queries nuevas) y `createDatabase` no se rompe.
- [ ] Vocabulario de dominio `Track`/`Etiqueta`, no `Categoría`/`Tipo`.
- [ ] Evidencia en `feature_list.json` / `PROGRESS.md`.
- [ ] No hay E2E persistente y la spec justifica por qué.
- [ ] `ARCHITECTURE.md` y `docs/technical-discovery.md` actualizados.
- [ ] `init.sh` no bloqueante y sin procesos de larga duración.
