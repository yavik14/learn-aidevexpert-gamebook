# Feature Implementation Spec: Publicación de Android en Play internal testing

## Source Feature

- `id`: `android-release-pipeline`
- `area`: `release`
- `depends_on`: `["create-text-note"]` (ya `accepted`)
- `status`: `not_started` (al momento de planificar)
- `source`: `feature_list.json`

## Goal

Habilitar la **publicación de un build de Android firmado hacia un canal de
pruebas internas** como primer paso real de "lanzar a producción" del MVP. La
feature agrega la **configuración de release/signing** que hoy no existe en
`:composeApp`, produce los artefactos de distribución (**APK release instalable y
AAB para Play**) con un **keystore fuera del repositorio**, y documenta el
procedimiento manual de subida a Play Console (internal testing).

El diseño respeta una restricción dura: **nunca se commitean keystores ni
secrets**, `./init.sh` sigue siendo un gate no bloqueante sin secretos y sin
procesos de larga duración, y el repo no depende de una cuenta real de Play
Console. Por eso la spec separa explícitamente **lo verificable localmente**
(config de signing, build firmado, verificación de firma, ausencia de secretos en
VCS, `init.sh` verde) de **lo que requiere Play Console** (crear la app, subir el
AAB al track internal testing, gestionar testers e instalar desde el canal), que
queda documentado como paso manual y **no** forma parte de la verificación
automatizada.

## Non-Goals

- **iOS/TestFlight**: es `ios-release-pipeline` (feature separada). No se toca
  `iosApp/**` ni el framework iOS.
- **Automatización de la subida a Play**: no se agrega `gradle-play-publisher`,
  `fastlane` ni ningún plugin que requiera un service-account JSON (secreto) o
  red. La subida a Play Console es manual y se documenta; su ejecución depende de
  credenciales externas del autor.
- **CI/CD**: no se configura pipeline de CI (el repo no tiene uno). El soporte de
  variables de entorno deja la puerta abierta, pero montar CI queda fuera.
- **Track producción / rollout**: sólo se documenta internal testing; no hay
  closed/open testing ni producción.
- **R8/ProGuard, minificación, resource shrinking u ofuscación**: se mantiene
  `isMinifyEnabled = false` para no introducir riesgo de romper Compose en
  release. Optimizaciones de tamaño son trabajo posterior.
- **Play App Signing enrollment/reset, store listing, iconos, screenshots,
  políticas de la tienda o release notes**: gestión manual en Play Console.
- **Cambios de comportamiento de producto, UI, esquema (`schema.version` sigue en
  1, sin `.sqm`), `:core` o lógica de negocio.**
- **Firma de debug**: se reutiliza la firma debug por defecto; esta feature sólo
  agrega la firma de release.

## Job Story

When quiero que otras personas prueben Playbook en un teléfono real,
I want construir y subir un AAB firmado con un keystore que no vive en el repo,
so I can ejercer el flujo real de "lanzar a producción" en Play internal testing
sin filtrar credenciales ni depender de una cuenta de Play para verificar el
build.

## Users And Permissions

- Autor/desarrollador (único actor): corre Gradle localmente, genera y custodia
  el keystore, y ejecuta la subida manual a Play Console. Sin usuarios finales
  en esta feature.
- El keystore de release es la **upload key** del desarrollador (Play App Signing
  gestiona la app signing key). Vive fuera del repo (`keystore.properties` local
  gitignored o variables de entorno).
- Sin auth en la app (MVP single-user local-first); la única credencial en juego
  es la de firma, que **nunca** se versiona.

## Acceptance Scenarios

### Scenario 1: Release build sin secretos (modo degradado)

Given un checkout limpio sin `keystore.properties` ni variables de entorno de
firma,
When se ejecuta `./gradlew :composeApp:assembleRelease`,
Then el build termina en `BUILD SUCCESSFUL` y produce un APK release **sin
firmar** (no falla por falta de keystore ni pide secrets).

### Scenario 2: Firma de release con `keystore.properties` (local, gitignored)

