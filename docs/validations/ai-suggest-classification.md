# Validación independiente: Sugerencia de clasificación (Track/Género) con `jev` (`ai-suggest-classification`)

- Fecha: 2026-10-20
- Validador: `feature-validator` (rol independiente del implementer)
- Feature: `ai-suggest-classification` — `feature_list.json` (status `passing`)
- Spec: `docs/specs/ai-suggest-classification.md`
- Rama: `feature/ai-suggest-classification` (worktree), árbol de trabajo sin commit; base `origin/develop` `9c75be4`
- Dependencias verificadas: `ai-fake-real-switch` y `genres-catalog-ui` (`accepted`)
- Veredicto: **accept** (el orquestador persiste `accepted`; este validador no cambió `feature_list.json` ni commiteó)

## 1. Alcance validado

- Servicio puro `ClassificationSuggestionService(jevClient, timeoutMillis = 20_000)` en
  `core/.../ai/ClassificationSuggestions.kt`: `trim`, `Empty` sin llamar al puerto si el
  texto queda vacío, `withTimeout`, normalización con `normalizeGenres` y resultado
  `Success`/`Empty`/`Failed`. `AiClientException` y timeout se mapean a `Failed`; la
  cancelación del llamador se propaga.
- `applyClassificationSuggestion`: `Track` sugerido no nulo reemplaza; `null` conserva;
  Géneros fusionados normalizados (unión), idempotente y sin persistencia.
- `FakeJevClient`: `Track` y un Género de `GenreCatalog.GENRES` deterministas por el
  mismo `text.hashCode()`; sin `Random` ni red; `GENRE_HINT` eliminado.
- `NoteEditorScreen`: sección "Sugerir clasificación" entre cuerpo/dictado y `Track`,
  estados `Idle`/`Loading`/`Ready`/`Empty`/`Error`, tarjeta con `Track` + `GenreLabelChip`,
  Confirmar/Descartar/Reintentar, descarte por cambio manual de body/track/Géneros y
  contador de request para ignorar resultados tardíos. Guardar no depende de la IA.
- Wiring: `App` recibe `jevClient`, crea el servicio recordado y lo pasa a
  Create/CreateWithImage/Edit; `MainActivity` y `MainViewController` pasan `runtime.jevClient`.
- Sin cambios en `AiRuntime.kt`, `AiPorts.kt`, `OpenAiAdapters.kt`, `Note`/`NoteDraft`,
  SQLDelight/`.sqm`, build files, `init.sh`, `iosApp/**` ni secrets. `Schema.version == 8`.

## 2. Comandos re-ejecutados por el validador

| Comando | Resultado |
| --- | --- |
| `./gradlew :core:testDebugUnitTest --rerun-tasks` | **BUILD SUCCESSFUL** — 31 suites / 96 tests / 0 failures / 0 errors; `ClassificationSuggestionAndroidTest` 6/6 |
| `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` | **BUILD SUCCESSFUL** — 31 suites / 96 tests / 0 failures / 0 errors; `ClassificationSuggestionIosTest` 6/6 |
| `./gradlew :composeApp:assembleDebug --rerun-tasks` | **BUILD SUCCESSFUL** (56 tasks) |
| `CI=true ./init.sh` | **exit 0** (Android-only, IA fake, sin dev servers) |
| `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` | **BUILD SUCCESSFUL** |
| `PlaybookDatabaseImpl.Schema.version` generado | **8** (7 `.sqm` existentes, sin `.sqm` nuevo) |

Conteos leídos de `core/build/test-results/{testDebugUnitTest,iosSimulatorArm64Test}/TEST-*.xml`.
El total coincide con el contrato "base vigente 30/90 + 6 tests nuevos" y con lo
registrado en `feature_list.json`/`PROGRESS.md`. Tests sin red y con IA fake.

## 3. Controles negativos propios

Sobre una copia byte-idéntica de `ClassificationSuggestions.kt`
(SHA-256 `e2a8c759f7b63bcda9d36410427823567fefa0173f953d6e8d2eede9451daf7a`),
ejecutando `--tests '*ClassificationSuggestionAndroidTest*'`:

