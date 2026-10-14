# Validación independiente — enrichment-observability-logging

- Feature: `enrichment-observability-logging`
- Spec: `docs/specs/enrichment-observability-logging.md`
- Fecha: 2026-10-14
- Rol: `validator` (`$feature-validator`), independiente del implementer
- Veredicto: **accept**

## Alcance validado
- `:core` `com.playbook.core.diagnostics`: puerto `Diagnostics` best-effort con `NoOpDiagnostics` (default) / `InMemoryDiagnostics` / `FileDiagnostics` (+ `DiagnosticStorage` `expect/actual` Android/iOS).
- Instrumentación: `NoteIndexingService` → `AI_INDEXING`, `RagQueryService` → `AI_QUERY`, `App` → `VOICE`/`IMAGE`/`STORAGE`; sink `FileDiagnostics` cableado en `MainActivity`/`MainViewController`.
- Sin UI, sin telemetría remota, sin librería de logging, sin dependencias nuevas, esquema **v5** sin `.sqm`.

## Verificación re-ejecutada por el validador
- `CI=true ./init.sh` → exit 0 (sin dev servers/simuladores; `init.sh` sin cambios).
- `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL; **42/42** Android/JVM y **42/42** iOS, 0 failures (`Diagnostics*Test` 1/1 por plataforma + `DiagnosticsStorage*Test` con los `actual` reales). Tests previos de IA intactos con `NoOpDiagnostics`.
- `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` → BUILD SUCCESSFUL.
- `PlaybookDatabaseImpl.Schema.version == 5`; `find core/src -name '*.sqm'` → `1..4.sqm`; `git diff` vacío en `init.sh`/`libs.versions.toml`/`iosApp/**`/`sqldelight/**`/`AiClient.kt`/`FakeAiClient.kt`.
- **Controles negativos propios (restaurados byte-idénticos):**
  - (a) quitar `recordFailure(AI_INDEXING, …)` del `catch` de `embed` → `DiagnosticsAndroidTest` FAILED (`expected:<[AI_INDEXING]> but was:<[]>`).
  - (b) quitar `enforceRetention()` en `FileDiagnostics` → `DiagnosticsAndroidTest` FAILED (`rawLog() no debe superar maxEntries`).
- Escenarios 1–7 verificados en `:core` (dos plataformas); privacidad (sin `body`/pregunta/rutas/IDs/secrets); smoke Android real con `IMAGE image_failed PERMISSION_DENIED`.

## Hallazgos
- Low (cosmético) — typo de espacios en `ARCHITECTURE.md` (paso (5) de `RagQueryService`): **corregido al persistir la aceptación**.
- Low (observación) — `VOICE`/`STORAGE` instrumentados verificados por inspección (el smoke real cubre `IMAGE`); dentro del plan de la spec.
- Low (forward-looking) — `detail` de fallos de IA reenvía `cause.message` recortado; vigilar que un `AiClient` cloud futuro no ponga `body`/prompt en `AiClientException.message` (ya cubierto por `CONSTRAINTS.md`).
- Sin hallazgos Critical/High/Medium; sin hallazgos de seguridad. Sin secretos, sin red, sin deps nuevas, sink en almacenamiento privado.

## Limitaciones honestas
- Smoke iOS interactivo no ejecutado (sin `idb`/`cliclick`); el `actual` iOS se ejercita en runtime por los tests de storage en `iosSimulatorArm64Test`.

## Resultado
Promovida a `accepted` por el orquestador (commit `feature: complete enrichment-observability-logging` + PR draft).