Given un keystore local y un `keystore.properties` en la raíz con
`storeFile`/`storePassword`/`keyAlias`/`keyPassword`,
When se ejecuta `./gradlew :composeApp:assembleRelease` (o `bundleRelease`),
Then se produce un artefacto **firmado** con ese keystore y
`apksigner verify --print-certs` confirma la firma con el certificado del
keystore (no el de debug).

### Scenario 3: Firma de release vía variables de entorno (sin archivo)

Given las mismas credenciales expresadas sólo como variables de entorno
(`PLAYBOOK_KEYSTORE_FILE`, `PLAYBOOK_KEYSTORE_PASSWORD`, `PLAYBOOK_KEY_ALIAS`,
`PLAYBOOK_KEY_PASSWORD`) y sin `keystore.properties`,
When se ejecuta el build de release,
Then se obtiene el mismo resultado firmado que en el Scenario 2, sin escribir
ningún secret en el repo.

### Scenario 4: AAB listo para Play e instalable localmente

Given el signing configurado (Scenario 2/3),
When se ejecuta `./gradlew :composeApp:bundleRelease`,
Then se genera `composeApp-release.aab` (artefacto de subida a Play) firmado y
verificable (`jarsigner -verify`); el APK release firmado también puede
instalarse en un emulador/dispositivo (`adb install`).

### Scenario 5: Secretos fuera del control de versiones

Given un keystore y un `keystore.properties` locales,
When se corre `git status` / `git check-ignore`,
Then `keystore.properties` y los archivos `*.jks`/`*.keystore` aparecen
ignorados y **no** figuran como archivos a commitear; el repo no contiene
secrets.

### Scenario 6: Subida a internal testing (manual, fuera del gate)

Given el AAB firmado del Scenario 4 y una cuenta de Play Console (paso manual),
When se crea la app, se sube el AAB al track internal testing y se agregan
testers,
Then el build queda disponible en el canal y es instalable desde Play. Este paso
requiere credenciales externas y **no** se puede verificar dentro del repo; se
registra como evidencia manual (o se declara honestamente como no ejecutado si
no hay cuenta).

### Scenario 7: Gate honesto

