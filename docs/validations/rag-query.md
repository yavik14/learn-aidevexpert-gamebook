# Validación independiente: `rag-query`

- **Feature id:** `rag-query`
- **Spec:** `docs/specs/rag-query.md`
- **Fecha:** 2026-10-13
- **Rol:** validator (independiente del implementer)
- **Worktree:** `worktree/gamebook/rag-query` (rama `feature/rag-query`)
- **Diferencias/a confirmar:** el prompt del orquestador declaraba el trabajo como
  **sin commitear** con `feature_list.json` en `passing`. En el estado real del
  worktree el trabajo aparece **commiteado** en `3b7b868 feature: complete rag-query`
  y `feature_list.json` ya figura como `accepted` (con una Session 029 en
  `PROGRESS.md` y `AGENTS.md` ya promocionado). Ver Findings.
- **Estado en `feature_list.json` al validar:** `accepted` (el orquestador declaró
  `passing`; se valida igualmente por pedido explícito del owner).
- **Veredicto:** **accept**

## Alcance validado

Consulta en lenguaje natural sobre las Notas: `cosineSimilarity(a, b)` y
`RagQueryService.ask(question)` en `:core` (retrieval top-K por similitud coseno
en memoria + `generate(GenerateRequest(prompt, context = cuerpos))`),
`AskNotesScreen`/`AskUiState` + acción "Preguntar" y el **primer wiring del runtime
de IA a la app** (`FakeAiClient` + `NoteIndexingService` index-on-save best-effort
+ `RagQueryService`) en los composition roots Android/iOS. Sin citas/navegación, sin
enlaces persistidos, sin estados/reintento de `NoteStatus`, sin adaptador real.

## Checks re-ejecutados por el validador (no se confió en el resumen)

| Check | Comando | Resultado |
| --- | --- | --- |
| Gate estándar | `./init.sh` | exit 0; sin dev servers ni simuladores; `init.sh` sin cambios |
| Tests core Android/JVM | `./gradlew :core:testDebugUnitTest --rerun-tasks --console=plain` | BUILD SUCCESSFUL; **22 tests, 0 failures/errors** |
| Tests core iOS | `./gradlew :core:iosSimulatorArm64Test --rerun-tasks --console=plain` | BUILD SUCCESSFUL; **22 tests, 0 failures/errors** |
| Build Android | `./gradlew :composeApp:assembleDebug --rerun-tasks --console=plain` (junto al link) | BUILD SUCCESSFUL (57 tasks executed) |
| Link framework iOS | `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks --console=plain` | BUILD SUCCESSFUL |
| Host iOS | `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination 'platform=iOS Simulator,id=2ABE2E3F-...' -derivedDataPath composeApp/build/xcode-validator build` | `** BUILD SUCCEEDED **` |

Conteos desde `core/build/test-results/*/TEST-*.xml` (idénticos en ambas plataformas):
`GreetingTest` 1, `AiClient*Test` 3, `NoteIndexing*Test` 1, `RagQuery*Test` 6,
`AiRuntimeSpike*Test` 2, `DatabaseMigration*Test` 1, `Migration*Test` 1,
`NotePersistence*Test` 2, `AttachmentRepository*Test` 1, `EmbeddingRepository*Test` 1,
`NoteRepository*Test` 3 → **22 por plataforma, 0 failures/errors/skipped**.

Esquema: `PlaybookDatabase.Schema.version.get() == 4` en el código generado
(`core/build/generated/sqldelight/.../core/PlaybookDatabaseImpl.kt:37`).

## Scope discipline

- `git diff HEAD~1 HEAD -- init.sh 'core/src/commonMain/sqldelight/**' gradle/libs.versions.toml composeApp/build.gradle.kts` → **vacío** (sin cambios, sin dependencias nuevas).
- `find core/src -name '*.sqm'` → sólo `1.sqm`, `2.sqm`, `3.sqm` (sin `.sqm` nuevos); diff de `*.sq`/`*.sqm` vacío (`Note.sq`/`embedding.sq` intactos).
- `git status --porcelain` limpio; sin keystores/secrets; artefactos de smoke en `composeApp/build/` (ignorado por `.gitignore`).
- Sin citas/navegación ni render de `sources` en la UI; sin enlaces persistidos; `indexBestEffort` no toca `NoteStatus`.

