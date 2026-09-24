# Feature Implementation Spec: Bootstrap del proyecto KMP con Compose Multiplatform

## Source Feature

- `id`: `kmp-project-bootstrap`
- `area`: `setup`
- `depends_on`: `[]`
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Dejar el repositorio con un proyecto Kotlin Multiplatform ejecutable en Android e
iOS, con UI en Compose Multiplatform y un módulo de lógica compartida (`:core`)
separado de la app (`:composeApp`). Al terminar, la app arranca en ambas
plataformas mostrando una pantalla placeholder y la ruta estándar
`./init.sh` ejecuta checks no bloqueantes sobre el estado bootstrapeado.

Esta feature no agrega comportamiento de producto: solo habilita la base sobre la
que se construirán las demás features.

## Non-Goals

- Persistencia (SQLDelight), modelo de `Nota` o cualquier lógica de dominio.
- Captura por voz/imagen, IA, enlaces, GDD o RAG.
- Diseño visual formal (`DESIGN.md` sigue diferido); la pantalla placeholder no
  define estilo de producto.
- Publicación en stores o firma de release.
- Bootstrap de CI.

## Job Story

When empiezo el proyecto de Playbook,
I want una app KMP + Compose MP que compile y corra en Android e iOS con un core
compartido,
so I can construir las features del MVP sobre una base real y verificable.

## Users And Permissions

- Desarrollador (autor): único actor. Ejecuta comandos locales y lanza la app en
  emulador Android y simulador iOS.
- Sin usuarios finales ni permisos: no hay autenticación ni datos.

## Acceptance Scenarios

### Scenario 1: Android compila y arranca

Given el repo con el bootstrap aplicado,
When se ejecuta `./gradlew :composeApp:assembleDebug`,
Then el build termina con éxito y la app lanza en un emulador Android mostrando
la pantalla placeholder.

### Scenario 2: iOS compila y arranca

Given el repo con el bootstrap aplicado y una Mac con Xcode,
When se compila el target iOS del simulador y se corre la app,
Then compila sin errores y la app lanza en el simulador iOS mostrando la misma
pantalla placeholder.

### Scenario 3: Ruta de arranque honesta

Given el bootstrap aplicado,
When se ejecuta `./init.sh`,
Then corre checks no bloqueantes (compilación/test) y termina con código 0 sin
levantar procesos de larga duración (no inicia dev servers).

### Scenario 4: Error de entorno accionable

Given un entorno sin JDK o sin Xcode,
When se ejecuta `./init.sh`,
Then el script informa el requisito faltante con un mensaje accionable y no
aparenta éxito silencioso.

## Repository Research

### Files Inspected

- `AGENTS.md` — reglas de trabajo, flujo de arranque y definición de hecho.
- `PROGRESS.md` — estado verificado actual (pre-bootstrap) y próximo paso.
- `feature_list.json` — metadata de `kmp-project-bootstrap` y de las features
  dependientes (`local-persistence-sqldelight`, etc.).
- `CONTEXT.md` — lenguaje de dominio (no aplica código en esta feature).
- `docs/build-brief.md` — MVP como secuencia; confirma que el bootstrap es el
  primer slice.
- `docs/technical-discovery.md` — stack objetivo: KMP + Compose Multiplatform,
  SQLDelight, local-first, Android + iOS en paralelo.
- `docs/risks-and-open-questions.md` — Android+iOS en paralelo duplica trabajo y
  requiere macOS/Xcode.
- `docs/domain-model.md` — no aplica (sin dominio todavía).
- `docs/specs/` — no existía; se crea en esta corrida.
- Raíz del repo — confirmado: **no hay** `settings.gradle(.kts)`, `build.gradle(.kts)`,
  wrapper, `gradle/libs.versions.toml`, módulos ni proyecto Xcode.

### Existing Patterns To Follow

- Convención de ramas gitflow (`feature/*` desde `develop`) y commits descriptivos.
- Documentos en español con términos técnicos en inglés.
- `init.sh` como ruta estándar de arranque/verificación; checks no bloqueantes.

### Current Gaps

- No existe toolchain Gradle ni wrapper.
- No existen los módulos `:core` ni `:composeApp`.
- No existe proyecto Xcode para iOS.
- Se desconoce el JDK, Android SDK y Xcode disponibles en la máquina; hay que
  verificarlos y fijarlos.

