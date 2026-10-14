# Validación independiente: `rag-answer-citations`

- **Feature id:** `rag-answer-citations`
- **Spec:** `docs/specs/rag-answer-citations.md`
- **Fecha:** 2026-10-14
- **Rol:** validator (independiente del implementer)
- **Estado en `feature_list.json` al validar:** `passing`
- **Veredicto:** **accept**

## Alcance validado

Surfacea `RagAnswer.Answer.sources` (ya ordenado por score, top-K, `rag-query`
`accepted`) como **citas numeradas** ("Fuentes", rank 1..N + fragmento del `body` +
`track`) en `AskNotesScreen`, y navega a la Nota fuente (`NotesDestination.Edit`).
Sólo UI (`AskNotesScreen.kt` + `App.kt`) + docs. Sin persistir citas/enlaces, sin
tocar retrieval/runtime (`RagAnswer`/`RagQueryService`/`cosineSimilarity`) ni
`NoteStatus`, sin score crudo, sin cambios en `:core`/esquema (v5)/deps/`init.sh`/
`iosApp` ni `NoteEditorScreen`.

## Checks re-ejecutados por el validador (no se confió en el resumen)

| Check | Comando | Resultado |
| --- | --- | --- |
| Gate estándar | `CI=true ./init.sh` | exit 0; corre `assembleDebug` + `testDebugUnitTest` + link iOS + `iosSimulatorArm64Test`; sin dev servers; `init.sh` sin cambios |
| Re-ejecución forzada (Android+JVM, iOS, app) | `./gradlew :core:testDebugUnitTest :core:iosSimulatorArm64Test :composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` | BUILD SUCCESSFUL; **66 tasks executed** (no `UP-TO-DATE`); compila `compileDebugKotlinAndroid`/`compileKotlinIosSimulatorArm64` desde las fuentes actuales |
| Esquema | `PlaybookDatabaseImpl.Schema.version` generado + `find core/src -name '*.sqm'` | **`version == 5`**; sólo `1.sqm`..`4.sqm` (sin `.sqm` nuevo) |
| Alcance | `git diff --stat` de paths protegidos (`core`, `libs.versions.toml`, `composeApp/build.gradle.kts`, `init.sh`, `iosApp`) | **vacío** |
| APK local | `md5` del APK del build actual | `1358c4be8d3c333b7195d7c37771db11` = el citado por el implementer |
| Seguridad | `git diff` + archivos nuevos | sin secrets/red/deps nuevas |

Conteos desde `core/build/test-results/*/TEST-*.xml` (**33/33 por plataforma,
0 failures/errors/skipped**): `Greeting` 1, `AiClient` 3, `NoteIndexing` 1,
`NoteIndexingOrchestrator` 1, **`RagQuery` 6/6**, `AiRuntimeSpike` 2,
`DatabaseMigration` 1, `Migration` 1, `NotePersistence` 2, `GddProjection` 1,
`CosineSimilarity` 7, `SemanticLinking` 1, `AttachmentRepository` 1,
`EmbeddingRepository` 1, `NoteLinkRepository` 1, `NoteRepository` 3.

## Verificación UI independiente (propia, no la del implementer)

Smoke real en `emulator-5554` con un APK **reconstruido y reinstalado por el
validador** (md5 `1358c4be…`), tras `pm clear` y con la DB sembrada de forma
determinista (Python emulando `FakeAiClient`: `String.hashCode` + LCG 32-bit +
`EmbeddingBlobCodec` big-endian). Se sembraron 3 Notas con embeddings 8-dim:

- `note-1` "Disenar el jefe final con tres fases" (`mecánicas`)
- `note-2` "La historia de la espada del valle" (`historia`)
- `note-3` body en blanco, sin `track`

Orden esperado **calculado por el validador** (coseno + desempate por id):
`note-1` 0.9272 > `note-2` 0.8858 > `note-3` 0.8551.

UI observada (dump `uiautomator`) tras la pregunta ASCII "como diseno al jefe
final":

- Respuesta: `[fake] como diseno al jefe final (context=3)`.
- **"Fuentes"** con: `Fuente 1` "Disenar el jefe final con tres fases"/`mecánicas`,
  `Fuente 2` "La historia de la espada del valle"/`historia`, `Fuente 3`
  "Sin texto"/`Sin track`.
- Orden observado **= orden esperado** (Scenario 1) y placeholder (Scenario 3).
- Tocar `Fuente 2` abrió `Editar nota` con el `body` de `note-2` y `✓ historia`
  (Scenario 2).

No se pudo leer el `onClickLabel` "Abrir nota fuente" desde `uiautomator` (no lo
expone como `content-desc`); se verificó por código
(`Modifier.clickable(onClickLabel = "Abrir nota fuente", role = Role.Button)`).

## Controles negativos

- **Propio (Scenario 5, runtime):** app recién `pm clear` (0 Notas, 0 embeddings)
  → consulta → `"Todavía no hay notas indexadas. Guardá una nota de texto y volvé
  a preguntar."` y **0** ocurrencias de `text="Fuentes"`.
- **No se hizo** un control negativo por mutación de fuente: no había herramienta
  de escritura disponible y no se quiso tocar la implementación sin commitear. Se
  inspeccionó el artefacto del implementer
  (`composeApp/build/smoke-evidence/rag-answer-citations/android-04-negative-control.xml`:
  "Respuesta" presente, **0** "Fuentes" con `emptyList()`), consistente con la
  prueba estructural del código (la sección "Fuentes" sólo existe en la rama
  `Answered` y está gateada por `if (state.sources.isNotEmpty())`).