Given la feature implementada,
When se ejecuta `./init.sh`,
Then sigue terminando en 0, **sin** secrets, sin tareas de release y sin
procesos de larga duración; los tests del core siguen verdes y `assembleDebug`
compila.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` — flujo SDD/WIP=1; `create-text-note` `accepted`
  (Session 013); regla "Nunca commitear secrets, API keys ni keystores";
  `init.sh` debe seguir no bloqueante.
- `feature_list.json` — metadata de `android-release-pipeline`; verificación
  provisional ("build firmado publicado en Play internal testing e instalable
  desde el canal"); nota "No commitear keystores ni secrets".
- `docs/technical-discovery.md` — "Deployment and Operations: Distribución en
  Android (Play / internal testing) e iOS (TestFlight) para ejercitar 'lanzar a
  producción'"; "nunca commitear keys"; `init.sh` sin simuladores.
- `docs/build-brief.md`, `docs/risks-and-open-questions.md` — criterio de éxito
  "Aproximarse a 'lanzar a producción' (distribución en Android/iOS)".
- `composeApp/build.gradle.kts` — `android { }` con `namespace`,
  `applicationId com.playbook.app`, `versionCode = 1`, `versionName = "1.0"`,
  `buildTypes { release { isMinifyEnabled = false } }`; **sin**
  `signingConfigs` ni signing en `release`.
- `gradle/libs.versions.toml`, `build.gradle.kts`, `settings.gradle.kts` —
  versiones centralizadas; plugins AGP 8.10.1 / Kotlin 2.1.21; módulos `:core` y
  `:composeApp`.
- `gradle.properties` — `android.useAndroidX=true`,
  `android.nonTransitiveRClass=true`; sin propiedades de firma.
- `.gitignore` — ignora `local.properties`, `*.apk`, `*.aab`, `build/`; **no**
  ignora `*.jks`/`*.keystore`/`keystore.properties`.
- `init.sh` — gate: `assembleDebug` + `testDebugUnitTest` + link framework iOS +
  `iosSimulatorArm64Test`; no levanta dev servers ni simuladores.
- `composeApp/src/androidMain/AndroidManifest.xml`,
  `.../MainActivity.kt` — app Android; no requieren cambios para release.
- `docs/specs/create-text-note.md` — dependencia `accepted`; estilo de spec y
  justificación de no-E2E.
- Búsquedas: no existe ningún `signingConfig`/`keystore` en el repo; no hay
  `docs/adr/`; no existe `docs/release/`.

### Existing Patterns To Follow

- Build/config centralizados en Gradle Kotlin DSL; versiones en
  `gradle/libs.versions.toml` (no se agregan dependencias).
- `init.sh` no bloqueante, sin secrets ni procesos de larga duración.
- Documentos en español, términos técnicos en inglés.
- Evidencia verificable y honesta; no fabricar evidencia de sistemas externos
  (`create-text-note` declara el límite de iOS).
- Sin DI/artefactos nuevos innecesarios; cambios acotados al alcance.

### Current Gaps

- No hay `signingConfigs` ni firma de release: `assembleRelease`/`bundleRelease`
  no están configurados ni verificados.
- No hay `keystore.properties`, plantilla de ejemplo, ni documentación de
  generación/custodia del keystore.
- `.gitignore` no protege keystores ni `keystore.properties`.
- No existe un runbook de release ni la distinción local vs. Play Console.
- `versionCode`/`versionName` fijos en el build script (subir un nuevo AAB exige
  editar el archivo).

## Technical Approach

### Configuración de signing en `composeApp/build.gradle.kts`

Cargar las credenciales con **precedencia variables de entorno > archivo**, sin
hardcodear nada ni fallar si no existen:

- Archivo: `keystore.properties` en la raíz del repo (gitignored), con las claves
  `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
- Variables de entorno: `PLAYBOOK_KEYSTORE_FILE`, `PLAYBOOK_KEYSTORE_PASSWORD`,
  `PLAYBOOK_KEY_ALIAS`, `PLAYBOOK_KEY_PASSWORD`.
- `storeFile` puede ser absoluto o relativo a la raíz (`rootProject.file(...)`).
- Sólo si las **cuatro** credenciales están presentes se registra
  `signingConfigs { create("release") { ... } }` y se asigna
  `signingConfig = signingConfigs.getByName("release")` al build type `release`.
- Si falta alguna, **no** se registra el signing config: `assembleRelease`
  produce el APK release **sin firmar** (modo degradado) y no falla. Esto es lo
  que permite que `init.sh` y cualquier entorno sin credenciales sigan funcionando
  (Scenario 1).

Esquema de referencia (el implementer ajusta detalles de DSL):

```kotlin
import java.util.Properties

val keystoreProperties = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun releaseSigningValue(fileKey: String, envKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: keystoreProperties.getProperty(fileKey)?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseSigningValue("storeFile", "PLAYBOOK_KEYSTORE_FILE")
val releaseStorePassword = releaseSigningValue("storePassword", "PLAYBOOK_KEYSTORE_PASSWORD")
val releaseKeyAlias = releaseSigningValue("keyAlias", "PLAYBOOK_KEY_ALIAS")
val releaseKeyPassword = releaseSigningValue("keyPassword", "PLAYBOOK_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
    .all { it != null }
```

Dentro de `android { }`: registrar el signing config sólo si
`hasReleaseSigning`, y en `buildTypes { getByName("release") { ... } }` mantener
`isMinifyEnabled = false` y asignar el signing config cuando exista.

### Versionado

Permitir override sin editar el script, con defaults actuales:

- `versionCode = (findProperty("playbook.versionCode") as String?)?.toIntOrNull() ?: 1`
- `versionName = (findProperty("playbook.versionName") as String?) ?: "1.0"`

