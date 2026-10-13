# Feature Implementation Spec: Distribución de iOS en TestFlight

## Source Feature

- `id`: `ios-release-pipeline-scaffolding` (dividido de `ios-release-pipeline`)
- `area`: `release`
- `depends_on`: `["create-text-note"]` (ya `accepted`)
- `status`: `passing` (scaffolding; el upload firmado es
  `ios-release-pipeline-first-upload`, `blocked`)
- `source`: `feature_list.json`

> Nota: esta spec cubre el pipeline completo. Los Scenarios 1, 2, 7 y 8 son el
> scaffolding verificado (`ios-release-pipeline-scaffolding`, `passing`); los
> Scenarios 3–6 (archive firmado, export `.ipa`, upload y device real) son el
> upload account-gated (`ios-release-pipeline-first-upload`, `blocked`).

## Goal

Habilitar un **pipeline de release de iOS repetible y seguro** que archive la app
en configuración `Release`, la firme y la distribuya a **TestFlight**, de modo
que un tester la instale desde la app TestFlight en un dispositivo real. Es el
objetivo de aprendizaje "lanzar a producción" del proyecto (`docs/build-brief.md`,
`docs/idea/concept-prompt.md`) y cubre el host Xcode `iosApp/`.

El entregable tiene dos capas: (1) artefactos versionados que hacen repetible el
archive/export/upload sin commitear credenciales (scripts, target settings,
versionado, runbook, `.gitignore`); y (2) la ejecución firmada del upload a
TestFlight, que **requiere una cuenta Apple Developer Program y credenciales de
firma**. Si esas credenciales no existen en el entorno, la feature **no puede**
satisfacer su `user_visible_behavior`: se registra como `blocked` (ver
"Bloqueo y evidencia honesta"), sin fabricar evidencia.

## Non-Goals

- Publicación pública en el App Store (sometimiento a revisión y release) — sólo
  TestFlight (interno primero; externo opcional).
- `android-release-pipeline` (Play internal testing) y cualquier cambio de firmado
  de Android.
- CI/CD: no se crea `.github/workflows` ni runners; el pipeline es local/manual y
  de invocación explícita.
- `fastlane`/`match`/CocoaPods o cualquier ecosistema Ruby nuevo como dependencia
  obligatoria (ver Technical Approach: sólo como fallback declarado).
- Cambios de producto, UI, `:core` o `:composeApp` Kotlin; sin features nuevas.
- App Store Connect metadata/URLs/privacidad/assets definitivos, App Icon final,
  notarización, TestFlight external review.
- Automatizar el bump de versión desde git tags (se pasa por parámetro).

## Job Story

When tengo el primer flujo de captura de texto funcionando,
I want archivar y subir un build de iOS a TestFlight e instalarlo en un
dispositivo de prueba,
so I can aprender el ciclo completo de firmado y distribución de iOS y validar la
app en hardware real.

## Users And Permissions

- Autor/desarrollador (owner de la cuenta Apple Developer Program): corre el
  pipeline, registra el App ID y los testers.
- Testers internos de TestFlight: instalan el build (sin Beta App Review).
- Sin auth en la app; los permisos relevantes son de firma/App Store Connect, no
  de la app.

## Acceptance Scenarios

### Scenario 1: Preflight detecta prerequisitos

Given el repo limpio,
When se corre `scripts/ios-release/preflight.sh` sin credenciales,
Then informa exactamente qué falta (macOS/Xcode, `TEAM_ID`, key de App Store
Connect, scheme) y termina con exit != 0, sin archivar ni subir nada.

### Scenario 2: Release archive compila (sin firma)

Given macOS + Xcode y el framework Kotlin,
When se corre `xcodebuild archive` en `Release` para `generic/platform=iOS` con
`CODE_SIGNING_ALLOWED=NO`,
Then el build termina en éxito y se produce `build/ios/Playbook.xcarchive`.
Esto valida que la app compila/enlaza en `Release` **sin** credenciales.

