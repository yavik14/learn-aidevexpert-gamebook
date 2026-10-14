# Validación independiente: `ios-release-pipeline-scaffolding`

- **Feature id:** `ios-release-pipeline-scaffolding`
- **Spec:** `docs/specs/ios-release-pipeline.md` (Scenarios 1, 2, 7, 8 verificables repo-side)
- **Fecha:** 2026-10-14
- **Rol:** validator (independiente del implementer)
- **Worktree:** `worktree/gamebook/ios-release-pipeline-scaffolding` (rama `feature/ios-release-pipeline-scaffolding`, base `develop` @ `0b4b1bc`; el contenido del feature ya está en `develop`, diff vs `develop` vacío)
- **Estado en `feature_list.json` al validar:** `passing` (implementada, auto-verificada)
- **Veredicto:** **accept**

## Alcance validado

Artefactos **repo-side** del pipeline de release de iOS (archive/export/upload sin
credenciales versionadas): `iosApp/Configuration/{Config.xcconfig,Local.xcconfig.example}`,
`iosApp/iosApp.xcodeproj/project.pbxproj` (bundle id estable, identidad de firma de
`Release`, versionado), `iosApp/iosApp/Info.plist` parametrizado, scheme compartido
`iosApp`, `scripts/ios-release/{preflight,archive,export-upload}.sh` +
`ExportOptions.plist.template`, `.gitignore`, runbook `docs/release/ios-testflight.md`,
`CONSTRAINTS.md`, `ARCHITECTURE.md` y `docs/technical-discovery.md`. El upload firmado
real (Scenarios 3–6) es `ios-release-pipeline-first-upload` (`blocked`, account-gated)
y **no** forma parte de esta validación.

## Checks re-ejecutados por el validador (no se confió en el resumen)

| Check | Comando | Resultado |
| --- | --- | --- |
| Gate estándar (Scenario 8) | `CI=true ./init.sh` | **exit 0**; Android `assembleDebug` + `:core:testDebugUnitTest` + link framework iOS + `:core:iosSimulatorArm64Test` BUILD SUCCESSFUL; sin dev servers/simuladores; **no** archivó ni subió; `git diff -- init.sh` vacío |
| Preflight sin credenciales (Scenario 1) | `env -u TEAM_ID -u ASC_KEY_ID -u ASC_ISSUER_ID bash scripts/ios-release/preflight.sh` | **exit 1**; `[OK]` macOS/Xcode/scheme/altool; `[FAIL] TEAM_ID no definido`; `[FAIL] Sin credenciales de firma`; no archivó ni subió |
| Archive sin `TEAM_ID` (fail-fast) | `env -u TEAM_ID bash scripts/ios-release/archive.sh --version 1.0 --build 1` | **exit 1**; `ERROR: falta TEAM_ID`; sin `.xcarchive` |
| Export/upload sin `TEAM_ID` | `env -u TEAM_ID bash scripts/ios-release/export-upload.sh` (con archive dummy) | **exit 1**; `ERROR: falta TEAM_ID` |
| Export/upload sin ASC keys | `env -u ASC_KEY_ID -u ASC_ISSUER_ID TEAM_ID=… bash scripts/ios-release/export-upload.sh` | **exit 1**; `ERROR: faltan ASC_KEY_ID/ASC_ISSUER_ID`; **no** renderizó `ExportOptions.plist` |
| Export/upload sin `.p8` | `env TEAM_ID=… ASC_KEY_ID=… ASC_ISSUER_ID=… bash scripts/ios-release/export-upload.sh` | **exit 1**; `ERROR: no existe la App Store Connect API key en …/AuthKey_<ID>.p8`; **no** renderizó `ExportOptions.plist` ni `.ipa` |
| Archive Release sin firma (Scenario 2) | `bash scripts/ios-release/archive.sh --unsigned --version 1.0 --build 1` | `** ARCHIVE SUCCEEDED **`; `build/ios/Playbook.xcarchive` creado |
| Contenido del archive | `plutil` / `file` sobre `Playbook.xcarchive` | app `CFBundleIdentifier=com.playbook.app`, `1.0`/`1`; binario `Mach-O 64-bit executable arm64`; `ComposeApp` presente en el binario (strings, 143 matches) |
| Build settings (firma/versionado) | `xcodebuild … -configuration Release -showBuildSettings MARKETING_VERSION=2.5 CURRENT_PROJECT_VERSION=42` | `PRODUCT_BUNDLE_IDENTIFIER=com.playbook.app` (sin team), `CODE_SIGN_IDENTITY=Apple Distribution`, `CODE_SIGN_STYLE=Automatic`, `MARKETING_VERSION=2.5`/`CURRENT_PROJECT_VERSION=42` (override aplicado); Debug → `Apple Development` |
| Seguridad (Scenario 7) | `find` de `.p8/.p12/.cer/.mobileprovision/.ipa/.xcarchive/ExportOptions.plist/Local.xcconfig/AuthKey_*/*.apiKey/*.jks/*.keystore`; `git check-ignore -v`; `git grep` de material de clave; `git status --porcelain` | sin artefactos de firma; sin secrets en archivos versionados; `git check-ignore` confirma las reglas; `git status` limpio |

