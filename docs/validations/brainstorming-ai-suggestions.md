# Validación independiente: Sugerencias de Brainstorming (`brainstorming-ai-suggestions`)

- Fecha: 2026-10-19
- Validador: `feature-validator` (rol independiente del implementer)
- Feature: `brainstorming-ai-suggestions` — `feature_list.json` (status `passing`)
- Spec: `docs/specs/brainstorming-ai-suggestions.md`
- Rama/worktree: `feature/brainstorming-ai-suggestions` en
  `worktree/brainstorming-ai-suggestions`, árbol de trabajo sin commit
- Base: `a0d556f` (`inline-dictation-30s`); `origin/develop` está en `6db4d38`
  (incluye `sketch-thumbnails`), por lo que la rama está 0 adelante / 3 atrás
- Dependencias verificadas: `brainstorming-wizard` y `ai-client-interface`
  (`accepted`)
- Veredicto: **accept**

## 1. Alcance validado

Botón "Sugerir" en los 9 pasos del wizard de Brainstorming, con servicio en
`:core` (`BrainstormingSuggestionService`), prompt/contexto por paso, parsing
robusto, máximo 2 ideas, dedupe dentro de la respuesta y contra el borrador,
resultado parcial, error no bloqueante, mecánicas con categoría y UI efímera con
Usar/Agregar/Ignorar. La IA nunca auto-rellena, no persiste y no bloquea la
escritura ni el guardado. `FakeAiClient` sigue siendo determinista y conserva el
comportamiento de RAG/Q&A fuera del marcador.

Confirmado como **no implementado** (non-goals): persistencia/tablas/migraciones
de sugerencias, creación automática de Notas, IA real/proveedor/key/flag,
streaming, TTS, transcripción, clasificación, Q&A, telemetría y cambios de
esquema (sigue v8), build files, `init.sh` o `iosApp`.

## 2. Comandos re-ejecutados

Entorno: `ANDROID_HOME=/Users/javierrodriguez/Library/Android/sdk`; emulador
`emulator-5556`; IA fake.

| Comando | Resultado |
| --- | --- |
| `./gradlew :core:testDebugUnitTest --rerun-tasks -Pplaybook.ai.mode=fake` | **BUILD SUCCESSFUL** — 27 suites / 86 tests / 0 failures / 0 errors / 0 skipped; 7 tests nuevos en `BrainstormingSuggestionAndroidTest` |
| `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` | **BUILD SUCCESSFUL** — 27 suites / 86 tests / 0 failures / 0 errors / 0 skipped; 7 tests nuevos en `BrainstormingSuggestionIosTest` |
| `./gradlew :composeApp:assembleDebug --rerun-tasks` | **BUILD SUCCESSFUL** |
| `CI=true ./init.sh` | **exit 0** (Android-only, fake, sin red ni dev servers) |
| `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` | **BUILD SUCCESSFUL** (iOS congelado, sólo compilación) |
| `PRAGMA user_version` en la DB del emulador | **8** |
| `find core -name '*.sqm'` | `1.sqm`..`7.sqm` — sin `.sqm` nuevo |
| `git diff HEAD` en paths protegidos (`core/src/commonMain/sqldelight`, `init.sh`, build files, `gradle/**`, `iosApp/**`, modelo/repositorio) | **vacío** |

## 3. Smoke Android independiente (emulator-5556, Pixel_API_35)

Se instaló el APK de la rama con `:composeApp:installDebug` y se recorrió el
flujo con `adb` + `uiautomator`, sin confiar sólo en la evidencia del
implementer.

| Escenario | Resultado observado |
| --- | --- |
| Paso 1 (Título), "Sugerir" | Aparecen exactamente 2 tarjetas ("Idea de prueba A/B") con Usar/Ignorar; el campo Título sigue vacío |
| "Usar" una tarjeta | Título = `Idea de prueba A`; la tarjeta usada desaparece y la otra queda |
| "Ignorar" la restante | No quedan tarjetas; el borrador queda con lo ya aceptado |
| Pedir de nuevo con A en el borrador | A se filtra; se muestra B con aviso parcial "La IA devolvió una sola idea…" |
| Siguiente → Atrás | Las tarjetas no sobreviven a la navegación; al volver no hay tarjetas ni aviso |
| Cancelar → reabrir Brainstorming | No hay tarjetas ni aviso; "Sugerir" vuelve a funcionar |
| Género (paso 2) | "Agregar" incorpora el texto como chip (`normalizeGenres`); al repetir el pedido, el valor ya presente se filtra y el otro queda como parcial |
| Inspiraciones (paso 6) | "Agregar" completa la fila vacía y luego agrega otra; contador `2/3`; sin duplicados |
| Mecánicas clave (paso 8) | Tarjetas con categoría visible (`Movimiento`/`Interacción`); al agregar quedan 2 filas con categoría + descripción editables (`2/5`) |
| Fuera de alcance (paso 9) | "Sugerir" devuelve 2 tarjetas con "Agregar"; Guardar persiste y vuelve a la lista |
| 0 usables | Con A y B ya en Inspiraciones, "Sugerir" muestra error no bloqueante "La IA no devolvió sugerencias nuevas o válidas" + "Reintentar", sin tarjetas y sin mutar el borrador |
| Guardado | `game_project` = `Idea de prueba A`; `game_project_genre` = A; `game_project_inspiration` = A/B; `game_project_key_mechanic` = `movimiento|A` y `interacción|B`; sin tablas nuevas |
| Tablas de la DB | `android_metadata`, `attachment`, `embedding`, `game_project`, `game_project_genre`, `game_project_inspiration`, `game_project_key_mechanic`, `game_project_out_of_scope`, `note`, `note_genre`, `note_link` — ninguna tabla de sugerencias |