### Scenario 3: Archive firmado y export

Given cuenta Apple Developer Program, `TEAM_ID` y credenciales (identidad de
firma o key de App Store Connect),
When se corre el archive firmado + `-exportArchive` con método App Store,
Then se produce un `.ipa` firmado listo para subir, sin exponer secretos en el
repo.

### Scenario 4: Subida a TestFlight

Given el `.ipa` exportado y credenciales válidas,
When se corre la subida,
Then el build aparece en App Store Connect → TestFlight, procesa sin errores y
queda asignado a los testers internos.

### Scenario 5: Instalación en dispositivo real

Given un tester interno invitado,
When instala desde la app TestFlight en un dispositivo real,
Then la app Playbook se instala, abre y muestra la lista de notas.

### Scenario 6: Versionado único por subida

Given una subida previa,
When se corre el pipeline con un `CURRENT_PROJECT_VERSION` nuevo,
Then App Store Connect acepta el build como único (marketing version y build
number visibles), y reusar el mismo build number es rechazado con un error claro.

### Scenario 7: Seguridad de credenciales

Given el pipeline preparado,
When se revisa `git status`/`git diff` y los archivos versionados,
Then no hay `.p8`, `.p12`, `.cer`, `.mobileprovision`, keystores, `ExportOptions`
renderizado, `.ipa` ni `.xcarchive` commiteados; el `TEAM_ID` real vive fuera de
los archivos versionados.

### Scenario 8: Gate honesto

Given la feature,
When se corre `./init.sh`,
Then sigue terminando en 0 sin dev servers ni procesos de larga duración, y **no**
archiva ni sube nada (el release se invoca explícitamente, aparte del gate).

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `create-text-note` `accepted`
  (Sessions 011/013); regla durable "nunca commitear secrets/API keys/keystores";
  `init.sh` no bloqueante.
- `feature_list.json` — `ios-release-pipeline` (verificación provisional: "build
  archivado y subido a TestFlight; instalable en un dispositivo real"; nota:
  requiere cuenta de desarrollador Apple).
- `iosApp/iosApp.xcodeproj/project.pbxproj` — scheme/target `iosApp`,
  configuraciones `Debug`/`Release`; build phase "Compile Kotlin Framework"
  invoca `:composeApp:embedAndSignAppleFrameworkForXcode`; `SDKROOT = iphoneos`,
  `IPHONEOS_DEPLOYMENT_TARGET = 15.3`; `ENABLE_USER_SCRIPT_SANDBOXING = NO`;
  `OTHER_LDFLAGS = -lsqlite3`; `FRAMEWORK_SEARCH_PATHS` al framework de
  `composeApp/build/xcode-frameworks/...`.
- `iosApp/Configuration/Config.xcconfig` — `TEAM_ID=` **vacío**, `BUNDLE_ID=com.playbook.app`,
  `APP_NAME=Playbook`.
- `iosApp/iosApp/Info.plist` — `CFBundleShortVersionString` y `CFBundleVersion`
  **hardcodeados** (`1.0`/`1`).
- `composeApp/build.gradle.kts` — framework estático `ComposeApp` por target
  (`iosArm64`, `iosX64`, `iosSimulatorArm64`); habilita tareas `linkReleaseFrameworkIosArm64`.
- `init.sh` — gate no bloqueante: `assembleDebug`, tests del core (Android/JVM e
  iOS) y link del framework iOS de debug; sin dev servers ni simuladores.
- `.gitignore` — no cubre artefactos de firma/release (`.p8`, `.p12`,
  `.mobileprovision`, `.ipa`, `.xcarchive`, `ExportOptions.plist`).
- `docs/technical-discovery.md` — "Deployment and Operations: distribución iOS en
  TestFlight para ejercitar lanzar a producción"; sin plan de firma.
- `docs/build-brief.md`, `docs/idea/concept-prompt.md` — criterio de éxito de
  aprendizaje "aproximarse a lanzar a producción".