Nota de entorno (honesta): el worktree no tiene `local.properties` (gitignored) y
`ANDROID_HOME` estaba vacío, por lo que el primer `./init.sh` abortó con "falta el
Android SDK" **por prerequisito de entorno**, no por un defecto de la feature. Se
re-ejecutó con `ANDROID_HOME=/Users/javierrodriguez/Library/Android/sdk` → exit 0.

## Escenarios de aceptación

1. **Scenario 1 — Preflight detecta prerequisitos:** verificado (exit 1, `[FAIL]`
   accionable por `TEAM_ID` y credenciales, `[OK]` para macOS/Xcode/scheme/altool; no
   archivó ni subió).
2. **Scenario 2 — Release archive compila sin firma:** verificado
   (`archive.sh --unsigned` → `** ARCHIVE SUCCEEDED **` + `.xcarchive` con bundle id
   estable y versiones 1.0/1; binario arm64 con `ComposeApp` enlazado).
3. **Scenario 7 — Seguridad de credenciales:** verificado (sin artefactos de firma y sin
   secrets versionados; `TEAM_ID=` vacío en `Config.xcconfig`; reglas de `.gitignore`
   confirmadas con `git check-ignore`).
4. **Scenario 8 — Gate honesto:** verificado (`./init.sh` exit 0, sin procesos de larga
   duración, sin archivar ni subir; `init.sh` sin cambios).

Los Scenarios 3–6 (archive firmado, export `.ipa`, upload a TestFlight, device real) no
son ejecutables sin Apple Developer Program + credenciales y pertenecen a
`ios-release-pipeline-first-upload` (`blocked`). **No se fabricó evidencia de
TestFlight.** La spec los declara account-gated y los separa del scaffolding.

## Scope discipline

- Sin cambios en `core/**`, `composeApp/src/**`, `composeApp/build.gradle.kts`,
  `gradle/libs.versions.toml` ni `init.sh` (diff vs `develop` vacío; `git diff -- init.sh`
  vacío).
- El diff es exclusivamente el pipeline de release iOS + docs durables; sin Android,
  sin App Store público, sin CI, sin `fastlane`/Ruby, sin cambios de producto/UI/core.
- El scheme compartido se versiona en `xcshareddata/xcschemes/iosApp.xcscheme`; no hay
  ningún otro scheme compartido.

## Seguridad (checklist scoped al diff)

- **Secretos:** ningún `.p8`/`.p12`/`.cer`/`.mobileprovision`/`.ipa`/`.xcarchive`/
  `ExportOptions.plist` renderizado/`Local.xcconfig`/`AuthKey_*`/`*.apiKey` versionado ni
  presente en el worktree; `git grep` sin material de clave privada; los archivos
  versionados son plantillas con placeholders (`TEAM_ID=` vacío, `__TEAM_ID__`).
- **Boundary:** el `TEAM_ID` real y la App Store Connect API key viven fuera del repo
  (`Local.xcconfig` gitignored o env; `.p8` en `~/.appstoreconnect/private_keys/`), y los
  scripts reciben las credenciales por entorno; nada se embebe en código versionado.
- **Fail-fast / no side effects:** `preflight.sh` no archiva; `archive.sh`/`export-upload.sh`
  fallan antes de producir artefactos cuando faltan prerequisitos (verificado: no se
  renderizó `ExportOptions.plist` ni `.ipa`). El `ExportOptions.plist` renderizado se
  escribe en `build/ios/` (gitignored).
- **`init.sh`:** no archiva/exporta/sube ni levanta procesos de larga duración.
- **No se encontraron hallazgos de seguridad.**

## Documentación durable