## Escenarios de aceptación 1–7

1. Fuentes 1..N en orden con fragmento + `track`/`Sin track` — **reproducido**
   (orden = ranking coseno recalculado). ✔
2. Tocar una cita abre `Edit(noteId)` con body/`track` reales — **reproducido**. ✔
3. `body` en blanco → "Sin texto"; largo → 2 líneas + ellipsis — placeholder
   **reproducido**; el `maxLines = 2`/`TextOverflow.Ellipsis` verificado por código
   (el dump reporta el texto completo, no el recorte visual). ✔
4. Nota fuente borrada entre respuesta y toque → sin crash, vuelve a la lista —
   **por código**: `onOpenSource` navega a `Edit(noteId)` y el guard existente
   (`notes.firstOrNull { it.id == current.noteId } == null → goToList()`) lo cubre.
5. Sin fuentes (`NoIndexedNotes`/`Error`) → sin "Fuentes" — **reproducido**
   (`NoIndexedNotes`); `Error` no renderiza "Fuentes" por código. ✔
6. Accesibilidad: "Fuente N" + fragmento + `track` como texto; `onClickLabel` +
   `Role.Button` — por código. ✔
7. `:core` intacto + gate honesto — **verificado**: `RagQuery` 6/6 en ambas
   plataformas, esquema v5 sin `.sqm` nuevo, `./init.sh` exit 0. ✔

## Scope discipline

- `git diff --stat` sólo: `AGENTS.md`, `ARCHITECTURE.md`, `PROGRESS.md`,
  `App.kt`, `AskNotesScreen.kt`, `docs/specs/rag-answer-citations.md`,
  `docs/technical-discovery.md`, `feature_list.json`. `core/**`, esquema,
  `libs.versions.toml`, `composeApp/build.gradle.kts`, `init.sh` e `iosApp/**`
  **sin diffs**. Sin archivos sin trackear.
- `AGENTS.md` sólo cambió la línea "Próxima feature en cola".

## Seguridad (checklist scoped al diff)

Sin secrets/tokens/URLs/`.env`; sin red ni dependencias nuevas; sin superficie de
auth nueva; la única entrada (la pregunta) ya se valida (`isNotBlank` en UI y
`require` en `RagQueryService`); el `body` de la cita se renderiza como `Text`
(sin inyección). **No se encontraron hallazgos de seguridad.**

## Documentación durable

- `ARCHITECTURE.md`: deja de afirmar que la UI no renderiza `sources`; describe
  "Fuentes" y la navegación — consistente con el código.
- `docs/technical-discovery.md`: nueva viñeta "Citas de la respuesta"; consistente
  salvo la línea preexistente "esquema v4" (ver hallazgo Low).
- `CONSTRAINTS.md`: no requerido (sin MUST/MUST NOT nuevo) y sin cambios.
- `AGENTS.md`: sólo la línea de próxima feature. `feature_list.json`/`PROGRESS.md`:
  estado `passing`, verificación y evidencia honestas.
- E2E persistente: no aplica (sin harness de UI; `:composeApp` sin source set de
  test; `init.sh` no levanta simuladores) y la spec lo justifica.

## Hallazgos

### Finding: "esquema v4" desactualizado en `docs/technical-discovery.md`

- **Severity:** Low (no bloqueante, preexistente)
- **Evidence:** `docs/technical-discovery.md` (sección "RAG Decisions (rag-query)")
  dice "(esquema v4, sin `.sqm` nuevos)" mientras el esquema real es **v5**
  (`PlaybookDatabaseImpl.Schema.version == 5`, `4.sqm` de `semantic-linking`). La
  línea es contexto sin cambios en este diff (introducida por `rag-query` cuando
  v4 era el esquema vigente).
- **Why it matters:** Puede desorientar a una sesión futura; no afecta la
  corrección de la feature.
- **Required change:** ninguna para aceptar; al tocar la sección, corregir v4→v5.
- **Verification after fix:** `grep "esquema v4" docs/technical-discovery.md` sin
  coincidencias en la sección de RAG.

### Finding (nota): la spec se editó más allá de "Expected File Changes"

- **Severity:** Low (no bloqueante)
- **Evidence:** `docs/specs/rag-answer-citations.md` figura como modificado
  (tasks `[x]`, corrección v4→v5, sección "Implementation Findings").
- **Why it matters:** No cambia goal/non-goals/escenarios ni relaja la
  verificación; documenta de forma explícita y honesta la corrección del esquema y
  desviaciones menores (scroll, placeholder).
- **Required change:** ninguna.

No hay hallazgos Critical/High/Medium.

## Veredicto final

**accept.** La implementación se ciñe a la spec, los escenarios 1–7 pasan (1/2/3/5
reproducidos por el validador de forma independiente; 4/6/7 por código + gates), la
verificación se re-ejecutó de verdad (66 tasks ejecutadas, 33/33 en dos
plataformas, `./init.sh` exit 0, esquema v5 sin `.sqm` nuevo), el alcance y la
seguridad se respetan y la documentación durable está actualizada. `feature_list.json`
permanece en `passing`; **el orquestador debe promoverlo a `accepted`** y registrar
la evidencia de validación.