Así cada subida a internal testing puede cortar un `versionCode` nuevo con
`-Pplaybook.versionCode=N` (Play exige incrementarlo por subida). No cambia el
default observable.

### Artefactos y verificación local

- **APK release** (`assembleRelease`): instalable con `adb install`; sirve para
  smoke local. Sin signing → nombre `composeApp-release-unsigned.apk`; con signing
  → `composeApp-release.apk`.
- **AAB** (`bundleRelease`): artefacto de subida a Play, en
  `composeApp/build/outputs/bundle/release/`.
- **Verificación de firma**: `apksigner verify --print-certs <apk>` (build-tools
  del Android SDK, ya requerido por `init.sh`) para el APK; `jarsigner -verify`
  (JDK) para el AAB, que usa firma JAR.
- Para la verificación local se genera un **keystore descartable** en un
  directorio ignorado (`composeApp/build/verify-release/` o `/tmp`), se exporta
  por variables de entorno, se corre el build firmado y se borra. **Nunca** se
  commitea ni se deja un keystore de producción en el repo.

### `keystore.properties`, `.gitignore` y plantilla

- `.gitignore`: agregar `keystore.properties`, `*.jks`, `*.keystore`.
- `keystore.properties.example` (versionado, **sin secrets**, sólo placeholders)
  como plantilla referenciada por el runbook.

### Documentación de release (`docs/release/android.md`, nuevo)

Runbook con: generación del keystore con `keytool`; plantilla
`keystore.properties` y variables de entorno; comandos `assembleRelease` /
`bundleRelease`; verificación de firma; gestión Play App Signing (upload key vs
app signing key); y el procedimiento manual de Play Console (crear app, track
internal testing, subir AAB, testers, instalar). Deja explícito qué se verifica
localmente y qué queda manual/externo.

### Alcance verificable localmente vs. Play Console

| Área | Verificable en el repo | Requiere Play Console (manual) |
| --- | --- | --- |
| Signing config + precedencia env/archivo | Sí (build firmado) | — |
| APK release firmado/instalable | Sí (`apksigner`, `adb install`) | — |
| AAB firmado y verificable | Sí (`jarsigner -verify`) | — |
| Sin secrets en VCS | Sí (`git check-ignore`) | — |
| Subida al track internal testing | No | **Sí** (cuenta + app + testers) |
| Instalación desde el canal | No | **Sí** (link de testers) |

### `init.sh`

**Sin cambios.** Sigue siendo el gate no bloqueante de debug
(`assembleDebug` + tests del core + link iOS), **no** corre tareas de release,
**no** requiere keystore ni secrets y **no** levanta procesos de larga duración.
La verificación de release se ejecuta a mano con los comandos del runbook; no es
parte del gate estándar (decisión explícita para no acoplar el arranque a
credenciales).

### Riesgos y decisiones

- **Límite honesto de "internal testing"**: la subida real no es verificable sin
  Play Console; la feature se acepta con evidencia local (build firmado + AAB
  listo + sin secrets) y el paso manual documentado. No se puede fabricar
  evidencia de la subida.
- **`apksigner`**: vive en `$ANDROID_HOME/build-tools/<versión>/`. Si faltara,
  instalar build-tools; el AAB se verifica igual con `jarsigner` (JDK).
- **Play App Signing**: perder la upload key exige reset en Play Console;
  documentar respaldo seguro del keystore (fuera del repo).
- **`storeFile` relativo**: resolver siempre contra `rootProject` para evitar
  ambigüedad.
- **Sin R8**: se mantiene `isMinifyEnabled = false` para no romper recursos/Compose
  en release; el tamaño no es objetivo de esta feature.

## Expected File Changes

- `composeApp/build.gradle.kts` — modificar; `signingConfigs`/`signingConfig` de
  release con precedencia env/archivo, `hasReleaseSigning` y override de
  `versionCode`/`versionName`.
