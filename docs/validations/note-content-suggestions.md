# Validación independiente: note-content-suggestions

- **Feature:** `note-content-suggestions` (`area: ai`)
- **Spec:** `docs/specs/note-content-suggestions.md`
- **Rama/worktree:** `feature/note-content-suggestions` · `worktree/note-content-suggestions` (base `origin/develop` `f72ea65`)
- **Veredicto:** **`accept`**
- **Fecha:** 2026-10-21
- **Estado recomendado:** `accepted`

## Alcance validado

Acción **"Sugerir ideas"** en Nueva nota y Edición: pide a `AiClient.generate`
hasta **2 ideas de contenido** con prompt es-ES y contexto acotado (Proyecto +
`Track` + Géneros + `body`), las muestra como tarjetas efímeras y permite
**"Añadir"** (anexa al final del `body` con una línea en blanco) o **"Ignorar"**
(sólo quita la tarjeta). Convive sin cambios con "Sugerir clasificación" y no
persiste sugerencias ni auto-guarda. El validador revisó código, diff, docs y
re-ejecutó la verificación y el smoke por su cuenta.

## Checks re-ejecutados

- `./gradlew :core:testDebugUnitTest -Pplaybook.ai.mode=fake --rerun-tasks` →
  `BUILD SUCCESSFUL`; **37 suites / 119 tests / 0 failures**
  (`NoteContentSuggestionAndroidTest` 8/0).
- `./gradlew :core:iosSimulatorArm64Test -Pplaybook.ai.mode=fake --rerun-tasks` →
  `BUILD SUCCESSFUL`; **37 suites / 119 tests / 0 failures**
  (`NoteContentSuggestionIosTest` 8/0). No bloqueante (iOS congelado).
- `./gradlew :composeApp:assembleDebug -Pplaybook.ai.mode=fake --rerun-tasks` →
  `BUILD SUCCESSFUL`.