| Control | Resultado | Restauración |
| --- | --- | --- |
| (a) Omitir `normalizeGenres` en `suggest` (`val genres = suggestion.genres`) | **2/6 fallan**: `normalizesCatalogCustomBlankAndDuplicates`, `unusableSuggestionIsEmpty` | Restaurado; hash idéntico y suite completa 31/96 en verde |
| (b) `apply` reemplaza Géneros (`normalizeGenres(suggestion.genres)`) | **1/6 falla**: `applyReplacesTrackAndMergesGenres` | Restaurado; hash idéntico y suite completa 31/96 en verde |
| (c) Quitar el catch de `TimeoutCancellationException` | **1/6 falla**: `failureAndTimeoutAreReported` | Restaurado; hash idéntico y suite completa 31/96 en verde |

Los tres controles reproducen exactamente los fallos que declara la evidencia del
implementer; el archivo quedó restaurado (SHA-256 verificado) y las suites Android e
iOS se re-ejecutaron en verde.

## 4. Smoke Android independiente (emulator-5556)

APK propio (`composeApp/build/outputs/apk/debug/composeApp-debug.apk`, SHA-256
`c334ac3d…`), `pm clear` para partir de primer arranque. Evidencia propia en
`composeApp/build/smoke-evidence/ai-suggest-classification-validator/` (ignorada por git).

1. Onboarding "Saltar" → lista vacía → Nueva nota.
2. **Body vacío**: nodo padre del botón "Sugerir clasificación" con `enabled="false"` y
   Guardar `enabled="false"`; sin tarjeta.
3. Body `"rpg"`: botón habilitado; **sin** tarjeta automática. DB antes de sugerir/guardar:
   `note=0`, `game_project=1`, `user_version=8`.
4. "Sugerir clasificación" → tarjeta "Sugerencia de la IA" con `Track: historia` y chip de
   catálogo `Shooter` (hash 113129 → índices 2 y 5); el `Track` seleccionado sigue siendo
   "✓ Sin track" (no auto-asignación); DB sigue con `note=0`.
5. **Confirmar** → tarjeta desaparece, `✓ historia` seleccionado y chip `Shooter ×` en
   "Seleccionados" (Género del catálogo fusionado). No hubo escritura en DB.
6. Quitar `Shooter`, agregar personalizado `"Zelda-like"`, Guardar → lista con
   `rpg`/`historia`/`Zelda-like`; DB: 1 Nota `rpg`/`historia`/`indexada`,
   `note_genre = Zelda-like`, `user_version=8`.
7. Force-stop + relanzar: la Nota conserva `historia` y `Zelda-like`.
8. Editar Nota existente: sugerir + **Descartar** → tarjeta desaparece, `✓ historia` y
   `Zelda-like ×` intactos, `updated_at` sin cambios. Guardar sin sugerencia avanza
   `updated_at` (1792490646824 → 1792490858498) y conserva la clasificación.
9. Editar, sugerir, cambiar el body a `"rpgx"` con la tarjeta visible → tarjeta
   descartada, vuelve el botón "Sugerir clasificación"; Cancelar no persiste el cambio.
10. `Loading` ("Sugiriendo…") no se pudo capturar por ser instantáneo con el fake;
    `Empty`/`Error` no son alcanzables con el fake de producción. Declarado honestamente
    en la spec y en la evidencia; ambos caminos están cubiertos por tests del core.

Nota de entorno: el emulador `emulator-5556` es compartido. A las 12:08 otra sesión
instaló un APK distinto sobre el nuestro (hash `0be12aa3…`, sin la sección de sugerencia).
Se detectó por hash, se reinstaló el APK propio (`c334ac3d…`, `lastUpdateTime 12:13:05`) y
se repitieron los pasos afectados (sección visible + descarte por cambio de body). No es
un defecto de la feature.

## 5. Evidencia del implementer inspeccionada

- `composeApp/build/smoke-evidence/ai-suggest-classification/`: UI dumps `01`–`21`,
  capturas `04/06/07/10/11/12/20` y copias `db-*`. Los dumps `03` (botón padre
  `enabled="false"`), `06` (tarjeta `historia` + `Shooter`), `12` (persistencia tras
  relanzar), `15` (descartar) y `20` (descarte por body) son coherentes con lo observado.