`CONSTRAINTS.md` (nuevo; MUST/MUST NOT de release y secretos), `ARCHITECTURE.md`
("Release / iOS distribution"), `docs/technical-discovery.md` ("Release Decisions"),
`docs/release/ios-testflight.md` (runbook con prerrequisitos, versionado, pipeline,
troubleshooting, evidencia y seguridad) y `AGENTS.md` (línea de próxima feature/estado)
actualizados y consistentes con el comportamiento implementado. `PROGRESS.md` y
`feature_list.json` reflejan el estado (`passing` scaffolding / `blocked` first-upload).

## Limitaciones declaradas honestamente

- **Scenarios 3–6 no ejecutados:** requieren Apple Developer Program + credenciales
  (account-gated); la feature queda partida y el upload es
  `ios-release-pipeline-first-upload` (`blocked`). Probado con `preflight.sh` → exit 1.
- **E2E persistente:** no aplica (sin harness E2E y `init.sh` no levanta simuladores);
  el E2E real de la feature es la instalación vía TestFlight (manual, account-gated),
  justificado en la spec.
- El archive sin firma se re-ejecutó; el archive firmado/export/upload **no** (requiere
  cuenta).

## Hallazgos

### Finding: `docs/technical-discovery.md` aún describe la feature original como `blocked`

- **Severity:** Low (no bloqueante)
- **Evidence:** `docs/technical-discovery.md` §"Release Decisions (ios-release-pipeline)"
  cierra con "La feature queda `blocked` (no `passing`) hasta ejecutar Scenarios 3–6".
  Tras el split, el scaffolding es `passing` y sólo `ios-release-pipeline-first-upload`
  está `blocked` (como ya reflejan `AGENTS.md`, `PROGRESS.md` y `feature_list.json`).
- **Why it matters:** un lector futuro podría creer que todo el pipeline está bloqueado;
  contradice el estado real del repo y la propia sección que dice "artefactos repo-side".
- **Required change:** no bloquea; al persistir la aceptación, ajustar la última frase
  para distinguir scaffolding (`passing`) de primer upload (`blocked`).
- **Verification after fix:** la sección distingue ambos estados y coincide con
  `feature_list.json`.

### Finding: `preflight.sh` acepta `iTMSTransporter` pero `export-upload.sh` hardcodea `altool`

- **Severity:** Low
- **Evidence:** `scripts/ios-release/preflight.sh:76-80` marca `[OK]` si existe
  `xcrun iTMSTransporter` (cuando no hay `altool`), mientras
  `scripts/ios-release/export-upload.sh:61` ejecuta siempre `xcrun altool --upload-app`.
- **Why it matters:** en un entorno sin `altool` el preflight pasaría pero la subida
  fallaría; hoy no se materializa porque la Xcode instalada (27.0) sí tiene `altool`, y
  el fallback `iTMSTransporter` está **declarado** en el runbook (no implementado), lo que
  la spec permite.
- **Required change:** no bloquea; al cerrar `ios-release-pipeline-first-upload`,
  alinear preflight/upload (implementar el fallback `iTMSTransporter` en
  `export-upload.sh` o degradar el preflight a `[WARN]`).
- **Verification after fix:** en un entorno sin `altool`, preflight y upload concuerdan en
  el estado final.

No hay hallazgos Critical/High/Medium.

## Veredicto final

**accept.** Los artefactos repo-side se ciñen a la spec y los Scenarios verificables sin
cuenta Apple (1, 2, 7, 8) se re-ejecutaron de verdad y pasan: `preflight.sh` falla con
`[FAIL]` accionable por prerequisito sin archivar; `archive.sh --unsigned` produce
`build/ios/Playbook.xcarchive` con bundle id estable `com.playbook.app`, versiones 1.0/1 y
binario arm64 con `ComposeApp` enlazado; `xcodebuild -showBuildSettings` confirma
`Apple Distribution` en `Release` (Debug `Apple Development`), bundle id sin sufijo de
team y overrides de versionado aplicados; no hay credenciales ni artefactos de firma
versionados y `.gitignore`/`CONSTRAINTS.md` cubren el contrato de secretos; `./init.sh`
sigue siendo un gate no bloqueante que no archiva ni sube. El alcance se respeta (sin
cambios en core/UI/build files/`init.sh`), la seguridad no arroja hallazgos y la
documentación durable está actualizada. Los Scenarios 3–6 quedan honestamente
account-gated en `ios-release-pipeline-first-upload` (`blocked`) sin evidencia fabricada.
Hallazgos Low no bloqueantes: statement stale en `docs/technical-discovery.md` y
inconsistencia menor preflight (`iTMSTransporter`) vs upload (`altool`). Recomendación:
promover `ios-release-pipeline-scaffolding` a `accepted` en `feature_list.json`.