- `.gitignore` — modificar; ignorar `keystore.properties`, `*.jks`, `*.keystore`.
- `keystore.properties.example` — crear; plantilla sin secrets.
- `docs/release/android.md` — crear; runbook de release y upload manual.
- `ARCHITECTURE.md` — modificar; sección de release/build y signing.
- `docs/technical-discovery.md` — modificar; "Deployment Decisions
  (android-release-pipeline)".
- `AGENTS.md` — modificar; línea "Próxima feature en cola" y referencia al
  runbook/secrets.
- `feature_list.json`, `PROGRESS.md` — modificar al cerrar; estado y evidencia.

No se esperan cambios en `core/**`, `gradle/libs.versions.toml`, `iosApp/**`,
`init.sh`, `Note.sq` ni en la app/UI. No se versiona ningún keystore ni
`keystore.properties`.

## Visual Design Impact

- UI involved: no.
- Design source: not applicable.
- Screens or states affected: ninguna.
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md`: **update** — aparece la superficie de release Android
  (build types, signing con keystore externo, artefactos APK/AAB) y la regla de
  que el keystore/secrets viven fuera del repo.
- `CONSTRAINTS.md`: not needed — no se crea el archivo; la regla "nunca
  commitear keystores/secrets" ya vive en `AGENTS.md` y se refuerza allí/runbook.
- `AGENTS.md`: **update** — cambia "Próxima feature en cola"; se agrega el
  puntero al runbook de release y se mantiene la regla de no commitear secrets.
- Other docs: `docs/release/android.md` — create (runbook);
  `docs/technical-discovery.md` — update (Deployment); `CONTEXT.md`,
  `docs/domain-model.md`, `DESIGN.md` — not needed (sin cambios de producto/UI);
  `feature_list.json`/`PROGRESS.md` — update al cerrar.

## Implementation Plan

1. Agregar la carga de credenciales y el `signingConfig("release")` condicional,
   más el override de versión, en `composeApp/build.gradle.kts`.
2. Actualizar `.gitignore` y crear `keystore.properties.example`.
3. Escribir `docs/release/android.md` con generación de keystore, build firmado,
   verificación y subida manual a Play Console.
4. Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y `AGENTS.md`.
5. Verificar localmente con keystore descartable (env y archivo), correr
   `./init.sh`, y registrar evidencia; cerrar `feature_list.json`/`PROGRESS.md`.

## Implementation Tasks

- [x] Cargar `keystore.properties` (raíz) y variables de entorno con precedencia
      env > archivo en `composeApp/build.gradle.kts`.
- [x] Registrar `signingConfigs.create("release")` sólo si están las 4
      credenciales y asignarlo al build type `release` (`isMinifyEnabled = false`).
- [x] Permitir override de `versionCode`/`versionName` por propiedades
      (`playbook.versionCode`/`playbook.versionName`) con los defaults actuales.
- [x] Agregar `keystore.properties`, `*.jks`, `*.keystore` a `.gitignore`.
- [x] Crear `keystore.properties.example` (placeholders, sin secrets).
- [x] Documentar el runbook en `docs/release/android.md` (local vs. Play Console).
- [x] Actualizar `ARCHITECTURE.md`, `docs/technical-discovery.md` y `AGENTS.md`.
- [x] Verificar `assembleRelease`/`bundleRelease` firmados con keystore
      descartable por env y por archivo; confirmar modo degradado sin secrets.
- [x] Confirmar que `./init.sh` sigue verde y que los secretos quedan ignorados;
      registrar evidencia y cerrar estado.

## Verification Plan

- **Modo degradado (sin secrets)**: sin `keystore.properties` ni env →
  `./gradlew :composeApp:assembleRelease` → `BUILD SUCCESSFUL`; APK
  `composeApp-release-unsigned.apk` (no firmado). Esperado: no falla y no pide
  credenciales.
- **Firmado por variables de entorno** (recomendado para la verificación local,
  evita dejar archivos): generar un keystore descartable y exportar
  `PLAYBOOK_KEYSTORE_FILE`/`PLAYBOOK_KEYSTORE_PASSWORD`/`PLAYBOOK_KEY_ALIAS`/
  `PLAYBOOK_KEY_PASSWORD`; `./gradlew :composeApp:assembleRelease
  :composeApp:bundleRelease` → `BUILD SUCCESSFUL`; APK/AAB firmados.
- **Firmado por `keystore.properties`**: crear un archivo temporal, correr los
  mismos comandos y borrarlo.
- **Verificar firma del APK**:
  `APKSIGNER=$(ls -1 "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)`
  y `"$APKSIGNER" verify --print-certs composeApp/build/outputs/apk/release/composeApp-release.apk`
  → "Verifies" y certificado del keystore descartable (no el de debug).
- **Verificar firma del AAB**:
  `jarsigner -verify -verbose -certs composeApp/build/outputs/bundle/release/composeApp-release.aab`
  → firma válida.
- **Instalabilidad (opcional, emulador)**: `adb install -r
  composeApp/build/outputs/apk/release/composeApp-release.apk` y smoke de
  arranque.
- **Secretos fuera de VCS**: `git check-ignore -v keystore.properties` imprime el
  match y `git status --porcelain` no lista keystores/properties de firma.
- **No regresión**: `./gradlew :core:testDebugUnitTest` (y
  `:core:iosSimulatorArm64Test` en macOS) siguen verdes; `./init.sh` → exit 0 sin
  secrets ni procesos de larga duración.
- **E2E persistente**: **no aplica** — no existe harness E2E en el repo y
  `init.sh` no levanta simuladores; el flujo de release se verifica con build
  Gradle + herramientas de firma. La subida real a Play Console es manual y su
  evidencia es externa (o declarada como no ejecutada si no hay cuenta).

## Evidence To Capture

- Salida de `assembleRelease` en modo degradado: `BUILD SUCCESSFUL` y nombre del
  APK sin firmar.
- Salida de `assembleRelease`/`bundleRelease` con keystore descartable (env y
  archivo) y rutas de APK/AAB firmados.
- Salida de `apksigner verify --print-certs` (certificado del keystore, no debug)
  y de `jarsigner -verify` del AAB.
- `git check-ignore -v keystore.properties` y `git status --porcelain` sin
  secrets.
- Salida de `./init.sh` (exit 0) y de los tests del core verdes.
- Paso manual de Play Console: captura/registro de la subida a internal testing
  **sólo si hay cuenta**; si no, registrar explícitamente "no ejecutado; requiere
  Play Console". No fabricar evidencia.

## Validator Checklist

- [x] Alcance respetado: signing de release Android + artefactos + runbook; sin
      iOS, CI, plugins de Play, R8/ProGuard ni cambios de UI/`core`.
- [x] Escenarios 1–4 y 7 verificados localmente; Scenario 5 (secretos) y, si hay
      cuenta, Scenario 6 (manual) registrados honestamente.
- [x] `assembleRelease` funciona **sin** secrets (modo degradado) y **con**
      keystore (por env y por `keystore.properties`).
- [x] `bundleRelease` genera un AAB firmado y verificable con `jarsigner`.
- [x] Firmado confirmado con `apksigner`/`jarsigner` y certificado distinto al de
      debug.
- [x] `.gitignore` protege `keystore.properties`/`*.jks`/`*.keystore`; no hay
      secrets versionados (verificado con `git check-ignore`/`git status`).
- [x] `versionCode`/`versionName` overrideables con defaults sin cambios.
- [x] `init.sh` sin cambios, no bloqueante, sin secrets ni procesos de larga
      duración; tests del core verdes.
- [x] `docs/release/android.md`, `ARCHITECTURE.md`, `docs/technical-discovery.md`
      y `AGENTS.md` actualizados.
- [x] Evidencia en `feature_list.json`/`PROGRESS.md`; el paso Play Console queda
      marcado como manual/externo, sin evidencia fabricada.
- [x] No hay E2E persistente y la spec justifica por qué.

> Validación independiente: **accept** (2026-10-09).