- `sqlite3` sobre `db-before-save/playbook.db` → `note=0`, `user_version=8`;
  `db-after-save`/`db-after-edit-save`/`db-final` → 1 Nota `rpg`/`historia`/`indexada`,
  Género `Zelda-like`, `user_version=8`.
- Ningún dump contiene "Sugiriendo…", consistente con la limitación declarada.
- `grep` no encontró "Sugiriendo" en la evidencia; la limitación está declarada en la
  spec, `PROGRESS.md`, `feature_list.json` y `docs/technical-discovery.md`.

## 6. Hallazgos

Sin hallazgos Critical/High/Medium.

- **Low (observación de evidencia):** `composeApp/build/smoke-evidence/ai-suggest-classification/db-before-final-save/playbook.db`
  tiene `note=0` mientras `db-after-save` y `db-after-edit-save` tienen 1 Nota. El nombre
  sugiere otra secuencia; no contradice el comportamiento (la evidencia propia y los
  dumps cubren "DB intacta antes de Guardar" y la persistencia posterior). No bloquea.
- **Low (limitación aceptada):** no hay test UI/E2E persistente; la UI se valida por smoke
  manual. `Loading` no se captura con el fake instantáneo y `Empty`/`Error` sólo se cubren
  en tests del core. Ya está declarado en la spec y en los artefactos durables.
- **Low (limitación aceptada):** la calidad real de `jev` (aciertos/`none`/formato) no se
  verifica en el gate; queda como smoke key-gated fuera del gate, tal como define la spec.

## 7. Checklist del spec

- [x] Sólo la acción explícita llama a `JevClient.suggest`; nunca auto-asigna ni persiste
  la propuesta; Guardar nunca se bloquea por la IA.
- [x] Botón habilitado sólo con body no vacío y fuera de carga; `Idle`/`Loading`/`Ready`/
  `Empty`/`Error` visibles y accionables; timeout/fallo no lanzan hacia la UI.
- [x] Confirmar aplica `Track` no nulo (reemplaza) / `null` (conserva) y fusiona Géneros
  normalizados sin duplicados ni pérdidas; Descartar no cambia nada.
- [x] Cambios manuales de body/track/Géneros o salida del editor descartan/cancelan; los
  resultados tardíos se ignoran por contador de request.
- [x] Normalización: trim, vacíos descartados, catálogo canonizado, personalizado
  preservado, dedupe/orden de `normalizeGenres`.
- [x] `FakeJevClient` devuelve `Track` + Género de `GenreCatalog.GENRES` deterministas por
  hash; único cambio en `FakeAiPorts.kt`.
- [x] Sin cambios en `AiRuntime.kt`, `AiPorts.kt`, `OpenAiAdapters.kt`, `Note`/`NoteDraft`,
  SQLDelight/`.sqm`, build files, `init.sh`, `iosApp/**` ni secrets; `Schema.version == 8`.
- [x] Scope: sin clasificación automática, sin persistencia de la sugerencia, sin
  Categoría/Tipo/Nivel, sin red/API key en tests/gate.
- [x] Docs durables y evidencia de `feature_list.json`/`PROGRESS.md` coherentes con lo
  ejecutado; `AGENTS.md` actualizado sólo con la cola.

## 8. Seguridad y alcance

- Sin secrets, API keys, keystores ni credenciales en el diff ni en los artefactos.
- Sin dependencias nuevas ni cambios en build files/`init.sh`.
- Sin cambios de esquema, migraciones, indexado, RAG, Enlaces ni creación/edición de Notas.
- Sin red en tests/gate (IA fake); el smoke usó el fake de producción.
- Sin desvíos del spec detectados.

## 9. Riesgos residuales

- Calidad semántica real de `jev`: smoke key-gated pendiente, fuera del gate.
- `Empty`/`Error` y `Loading` sin cobertura de UI automatizada; cubiertos por core/smoke
  declarado.
- Timeout best-effort: el I/O bloqueante del transporte Android puede demorar hasta su
  `readTimeout`; Guardar nunca espera la sugerencia.
- iOS congelado: sólo se verificó compilación/link; no hay smoke iOS.
