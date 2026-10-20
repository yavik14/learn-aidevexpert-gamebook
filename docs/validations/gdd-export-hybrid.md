# Validación independiente — gdd-export-hybrid

- Fecha: 2026-10-20
- Rol: `feature-validator` (independiente del implementer)
- Feature: `gdd-export-hybrid`
- Spec: `docs/specs/gdd-export-hybrid.md`
- Base validada: `0cffd1e` en un worktree de validación **detached** (sin rama de
  feature), con la feature ya integrada en `ca2261c` (padre `dc74959`).
- Estado reclamado al iniciar: `passing`. Recomendación: **`accepted`**.
- Veredicto: **accept** (sin hallazgos Critical/High/Medium).

Nota de coordinación: durante la validación, la rama principal avanzó de
`0cffd1e` a `b935250` (`docs: acepta image-generation-from-text`). Ese commit
sólo toca `AGENTS.md`, `PROGRESS.md`, `docs/validations/image-generation-from-text.md`
y `feature_list.json`; el código es idéntico al validado. `ca2261c` sigue siendo
ancestro de la rama principal. No se tocó el worktree `worktree/save-answer-as-note`.

## Alcance revisado

- `core/src/commonMain/kotlin/com/playbook/core/gdd/GddMarkdown.kt` y su diff
  contra `ca2261c^`; `GddDocument.kt`/`GddProjection.kt` (sin cambios).
- `GddMarkdownHybridCheck.kt` (`verifyGddMarkdownHybrid`, escenarios 1–8) y los
  runners Android/JVM e iOS; `GddMarkdownCheck.kt` como regresión de Notas.
- `composeApp/src/commonMain/kotlin/com/playbook/app/GddScreen.kt`,
  `GddSharer*` (sin cambios) e `App.kt` (sin cambios).
- `ARCHITECTURE.md`, `docs/technical-discovery.md`, `AGENTS.md`, spec y estado
  durable. `CONSTRAINTS.md`/`CONTEXT.md`/`docs/domain-model.md`/`DESIGN.md` no
  requerían cambios (impacto visual mínimo, sin dirección nueva).
- Paths protegidos del diff de la feature: esquema/`.sqm`, build files,
  `App.kt`, `GddSharer*`, `init.sh`, `iosApp/**`, `AiRuntime.kt`,
  `GddDocument.kt`, `GddProjection.kt`, `GddMarkdownCheck.kt` → `git diff` vacío.

## Checks re-ejecutados

Todos con `ANDROID_HOME=$HOME/Library/Android/sdk` e IA fake
(`-Pplaybook.ai.mode=fake`), sin red ni API key.

| Check | Resultado |
|---|---|
| `:core:testDebugUnitTest --rerun-tasks` | BUILD SUCCESSFUL; **34 suites / 99 tests / 0 failures**; `GddMarkdownHybridAndroidTest` 1/1 y `GddMarkdownAndroidTest` 1/1 verdes. |
| `:core:iosSimulatorArm64Test --rerun-tasks` | BUILD SUCCESSFUL; **34 suites / 99 tests / 0 failures**; `GddMarkdownHybridIosTest` 1/1 verde. |
| `:composeApp:assembleDebug :composeApp:linkDebugFrameworkIosSimulatorArm64` | BUILD SUCCESSFUL (59 tareas). |
| `CI=true ./init.sh` | **exit 0**; gate Android-only, sin dev servers. |
| Esquema | `PlaybookDatabase.Schema.version == 8`; `.sqm` = `1.sqm`..`7.sqm`; sin `.sqm` nuevo. |
| Probe de regresión (temporal, eliminado) | Copia exacta de `buildGddMarkdown(notes, now)` de `ca2261c^` comparada byte a byte contra el overload nuevo y contra `buildGddMarkdown(null, …)` en 5 conjuntos de Notas × 3 valores de `now` → todas iguales. |

## Controles negativos

En el worktree de validación, restaurando byte-idéntico con `git checkout` y
verificando SHA-256:

- (a) Quitar el escapado de los valores de la ficha
  (`escapeMarkdown(singleLine(field.value))` → `singleLine(field.value)`):
  `GddMarkdownHybridAndroidTest` **FAILED** (`ComparisonFailure`).
- (b) Omitir por completo el bloque `## Proyecto` (`projectBlock = null`):
  `GddMarkdownHybridAndroidTest` **FAILED** (`ComparisonFailure`).
- Restaurado `GddMarkdown.kt` byte-idéntico
  (`sha256 9969bbbdf6b7d528cc3c8a5c0de36708a91e14d688e2b4baafc9a185e2be55c1`) y
  re-ejecutado el test enfocado en verde.

## Smoke Android del share

Emulador `emulator-5558` (API 35), APK debug del worktree de validación. La BD
(v8) se sembró por `sqlite3` vía `run-as` y se verificó la UI con
`uiautomator`/screenshots. Evidencia en
`composeApp/build/smoke-evidence/gdd-export-hybrid/` (ignorado por git).

1. **Proyecto completo sin Notas** → “Compartir” habilitado; el chooser muestra
   exactamente la cabecera + `Total: 0 notas` + `## Proyecto` con todos los
   campos/orden, mecánicas como sub-viñetas y `Zelda\-like` escapado, sin
   secciones de Notas (`01`, `02`).
