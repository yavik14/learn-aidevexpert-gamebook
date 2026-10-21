# Validación independiente: design-system-brand-header

- **Feature:** `design-system-brand-header` (`area: ui`)
- **Spec:** `docs/specs/design-system-brand-header.md`
- **Rama/worktree:** `feature/design-system-brand-header` · `worktree/design-system-brand-header` (base `origin/develop` `547b024`; rebasada sobre `9a3837b`)
- **Veredicto:** **`accept`**
- **Fecha:** 2026-10-21
- **Estado recomendado:** `accepted`

## Alcance validado

Feature combinada aprobada por el autor: theme claro Material 3 (`PlaybookTheme`,
paleta, tipografía OFL, shapes y tokens extra), cabecera de marca
(`PlaybookBrandHeader`) en onboarding/principal y secundarias, y auditoría visual
de pantallas/diálogos/chips/badges/snackbars/barras del sistema con edge-to-edge.
Sin dark mode, splash, logo nuevo ni cambios de producto/datos/IA/esquema.

## Checks re-ejecutados

- `CI=true ./init.sh` → exit 0, `BUILD SUCCESSFUL` (gate Android-only, IA fake, sin red).
- `./gradlew :composeApp:assembleDebug -Pplaybook.ai.mode=fake` → `BUILD SUCCESSFUL`.
- `./gradlew :composeApp:testDebugUnitTest :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks -Pplaybook.ai.mode=fake` → `BUILD SUCCESSFUL`; **6 suites / 15 tests / 0 failures** post-rebase (`5 suites / 14 tests` pre-rebase); `PlaybookContrastTest` **3/0**; link iOS OK (iOS congelado: sólo compila).
- Greps del spec: 0 `MaterialTheme {`; 0 `TopAppBar`; literales `Color(0x…)` sólo en `theme/Color.kt`; `fontSize`/`.sp` sólo en `theme/Type.kt`.
- Smoke Android en `emulator-5556` (IA fake): onboarding, principal + speed-dial/scrim, editor, GDD, Q&A, wizard y relacionadas; font scale 130 % y 200 % sin recortes; font scale restaurado a 1.0.
- Evidencia final: `30-onboarding-200-final.png`, `31-main-200-final.png`, `32-main-speeddial-200-final.png`, `33-gdd-200-final.png` (APK final, `lastUpdateTime=2026-10-21 11:20:55`).

### Re-verificación post-rebase

- Rebase sobre `origin/develop` `9a3837b` (incluye `spain-spanish-copy`), conflictos resueltos preservando ambos cambios (copy es-ES y design system).
- `CI=true ./init.sh` → exit 0; `assembleDebug` → OK; `testDebugUnitTest` + link iOS → OK, **6 suites / 15 tests / 0 failures** (`PlaybookContrastTest` 3/0).
- Greps post-rebase: 0 `MaterialTheme {`, 0 `TopAppBar`, 0 colores fuera de `theme/Color.kt`, 0 `fontSize/.sp` fuera de `theme/Type.kt`.
- Smoke mínimo post-rebase en `emulator-5556`: 3 acciones de cabecera y `Volver` con `clickable='true'`; scrim del speed-dial capturado; `font_scale` restaurado a 1.0. Evidencia en `composeApp/build/smoke-evidence/design-system-brand-header/postrebase/`.

## Verificación de hallazgos previos

| Hallazgo | Estado | Evidencia |
|---|---|---|
| HIGH — semántica de acciones/Back separada del nodo clickeable | **Resuelto** | `uiautomator dump` en `emulator-5556`: `content-desc='Ver GDD'`, `'Brainstorming'`, `'Preguntar'` y `'Volver'` con `class=android.widget.Button`, `clickable='true'`, bounds 132×132 px (~50 dp); sin nodo padre/etiqueta duplicada. |
| MEDIUM — `PROGRESS.md` contradecía el estado real | **Resuelto** | Estado refleja `passing` → `accepted`, cola 49/0/1/1 y `Last verified at: 2026-10-21`. |
| LOW — ratio AA error/blanco incorrecto | **Resuelto** | WCAG real 5.438:1 (documentado 5.4:1) en `DESIGN.md` y spec; sigue AA; `PlaybookContrastTest` 3/0. |
| LOW — precisión sobre fallback tipográfico | **Resuelto** | Docs: familias desde Compose resources; sólo `✦` (U+2726) usa fallback del sistema; `←` (U+2190) está en Bricolage. |
| LOW — evidencia de smoke frágil | **Resuelto** | Capturas intermedias `08`/`19` eliminadas; capturas finales 30–33 con APK final, serial explícito y timestamp corregido a `11:20:55`. |

## Nits Low y follow-up

- Nit documental corregido en el cierre: timestamp real `11:20:55` y glifo de
  fallback acotado a `✦`.
- **Follow-up fuera de alcance:** el FAB/speed-dial y `ReadAloudButton` conservan
  el patrón previo de semántica anidada (padre con `content-desc` no clickeable +
  hijo clickeable sin descripción). No bloquea esta feature; debe tratarse en una
  feature de accesibilidad posterior.

## Seguridad

Sin hallazgos relevantes: no hay secrets, API keys ni keystores en el diff; no
cambian datos, red, autenticación ni permisos. La única dependencia nueva es
`compose.components.resources`, usada para fuentes/icono locales versionados con
sus licencias OFL.

## Documentación y harness

`DESIGN.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md`, `docs/technical-discovery.md`,
`docs/risks-and-open-questions.md`, `AGENTS.md`, `PROGRESS.md` y
`feature_list.json` son coherentes con lo implementado. Sin cambios en `:core`,
SQLDelight/`.sqm`, IA, `init.sh`, `iosApp/**`, `libs.versions.toml`, launcher ni
`AndroidManifest.xml`.

## Conclusión

La implementación satisface el spec y los escenarios verificables, la evidencia
es reproducible y el working tree queda listo para integrarse. Se recomienda
persistir `accepted`.
