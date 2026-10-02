# Progress Log

## Current Verified State

- Repository root: `/Users/javierrodriguez/Alt10/course/ai/devexpert/learn-aidevexpert-gamebook`
- Standard startup path: `./init.sh`
- Standard verification path: bootstrapeada — `./init.sh` corre `:composeApp:assembleDebug`, `:core:testDebugUnitTest` y `:composeApp:linkDebugFrameworkIosSimulatorArm64` sin levantar dev servers.
- Current next ready feature: `kmp-project-bootstrap` (estado `passing`, pendiente de validación independiente). Después: `local-persistence-sqldelight` (bloqueada hasta que su dependencia quede `accepted`).
- Current blocker: none.
- Last verified at: 2026-10-01.

## Session Log

### Session 001

- Date: 2026-09-21
- Goal: Create the minimal startup harness.
- Completed: `AGENTS.md`, `init.sh`, `PROGRESS.md` and `feature_list.json` created.
- Verification run: JSON validation of `feature_list.json` and `chmod +x init.sh`.
- Evidence captured: deterministic resolution of first ready feature.
- Files or artifacts updated: `AGENTS.md`, `init.sh`, `PROGRESS.md`, `feature_list.json`.
- Known risk or unresolved issue: app not bootstrapped yet; feature-level verification commands were provisional until bootstrap.
- Next best step: run `$feature-spec` for `kmp-project-bootstrap` (technical bootstrap of the KMP + Compose Multiplatform project).

### Session 002

- Date: 2026-10-01
- Goal: Implement `kmp-project-bootstrap` (spec `docs/specs/kmp-project-bootstrap.md`).
- Completed:
  - Toolchain Gradle: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, wrapper 8.14.5, `local.properties` (no versionado).
  - `:core` KMP library (targets `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`) con `Greeting` + test trivial.
  - `:composeApp` Compose Multiplatform: `App()` placeholder en `commonMain`, `MainActivity` + `AndroidManifest.xml` + `themes.xml` en `androidMain`, `MainViewController` + framework `ComposeApp` en `iosMain`.
  - Host `iosApp` (Xcode, SwiftUI) enlazado al framework vía `:composeApp:embedAndSignAppleFrameworkForXcode`.
  - `init.sh` bootstrapeado (gate no bloqueante), `.gitignore`, `ARCHITECTURE.md` (nuevo), `AGENTS.md` y `docs/technical-discovery.md` actualizados.
- Verification run:
  - `./gradlew :composeApp:assembleDebug :core:testDebugUnitTest` → BUILD SUCCESSFUL (APK 10.0 MB, 1 test OK).
  - `./gradlew :core:iosSimulatorArm64Test` → 1 test OK, 0 failures.
  - `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → BUILD SUCCESSFUL.
  - `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator` → BUILD SUCCEEDED; `Playbook.app` instalada y lanzada en iPhone 15 (iOS 17.2).
  - App lanzada en Android (emulator-5554, Pixel_3A_API_34): MainActivity en foco, sin excepciones.
  - `./init.sh` → exit 0 sin dev servers; simulación sin JDK/Xcode → exit 1 con mensaje accionable.
- Evidence captured: ver arreglo `evidence` de `kmp-project-bootstrap` en `feature_list.json`; capturas de pantalla locales (Android/iOS).
- Files or artifacts updated: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/*`, `gradlew`, `gradlew.bat`, `core/**`, `composeApp/**`, `iosApp/**`, `init.sh`, `.gitignore`, `ARCHITECTURE.md`, `AGENTS.md`, `docs/technical-discovery.md`, `feature_list.json`, `PROGRESS.md`.
- Known risk or unresolved issue: el proyecto depende de macOS + Xcode para el target iOS (documentado). `local.properties` no se versiona, cada entorno debe fijar `sdk.dir` o `ANDROID_HOME`.
- Next best step: validación independiente de `kmp-project-bootstrap` con `$feature-validator`; tras `accept`, arrancar `local-persistence-sqldelight`.