## Controles negativos propios (reproducidos y restaurados byte-idénticos)

sha256 antes/después de cada archivo intervenido (`git checkout --` para restaurar):

1. **Invertir el signo del `dot product`** en `cosineSimilarity`
   (`dot +=` → `dot -=`, `CosineSimilarity.kt:26`):
   `./gradlew :core:testDebugUnitTest --rerun-tasks --tests com.playbook.core.ai.RagQueryAndroidTest`
   → **6 tests, 2 failed** (`cosineSimilarityContract`, `rankingIsDeterministicAndTopK`).
   Restaurado: `CosineSimilarity.kt` sha256 `736b7778…` (idéntico).
2. **Forzar `generate` en la rama sin embeddings** (`RagQueryService.kt`, el
   `if (embeddings.isEmpty()) return NoIndexedNotes` pasa a generar y devolver
   `Answer`): el mismo comando → **6 tests, 1 failed** (`noIndexedNotesSkipsRuntime`).
   Restaurado: `RagQueryService.kt` sha256 `706d6ace…` (idéntico).
3. Tras restaurar, la suite completa vuelve a **22/0 Android y 22/0 iOS**
   (`:core:testDebugUnitTest :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL).

## Escenarios de aceptación 1–9

1. Happy path (retrieval + generación anclada) — cubierto (`verifyRagAnswer`): `embed`
   1× con `question.trim()`, `generate` 1× con `prompt = question.trim()` y `context`
   = cuerpos de las `sources` en orden, `sources` ordenadas por score desc (top-K),
   `text` = salida del runtime, ranking esperado recomputado.
2. Sin Notas indexadas no se genera — cubierto (`verifyRagNoIndexedNotes`):
   `NoIndexedNotes` con `embed`/`generate` sin invocar.
3. Fallo del runtime → `Failed(cause)` — cubierto (`verifyRagRuntimeFailure`): propaga
   la causa exacta (`assertSame`) de `embed` y de `generate`, sin excepción sin capturar.
4. Respuesta inválida de `embed` — cubierto (`verifyRagInvalidEmbedding`): 0 vectores,
   >1 vector y dimensión 0 → `Failed`, `generate` no se llama.
5. Contrato de similitud coseno — cubierto (`verifyCosineSimilarity`) y **control
   negativo propio reproducido**.
6. Ranking determinista/top-K/dimensiones incompatibles — cubierto (`verifyRagRanking`):
   top-K con desempate por `note.id`, dimensión incompatible descartada,
   `maxSources = 0` y todos descartados → `NoIndexedNotes`.
7. Indexado best-effort al guardar — verificado por **revisión de código** (`App.indexBestEffort`:
   `body` en blanco no indexa, `scope.launch` silencioso, sin tocar `NoteStatus`) y por
   evidencia de smoke (`composeApp/build/smoke-evidence/`); **no** cubierto por test
   automatizado (ver Findings).
8. UI de "Preguntar" — verificado por **revisión de código** (`AskNotesScreen`:
   acción deshabilitada con pregunta en blanco/`Asking`, "Pensando…", `Answered` sólo
   texto, mensaje explícito de sin notas, error con "Reintentar" y "Volver"; estados con
   texto). Smoke interactivo no re-ejecutado por el validador (ver Findings).
9. Ambas plataformas + gate honesto — verificado por re-ejecución (22/22 en Android/JVM
   e iOS, builds + link + `xcodebuild` verdes, `./init.sh` exit 0, esquema v4, sin `.sqm`).

## Seguridad (checklist scoped al diff)

- Sin secrets/tokens/keys/URLs privadas en el diff: grep de patrones sobre los archivos
  cambiados sólo encuentra menciones documentales de "secretos"/"API keys".
- Todo local-first; `FakeAiClient` no usa red ni credenciales; sin dependencias nuevas.
- Sin superficie de entrada/auth nueva; la pregunta del usuario pasa por `require(isNotBlank)`
  y el retrieval es en memoria (sin SQL dinámico ni rutas de archivo nuevas).
- Sin artefactos de build/binarios versionados (smoke en `composeApp/build/`, ignorado).
- **No se encontraron hallazgos de seguridad.**

## Documentación durable

`ARCHITECTURE.md` (nueva sección "RAG", wiring, surfaces; "Deferred" corregido),
`docs/technical-discovery.md` (nueva "RAG Decisions") y
`docs/risks-and-open-questions.md` (respuesta desde las Notas resuelta; runtime real y
calidad abiertos) actualizados y consistentes con el código. `AGENTS.md` cambió el bloque
de estado "Próxima feature en cola". `CONSTRAINTS.md` no era necesario. `init.sh` sin
cambios y sigue siendo un gate real no bloqueante (sin dev servers/simuladores).

## Limitaciones declaradas honestamente (y las propias)

- **Respuesta de `FakeAiClient`:** la "respuesta generada" es `[fake] <pregunta>
  (context=N)`; el adaptador cloud/on-device real y la calidad siguen siendo follow-up
  del ADR 0001. La verificación prueba el **pipeline**, no la calidad. Declarado en spec
  y docs.
- **Smoke interactivo Android/iOS:** el implementer declaró el smoke Android bloqueado
  por emuladores compartidos. El validador **no re-ejecutó** el smoke interactivo (ni
  Android ni iOS); se apoyó en pruebas de UI dumps/capturas existentes en
  `composeApp/build/smoke-evidence/` (`ios-rag-0{1,2,3}-*.png`, `rag-0{1,2}-*.xml`) y en
  la revisión de código. No se fabricó evidencia.
- **E2E persistente:** no aplica (no hay harness de UI/E2E y `init.sh` no levanta
  simuladores); justificado en la spec.

## Hallazgos

### Finding: estado/evidencia pre-persistidos antes de existir el artefacto de validación

- **Severity:** Low (no bloqueante)
- **Evidence:** el worktree está limpio y `rag-query` ya figura como `accepted` en
  `feature_list.json` (HEAD `3b7b868`), con Session 029 en `PROGRESS.md` y `AGENTS.md`
  promovido a `accepted`; sin embargo **no existía** `docs/validations/rag-query.md`
  (este documento lo crea). El arreglo `evidence` incluye una entrada que afirma
  "validación independiente (accept)" con smoke Android propio del validador y controles
  negativos restaurados por sha256, sin artefacto de validación primario que lo respalde.
- **Why it matters:** debilita la trazabilidad de la aceptación (el estado del repo
  afirmaba una validación que no dejó artefacto) y puede erosionar la confianza en
  `feature_list.json` como fuente de verdad.
- **Required change:** el orquestador debe mantener el `accepted` (esta validación
  independiente lo confirma) y asegurarse de que la evidencia citada corresponda a
  artefactos reales; el presente informe es el respaldo primario faltante.
- **Verification after fix:** `test -f docs/validations/rag-query.md` y coherencia entre
  `feature_list.json` (`accepted`), `PROGRESS.md` y este informe.

### Finding: Scenarios 7 y 8 (index-on-save y UI) sin test automatizado

- **Severity:** Low
- **Evidence:** `App.indexBestEffort` y `AskNotesScreen` sólo se validan por revisión de
  código y evidencia de smoke; no hay test de UI/Compose ni aserción automatizada de que
  guardar una Nota persista la fila `embedding` / `NoteStatus` no cambie.
- **Why it matters:** menor reproducibilidad de Scenario 7/8; el resto de la lógica
  (retrieval/generación) sí está cubierta por tests portables.
- **Required change:** considerarlo deuda cuando exista el harness E2E/Compose UI; no
  bloquea (la spec justifica la ausencia de E2E y el core está cubierto).
- **Verification after fix:** test de UI o E2E que cubra index-on-save y los estados de
  `AskNotesScreen`.

No hay hallazgos Critical/High/Medium.

## Veredicto final

**accept.** La implementación se ciñe a la spec: los Scenarios 1–6 y 9 quedan cubiertos
por tests del core re-ejecutados en dos plataformas (22/22, 0 failures) y los Scenarios
7–8 por revisión de código + evidencia de smoke; la verificación se re-ejecutó de verdad;
los controles negativos propios prueban que los tests son sensibles a fallos reales
(restaurados byte-idénticos); el alcance se respeta (esquema v4, sin `.sqm`, sin
dependencias, sin cambios en `init.sh`), la seguridad no arroja hallazgos y la
documentación durable está actualizada. El único punto a reconciliar es el estado
pre-persistido `accepted` sin artefacto de validación (Low), resuelto con este informe.
`feature_list.json` permanece en `accepted`.