- `ARCHITECTURE.md`, `docs/risks-and-open-questions.md` — sin sección de release.
- Glob `**/*.xcscheme` → **sin scheme compartido**; `-scheme iosApp` funciona
  porque Xcode autocrea el scheme en user data (no versionado).

### Existing Patterns To Follow

- Gate `init.sh` no bloqueante: no archiva sube ni levanta simuladores/servidores.
- Sin dependencias nuevas sin justificación (ver decisión de no agregar
  `kotlinx-coroutines-core` en `create-text-note`); el pipeline evita un
  ecosistema Ruby si el CLI de Apple alcanza.
- Nada de credenciales versionadas (regla de `AGENTS.md`).
- Documentos en español, términos técnicos en inglés.
- Evidencia honesta, no fabricada (ver sesiones previas que retiraron claims).

### Current Gaps

- `TEAM_ID` vacío; sin identidad de firma ni key de App Store Connect.
- `PRODUCT_BUNDLE_IDENTIFIER = "${BUNDLE_ID}${TEAM_ID}"` (Debug y Release):
  concatenar el team id rompería el bundle id estable que debe registrarse.
- `CODE_SIGN_IDENTITY = "Apple Development"` también en `Release`: no es una
  identidad de distribución.
- Versiones hardcodeadas en `Info.plist`: TestFlight exige build number único.
- No hay scheme compartido para invocaciones CLI deterministas.
- No existen `scripts/`, ni `ExportOptions`, ni runbook, ni `.gitignore` de
  release.
- No hay cuenta/credenciales verificadas en el entorno (ver Bloqueo).

## Technical Approach

### Herramienta

Pipeline **shell + Xcode CLI** (sin `fastlane`), coherente con un repo cuyo único
tooling es `init.sh`. Estructura: `scripts/ios-release/`. La subida usa el CLI de
Apple disponible en la Xcode instalada (`xcrun altool --upload-app`, o
`xcrun iTMSTransporter`/Transporter si `altool` no está); el implementer debe
confirmar cuál existe y dejar el comando exacto en el runbook. `fastlane`
(`pilot`/`upload_to_testflight`) es el **fallback declarado** sólo si ningún CLI
sirve, y en ese caso debe registrarse como dependencia nueva (Gemfile), no
introducirse en silencio.

### Correcciones de firma e identidad (`project.pbxproj`, `Config.xcconfig`)

- `PRODUCT_BUNDLE_IDENTIFIER = "${BUNDLE_ID}"` (quitar `${TEAM_ID}`) en Debug y
  Release, para un bundle id estable `com.playbook.app` que se registra en el
  portal y en App Store Connect.
- `Release` → `CODE_SIGN_IDENTITY = "Apple Distribution"` (mantener
  `CODE_SIGN_STYLE = Automatic`); `Debug` conserva `Apple Development`.
- `Config.xcconfig` versionado con `TEAM_ID=` vacío y `#include? "Local.xcconfig"`;
  `Local.xcconfig` (gitignored) o la env `TEAM_ID` aportan el team real. El team
  id no es un secreto criptográfico, pero se mantiene fuera del repo por higiene.
- El implementer **no** debe commitear credenciales; sólo referencias/plantillas.

### Versionado

- Agregar `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` a los build settings y
  hacer que `Info.plist` use `$(MARKETING_VERSION)`/`$(CURRENT_PROJECT_VERSION)`.
- `archive.sh` recibe `VERSION` (marketing) y `BUILD_NUMBER`; los pasa como
  overrides de `xcodebuild`. Cada subida usa un `BUILD_NUMBER` nuevo (Scenario 6).

### Scripts

- `preflight.sh`: valida macOS/Xcode/scheme, `TEAM_ID`, presencia de credenciales
  (identidad `security find-identity -v -p codesigning` o env de la API key) y
  herramientas de subida. No archiva. Mensajes accionables, estilo `init.sh`.