- `./gradlew :composeApp:testDebugUnitTest -Pplaybook.ai.mode=fake --rerun-tasks`
  → `BUILD SUCCESSFUL`; **6 suites / 15 tests / 0 failures**.
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 -Pplaybook.ai.mode=fake --rerun-tasks`
  → `BUILD SUCCESSFUL`. No bloqueante.
- `CI=true ./init.sh` → **exit 0** (gate Android-only, IA fake, sin red ni key).
- `git diff --check` → 0; `python3 -c "import json; json.load(open('feature_list.json'))"`
  → OK.
- Esquema: `PlaybookDatabase.Schema.version == 8` (tests `DatabaseMigrationCheck`/
  `MigrationCheck`/`GenreCheck`); `.sqm` existentes sólo `1.sqm`…`7.sqm`, sin
  migraciones nuevas.
- Paths protegidos sin cambios: `AiRuntime`/`AiPorts`/adaptadores OpenAI,
  `BrainstormingSuggestions.kt`, `ClassificationSuggestions.kt`/sección de
  clasificación, `Note`/`NoteDraft`/repositorios, build files, `init.sh`,
  `iosApp/**`, `MainActivity`/`MainViewController`, secrets/keys.

## Smoke Android independiente

Serial usado: **`emulator-5558`** (API 34, Android 14, `sdk_gphone64_arm64`).
No se tocaron `emulator-5556` ni el Pixel físico.

- Baseline: paquete `com.playbook.app` instalado y **sin** directorio `databases`
  (primer arranque). Se instaló el APK debug fake con `adb install -r`.
- Onboarding: "Saltar" crea el Proyecto singleton vacío y navega a la lista.
- Nueva nota con `body` vacío: "Sugerir ideas" habilitado, sin escribir en la
  base; aparecen las **2 tarjetas** y `Guardar` sigue deshabilitado por el
  `body` vacío (no por la IA).
- "Añadir" la primera: el `body` queda `idea` y permanece **sólo** la segunda
  tarjeta. "Ignorar" la restante: desaparece y el `body` no cambia.
- Con texto previo: "Añadir" las dos deja `texto\n\nidea1\n\nidea2` (párrafos
  separados por línea en blanco) y la sección vuelve a `Idle`.
- Editar a mano el `body` con tarjetas visibles → las tarjetas se descartan.
  Cambiar `Track` → se descartan. Cambiar un Género → se descartan.
- Convivencia: con 2 tarjetas de contenido visibles se pide "Sugerir
  clasificación"; **Confirmar** la clasificación no limpia las tarjetas y
  "Añadir" una idea no limpia la clasificación.
- Guardar con tarjetas visibles → vuelve a la lista y **no** anexa las tarjetas
  visibles; relanzar la app (`force-stop` + `am start`) conserva exactamente el
  `body` guardado.
- Editar la Nota existente, pedir ideas y **Cancelar** → el `body` persistido no
  cambia. Volver (`Back`) usa la misma ruta de descarte.
- DB volcada (`run-as com.playbook.app cat databases/playbook.db`):
  `PRAGMA user_version = 8`; tablas `android_metadata`, `attachment`, `embedding`,
  `game_project*`, `note`, `note_genre`, `note_link`; **0** objetos que contengan
  `suggest`; `note.body` contiene los dos párrafos separados por línea en blanco.
- Restauración: `pm clear com.playbook.app` en `emulator-5558` devuelve el
  emulador al estado previo (primer arranque sin `databases`); el paquete sigue
  instalado. Capturas y dump del validador en
  `composeApp/build/smoke-evidence/note-content-suggestions/validator/`
  (ignorado por git).

### Limitación declarada del smoke

- **Parcial**: no se pudo inyectar por `adb input text` una idea con acentos
  (`ñ`/`í`) para forzar el dedupe contra el `body`; `adb` lanzó
  `NullPointerException` en `sendText`. El camino parcial queda cubierto por
  `NoteContentSuggestionCheck` (`Success(partial = true)`), que pasa en Android e
  iOS; el smoke del implementer sí lo reprodujo. No se fingió un resultado.
- **Error/Inválida**: no alcanzables con el fake de producción; cubiertos por
  stubs en tests del core (spec §Accepted Limitations).

## Controles negativos (reproducidos y restaurados)

Hashes `sha256` del árbol original antes/después; restauración verificada
byte-idéntica.

| Control | Mutación | Resultado observado | Restauración |
|---|---|---|---|
| (a) | Quitar la rama `NOTE_CONTENT_PROMPT_MARKER` de `FakeAiClient` | Falla `fakeReturnsTwoParseableIdeasWithoutBreakingOtherBranches` (1/8) | `FakeAiClient.kt` `e057e8d6…ac9c6` |
| (b) | Anexar con `"\n"` en `appendNoteContentSuggestion` | Falla `appendHelperKeepsBlankLineAndPreservesBody` (1/8) | `NoteContentSuggestions.kt` `fca5d344…f9c6a8` |
| (c) | Omitir el dedupe en `parseNoteContentSuggestions` | Falla `deduplicatesBodyAndTruncatesTo200Chars` (1/8) | idem `fca5d344…f9c6a8` |
| (d) | No descartar en cambios manuales | **No automatizable**: no hay harness UI/E2E. Verificado en el smoke independiente (body/Track/Género descartan) y por inspección de `onValueChange`/`onClick` + request-id/`Job`. Limitación honesta, no se simula un control. | N/A (sin mutación) |

Tras restaurar, `NoteContentSuggestionAndroidTest` vuelve a **8/0**.

## Revisión de código (resumen)

- **Cancelación/request-id:** `discardContentSuggestions()` incrementa el
  contador, cancela el `Job` y vuelve a `Idle`; el resultado tardío se compara
  contra `contentSuggestionRequest` antes de tocar el estado. El `Job` se anula
  al terminar. Independiente del de clasificación.
- **Rutas de descarte:** `onValueChange` del `body`, selector de `Track`,
  `GenrePicker`, dictado (`LaunchedEffect`), transcripción (aplicación directa y
  Anexar/Reemplazar), Guardar, Cancelar y `Back` (`PlaybookBrandHeader.onBack`)
  descartan las tarjetas. Confirmar clasificación **no** las descarta (escenario
  11 del spec).
- **Añadir/Ignorar:** `addContentSuggestion` anexa con
  `appendNoteContentSuggestion` y filtra sólo la tarjeta usada; no llama a
  `discardSuggestion()` (clasificación intacta). `ignoreContentSuggestion` no
  toca el `body`. Ninguno auto-guarda.
- **Contexto:** 9 campos del Proyecto + `Track` + Géneros + `body`, máximo 12
  entradas; valores de Proyecto/listas ≤300, `body` ≤1200; sólo valores no
  vacíos; sin `owner`/ids/timestamps; sin Proyecto usa lo disponible; `body`
  vacío no agrega entrada vacía. Se llama al runtime incluso con contexto vacío.
- **Parser:** normaliza `\r\n`/`\r`, fence envolvente, encabezados, blockquotes,
  viñetas, numeración, énfasis, prefijos `Idea/Opción N:`, comillas, espacios,
  separadores, ruido y preámbulos; dedupe case-insensitive; descarta la idea
  idéntica al `body` normalizado; trunca a 200 y toma las 2 primeras.
  `AiClientException` → `Failed`; `CancellationException` se propaga.
- **UI:** sección antes de clasificación; `Idle`/`Loading`/`Loaded(partial)`/
  `Error` con textos es-ES; "Añadir"/"Ignorar"/"Reintentar"; acciones con
  `defaultMinSize(minHeight = 48.dp)`; Guardar nunca depende de la IA.
- **Seguridad:** sin secrets ni logs con contenido del usuario; sin red en el
  gate (fake); sin persistencia ni cambios de esquema; sin cambios en runtime,
  puertos, adaptadores ni build files.

## Hallazgos

Sin hallazgos Critical/High/Medium.

### Finding: truncado puede quedar en 199 caracteres en un caso borde

- Severity: **Low**
- Evidence: `truncateSuggestion()` hace
  `text.take(199).trimEnd() + "…"`; si el carácter 199 es un espacio, el
  resultado mide 199 en vez de 200 (`NoteContentSuggestions.kt:230-235`). El test
  actual usa `"x".repeat(400)` y no cubre ese borde.
- Why it matters: la decisión cerrada #9 pide "199 + `…`" (200 exactos). El
  texto sigue truncado y marcado con `…`; no afecta al comportamiento ni a la
  aceptación.
- Required change: opcional/follow-up; si se toca, usar `take(199) + "…"` sin
  `trimEnd()` (o recortar antes de tomar 199) y agregar un test con espacio en la
  posición 199.
- Verification after fix: `./gradlew :core:testDebugUnitTest -Pplaybook.ai.mode=fake`.

### Finding: checkboxes de Implementation Tasks del spec sin marcar

- Severity: **Low**
- Evidence: `docs/specs/note-content-suggestions.md` §Implementation Tasks sigue
  con `- [ ]` aunque las tareas están implementadas y verificadas.
- Why it matters: no afecta comportamiento; sólo precisión documental.
- Required change: opcional/follow-up al cerrar el spec.

## Seguridad

Sin hallazgos relevantes para el slice: no hay secrets, API keys ni keystores;
no cambian autenticación, permisos, red, persistencia ni esquema. La IA sigue
detrás de `AiClient` y el gate usa fake determinista.

## Documentación y harness

`ARCHITECTURE.md`, `CONSTRAINTS.md`, `DESIGN.md`, `docs/technical-discovery.md`,
`docs/risks-and-open-questions.md`, `AGENTS.md`, `PROGRESS.md` y
`feature_list.json` coinciden con lo implementado. `AGENTS.md` sigue siendo un
router corto. Sin E2E persistente y la spec justifica por qué (no hay harness UI;
la lógica se cubre con tests cross-platform y smoke manual Android).

## Conclusión

La implementación cumple el contrato y las decisiones cerradas del autor, la
verificación se re-ejecutó en verde y el smoke independiente confirmó los flujos
principales y las rutas de descarte. Se recomienda persistir `accepted`.