2. **Proyecto completo + 2 Notas** (mecánicas y `Sin track`) → “Compartir”
   habilitado; el chooser reproduce **byte a byte** el documento canónico del
   contrato (ficha + secciones, sin duplicar Notas) (`03`).
3. **Proyecto vacío sin Notas** → “Compartir” no abre el chooser al tocarlo
   (tap sin efecto; nodo `clickable=false`); la pantalla muestra `GDD`,
   `0 notas`, “Proyecto sin datos adicionales” y “Todavía no hay notas” (`04`).
4. **Proyecto vacío + 1 Nota** → “Compartir” habilitado; chooser:
   `# GDD — Playbook\n\nTotal: 1 nota\n\n## Sin track\n\n### Protagonista sin nombre\nGéneros: lore — Estado: indexada\n` (`05`).
5. **Proyecto sólo con título, sin Notas** → “Compartir” habilitado; chooser:
   `# GDD — Sólo título\n\nTotal: 0 notas\n`, **sin** `## Proyecto` (`06`).

Limitaciones honestas del smoke: `project == null` real no es alcanzable por UI
porque el primer arranque exige crear un Proyecto (`FirstRunOnboardingScreen`);
se cubrió con el Proyecto vacío (misma salida `# GDD — Playbook`) y con los tests
del core. En el nodo de texto de un `TextButton` de Compose, `uiautomator` reporta
`enabled=true` aunque el botón esté deshabilitado; el estado se verificó por
`clickable=false` y tap sin efecto.

## Escenarios 1–10

1. Documento completo: test canónico + smoke #2.
2. Sin Proyecto / compatibilidad: tests + probe de regresión byte a byte.
3. Proyecto sin Notas: test + smoke #1.
4. Proyecto sin campos (sólo título): test + smoke #5.
5. Sin Proyecto ni Notas: test exacto; UI deshabilitada en smoke #3/#4.
6. Orden, listas y mecánicas: test + smoke #1/#2.
7. Escapado: test (título/listas/escalares/mecánicas; labels y secciones sin
   escapar) + smoke #1/#2.
8. Determinismo y no duplicación: test (dos llamadas, no mutación, conteo de
   Notas y orden de secciones).
9. UI Compartir/snackbar: inspección de `GddScreen` (llama
   `buildGddMarkdown(project, notes, currentTimestampLabel())` + `GddSharer.share`
   y mantiene los mensajes) + smoke; `App.kt`/`GddSharer` sin cambios.
10. Gate honesto: tests Android/iOS, `assembleDebug`, link iOS y
    `CI=true ./init.sh` verdes; esquema v8 sin `.sqm`/deps nuevas.

## Hallazgos

### Critical / High / Medium

Ninguno.

### Low (no bloqueantes)

- **Cobertura de escapado de la ficha.** El test híbrido no ejercita
  individualmente todos los caracteres del set (`{ } + ( ) [ ] \``) en valores de
  ficha; sí cubre `# * _ | . ! -` y delega en el `escapeMarkdown` compartido,
  cubierto parcialmente por los tests de Notas. Recomendación futura: ampliar el
  caso de escapado de `verifyGddMarkdownHybrid` con el set completo.
- **Semántica de “deshabilitado” en uiautomator.** El nodo de texto de un
  `TextButton` deshabilitado reporta `enabled=true`; el smoke lo compensó con
  `clickable=false` y tap sin efecto. Es una limitación de la herramienta, no del
  producto.
- **`project == null` no alcanzable por UI.** Consecuencia del onboarding de
  primer arranque, no de esta feature; cubierto por tests y por el Proyecto vacío
  (misma cabecera `Playbook`). La spec ya admitía salvedad honesta para el smoke.

## Seguridad

Sin hallazgos relevantes. El export es una función pura en memoria; no agrega
archivos, `FileProvider`, permisos, red, persistencia, dependencias ni cambios de
esquema. No hay secrets ni contenido de usuario en logs; el texto sólo se entrega
al share sheet del sistema. `local.properties` sigue fuera de git
(`.gitignore`) y no forma parte del diff de la feature.

## Documentación durable y handoff

- `ARCHITECTURE.md` y `docs/technical-discovery.md` describen el export híbrido,
  el overload compatible y la habilitación de “Compartir” de forma coherente con
  el código; `AGENTS.md` actualizó la cola de features.
- El ejemplo canónico de la spec quedó corregido (`Zelda\-like`), consistente con
  el set de escapado.
- `CONSTRAINTS.md`/`CONTEXT.md`/`docs/domain-model.md`/`DESIGN.md` no requerían
  cambios.
- Por instrucción de la corrida, este validador **no** modificó
  `feature_list.json` ni `PROGRESS.md`; la persistencia de `accepted` corresponde
  al orquestador.

## Veredicto

**accept.** El implementer cumplió la spec y los escenarios 1–10 con evidencia
reproducida de forma independiente (tests Android/JVM e iOS, builds, gate,
controles negativos, probe de compatibilidad y smoke Android del share). Estado
recomendado: `passing` → **`accepted`**. Sin Implementation Repair Brief.