- `archive.sh`:
  ```
  xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
    -destination 'generic/platform=iOS' -archivePath build/ios/Playbook.xcarchive \
    -allowProvisioningUpdates [auth key args] \
    MARKETING_VERSION="$VERSION" CURRENT_PROJECT_VERSION="$BUILD_NUMBER" archive
  ```
  Con `CODE_SIGNING_ALLOWED=NO` sirve como verificación local sin credenciales
  (Scenario 2); con credenciales produce el archive firmado (Scenario 3).
- `export-upload.sh`: renderiza `ExportOptions.plist` (método `app-store-connect`,
  `signingStyle automatic`, `teamID` desde env) en `build/ios/`, corre
  `xcodebuild -exportArchive` y sube el `.ipa` con el CLI de Apple usando la App
  Store Connect API key (`.p8` en `~/.appstoreconnect/private_keys/`, fuera del
  repo) vía `ASC_KEY_ID`/`ASC_ISSUER_ID`. Debe fallar con mensaje claro si falta algo.

### Secretos y `.gitignore`

- Ignorar: `*.p8`, `*.p12`, `*.cer`, `*.mobileprovision`, `*.ipa`, `*.xcarchive`,
  `iosApp/Configuration/Local.xcconfig`, `build/ios/**`,
  `**/ExportOptions.plist` (renderizado), `*.apiKey`, `AuthKey_*`.
- Las plantillas versionadas no contienen valores reales.

### `init.sh`, E2E y bloqueo

- `init.sh` **no cambia**: sigue no bloqueante y sin procesos de larga duración;
  no debe archivar ni subir. Los scripts de release se invocan explícitamente.
- No hay harness E2E persistente; la instalación en TestFlight sobre device real
  ES el E2E de esta feature (manual, account-gated).

### Bloqueo y evidencia honesta

Si en el entorno no hay Apple Developer Program ni credenciales, el implementer:
1. completa los artefactos repo-side y la verificación de Scenario 2
   (`CODE_SIGNING_ALLOWED=NO`) y Scenario 7 (seguridad) y Scenario 8 (gate);
2. registra la feature como `blocked` (no `passing`) en `feature_list.json` con
   el prerequisito faltante exacto y el comando de preflight que lo prueba;
3. **no** inventa evidencia de TestFlight. Si el usuario quiere aceptar el
   scaffolding repo-side como unidad de done independiente, se recomienda dividir
   en `feature_list.json` en "pipeline scaffolding" (verificable) y "primer
   upload a TestFlight" (account-gated), en vez de declarar `accepted` sin subida.

## Expected File Changes

- `iosApp/iosApp.xcodeproj/project.pbxproj` — modificar; bundle id estable, identidad
  de distribución en `Release`, `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION`.
- `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` — crear; scheme
  compartido para archive/export CLI deterministas.
- `iosApp/iosApp/Info.plist` — modificar; versiones vía build settings.
- `iosApp/Configuration/Config.xcconfig` — modificar; soporte `#include? Local.xcconfig`,
  `TEAM_ID=` vacío en el repo.
- `iosApp/Configuration/Local.xcconfig.example` — crear; plantilla sin valores reales.
- `scripts/ios-release/preflight.sh` — crear; chequeo de prerequisitos.
- `scripts/ios-release/archive.sh` — crear; archive Release (firmado y no firmado).
- `scripts/ios-release/export-upload.sh` — crear; export `.ipa` + subida a TestFlight.
- `scripts/ios-release/ExportOptions.plist.template` — crear; plantilla del export.
- `docs/release/ios-testflight.md` — crear; runbook (prerrequisitos, pasos, evidencia,
  troubleshooting, comando de subida exacto).
- `.gitignore` — modificar; artefactos de firma/release.
- `ARCHITECTURE.md` — modificar; sección "Release / iOS distribution".
- `CONSTRAINTS.md` — crear; reglas MUST/MUST NOT de release y secretos.
- `docs/technical-discovery.md` — modificar; decisiones de deployment/firma.
- `AGENTS.md` — modificar al cerrar; línea "Próxima feature en cola".
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado, evidencia,
  bloqueo si aplica.
