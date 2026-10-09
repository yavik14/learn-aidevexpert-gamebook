# Validación independiente: `embeddings-generation`

- **Feature id:** `embeddings-generation`
- **Spec:** `docs/specs/embeddings-generation.md`
- **Fecha:** 2026-10-09
- **Rol:** validator (independiente del implementer)
- **Estado en `feature_list.json` al validar:** `passing`
- **Veredicto:** **accept**

## Alcance validado

Generación + persistencia de embeddings como BLOB (esquema v2 con `1.sqm`),
`EmbeddingRepository`/`SqlDelightEmbeddingRepository`, `EmbeddingBlobCodec`,
`NoteIndexingService` como primer consumidor de `AiClient` y
`PRAGMA foreign_keys = ON` en `createDatabase`. Sin UI/wiring, sin
`NoteStatus`, sin enlaces/RAG, sin runtime real.

## Checks re-ejecutados por el validador (no se confió en el resumen)

| Check | Comando | Resultado |
| --- | --- | --- |
| Gate estándar | `./init.sh` | exit 0; sin dev servers ni simuladores |
| Tests core Android/JVM | `./gradlew :core:testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL; **10 tests, 0 failures** |
| Tests core iOS | `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` | BUILD SUCCESSFUL; **10 tests, 0 failures** |
| Build Android | `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` | BUILD SUCCESSFUL (57 tasks) |

Conteos desde `core/build/test-results/*/TEST-*.xml` (ambas plataformas):
`GreetingTest` 1, `AiClient*Test` 3, `NotePersistence*Test` 2,
`NoteRepository*Test` 1, `EmbeddingRepository*Test` 1, `NoteIndexing*Test` 1,
`DatabaseMigration*Test` 1 → 10 por plataforma, 0 failures/errors.

Esquema: `PlaybookDatabaseImpl.Schema.version.get() == 2` en el código generado
(`core/build/generated/sqldelight/.../core/PlaybookDatabaseImpl.kt`); `1.sqm`
presente; el `CREATE TABLE embedding` de la migración y el de `embedding.sq`
coinciden con el de `Schema.create`.

## Controles negativos propios (reproducidos y restaurados)

1. **Persistir antes de validar la respuesta del runtime** (Scenario 5):
   inyectar `embeddingRepository.upsert(...)` antes del chequeo de tamaño →
   `NoteIndexingAndroidTest.generateOverwriteFailureAndInvalidResponse` **falla**
   (`>1 vector no debe pisar el embedding previo`). Restaurado (hash idéntico).
2. **Quitar `PRAGMA foreign_keys = ON`** (Scenario 8): el cascade deja de aplicar →
   `EmbeddingRepositoryAndroidTest.roundTripCodecOverwriteAndCascade` **falla**
   (`borrar la Nota debe cascadear el embedding`). Restaurado (hash idéntico).
3. **Endianness del codec** (Scenario 6): invertir `EmbeddingBlobCodec.encode` a
   little-endian → el round-trip deja de ser bit-exacto →
   `EmbeddingRepositoryAndroidTest` **falla**. Restaurado.

Tras restaurar los tres, la suite completa vuelve a **10/0 Android y 10/0 iOS**.

## Escenarios de aceptación 1–9

1. Persistir/recuperar intacto — cubierto (`verifyEmbeddingRepository`).
2. Generar vía `AiClient` (un único texto = `body`) — cubierto (`verifyNoteIndexing`).
3. Re-generación idempotente (overwrite, `indexedAt` avanza, sin duplicados) — cubierto.
4. Fallo del runtime no corrompe (causa propagada, fila previa intacta, sin crear fila) — cubierto.
5. Respuesta inválida (0 vectores, >1, dimensión 0) → `Failed` sin persistir ni pisar — cubierto.
6. Codec big-endian, `decode` no múltiplo de 4 falla, control negativo de endianness — cubierto.
7. Migración v1→v2 + `Schema.create` equivalente (columnas y FKs) — cubierto.
8. Ciclo de vida por FK (`deleteByNoteId` + cascade) — cubierto.
9. Ambas plataformas + gate honesto — verificado por re-ejecución.

## Scope discipline

- Sin cambios en `:composeApp`, `iosApp/**`, `init.sh`, `Note.sq`,
  `gradle/libs.versions.toml` (`git status` limpio en esos paths).
- `NoteIndexingService` no toca `NoteStatus` (solo lo menciona en comentario); el
  test aserta que la Nota sigue `CAPTURED`.
- Sin UI/wiring, sin enlaces/RAG, sin runtime real (`FakeAiClient`).

## Seguridad (checklist scoped al diff)

- Sin secrets, tokens, credenciales ni URLs privadas en el diff (grep sobre `core/src`).
- Todo es local-first, sin red ni llamadas externas; sin nuevas dependencias
  (`libs.versions.toml` sin cambios); sin binarios/build artifacts versionados.
- Sin superficie de entrada/auth nueva. **No se encontraron hallazgos de seguridad.**

## Documentación durable

`ARCHITECTURE.md`, `docs/technical-discovery.md` y
`docs/risks-and-open-questions.md` actualizados y consistentes con el código;
`AGENTS.md` solo cambió la línea "Próxima feature en cola". `CONSTRAINTS.md` no
era necesario (la regla del puerto `AiClient` ya vive en `ARCHITECTURE.md`).

## Smoke real de migración (declarado honestamente)

- **iOS: corroborado indirectamente.** El simulador iPhone 15 (iOS 17.2,
  `2ABE2E3F-...`) tiene la app instalada y su `playbook.db` en disco muestra
  `PRAGMA user_version = 2`, tablas `note` + `embedding` con `FK note(id) ON
  DELETE CASCADE`, y la fila legacy `legacy-ios | local | Nota legacy iOS |
  historia | capturada` preservada — coincide exactamente con el claim del
  implementer. **No re-ejecuté** la instalación/lanzamiento; inspeccioné el
  estado persistido del simulador.
- **Android: NO reproducido por el validador.** El emulador compartido
  `emulator-5556` hoy tiene `user_version = 2` pero el esquema de **otra**
  feature paralela (`note_tag`, sin tabla `embedding`); `emulator-5554` está en
  v1. El estado del smoke de esta feature fue sobrescrito por sesiones
  paralelas (ya advertido en `PROGRESS.md`). No se fabricó evidencia. La
  corrección de la migración en Android sí queda cubierta por
  `DatabaseMigrationAndroidTest`/`EmbeddingRepositoryAndroidTest` (JVM),
  re-ejecutados en verde.

## Hallazgos

### Finding: smoke de migración Android no reproducible en el entorno compartido

- **Severity:** Low (no bloqueante)
- **Evidence:** `emulator-5556` con `note_tag` y sin `embedding`;
  `emulator-5554` con `user_version=1`. `PROGRESS.md` Session 015 ya documenta
  la contaminación por sesiones paralelas.
- **Why it matters:** reduce la reproducibilidad de la evidencia de smoke en
  dispositivo, no la corrección (cubierta por tests portables en ambas
  plataformas + corroboración iOS del DB real).
- **Required change:** ninguna para aceptar; cuando el entorno esté libre,
  re-correr el smoke Android sobre `Pixel_3A_API_34` para dejar el artefacto.
- **Verification after fix:** `user_version=2` + tabla `embedding` + nota legacy
  preservada en el `playbook.db` del emulador.

No hay hallazgos Critical/High/Medium. No hay E2E persistente y la spec lo
justifica (sin flujo observable; no existe harness E2E). `init.sh` sigue siendo
un gate real no bloqueante.

## Veredicto final

**accept.** La implementación se ciñe a la spec, los escenarios 1–9 pasan, la
verificación se re-ejecutó de verdad, los controles negativos prueban que los
tests son sensibles a fallos reales, el alcance y la seguridad se respetan y la
documentación durable está actualizada. `feature_list.json` permanece en
`passing` (el orquestador debe promoverlo a `accepted`).
