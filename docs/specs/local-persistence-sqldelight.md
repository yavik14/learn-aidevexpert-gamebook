# Feature Implementation Spec: Persistencia local con SQLDelight

## Source Feature

- `id`: `local-persistence-sqldelight`
- `area`: `persistence`
- `depends_on`: `["kmp-project-bootstrap"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Introducir la capa de persistencia local con **SQLDelight** en el módulo `:core`
KMP: base de datos generada, esquema inicial de la tabla `note` (esquema v1) y
un `DatabaseDriverFactory` con implementaciones para Android
(`AndroidSqliteDriver`) e iOS (`NativeSqliteDriver`). Al terminar, existe un
test del core que abre una base en memoria, escribe y lee un registro, y ese
mismo test corre tanto en Android/JVM como en iOS; la app instancia la base al
arrancar para aplicar el esquema.

Esta feature habilita una base local-first sobre la que `note-model-crud-core`
construirá el modelo y el CRUD. No agrega lógica de dominio ni UI de producto.

## Non-Goals

- Modelo de dominio `Note` en Kotlin, repositorio, mappers o casos de uso CRUD;
  todo eso pertenece a `note-model-crud-core`.
- Tablas o columnas de `tag`, `attachment`, `link` y `embedding`; pertenecen a
  `note-category-and-tags`, `voice-attachment-storage`, `embeddings-generation`,
  etc. Aquí solo se crea la tabla `note` con sus columnas escalares.
- Migraciones/versionado más allá del esquema inicial v1 (`schema.version = 1`,
  sin archivos `.sqm`).
- UI de lista/detalle, captura (texto/voz/imagen), IA, GDD, RAG, sync/backend.
- Cambios de versión de Kotlin/Compose/AGP del bootstrap. SQLDelight debe
  convivir con las versiones fijadas (ver Decisiones Abiertas).

## Job Story

When empiezo a construir Playbook sobre la base KMP,
I want una base de datos local compartida con sus drivers por plataforma,
so I can persistir las notas y que sobrevivan al reinicio en Android e iOS.

## Users And Permissions

- Desarrollador (autor): ejecuta Gradle, tests y lanza la app. Único actor.
- Sin usuarios finales, permisos, auth ni datos sensibles en esta feature.

## Acceptance Scenarios

### Scenario 1: Escribir y leer en Android/JVM

Given el esquema y el driver de test configurados,
When se ejecuta `./gradlew :core:testDebugUnitTest`,
Then el test abre una base in-memory, inserta una nota y la vuelve a leer con
el mismo contenido, sin errores.

### Scenario 2: Escribir y leer en iOS

Given el driver nativo de test configurado,
When se ejecuta `./gradlew :core:iosSimulatorArm64Test` (macOS),
Then el mismo test común corre con `inMemoryDriver`, inserta y lee la nota.

### Scenario 3: Drivers por plataforma compilan en la app

Given las implementaciones `AndroidDatabaseDriverFactory` e
`IosDatabaseDriverFactory`,
When se compilan `:composeApp:assembleDebug` y
`:composeApp:linkDebugFrameworkIosSimulatorArm64`,
Then ambos targets compilan y la app instancia la base al arrancar para
aplicar el esquema (sin consultas ni UI nueva).

### Scenario 4: Gate honesto

Given la feature implementada,
When se ejecuta `./init.sh`,
Then sigue terminando en 0 sin procesos de larga duración; en macOS incluye
también el test del core en iOS.

## Repository Research

### Files Inspected

- `AGENTS.md` — reglas de trabajo, flujo SDD, gate no bloqueante y definición de hecho.
- `PROGRESS.md` — estado verificado: `kmp-project-bootstrap` `accepted`; próximo paso esta feature.
- `feature_list.json` — metadata y dependencia; verificación provisional ("abre la DB, escribe y lee").
- `ARCHITECTURE.md` — módulos, dirección de dependencias (`:composeApp` → `:core`) y regla "lógica de dominio en `:core`".
- `docs/technical-discovery.md` — SQLDelight es la persistencia decidida, local-first, sin backend.
- `docs/domain-model.md` — `Nota` con `owner`, cuerpo, `track`, `etiquetas`, `estado`, `embedding`, `adjuntos`; estado `capturada → indexada/pendiente/fallida`.
- `CONTEXT.md` — glosario: `Track` (enum fijo) reemplaza a `Categoría`; `Etiqueta` es libre.
- `docs/risks-and-open-questions.md` — `feature_list.json` quedó desactualizado tras introducir `track` (menciona "categoría/tipo").
- `core/build.gradle.kts` — KMP library con `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`; `commonTest` con `kotlin-test`.
- `composeApp/build.gradle.kts` — Compose MP; framework iOS estático; depende de `:core`.
- `gradle/libs.versions.toml` — Kotlin 2.1.21, AGP 8.10.1, Gradle 8.14.5.
- `core/src/commonMain/kotlin/com/playbook/core/Greeting.kt` y su test — patrón de test trivial a reemplazar/complementar.
- `composeApp/src/androidMain/.../MainActivity.kt`, `composeApp/src/iosMain/.../MainViewController.kt` — puntos de arranque para el wiring.
- `init.sh` — gate actual: `:composeApp:assembleDebug`, `:core:testDebugUnitTest` y link del framework iOS.
- `docs/specs/kmp-project-bootstrap.md` — estilo y nivel de detalle de spec.

### Existing Patterns To Follow

- Módulos y dirección de dependencia fijados en `ARCHITECTURE.md`: dominio y
  persistencia en `:core`; UI y wiring de plataforma en `:composeApp`.
- Versiones centralizadas en `gradle/libs.versions.toml` (nunca hardcodear).
- Tests del core en `commonTest` + targets de test por plataforma.
- `init.sh` como gate no bloqueante, sin dev servers ni simuladores.
- Documentos en español, términos técnicos en inglés.

### Current Gaps

- No existe SQLDelight ni ninguna dependencia de persistencia.
- No existe esquema `.sq`, ni el módulo generado `PlaybookDatabase`.
- No existen drivers por plataforma ni wiring de base de datos en la app.
- `:core` no tiene `androidUnitTest` con dependencias; no hay test iOS en el gate.
- `feature_list.json` usa terminología obsoleta ("categoría/tipo") para
  `note-model-crud-core`; el esquema debe usar `track` según `CONTEXT.md`/`domain-model.md`.

### Environment (verificado en el bootstrap; re-verificar al implementar)

- macOS con Xcode 27.0; JDK 21; Android SDK vía `local.properties`. iOS solo
  compila/enlaza en macOS (los checks iOS de `init.sh` ya están guardados).

## Technical Approach

### SQLDelight y generación

- Aplicar el plugin `app.cash.sqldelight` en `:core` y configurar una base
  `PlaybookDatabase` con `packageName = "com.playbook.core.db"`.
- Versión propuesta: **SQLDelight 2.1.0** (contemporánea de Kotlin 2.1.21; su
  `gradle-plugin` declara KGP 2.0.20, que Gradle resuelve por encima al KGP
  2.1.21 del proyecto). No usar 2.2.1+ porque arrastra KGP 2.2.21/2.3.10 y
  pisaría la Kotlin 2.1.21 fijada (riesgo alto para Compose MP 1.8.2).

### Esquema inicial (`Note.sq`, v1)

```sql
CREATE TABLE note (
  id TEXT NOT NULL PRIMARY KEY,
  owner TEXT NOT NULL,
  body TEXT NOT NULL,
  track TEXT,
  status TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
```

- `track` nullable para respetar el edge case "Nota sin `track` no rompe la vista"
  (`docs/domain-model.md`); la generación de `id` y los defaults de `status` son
  responsabilidad de `note-model-crud-core`.
- Consultas mínimas para la verificación (plumbing de esquema, no CRUD):
  `insertNote`, `selectAllNotes`, `selectNoteById`.
- Sin `embedding`, `tag`, `attachment` ni `link` (features vecinas las agregan).

### Drivers y wiring

- `commonMain`: interfaz `DatabaseDriverFactory { fun createDriver(): SqlDriver }`
  y helper `createDatabase(factory): PlaybookDatabase`.
- `androidMain`: `AndroidDatabaseDriverFactory(context)` →
  `AndroidSqliteDriver(PlaybookDatabase.Schema, context, "playbook.db")`.
- `iosMain`: `IosDatabaseDriverFactory()` →
  `NativeSqliteDriver(PlaybookDatabase.Schema, "playbook.db")`.
- `:composeApp` (wiring mínimo, sin producto): `MainActivity` y
  `MainViewController` instancian `createDatabase(...)` al arrancar para aplicar
  el esquema. Sin consultas, sin UI nueva.

**Hallazgo de implementación (2026-10-05):** `NativeSqliteDriver` abre la conexión
de forma perezosa; instanciar el driver sólo crea el directorio
`Library/Application Support/databases`, no el archivo ni el esquema. Por eso
`createDatabase(...)` ejecuta un `PRAGMA user_version` inocuo para forzar la
apertura y que el esquema v1 quede aplicado al arrancar (en Android es
idempotente). Además, el host Xcode necesita `OTHER_LDFLAGS = -lsqlite3` para
enlazar los símbolos `sqlite3_*` que aporta el driver nativo.

**Corrección post-validación (2026-10-05):** `PRAGMA user_version` retorna filas,
por lo que el statement de apertura debe ejecutarse con `driver.executeQuery(...)`
(no `driver.execute(...)`, que sólo admite statements sin resultado). La primera
implementación usaba `execute(...)` y hacía crashear el arranque en Android e iOS
con `SQLiteException: Queries can be performed using ... query or rawQuery methods
only`; el validador lo detectó (`revise`). Se agregó el test de regresión
`createDatabaseOpensConnection`, que ejercita `createDatabase(...)` con un driver
in-memory en Android/JVM y en iOS y reproduce el fallo si se revierte el fix.

### Tests

- Lógica compartida en `commonTest`: helper que recibe un `SqlDriver`, inserta y
  lee una nota, y asserta el contenido. Evita `expect/actual` para no romper
  `androidInstrumentedTest`.
- `androidUnitTest`: `@Test` con `JdbcSqliteDriver.IN_MEMORY` +
  `PlaybookDatabase.Schema.create(driver)`.
- `iosTest`: `@Test` con `app.cash.sqldelight.driver.native.inMemoryDriver(PlaybookDatabase.Schema)`.
- Regresión del path cableado (post-validación): `verifyCreateDatabaseOpensConnection`
  en `commonTest` y un `@Test createDatabaseOpensConnection` por plataforma que
  llaman a `createDatabase(...)` y verifican que no lanza y que la DB responde.

### `init.sh`

- Extender el gate para correr también `:core:iosSimulatorArm64Test` en macOS
  (junto al link del framework), validando el driver nativo en el gate.
- Mantenerlo no bloqueante, sin dev servers ni simuladores, e imprimir los
  comandos manuales igual que hoy.

### Decisiones abiertas y riesgos

- **Riesgo alto — SQLDelight 2.1.0 con AGP 8.10.1:** el changelog 2.4.0 corrige
  "generated sources not being picked up by Kotlin compilation on AGP 8.9
  through 8.11". Verificar primero con codegen + `:core:testDebugUnitTest`; si
  los fuentes generados no se compilan, mitigar de forma acotada (declarar
  explícitamente el `srcDir` generado en el source set) o escalar a un spike de
  versiones con ADR. No cambiar Kotlin/Compose sin registrar decisión.
  **Resuelto 2026-10-05:** el codegen y `:core:testDebugUnitTest` compilaron sin
  mitigaciones; no hizo falta tocar versiones.
- **Riesgo resuelto — iOS no creaba el archivo/esquema:** `NativeSqliteDriver` es
  perezoso y el host Xcode no enlazaba SQLite. Mitigado con el `PRAGMA` de
  apertura en `createDatabase(...)` y `-lsqlite3` en el proyecto Xcode (ver
  Technical Approach y `docs/technical-discovery.md`).
- **Nombre de archivo/lógica de negocio:** `"playbook.db"` propuesto; sin
  cifrado ni backup especial en el MVP (Android `useNoBackupDirectory` no se usa).
- **Terminología:** el esquema usa `track` (no `categoría`/`tipo`); actualizar la
  entrada de `note-model-crud-core` en `feature_list.json` cuando se re-derive.
- **Wiring en `:composeApp`:** se mantiene al mínimo (instanciar la DB); si el
  validador lo considera código muerto, justificar que es el punto de aplicación
  del esquema consumido por la siguiente feature.

## Expected File Changes

- `gradle/libs.versions.toml` — modificar; versión y artefactos SQLDelight.
- `build.gradle.kts` (raíz) — modificar; `alias(libs.plugins.sqldelight) apply false`.
- `core/build.gradle.kts` — modificar; plugin SQLDelight, config de `PlaybookDatabase`,
  deps `runtime`/`android-driver`/`native-driver` y `sqlite-driver` (androidUnitTest).
- `core/src/commonMain/sqldelight/com/playbook/core/db/Note.sq` — crear; esquema v1 + queries mínimas.
- `core/src/commonMain/kotlin/com/playbook/core/db/DatabaseDriverFactory.kt` — crear; interfaz + helper.
- `core/src/androidMain/kotlin/com/playbook/core/db/AndroidDatabaseDriverFactory.kt` — crear.
- `core/src/iosMain/kotlin/com/playbook/core/db/IosDatabaseDriverFactory.kt` — crear.
- `core/src/commonTest/kotlin/com/playbook/core/db/NotePersistenceCheck.kt` — crear; helper compartido.
- `core/src/androidUnitTest/kotlin/com/playbook/core/db/NotePersistenceAndroidTest.kt` — crear; test JVM in-memory.
- `core/src/iosTest/kotlin/com/playbook/core/db/NotePersistenceIosTest.kt` — crear; test iOS in-memory.
- `composeApp/src/androidMain/kotlin/com/playbook/app/MainActivity.kt` — modificar; instanciar la DB.
- `composeApp/src/iosMain/kotlin/com/playbook/app/MainViewController.kt` — modificar; instanciar la DB.
- `iosApp/iosApp.xcodeproj/project.pbxproj` — modificar; `OTHER_LDFLAGS = -lsqlite3` para que el host iOS enlace el SQLite del sistema que usa `NativeSqliteDriver`.
- `init.sh` — modificar; agregar `:core:iosSimulatorArm64Test` en macOS.
- `ARCHITECTURE.md` — modificar; documentar la capa de persistencia.
- `docs/technical-discovery.md` — modificar; registrar SQLDelight 2.1.0 y el esquema v1.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar la implementación; estados y evidencia.

## Visual Design Impact

- UI involved: no (no cambia ninguna pantalla ni estado visual).
- Design source: not applicable.
- Screens or states affected: ninguna; la DB se instancia en los puntos de arranque.
- New design artifact required: no. `DESIGN.md` sigue diferido.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — se agrega la capa de persistencia (SQLDelight,
  esquema y drivers en `:core`; wiring de plataforma en `:composeApp`).
- `CONSTRAINTS.md`: not needed — la regla "persistencia/dominio en `:core`" ya
  está cubierta por la dirección de dependencias en `ARCHITECTURE.md` y `AGENTS.md`.
- `AGENTS.md`: not needed — no cambia el flujo ni la ruta de arranque
  (`./init.sh` sigue siendo el gate; el texto "tests del core" sigue vigente).
- Other docs: `docs/technical-discovery.md` — update (versión SQLDelight y
  esquema inicial); `PROGRESS.md` y `feature_list.json` — update al cerrar.

## Implementation Plan

1. Agregar SQLDelight 2.1.0 al version catalog, raíz y `:core`; validar codegen
   y compatibilidad con AGP 8.10.1 en un build temprano.
2. Crear `Note.sq` (esquema v1 + queries mínimas) y confirmar que se genera
   `PlaybookDatabase` en `com.playbook.core.db`.
3. Implementar `DatabaseDriverFactory` y las implementaciones Android/iOS.
4. Escribir el helper compartido de test y los tests por plataforma.
5. Cablear la instanciación de la DB en los puntos de arranque de `:composeApp`.
6. Extender `init.sh` con el test iOS del core; actualizar docs durables.
7. Correr verificación completa, lanzar la app en Android/iOS y registrar evidencia.

## Implementation Tasks

- [x] Agregar SQLDelight 2.1.0 a `libs.versions.toml`, raíz y `core/build.gradle.kts`.
- [x] Crear `core/src/commonMain/sqldelight/com/playbook/core/db/Note.sq` con el esquema y las queries.
- [x] Configurar `sqldelight { databases { create("PlaybookDatabase") { packageName.set(...) } } }`.
- [x] Implementar `DatabaseDriverFactory` + `createDatabase(...)` en `commonMain` (forzando la apertura de la conexión).
- [x] Implementar `AndroidDatabaseDriverFactory` (androidMain) e `IosDatabaseDriverFactory` (iosMain).
- [x] Agregar `sqlite-driver` a `androidUnitTest` para el test JVM.
- [x] Escribir el helper `NotePersistenceCheck` en `commonTest`.
- [x] Escribir `NotePersistenceAndroidTest` (`JdbcSqliteDriver.IN_MEMORY`).
- [x] Escribir `NotePersistenceIosTest` (`inMemoryDriver`).
- [x] Instanciar la DB al arrancar en `MainActivity` y `MainViewController`.
- [x] Enlazar `libsqlite3` en el host Xcode (`OTHER_LDFLAGS = -lsqlite3`).
- [x] Extender `init.sh` con `:core:iosSimulatorArm64Test` (guardado por macOS).
- [x] Actualizar `ARCHITECTURE.md` y `docs/technical-discovery.md`.
- [x] Reparación post-validación: statement de apertura con `executeQuery(...)` y test de regresión `createDatabaseOpensConnection`.

## Verification Plan

- `./gradlew :core:testDebugUnitTest` → test de persistencia común en verde (Android/JVM).
- `./gradlew :core:iosSimulatorArm64Test` → el mismo test en verde en iOS (macOS).
- `./gradlew :composeApp:assembleDebug` → BUILD SUCCESSFUL; driver Android compila.
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL; driver iOS compila.
- Arranque manual: abrir la app en emulador Android y simulador iOS; verificar
  en logs/inspección que la DB se crea sin excepciones (sin UI nueva).
- `./init.sh` → exit 0; corre los checks no bloqueantes (incluido el test iOS en
  macOS), **no** inicia dev servers ni simuladores, e imprime comandos manuales.
- E2E persistente: no aplica; no hay flujo de usuario/API observable todavía
  (la UI de notas llega en features posteriores). La cobertura es de test del core
  en dos plataformas + compilación de la app.
- `./init.sh`: debe ejecutar el gate estándar no bloqueante para el estado
  actual, no debe levantar procesos de larga duración, y puede imprimir
  comandos manuales tras pasar los checks.

## Evidence To Capture

- Salida de `:core:testDebugUnitTest` y `:core:iosSimulatorArm64Test` (tests en verde, con nombre del test).
- Salida de `:composeApp:assembleDebug` y `:composeApp:linkDebugFrameworkIosSimulatorArm64` (BUILD SUCCESSFUL).
- Salida de `./init.sh` (exit 0) y confirmación de que no inicia procesos de larga duración.
- Registro de la versión SQLDelight usada y de cualquier mitigación por el riesgo AGP.
- Confirmación de arranque de la app en Android/iOS aplicando el esquema (log o descripción), sin UI nueva.

## Validator Checklist

- [ ] La implementación se mantiene en el alcance: SQLDelight, esquema `note` v1, drivers y tests; sin modelo/CRUD/UI.
- [ ] Los escenarios de aceptación 1–4 pasan.
- [ ] La evidencia de verificación está en `feature_list.json` / `PROGRESS.md`.
- [ ] No se agregaron tablas ni lógica de features vecinas (tags/attachments/links/embeddings/CRUD).
- [ ] No hay cobertura E2E persistente y la spec justifica por qué.
- [ ] `feature_list.json` y `PROGRESS.md` se actualizaron correctamente.
- [ ] `init.sh` sigue siendo no bloqueante y sin procesos de larga duración.