- `init.sh` — **no** requiere cambios; explícitamente no ejecuta archive/upload.

No se esperan cambios en `core/**`, `composeApp/src/**`,
`composeApp/build.gradle.kts` ni `gradle/libs.versions.toml` (salvo que se adopte
`fastlane` como fallback, que requeriría `Gemfile`).

## Visual Design Impact

- UI involved: no.
- Design source: not applicable.
- Screens or states affected: ninguna; la app no cambia.
- New design artifact required: no — App Icon/metadata definitivos quedan fuera.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — nueva superficie de release (archive/export/upload,
  versionado y firma del host Xcode).
- `CONSTRAINTS.md`: **create** — reglas durables: no commitear credenciales de
  firma/App Store Connect; el pipeline de release no forma parte de `init.sh` y
  se invoca explícitamente; build number único por subida.
- `AGENTS.md`: **update** — cambia la línea "Próxima feature en cola"; el workflow
  y el gate no cambian.
- Other docs: `docs/release/ios-testflight.md` — create (runbook);
  `docs/technical-discovery.md` — update (Deployment and Operations);
  `feature_list.json`/`PROGRESS.md` — update al cerrar.

## Implementation Plan

1. Corregir `project.pbxproj` (bundle id estable, `Apple Distribution` en Release)
   y agregar `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION`; ajustar `Info.plist`.
2. Crear el scheme compartido `iosApp` y el soporte `#include? Local.xcconfig`
   con plantilla.
3. Crear `scripts/ios-release/` (`preflight`, `archive`, `export-upload`, plantilla
   de `ExportOptions`) y hacerlos ejecutables.
4. Actualizar `.gitignore` con artefactos de firma/release.
5. Escribir `docs/release/ios-testflight.md` (prerrequisitos, pasos, credenciales,
   evidencia, troubleshooting) y verificar el comando de subida real en la Xcode
   instalada.
6. Actualizar `ARCHITECTURE.md`, `CONSTRAINTS.md` y `docs/technical-discovery.md`.
7. Correr la verificación repo-side; si hay credenciales, ejecutar Scenario 3–4 y
   la instalación en device; si no, registrar `blocked`. Actualizar
   `feature_list.json`/`PROGRESS.md`/`AGENTS.md` y registrar evidencia.

## Implementation Tasks

- [x] Corregir `PRODUCT_BUNDLE_IDENTIFIER` y la identidad de firma de `Release`.
- [x] Agregar `MARKETING_VERSION`/`CURRENT_PROJECT_VERSION` y parametrizar `Info.plist`.
- [x] Crear el scheme compartido `iosApp`.
- [x] Agregar `#include? Local.xcconfig` + `Local.xcconfig.example`.
- [x] Crear `preflight.sh` con mensajes accionables.
- [x] Crear `archive.sh` (firmado y `CODE_SIGNING_ALLOWED=NO`).
- [x] Crear `export-upload.sh` + plantilla `ExportOptions.plist`.
- [x] Actualizar `.gitignore` (credenciales, `.ipa`, `.xcarchive`, `ExportOptions`).
- [x] Escribir el runbook `docs/release/ios-testflight.md`.
- [x] Actualizar `ARCHITECTURE.md`, crear `CONSTRAINTS.md`, actualizar
      `docs/technical-discovery.md`.
- [x] Correr Scenario 1, 2, 7 y 8 (verificados 2026-10-09); Scenarios 3–5 no
      ejecutables sin credenciales; registrar evidencia y estado `blocked`.
- [x] Actualizar `feature_list.json`, `PROGRESS.md` y `AGENTS.md` al cerrar.

## Verification Plan

- `./gradlew :composeApp:linkReleaseFrameworkIosArm64` → BUILD SUCCESSFUL (el
  framework compartido compila/enlaza en `Release`, sin firma).
