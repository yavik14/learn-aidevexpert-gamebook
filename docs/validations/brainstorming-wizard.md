# Validación independiente: Wizard de Brainstorming (`brainstorming-wizard`)

- Fecha: 2026-10-19
- Validador: `feature-validator` (rol independiente del implementer)
- Feature: `brainstorming-wizard` — `feature_list.json` (status `passing`)
- Spec: `docs/specs/brainstorming-wizard.md`
- Rama: `feature/brainstorming-wizard` (worktree), árbol de trabajo sin commit
- Dependencias verificadas: `game-project-persistence` y `genres-catalog-ui`
  (`accepted`)
- Veredicto: **accept**

## 1. Alcance validado

Wizard de 9 pasos que crea/itera **sólo la ficha del Proyecto** con
`GameProjectRepository`:

- Pantalla nueva `BrainstormingWizardScreen.kt` con progreso `Paso X/9`,
  Atrás/Siguiente, Guardar explícito, Cancelar sin escritura y precarga desde
  `initialProject`.
- Draft **completo** de `GameProjectDraft` (nunca sólo el paso actual), owner
  preservado, listas normalizadas y topes 3/3/5.
- Reutilización de `GenrePicker`; sin Nota por paso, IA, onboarding, GDD
  híbrido, rediseño de la pantalla principal, cambios en `:core`, esquema,
  build files, `init.sh` ni `iosApp/**`.

Fuera de alcance confirmado como no implementado:
`brainstorming-note-per-step` sigue `not_started` y sin código asociado.

## 2. Comandos re-ejecutados

| Comando | Resultado |
| --- | --- |
| `./gradlew :composeApp:assembleDebug --rerun-tasks` | **BUILD SUCCESSFUL** (54 tasks ejecutadas) |
| `./gradlew :core:testDebugUnitTest --rerun-tasks` | **BUILD SUCCESSFUL** — 23 suites / 55 tests / 0 failures / 0 errors / 0 skipped (`core/build/test-results/testDebugUnitTest/TEST-*.xml`) |
| `CI=true ./init.sh` | **exit 0** (Android-only, sin red ni dev servers) |
| `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` | **BUILD SUCCESSFUL** (iOS congelado, sólo compilación) |
| `pragma user_version` en la DB del emulador | **8** |
| `find core -name '*.sqm'` | `1.sqm`..`7.sqm` (sin `.sqm` nuevo) |
| `git status`/`git diff` en paths protegidos | vacío: `core/**`, build files, `gradle/**`, `init.sh`, `iosApp/**`, `GenrePicker.kt` |

## 3. Smoke Android independiente (emulator-5556, Pixel_3A_API_35)

Se instaló el APK de la rama, se hizo `pm clear` y se ejecutó un recorrido
propio con `adb` + `uiautomator` (no se confió sólo en la evidencia del
implementer).

| Escenario | Resultado observado |
| --- | --- |
| Entrada desde la lista | Botón "Brainstorming" visible; abre `Paso 1/9 · Título`, `Atrás` deshabilitado (`enabled=false`), barra en 1/9 |
| Entrada sin Proyecto | `SELECT count(*) FROM game_project` = **0** antes de guardar |
| Guardar vacío en paso 9 | Vuelve a la lista; DB `singleton|local|<null>...`, hijas en 0 |
| Título + navegación | Título escrito en paso 1 se conserva al recorrer los 9 pasos |
| Géneros | `JRPG` + `Platformer` seleccionados con `✓`; persistidos en `game_project_genre` |
| Inspiraciones (tope 3) | 3 filas, `3/3`, "Agregar inspiración" deshabilitado (`enabled=false`); persisten `Hollow`, `Celeste`, `Hades` en orden |
| Mecánicas (tope 5) | 5 filas, `5/5`, "Agregar mecánica" deshabilitado; filas vacías descartadas |
| Mecánica incompleta | Fila "Salto" sin categoría + "Guardar" → `AlertDialog` "Elegí una categoría para cada mecánica con descripción."; DB sin cambios (`title` seguía vacío, 0 mecánicas) |
| Ficha completa | Tras elegir categoría, DB con `ValidadorDemo` + géneros + 3 inspiraciones + 2 mecánicas (`movimiento|Dash`, `movimiento|Salto`) + 3 fuera de alcance (`Multijugador`, `Editor`, `VR`) |
| Reapertura | Precarga Título, `✓ JRPG`/`✓ Platformer`, `Hollow`/`Celeste`/`Hades`, `Dash`/`Salto` y `Multijugador`/`Editor`/`VR` |
| Cancelar | Se editó el Título y se canceló: DB byte-equivalente (`ValidadorDemo`, mismo `updated_at`) |
| Iteración/reemplazo | Título → `ValidadorIter`; se quitó `Celeste` y `Salto`; DB queda exactamente con `Hollow`/`Hades` y `Dash`; `owner`/`created_at` preservados, `updated_at` avanzó; **0 huérfanas** |
| Guardado desde un paso intermedio (8) | Persiste la ficha completa, no sólo el paso actual |
| Notas | Ninguna Nota ni Adjunto creado durante todo el recorrido |