Limitaciones declaradas del smoke: con el fake la respuesta es inmediata, por lo
que no se pudo observar en runtime el estado "Sugiriendo…", la cancelación de un
Job en vuelo ni un resultado tardío; esos caminos se revisaron en código
(`BrainstormingWizardScreen.kt:181-236`, `:323-332`) y el fallo del `AiClient` se
cubre con el test de `:core`. Tampoco hay harness UI/E2E persistente, como
justifica la spec.

## 4. Escenarios de aceptación 1–10

1. **Texto escalar:** cumple (2 tarjetas, Usar copia al Título, tarjeta desaparece).
2. **Nunca auto-rellena:** cumple (el título permanece vacío tras Sugerir y hasta Usar).
3. **Listas y topes:** cumple (completa la primera fila vacía y agrega hasta el tope; el botón se deshabilita según `canAcceptSuggestion`, `BrainstormingWizardScreen.kt:261-293`).
4. **Mecánicas:** cumple (categoría + descripción; una línea sin categoría válida se descarta en `BrainstormingSuggestions.kt:239-251`).
5. **Género:** cumple (filtra repetidos y agrega vía `normalizeGenres`).
6. **Ignorar y volver a pedir:** cumple (Ignorar sólo quita la tarjeta; un pedido nuevo puede repetir el texto).
7. **Parcial/vacía/fallo:** cumple (parcial con aviso; 0 usables y `AiClientException` → error no bloqueante con Reintentar; sin tocar el borrador).
8. **Navegación:** cumple (limpieza en Atrás/Siguiente/Cancelar/Guardar/`DisposableEffect`; resultado tardío descartado por `requestId` + paso capturado).
9. **Deduplicación:** cumple en respuesta (case/espacios) y contra valores del borrador, con la salvedad Low del punto 6.
10. **Gate honesto:** cumple (86/86 en Android e iOS, `assembleDebug`, `init.sh` exit 0, esquema v8 sin `.sqm` nuevo).

## 5. Controles negativos declarados

No se mutó código de producto en esta validación. Se revisó el código y se
corroboraron los caminos positivos:

- (a) Dedupe/filtro: el parser usa `seen` + `isAlreadyInDraft`
  (`BrainstormingSuggestions.kt:212-223`, `:261-281`) y el test
  `deduplicatesAndFiltersDraftValues` lo ejerce; el smoke mostró parcial por
  dedupe, no tarjetas repetidas.
- (b) Auto-relleno: el borrador sólo muta en `acceptSuggestion`
  (`BrainstormingWizardScreen.kt:305-321`); tras "Sugerir" el campo siguió vacío.
- (c) Limpieza: `goToStep`/`save`/Cancelar/`LaunchedEffect(projectKey)` llaman
  `resetSuggestions` (`:181-186`, `:208-236`, `:323-332`); el smoke confirmó que
  no quedan tarjetas al navegar ni al reabrir.

La evidencia del implementer en
`composeApp/build/smoke-evidence/brainstorming-ai-suggestions/` es coherente con
estos resultados (02/03/04/05, 06, 08/09, 13/14/15, 22 y `12-db.txt`/`21-final-save-db.txt`).

## 6. Hallazgos

No hay hallazgos Critical, High ni Medium.

### Finding: el dedupe contra el borrador no normaliza espacios internos del borrador

- Severity: Low
- Evidence: `BrainstormingSuggestions.kt:261-281` compara `suggestion.text`
  (ya colapsado por `parseLine`, `:226-233`) contra valores crudos del draft con
  `equals(..., ignoreCase = true)`, sin colapsar espacios internos del lado del
  borrador. La UI repite el mismo criterio en `canAcceptSuggestion`
  (`BrainstormingWizardScreen.kt:261-293`).