- `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/ios/Playbook.xcarchive \
  CODE_SIGNING_ALLOWED=NO archive` → BUILD SUCCEEDED + `.xcarchive` creado
  (Scenario 2; verifica el pipeline sin credenciales).
- `scripts/ios-release/preflight.sh` sin credenciales → exit != 0 con prerequisito
  exacto; no archiva (Scenario 1).
- Con Apple Developer Program + credenciales: archive firmado + `-exportArchive`
  → `.ipa`; subida → build en TestFlight procesado; tester instala y abre en
  device real (Scenarios 3–5). Requiere cuenta y dispositivo físico.
- Versionado: subir con `CURRENT_PROJECT_VERSION` nuevo y confirmar que TestFlight
  lo acepta; reusar el anterior es rechazado (Scenario 6).
- Seguridad: `git status`/`git diff` sin `.p8`/`.p12`/`.mobileprovision`/`.ipa`/
  `.xcarchive`/`ExportOptions.plist`/`Local.xcconfig`; grep sin secretos en
  archivos versionados (Scenario 7).
- `./init.sh` → exit 0 sin dev servers ni simuladores; **no** archiva ni sube
  (Scenario 8).
- E2E persistente: **no aplica** — no existe harness E2E en el repo y `init.sh`
  no levanta simuladores. El E2E de esta feature es la instalación real vía
  TestFlight (manual, account-gated); la verificación local cubre build `Release`,
  archive sin firma, preflight y seguridad.

For `init.sh`, be explicit:

- `./init.sh` sigue ejecutando el gate no bloqueante actual y no cambia.
- `./init.sh` **no** debe archivar ni subir (lento y con red); no levanta procesos
  de larga duración.
- Puede imprimir los comandos manuales actuales; opcionalmente una línea con la
  ruta del runbook de release.

## Evidence To Capture

- Salida de `linkReleaseFrameworkIosArm64` y del `xcodebuild archive` con
  `CODE_SIGNING_ALLOWED=NO` (ruta del `.xcarchive`).
- Salida de `preflight.sh` sin credenciales (prerequisito exacto, exit code).
- Con cuenta: `.ipa` exportado, build en App Store Connect/TestFlight (número de
  versión/build), estado "processing → ready", y captura de la app instalada por
  TestFlight en un device real.
- `git status`/`git diff` mostrando que no se commiteó ningún artefacto de firma;
  entradas nuevas de `.gitignore`.
- Salida de `./init.sh` (exit 0, sin procesos de larga duración).
- Si aplica: línea de `blocked` con el prerequisito faltante (contrato Apple
  Developer Program, identidad de firma, key de App Store Connect o device),
  probado por preflight.

## Validator Checklist

- [ ] Alcance respetado: pipeline iOS/TestFlight; sin Android, sin App Store
      público, sin CI, sin cambios de producto/UI/core.
- [ ] Scenarios 1, 2, 7, 8 verificables sin cuenta Apple y pasan.
- [ ] Scenarios 3–6 requieren Apple Developer Program: si no hay credenciales, la
      feature está `blocked` con evidencia honesta y **no** `passing`/`accepted`.
- [ ] Bundle id estable (`com.playbook.app`, sin sufijo de team) y registrable en
      el portal; identidad `Apple Distribution` en `Release`.
- [ ] Versionado permite build number único por subida.
- [ ] No hay credenciales ni artefactos de firma commiteados; `.gitignore` actualizado.
- [ ] `init.sh` sin cambios de comportamiento, no archiva ni sube.
- [ ] `ARCHITECTURE.md`, `CONSTRAINTS.md`, `docs/technical-discovery.md`,
      `docs/release/ios-testflight.md` actualizados/creados.
- [ ] `feature_list.json` y `PROGRESS.md` reflejan estado y evidencia correctos.
- [ ] No hay E2E persistente y la spec lo justifica.
