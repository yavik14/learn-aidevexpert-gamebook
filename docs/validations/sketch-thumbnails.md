# Validación: sketch-thumbnails

- **Feature:** `sketch-thumbnails`
- **Spec:** `docs/specs/sketch-thumbnails.md`
- **Veredicto:** `accept` (re-validación independiente, 2026-10-19).
- **Estado previo:** `passing` (implementada y auto-verificada, sin commit).
- **Estado resultante:** `accepted` tras cerrar los dos hallazgos Low y rebasar
  sobre `origin/develop`.

## Contexto

La validación independiente devolvió `accept` con dos hallazgos Low accionables y
un tercero no bloqueante:

1. El recorte de la miniatura usaba 8 dp, pero `DESIGN.md` define
   `rounded.md = 16 dp` y el spec pide `rounded.md`.
2. En la degradación por archivo faltante o no decodificable, la tarjeta dejaba
   un `Spacer(8.dp)` aunque `NoteThumbnail` no emitiera imagen.
3. El guard de path traversal de iOS es léxico y no resuelve symlinks.

## Repairs cerrados

- `NoteThumbnail.kt`: `THUMBNAIL_CORNER = 16.dp`; el espaciado se emite **dentro**
  del composable, junto con la imagen (`Column { Image; Spacer(8.dp) }`), y no
  existe si la imagen falta o no decodifica.
- `NotesListScreen.kt` / `GddScreen.kt`: se quitaron los `Spacer` posteriores a
  `NoteThumbnail`; se preservan 72 dp, `ContentScale.Crop`, cuerpo/chips/Géneros/
  status y el chip "Imagen" del GDD.
- `docs/specs/sketch-thumbnails.md`: Technical Approach corregido a
  `RoundedCornerShape(16.dp)` + espaciado interno, sin cambiar escenarios,
  non-goals ni alcance.
- `ARCHITECTURE.md`, `docs/technical-discovery.md`, `PROGRESS.md` y
  `feature_list.json`: referencias actualizadas a 16 dp.
- `docs/risks-and-open-questions.md`: guard iOS léxico documentado como riesgo
  residual aceptado (rutas generadas por la app, single-user), sin cambiar el
  contrato de `read`/`delete`.

## Verificación de cierre (post-rebase)

Base: `origin/develop` `a0d556f` (incluye `ai-real-embeddings` e
`inline-dictation-30s`). Conflictos resueltos preservando ambas features y el
cierre de `sketch-thumbnails`.

- `./gradlew :core:testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL;
  **27 suites / 80 tests / 0 failures / 0 errors / 0 skipped**.
- `./gradlew :composeApp:assembleDebug --rerun-tasks` → BUILD SUCCESSFUL.
- `CI=true ./init.sh` → exit 0 (Android-only, sin red ni dev servers).
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 --rerun-tasks` →
  BUILD SUCCESSFUL.
- `./gradlew :core:iosSimulatorArm64Test --rerun-tasks` → BUILD SUCCESSFUL;
  **27 suites / 80 tests / 0 failures** (no bloqueante).
- Sin cambios en `*.sq`/`*.sqm` (`1.sqm`..`7.sqm`, esquema v8), `Attachment.sq`,
  build files, `gradle/libs.versions.toml`, `gradle.properties`, `init.sh` ni
  `iosApp/**`; sin dependencias nuevas ni secrets.

## Límites

- Smoke visual Android **no ejecutado**: `adb`/`emulator` no están disponibles en
  el entorno. No se fabricó evidencia; la cobertura es el test del store en
  `:core` (Android/JVM e iOS) + compilación de la app.
- El cacheo/redimensionado de miniaturas sigue diferido; se decodifica a tamaño
  completo.
- El `actual` iOS no se ejecuta en el gate (iOS congelado); sólo se exige que
  compile/linkee.
