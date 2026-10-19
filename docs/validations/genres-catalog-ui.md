# Validación independiente: UI de Géneros (`genres-catalog-ui`)

- Fecha: 2026-10-19
- Validador: `feature-validator` (rol independiente del implementer)
- Feature: `genres-catalog-ui` — `feature_list.json` (status `passing`)
- Spec: `docs/specs/genres-catalog-ui.md`
- Rama/worktree: `feature/genres-catalog-ui` (validada en la base `6ba979f`;
  rebasada a `origin/develop` para el PR)
- Veredicto: **accept**
- Estado recomendado: `accepted` (lo persiste la sesión integradora)

## 1. Alcance validado

Picker de Géneros del catálogo fijo (`GenreCatalog.GENRES`, 12) + Género
personalizado en el editor, chip de lectura con variante catálogo/personalizado
en tarjetas, GDD y Notas relacionadas, y fix de layout del editor
(contenido scrollable + fila `Borrar/Cancelar/Guardar` fija, cuerpo
`heightIn(200..320 dp)`).

Sólo `:composeApp` + docs/evidencia. Sin cambios de esta feature en `:core` ni
SQLDelight; el esquema global del árbol rebasado es **v8** (`7.sqm` de
`game-project-persistence`). Sin cambios en `App.kt`, entry points, build files,
dependencias, `init.sh` ni `iosApp`.

## 2. Comandos re-ejecutados

