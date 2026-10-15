# Validación independiente — offline-pending-retry

- Feature: `offline-pending-retry`
- Spec: `docs/specs/offline-pending-retry.md`
- Fecha: 2026-10-15
- Rol: `validator` (`$feature-validator`), independiente del implementer
- Veredicto: **accept**

## Alcance validado
- `:core`: `PendingIndexingRetry.retryPending()` (reintenta las Notas `PENDING`, resetea `index_attempts` a `0` al indexar, incrementa en fallo y promueve a `FAILED` al agotar `budget` default `3`) + puerto `AiAvailability` (`AlwaysAvailableAiAvailability` default; `false` = no-op).
- Esquema **v6** con `5.sqm` (`note.index_attempts INTEGER NOT NULL DEFAULT 0`) + queries `selectNotesByStatus`/`updateNoteIndexAttempts`; `Note.indexAttempts`/mapper; `NoteRepository.getByStatus`/`setIndexAttempts` (sin re-sellar `updated_at`).
- Wiring mínimo: los composition roots construyen `PendingIndexingRetry`; `App` corre el reintento una vez al arrancar (`LaunchedEffect(Unit)`) + `reload()`. Sin UI nueva, sin scheduling, sin conectividad real (ADR 0001), sin deps nuevas.

## Verificación re-ejecutada por el validador
- `CI=true ./init.sh` → exit 0 (sin dev servers/simuladores/red; `init.sh` sin cambios).
- `CI=true ./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; **45/45** Android/JVM, 0 failures (incl. `PendingIndexingRetryAndroidTest` 1/1, `NoteRepositoryAndroidTest` 4/4, `MigrationAndroidTest` 2/2, `DatabaseMigrationAndroidTest` 1/1, `NoteIndexingOrchestratorAndroidTest` 1/1).
- `CI=true ./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; **45/45** iOS, 0 failures (equivalentes `IosTest`).
- `CI=true ./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL (57 tasks).
- `PlaybookDatabaseImpl.Schema.version == 6`; `find core/src -name '*.sqm'` = `1.sqm`..`5.sqm` (sin extra); `5.sqm` = `ALTER TABLE note ADD COLUMN index_attempts INTEGER NOT NULL DEFAULT 0;`.
- **Control negativo propio** (distinto de los del implementer): romper el reseteo del presupuesto en el camino de éxito (`setIndexAttempts(note.id, 0)` → `1`) hace fallar `PendingIndexingRetryAndroidTest.retriesPendingAndPromotesToFailed` (`AssertionError: el éxito resetea el presupuesto a 0 expected:<0> but was:<1>`, Scenario 1); restaurado byte-idéntico (SHA-256 `de0e59ce…`, coincide con el hash del implementer) y re-verificado en verde (45/45).
- Invariantes confirmados: `FAILED` sólo lo escribe `PendingIndexingRetry` y sólo tras agotar `budget`; `CAPTURED`/`INDEXED` intactas; `AiAvailability=false` no-op (`embedCalls == 0`, sin cambio de estado ni presupuesto); `budget > 0` (`require` + `assertFailsWith`); migración v5→v6 preserva Notas con `index_attempts` default `0`.

## Hallazgos
- Low (no bloqueante) — `core/src/commonTest/.../repository/NoteTagCheck.kt` y `docs/risks-and-open-questions.md` cambiaron fuera de la lista de *Expected File Changes* de la spec; se juzgan **arrastres necesarios del bump a v6**, no scope creep: `verifyNoteTagMigration` migra a v5 y necesita la columna `index_attempts`; el cambio en el doc es un fix de 1 línea de un símbolo renombrado (`verifyMigrationV1ToV5` → `V1ToV6`).
- Sin hallazgos Critical/High/Medium ni de seguridad: SQL parametrizado (sin SQL armado por strings), sin secrets/keys/keystores, sin deps nuevas, sin red en el gate, sin exposición de datos (no se loguea `body`). `Gdd*` (gdd-export) sin tocar.

## Limitaciones honestas
- Sin conectividad real ni scheduling (trigger = arranque de la app); runtime `FakeAiClient`; sin E2E persistente (la spec lo justifica). La sonda real de conectividad es follow-up del ADR 0001.

## Resultado
Promovida a `accepted` por el orquestador (commit `feature: complete offline-pending-retry`).
