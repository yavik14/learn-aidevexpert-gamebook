# Validación independiente: `ai-enrichment-classify-tags-type`

- **Feature id:** `ai-enrichment-classify-tags-type`
- **Spec:** `docs/specs/ai-enrichment-classify-tags-type.md`
- **Fecha:** 2026-10-13
- **Rol:** validator (independiente del implementer)
- **Estado en `feature_list.json` al validar:** `passing`
- **Veredicto:** **accept**

## Alcance validado

Orquestación del **indexado asíncrono** sobre lo ya construido
(`NoteIndexingService`/`EmbeddingRepository`) + la **transición de `NoteStatus`**:
`NoteIndexingOrchestrator.index(note)`, `NoteRepository.updateStatus` y la query
`updateNoteStatus`. Sin clasificación IA, `categoría`/`tipo`, etiquetas IA,
Enlaces, reintentos/scheduling, UI/wiring ni runtime real; sin cambio de esquema
(sigue v4, sin `.sqm` nuevo); `FAILED` nunca se escribe (es de
`offline-pending-retry`).

## Checks re-ejecutados por el validador (no se confió en el resumen)

| Check | Comando | Resultado |
| --- | --- | --- |
| Gate estándar | `./init.sh` | exit 0; sin dev servers ni simuladores; `init.sh` sin cambios |
| Tests core Android/JVM | `./gradlew :core:testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL (16 tasks); **17 tests, 0 failures** |
| Tests core iOS | `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` | BUILD SUCCESSFUL; **17 tests, 0 failures** |
| Build Android + link iOS | `./gradlew :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` | BUILD SUCCESSFUL (57 tasks) |
| Esquema | `find core/src -name '*.sqm'` + `PlaybookDatabaseImpl.Schema.version` | sólo `1.sqm`/`2.sqm`/`3.sqm`; **`version == 4`** |
| Alcance | `git diff --stat` de paths protegidos | **vacío** |
| Secretos/contenido prohibido | grep sobre diff + archivos nuevos | sin secrets; sin clasificación IA ni `enlace`/link en código |

Conteos desde `core/build/test-results/*/TEST-*.xml` (17 por plataforma,
0 failures/errors/skipped):

- Android/JVM: `GreetingTest` 1, `AiClient` 3, `NoteIndexing` 1,
  **`NoteIndexingOrchestrator` 1 (nuevo)**, `AiRuntimeSpike` 2,
  `DatabaseMigration` 1, `Migration` 1, `NotePersistence` 2,
  `AttachmentRepository` 1, `EmbeddingRepository` 1, **`NoteRepository` 3**
  (incluye el Scenario 8 de `updateStatus` en `crudRoundTrip`).
- iOS: mismos conteos con las variantes `*IosTest`
  (**`NoteIndexingOrchestratorIosTest` 1/1 nuevo**).
- El `.sq` generado expone `updateNoteStatus(status, id): QueryResult<Long>` con
  `UPDATE note SET status = ? WHERE id = ?` (sin columnas de esquema).

## Controles negativos propios (reproducidos y restaurados byte-idénticos)

1. **Orquestador forzado a `INDEXED`** (`else -> NoteStatus.PENDING` →
   `else -> NoteStatus.INDEXED`, ignorando `Failed`):
   `:core:testDebugUnitTest --tests ...NoteIndexingOrchestratorAndroidTest
   --rerun-tasks` → **FAILED**:
   `java.lang.AssertionError: un fallo de IA debe devolver PENDING
   expected:<PENDING> but was:<INDEXED>` (1 test, 1 failed). Restaurado
   (`sha256 8dcfd14d…`, `sha1 789e78bd…` = hash del implementer) y re-ejecutado
   en verde.
2. **`updateStatus` re-sella `updated_at`** (`UPDATE note SET status = ?` →
   `UPDATE note SET status = ?, updated_at = updated_at + 1`):
   `:core:testDebugUnitTest --tests ...NoteRepositoryAndroidTest --rerun-tasks` →
   **FAILED**: `java.lang.AssertionError: updateStatus no re-sella updatedAt
   expected:<1700000001000> but was:<1700000001001>` (3 tests, 1 failed).
   Restaurado (`sha256 91662733…`, `sha1 18a0f10c…` = hash del implementer) y
   re-ejecutado en verde.

Tras restaurar ambos, la suite completa vuelve a **17/0 Android/JVM y 17/0 iOS**.
Los hashes de restauración citados en la evidencia del implementer son SHA-1 y
coinciden exactamente con los archivos actuales.

## Escenarios de aceptación 1–9

1. `CAPTURED` + éxito → `INDEXED` (embedding persistido) — cubierto.
2. Fallo de IA no bloquea: no lanza, devuelve `PENDING`, sin fila `embedding` —
   cubierto.
3. Respuesta inválida (0 vectores, >1 vector, dimensión 0) → `PENDING`, sin fila —
   cubierto.
4. `PENDING` + éxito → `INDEXED` (reintento con embedding) — cubierto.
5. `PENDING` + fallo → sigue `PENDING`, nunca `FAILED` — cubierto.
6. Re-indexado exitoso de `INDEXED` → sigue `INDEXED`, una sola fila con `values`
   nuevos — cubierto.
7. Re-indexado fallido de `INDEXED` → no se degrada; embedding previo intacto y no
   se crea/pisa fila — cubierto.
8. `updateStatus` cambia sólo `status` (preserva body/track/tags/owner/createdAt/
   updatedAt) y `false` con id inexistente — cubierto (`crudRoundTrip`).
9. Esquema v4 sin `.sqm` nuevo; ambas plataformas + `assembleDebug`/link iOS +
   `./init.sh` exit 0 — verificado por re-ejecución.

## Scope discipline

- `git diff --stat` **vacío** para `:composeApp`, `iosApp/**`, `init.sh`,
  `gradle/libs.versions.toml`, `AiClient.kt`, `NoteIndexingService.kt`,
  `embedding.sq` y todo `.sqm`.
- `Note.sq` sólo agrega la query `updateNoteStatus` (no toca `CREATE TABLE`).
- Sin clasificación IA/`categoría`/`tipo`/etiquetas IA ni tabla/entidad `enlace`;
  las únicas menciones son documentales ("no clasifica", "no crea Enlaces").
- Sólo una implementación de `NoteRepository` (`SqlDelightNoteRepository`), con el
  nuevo `updateStatus`; sin otras implementaciones rotas.
- `updateStatus`/`updateNoteStatus` no está cableado a UI/entry points (fuera de
  alcance).

## Seguridad (checklist scoped al diff)

- Sin secrets, tokens, credenciales, URLs ni `.env` en el diff/archivos nuevos.
- Todo local-first: sin red, sin llamadas externas, sin dependencias nuevas
  (`libs.versions.toml` sin cambios); sin artefactos generados versionados.
- Sin superficie de entrada/auth nueva ni datos sensibles nuevos. **No se
  encontraron hallazgos de seguridad.**

## Documentación durable

- `ARCHITECTURE.md`: sección AI (`NoteIndexingOrchestrator` + regla de transición
  sin `FAILED`), `NoteRepository.updateStatus` y la lista de queries — consistente
  con el código.
- `CONSTRAINTS.md`: MUST NOT durable ("el indexado no clasifica ni etiqueta y no
  bloquea la captura"; `pendiente → fallida`/reintentos son de
  `offline-pending-retry`).
- `docs/technical-discovery.md`: nueva "Indexing Decisions
  (ai-enrichment-classify-tags-type)".
- `AGENTS.md`: sólo cambió la línea "Próxima feature en cola".
- `feature_list.json`: se actualizó **sólo** la entrada de esta feature
  (título/`user_visible_behavior`/verificación/evidencia/notas). Las entradas
  vecinas con terminología obsoleta (`note-enrichment-status-ui`, `gdd-view`,
  `offline-pending-retry`, `enrichment-observability-logging`) quedan **sin
  tocar** (WIP=1) y **registradas** para re-derivarse en la spec y en
  `PROGRESS.md` (Session 028) — según exige la spec.
- `PROGRESS.md`: estado actual + Session 028 honestos y consistentes.

## Hallazgos

### Finding: cita imprecisa de escenarios en el control negativo del orquestador

- **Severity:** Low (no bloqueante)
- **Evidence:** La evidencia en `feature_list.json`/`PROGRESS.md` dice que forzar
  `INDEXED` "hace fallar … (escenarios 2/3/7)". Al forzar `INDEXED`, las
  aserciones que exigen `PENDING` (escenarios 2, 3 y 5) fallan; el Scenario 7
  espera `INDEXED` y seguiría pasando (el test aborta antes, en el Scenario 2).
- **Why it matters:** Precisión de la evidencia durable; no afecta la corrección
  ni la sensibilidad del test (que sí se reprodujo fallando).
- **Required change:** ninguna para aceptar; al re-derivar, corregir la cita a
  "escenarios 2/3/5 (fallo → `PENDING`)".
- **Verification after fix:** el control negativo sigue fallando y la suite vuelve
  a 17/17.

### Finding (nota): el orquestador confía en el `status` del argumento `Note`

- **Severity:** Low (nota, no bloqueante)
- **Evidence:** `NoteIndexingOrchestrator.index` decide con `note.status` (el
  pasado por el llamador), no con la fila persistida.
- **Why it matters:** Un llamador que pase una `Note` desactualizada (`CAPTURED`)
  para una fila ya `INDEXED` podría, ante un fallo, escribir `PENDING` y degradar
  la fila. Coincide con la implementación de referencia de la spec y con el
  contrato del llamador; hoy no hay llamador cableado ni superficie observable.
- **Required change:** ninguna en esta feature; documentar el contrato del
  llamador ("pasar la `Note` vigente") cuando se cablee en
  `offline-pending-retry`/`note-enrichment-status-ui`.
- **Verification after fix:** n/a (documentación).

No hay hallazgos Critical/High/Medium. No hay E2E persistente y la spec lo
justifica (sin UI ni wiring, sin flujo observable, sin harness E2E; `init.sh` no
levanta simuladores); la cobertura es de tests del core en Android/JVM e iOS más
la compilación de la app. `init.sh` sigue siendo un gate real no bloqueante y sin
procesos de larga duración.

## Veredicto final

**accept.** La implementación se ciñe a la spec re-derivada, los escenarios 1–9
pasan, la verificación se re-ejecutó de verdad (17/17 en ambas plataformas,
`./init.sh` exit 0, esquema v4 sin `.sqm` nuevo), los controles negativos prueban
que los tests son sensibles a fallos reales y se restauraron byte-idénticos, el
alcance y la seguridad se respetan y la documentación durable está actualizada.
`feature_list.json` permanece en `passing` (el orquestador debe promoverlo a
`accepted`).