La DB del emulador se restauró byte-idéntica al backup previo a la validación
(`sha256 7e95cb6c…`). Limitación: al iniciar, ese backup ya no contenía filas
del Proyecto del implementer (emulador compartido entre sesiones), por lo que
el smoke se re-ejecutó desde cero.

## 4. Revisión de controles negativos declarados

No se mutó código de producto (restricción de esta validación). Se revisó el
código y se ejercieron los caminos positivos equivalentes:

- (a) `save()` sólo llama a `onSave(draft)` desde "Guardar"; jamás desde
  "Cancelar"/"Atrás" (`BrainstormingWizardScreen.kt:121-151`, `:164-180`,
  `:212-236`). El guardado vacío y el guardado con datos crearon fila; Cancelar
  no la modificó.
- (b) El estado inicial lee `initialProject` (`:91-116`); reabrir mostró Título,
  Géneros, Inspiraciones y Mecánicas persistidos.
- (c) La validación de mecánica incompleta ocurre antes de construir el draft
  (`:121-128`); se reprodujo el diálogo y la DB no cambió.

## 5. Hallazgos

### Low — El encabezado no muestra `Paso X/9 · <paso>` ni queda centrado

`BrainstormingWizardScreen.kt:164-180` muestra `Paso X/9` a la derecha del
`Spacer` (no centrado entre Cancelar/Guardar) y `:193-197` muestra el nombre del
paso por separado en el contenido. La spec (`Job Story`/Layout) pedía
`Paso X/9 · <paso>` en la fila superior. Funcionalmente no afecta: el progreso y
el nombre se ven, y el smoke confirmó ambos. No bloqueante.

### Low — Evidencia del implementer sobre "5 Mecánicas" no coincide con el dump

`feature_list.json:963` declara un smoke con "5 Mecánicas", pero el artefacto
`composeApp/build/smoke-evidence/brainstorming-wizard/07-db.txt` sólo muestra
`game_project_key_mechanic` = `movimiento|Dash` y la captura `04` muestra `1/5`.
El propio arreglo `evidence` (`feature_list.json:975`) sí es consistente con
ese dump. En esta validación se verificó el tope 5/5 en UI y que las filas
vacías se descartan; no se persistieron 5 mecánicas completas en el smoke
independiente. No bloqueante, pero conviene aclarar la evidencia al persistir
`accepted`.

### Low — Emulador compartido

Al comenzar no había Proyecto en la DB (el backup previo estaba vacío), pese a
la evidencia `01`–`07`. Se re-ejecutó el smoke completo y se restauró el backup
byte-idéntico. Riesgo de coordinación entre sesiones, ya declarado por el
implementer; no es un defecto de la feature.

## 6. Seguridad

- El diff no introduce secrets, claves ni keystores.
- Sin red, sin `AiClient`, sin permisos nuevos ni acceso a datos fuera de la DB
  local y el repositorio existente.
- `local.properties` (ignorado por git) existe en el entorno local con una key;
  no está versionado ni forma parte del diff.
- Sin hallazgos de seguridad bloqueantes para esta feature.

## 7. Documentación y harness

- `ARCHITECTURE.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md` y `AGENTS.md` describen el wizard, el
  wiring y la separación de `brainstorming-note-per-step` de forma coherente con
  el diff.
- `feature_list.json`/`PROGRESS.md` (Session 056) registran `passing` y la
  evidencia; la única inconsistencia es la del hallazgo Low de "5 Mecánicas".
- `CONSTRAINTS.md` y `DESIGN.md` no requerían cambios y no se tocaron.

## 8. Veredicto

**accept.** La feature cumple el goal y los escenarios 1–14 dentro de las
limitaciones declaradas (sin E2E persistentes; smoke manual), respeta el alcance
y no introduce cambios en `:core`, esquema, build files, `init.sh`, `iosApp` ni
`GenrePicker`. Los hallazgos son Low y no bloquean la aceptación.