## Technical Approach

Bootstrapear con estructura estándar de **Compose Multiplatform**:

- **Gradle**: `settings.gradle.kts` con `pluginManagement`/`dependencyResolutionManagement`,
  `build.gradle.kts` raíz con plugins declarados `apply false`, version catalog
  `gradle/libs.versions.toml`, wrapper Gradle, y `gradle.properties`.
- **Módulo `:core`** (KMP library, sin UI): Kotlin Multiplatform con targets
  `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`; por ahora vacío más
  un test trivial en `commonTest`.
- **Módulo `:composeApp`** (Compose MP app): depende de `:core`; `commonMain` con
  `App()` (pantalla placeholder), `androidMain` con `MainActivity` +
  `AndroidManifest.xml`, `iosMain` con `MainViewController`; framework estático
  `composeApp` para iOS.
- **`iosApp`**: proyecto Xcode que embebe el framework y arranca la UI compartida.
- **Versiones**: fijar en `libs.versions.toml` versiones compatibles entre sí
  (Kotlin 2.x, Compose Multiplatform 1.7+/1.8, AGP 8.x, Gradle 8.x). No inventar
  versiones: partir del template oficial de Compose Multiplatform y verificar
  compatibilidad; registrar las elegidas en la spec/PR.
- **`init.sh`**: actualizar a variante bootstrapeada: verificar JDK, correr
  compilación Android y compilación del target iOS; no iniciar dev servers;
  imprimir al final los comandos manuales (instalar/abrir en emulador/simulador).

Riesgo de versión: el mayor riesgo es el desalineo Kotlin/Compose/AGP. Mitigación:
usar versiones del template oficial y compilar temprano en Android antes de tocar iOS.

## Expected File Changes

Rutas **provisionales** (el proyecto no existe todavía; los nombres pueden ajustarse
al template oficial durante la implementación):

- `settings.gradle.kts` — crear; declarar módulos `:core`, `:composeApp`.
- `build.gradle.kts` — crear; plugins raíz `apply false`.
- `gradle.properties` — crear; flags de Kotlin/Android.
- `gradle/libs.versions.toml` — crear; versiones centralizadas.
- `gradle/wrapper/gradle-wrapper.properties`, `gradlew`, `gradlew.bat` — crear;
  wrapper reproducible.
- `core/build.gradle.kts` — crear; KMP library con targets Android/iOS.
- `core/src/commonMain/kotlin/...` — crear; placeholder de paquete del core.
- `core/src/commonTest/kotlin/...` — crear; test trivial.
- `composeApp/build.gradle.kts` — crear; Compose MP + dependencia `:core` + framework iOS.
- `composeApp/src/commonMain/kotlin/.../App.kt` — crear; `App()` placeholder.
- `composeApp/src/androidMain/kotlin/.../MainActivity.kt` — crear.
- `composeApp/src/androidMain/AndroidManifest.xml` — crear.
- `composeApp/src/iosMain/kotlin/.../MainViewController.kt` — crear.
- `iosApp/iosApp.xcodeproj/...`, `iosApp/iosApp/*.swift` — crear; host iOS.
- `.gitignore` — modificar; ignorar `build/`, `.gradle/`, `local.properties`, `xcuserdata/`, `*.xcworkspace` efímeros, `DerivedData/`.
- `init.sh` — modificar; variante bootstrapeada con checks no bloqueantes.
- `AGENTS.md` — modificar; actualizar la ruta de verificación (de pre-bootstrap a real).
- `ARCHITECTURE.md` — crear (mínimo); mapear módulos y dirección de dependencias.
- `docs/technical-discovery.md` — modificar; registrar versiones/structure elegidas si difieren del plan.

## Visual Design Impact

- UI involved: yes (pantalla placeholder).
- Design source: `DESIGN.md` **no aplica todavía** (diferido explícitamente en discovery).
- Screens or states affected: una sola pantalla placeholder sin estados.
- New design artifact required: no — es solo un placeholder sin decisiones de estilo.
- Planning gap: cuando se planifique la primera feature de UI real, `DESIGN.md`
  deberá crearse (ya registrado en `docs/risks-and-open-questions.md`).

## Durable Documentation Impact