- Why it matters: el Scope Decision 8 de la spec pide dedupe
  "case-insensitive, espacios normalizados". Si el usuario escribió, por ejemplo,
  `Mi   Juego`, una sugerencia `Mi Juego` no se filtraría y podría mostrarse una
  tarjeta que parece duplicada. Es un caso borde; no afectó los escenarios
  ejercitados.
- Required change: normalizar ambos lados antes de comparar.
- Suggested implementation:
  1. Agregar un helper interno (p. ej. `normalizeForCompare`) que haga
     `lowercase()` + colapso de `\s+` a un espacio + `trim()`.
  2. Usarlo en `isAlreadyInDraft` para escalares, Géneros, listas y
     categoría+descripción de mecánicas.
  3. Usar el mismo helper en `canAcceptSuggestion` para que la tarjeta se
     deshabilite de forma consistente.
- Verification after fix:
  - test de `:core` con draft `title = "Mi   Juego"` y respuesta
    `"mi juego"` → 0 sugerencias usables (`InvalidResponse`).
  - smoke: escribir el mismo texto con espacios distintos y pedir sugerencias →
    no aparece una tarjeta duplicada.

### Limitación: estados de carga/cancelación/resultado tardío no observados en runtime

- Severity: Low (limitación declarada, no defecto)
- Evidence: el fake responde de forma síncrona; los estados se verificaron por
  lectura de `requestSuggestions`/`resetSuggestions`/`DisposableEffect`
  (`BrainstormingWizardScreen.kt:181-236`, `:323-332`) y el fallo del cliente por
  `runtimeFailureIsReported` en `:core`.
- Why it matters: no hay evidencia visual del spinner ni de la cancelación de un
  Job real.
- Required change: ninguna para esta feature; si se agrega un harness UI,
  inyectar un `AiClient` con suspensión controlada.
- Verification after fix: cubierto por un futuro test de UI, fuera del alcance.

### Nota de handoff: base desactualizada respecto de `origin/develop`

- Severity: Note (no es un defecto de la feature)
- Evidence: `git rev-list --count HEAD...origin/develop` = `0 3`; `origin/develop`
  incluye `6db4d38` y `82ef4ec feature: complete sketch-thumbnails`. El diff
  `origin/develop..worktree` mezcla cambios ajenos (p. ej. reversiones de
  `NoteThumbnail`) porque la rama no está rebasada.
- Why it matters: la sesión integradora debe rebasar/cherry-pickear el árbol de
  trabajo sobre `develop` antes de commitear/mergear; commitear el árbol tal cual
  sobre `a0d556f` revertiría `sketch-thumbnails`.
- Required change: rebase de la sesión integradora; no requiere cambios de código
  de esta feature.
- Verification after fix: repetir el gate y el smoke sobre la base rebasada.

## 7. Seguridad

- Sin secrets, API keys ni keystores en el diff. No se creó `local.properties`
  ni se agregaron dependencias.
- El contexto enviado al `AiClient` excluye `owner`, ids y timestamps, y trunca
  valores a 300 caracteres; el prompt/contexto/respuesta no se loguean.
- La respuesta se trata como texto plano y se trunca a 280 caracteres; se
  renderiza con `Text` de Compose (sin HTML/webview).
- En el gate se usa siempre el fake determinista, sin red. Cuando se habilite el
  runtime real (`ai-real-chat`), el contenido del borrador se enviará al
  proveedor según el diseño del producto; queda fuera del alcance de esta
  feature.
- No hay persistencia de sugerencias ni creación de Notas, por lo que no hay
  exposición ni retención nueva de datos.

## 8. Documentación y handoff

- `ARCHITECTURE.md`, `docs/technical-discovery.md`, `DESIGN.md` y
  `docs/risks-and-open-questions.md` describen el servicio, el parsing, el
  límite 2, el fake determinista y la UI efímera, sin contradecir el código.
- `AGENTS.md` sólo actualiza la cola; `feature_list.json` queda en `passing` con
  evidencia y `PROGRESS.md` registra la Session 065 y la limitación declarada.
- `CONSTRAINTS.md`, `CONTEXT.md`, `docs/domain-model.md`, esquema y `init.sh` no
  se tocaron, como pedía la spec.
- Estado recomendado: el orquestador puede persistir `accepted` tras el rebase e
  integración; esta validación **no** modifica `feature_list.json` ni commitea.

## 9. Veredicto

**accept.** La implementación cumple los escenarios 1–10 con evidencia
re-ejecutada, respeta el alcance y el gate. El único hallazgo funcional es Low
(normalización de espacios del borrador en el dedupe) y no bloquea la
aceptación; conviene corregirlo en una sesión de pulido o junto con
`ai-real-chat`.