| Comando | Resultado |
| --- | --- |
| `./gradlew :composeApp:assembleDebug --rerun-tasks` | **BUILD SUCCESSFUL** en 40s — 54 tasks ejecutadas |
| `./gradlew :core:testDebugUnitTest --rerun-tasks` | **BUILD SUCCESSFUL** — **22 suites / 53 tests / 0 failures / 0 errors / 0 skipped**; `AiPortsAndroidTest` 6/6 |
| `./init.sh` | **exit 0** — `assembleDebug` + `testDebugUnitTest`, Android-only, IA fake, sin dev servers ni red |
| `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` | **BUILD SUCCESSFUL** en 53s (iOS no bloqueante) |
| `PlaybookDatabase.Schema.version` | **7**; `.sqm` = `1.sqm`..`6.sqm` (sin altas) |
| `git diff --stat -- core composeApp/build.gradle.kts gradle/libs.versions.toml init.sh iosApp composeApp/src/commonMain/kotlin/com/playbook/app/App.kt composeApp/src/androidMain composeApp/src/iosMain` | **vacío** |
| `git diff --check` | **exit 0** |
| `git status --short` | sólo `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `docs/technical-discovery.md`, `docs/specs/genres-catalog-ui.md` y los archivos UI de la feature; sin artefactos extra |

## 3. Smoke independiente (emulator-5556)

Entorno: Pixel_3A_API_35, 1080x2220, 440 dpi, Android 15. `installDebug` +
`MainActivity`. No se agregaron archivos a
`composeApp/build/smoke-evidence/genres-catalog-ui/`; se inspeccionó la
evidencia existente (**74 archivos**, incluidos `29`–`41`) y se re-dirigió el
flujo crítico con UI dumps temporales.

- **3 Géneros (flujo pedido):** Nota temporal con `Platformer` + `JRPG` +
  `Puzzle`; al reabrirla, cuerpo `[66,385][1014,935]` (**550 px = 200 dp**) y
  `Borrar/Cancelar/Guardar` completos en `[2061,2116]`; luego se borró la Nota
  temporal.
- **12 catálogo + personalizado:** Nota con los 12 + `Zelda-like`; cuerpo 200 dp,
  acciones fijas visibles; scroll hasta catálogo, campo personalizado y
  "Ver relacionadas" sin mover la fila inferior.
- **Creación:** 12 chips en orden, sin "Seleccionados", cuerpo 200 dp, acciones
  completas; "Agregar" deshabilitado con campo vacío.
- **Toggle:** `Arcade` se quita y se re-agrega desde el catálogo, reflejándose en
  "Seleccionados" y en `✓`/borde.
- **Dedupe/canonización:** con `JRPG` ya seleccionado, `" jrpg "` + "Agregar"
  limpia el campo, no duplica y conserva `JRPG`.
- **Persistencia:** alta de `ValSmoke`, guardado, reapertura y `force-stop` +
  relanzar conservan el conjunto; se restauró luego a 12 + `Zelda-like`.
- **Remoción/alta:** quitar un Género de catálogo y guardar lo elimina de la DB;
  re-agregarlo lo devuelve; `updated_at` avanza.
- **GDD:** catálogo (`ActionRPG`/`Adventure`/`Arcade`/`Farming`) + personalizado
  (`Zelda-like`).
- **Notas relacionadas:** `JRPG` (catálogo) + `Zelda-like` (personalizado).
- **`jrpg` sembrado:** `note_genre` con `jrpg` → editor muestra `JRPG ×` y
  `✓ JRPG`, sin duplicado; DB restaurada a `JRPG`.

No hay harness E2E/UI persistente; la spec lo justifica. No se ejecutó runtime
iOS (sólo link no bloqueante) ni se probaron tamaños ≤320 dp/font scale grande.

## 4. Cierre del hallazgo High previo

El defecto previo (edición con cuerpo en una línea y fila de acciones recortada)
está **cerrado**:

- Contenido en `Column` con `Modifier.weight(1f).verticalScroll(...)`; la fila
  `Borrar/Cancelar/Guardar` queda fija fuera del scroll.
- Cuerpo con `heightIn(min = 200.dp, max = 320.dp)`.
- Medición independiente en edición con 3 y con 13 Géneros: cuerpo 550 px
  (200 dp) y acciones completas en `[2061,2116]`; scroll funcional.
- Sin regresión en creación: 12 chips en orden, cuerpo de 200 dp y acciones
  visibles. La evidencia previa `02-editor-empty.xml` mostraba 229 px (~83 dp)
  del layout anterior; `37-create-mode.xml` muestra 550 px (200 dp).
- El fix de Session 053 sólo tocó `NoteEditorScreen.kt` además de docs: el resto
  de archivos UI conserva mtime anterior y sus diffs no cambiaron en esta sesión.

## 5. Escenarios 1–9

1. **Picker muestra catálogo:** OK — 12 chips en orden, ninguno seleccionado y sin
   "Seleccionados" en creación (`02`, `37` + dump independiente).
2. **Toggle:** OK — quitar/re-agregar actualiza selección, `✓` y borde; chips de
   "Seleccionados" removibles en orden alfabético (`03`–`06`, `31`, `34` +
   verificación independiente).
3. **Personalizado y normalización:** OK — `jrpg` → `JRPG`; `Zelda-like`
   personalizado; campo limpio; "Agregar" deshabilitado con blancos (`07`–`10`,
   `37` + dumps independientes).
4. **Dedupe case-insensitive:** OK — `" jrpg "` con `JRPG` ya presente no cambia
   el conjunto ni duplica; campo limpio (dump independiente).
5. **Editar Nota existente:** OK — `jrpg` persistido se muestra `JRPG` en el
   editor y marca el chip; quitar/agregar/guardar persiste el conjunto exacto;
   `updated_at` avanza; la tarjeta refleja los cambios (`29`–`35`, `40`, `41` +
   verificación independiente con DB).
6. **Chips consistentes:** OK — editor, tarjeta, GDD y relacionadas muestran
   catálogo (`primaryContainer`) vs personalizado (`secondaryContainer`), con
   texto; GDD y relacionadas verificados también de forma independiente
   (`12`, `18`, `23`, `25`, `27`, `36`).
7. **Persistencia tras relanzar:** OK — force-stop + relanzar conserva lista y
   selección en el editor (`16`/`17`, `38`/`39` + verificación independiente).
8. **Accesibilidad y tamaño táctil:** OK — `clickable(onClickLabel = "Agregar
   género X"/"Quitar género X", role = Role.Button)`, `semantics { selected }`,
   `✓` + borde 2.dp, `defaultMinSize(minHeight = 48.dp)`; dumps muestran
   `checkable/checked` y bounds de 48 dp (`03`, `31` + dumps).
9. **Gate y alcance:** OK — build, 53/53 tests, `init.sh` exit 0, iOS link,
   esquema v7, sin `.sqm` nuevos, diff vacío en `:core`/`App.kt`/build
   files/deps y sin secrets.

## 6. Scope discipline

- Sólo `:composeApp` + docs; sin cambios en `:core`, esquema (v7), `App.kt`,
  `MainActivity`/`MainViewController`, build files, `gradle/libs.versions.toml`,
  `init.sh` ni `iosApp/**`.
- `GenrePicker.kt` es nuevo y `internal`; `NoteEditorScreen` delega en él y
  elimina el `GenreChip` privado; no queda "Nuevo género" ni el chip viejo
  (`grep` sin resultados).
- `LabelChip` conserva su comportamiento para `track`/`status`/adjuntos;
  `GenreLabelChip` se usa únicamente en las filas de Géneros.
- Sin dependencias nuevas, permisos nuevos, red nueva ni secrets.

## 7. Seguridad

Checklist feature-scoped:

- Sin secrets/API keys/tokens/keystores en el diff.
- Sin dependencias, permisos ni llamadas de red nuevas.
- Entrada de Géneros normalizada (`trim`, canonización, dedupe) y persistida con
  queries SQLDelight parametrizadas (sin cambios en SQL).
- Sin logs con datos de usuario ni exposición de datos fuera de la UI local.
- App single-user local; no hay cambios de autenticación/autorización.
- **Sin hallazgos de seguridad.**

## 8. Documentación durable y harness

- `feature_list.json`: `passing` con `verification`/`evidence` coherentes con las
  ejecuciones re-verificadas.
- `PROGRESS.md`: Sessions 052 y 053 documentan implementación, fix y evidencia;
  el riesgo de Session 052 queda cerrado por 053.
- `docs/technical-discovery.md`: sección “Géneros UI Decisions” con picker,
  variantes, accesibilidad y decisión de layout.
- `docs/specs/genres-catalog-ui.md`: conteo 53/53, tareas marcadas y paso 5 de
  smoke. El spec está untracked, por lo que no se puede diffear contra una
  versión commiteada; revisado el contenido, no se relajaron escenarios ni
  non-goals para acomodar la implementación.
- `AGENTS.md`: sólo la cola de próxima feature; el mtime corresponde a Session
  052 y no fue tocado en Session 053.
- `init.sh`: sin cambios y en verde.
- E2E: no existe harness persistente; la spec lo justifica y no se incorpora uno
  (non-goal). Cobertura por tests de `:core` + smoke manual.

## 9. Hallazgos y notas

No hay hallazgos Critical, High ni Medium. Notas no bloqueantes:

- **Low — casing legacy en lectura:** un `jrpg` sembrado/pre-v7 se muestra como
  `jrpg` en la tarjeta hasta abrir/guardar el editor; el editor sí normaliza a
  `JRPG` como exige el Scenario 5. Posible pulido futuro, no incumplimiento.
- **Low — ellipsis en filas con scroll horizontal:** `GenreLabelChip` define
  `maxLines`/ellipsis, pero las superficies de lectura lo envuelven en un
  `Row(horizontalScroll)`; etiquetas personalizadas extremadamente largas podrían
  no elidirse visualmente y quedar accesibles por scroll. El dato no se recorta y
  no hay fallo observado.
- **Low — cobertura de tamaños extremos:** verificado en el emulador de
  referencia y con teclado; no se probaron ≤320 dp ni font scale grande. El
  editor no migró a edge-to-edge, fuera de alcance y sin regresión observada.
- **Low — altura del cuerpo:** `heightIn(200..320 dp)` es un tradeoff explícito;
  el contenido scrollea, las acciones quedan fijas y la creación mejoró respecto
  del layout anterior. Aceptable para el MVP.

## 10. Handoff

- `./init.sh` sigue en verde; no se modificó ningún archivo del repo durante la
  validación.
- El estado de smoke del emulador se restauró: Nota con 12 Géneros + `Zelda-like`,
  Nota con `JRPG` + `Zelda-like`; el `jrpg` sembrado volvió a `JRPG`; la Nota
  temporal de 3 Géneros fue eliminada.
- La sesión integradora puede promover `genres-catalog-ui` a `accepted` y
  persistir este documento en `docs/validations/genres-catalog-ui.md`.

## 11. Nota de integración (rebase 2026-10-19)

La validación se realizó sobre la base `6ba979f`, antes de que `develop` integrara
`game-project-persistence`. Tras rebasar esta rama sobre `origin/develop`, el árbol
incluye el esquema **v8** (`7.sqm`, 23 suites / 55 tests). `genres-catalog-ui` no
modifica `:core` ni el esquema: su diff de feature es el mismo. Gate re-ejecutado
en la base rebasada:

- `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL (60 tasks).
- `./gradlew :core:testDebugUnitTest --rerun-tasks` → 23 suites / **55 tests / 0 failures / 0 errors**.
- `./init.sh` → exit 0; iOS link → BUILD SUCCESSFUL.
- `PlaybookDatabase.Schema.version == 8`; `.sqm` = `1.sqm`..`7.sqm` (el `7.sqm` es de `game-project-persistence`).

El veredicto `accept` se mantiene porque el diff de `genres-catalog-ui` es el
mismo y no toca `:core` ni el esquema.

**Veredicto: `accept`.**