- `ARCHITECTURE.md`: **create** (mínimo) — el bootstrap fija módulos (`:core`, `:composeApp`),
  superficies (Android/iOS) y dirección de dependencias (UI → core), que son decisiones durables.
- `CONSTRAINTS.md`: not needed — no surge aún una regla MUST/MUST NOT nueva; las
  reglas de trabajo viven en `AGENTS.md`.
- `AGENTS.md`: **update** — cambia la ruta de verificación (de pre-bootstrap a
  comandos reales de Gradle).
- Other docs: `docs/technical-discovery.md` — update si las versiones/estructura
  elegidas difieren del plan; `PROGRESS.md` y `feature_list.json` — update al
  cerrar la implementación (evidencia).

## Implementation Plan

1. Verificar toolchain del entorno (JDK, Android SDK, Xcode) y fijar versiones compatibles.
2. Crear el esqueleto Gradle (settings, root build, version catalog, wrapper, properties).
3. Crear `:core` (KMP library con targets Android/iOS) y un test trivial.
4. Crear `:composeApp` con `App()` placeholder, `MainActivity` (Android) y `MainViewController` (iOS).
5. Crear el host `iosApp` (Xcode) que embebe el framework.
6. Actualizar `init.sh` y `.gitignore`; crear `ARCHITECTURE.md`; actualizar `AGENTS.md`.
7. Compilar Android, compilar iOS y lanzar en emulador/simulador; registrar evidencia.

## Implementation Tasks

- [ ] Verificar `java -version`, Android SDK y `xcodebuild -version`; registrar versiones.
- [ ] Crear wrapper y esqueleto Gradle con version catalog.
- [ ] Crear módulo `:core` con targets `androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64`.
- [ ] Agregar un test trivial en `core/src/commonTest` que corra en el gate.
- [ ] Crear módulo `:composeApp` con `App()` placeholder en `commonMain`.
- [ ] Agregar `MainActivity` + `AndroidManifest.xml` en `androidMain`.
- [ ] Agregar `MainViewController` y configurar el framework iOS.
- [ ] Crear el proyecto `iosApp` enlazado al framework.
- [ ] Actualizar `init.sh` a variante bootstrapeada (checks no bloqueantes).
- [ ] Actualizar `.gitignore` (Gradle, `local.properties`, Xcode).
- [ ] Crear `ARCHITECTURE.md` mínimo y actualizar `AGENTS.md`.

## Verification Plan

- `./gradlew :composeApp:assembleDebug` → compila el APK Android (Resultado esperado: BUILD SUCCESSFUL).
- `./gradlew :core:allTests` (o el target de test equivalente del proyecto una vez creado) → test trivial en verde.
- `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64` → compila el framework iOS del simulador.
- Arranque manual: instalar/abrir en emulador Android y simulador iOS; verificar la pantalla placeholder en ambos.
- `./init.sh` → ejecuta los checks no bloqueantes y termina en 0; **no** inicia dev servers; imprime los comandos manuales.
- E2E: no aplica (no hay flujo de usuario/API todavía); la verificación es de build y arranque manual.
- `./init.sh`: debe ejecutar el gate estándar no bloqueante para el estado actual del repo, no debe levantar procesos de larga duración, y puede imprimir los comandos manuales tras pasar los checks.

## Evidence To Capture

- Salida de `./gradlew :composeApp:assembleDebug` (BUILD SUCCESSFUL).
- Salida de `./gradlew :core:allTests` (tests OK).
- Salida de `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`.
- Salida de `./init.sh` (exit 0) y registro de que no inicia dev servers.
- Captura de pantalla del placeholder en Android y iOS.
- Versiones de JDK/Android SDK/Xcode usadas, registradas en `PROGRESS.md` y `feature_list.json`.

## Validator Checklist

- [ ] La implementación se mantiene dentro del alcance del bootstrap (sin dominio, sin IA).
- [ ] Los escenarios de aceptación (Android, iOS, `init.sh`, error de entorno) pasan.
- [ ] La evidencia de verificación está presente en `feature_list.json` / `PROGRESS.md`.
- [ ] No aplica cobertura E2E persistente (no hay flujo observable aún); el spec lo justifica.
- [ ] `feature_list.json` y `PROGRESS.md` se actualizaron correctamente.
- [ ] No se agregó comportamiento de producto ni trabajo de features vecinas.
