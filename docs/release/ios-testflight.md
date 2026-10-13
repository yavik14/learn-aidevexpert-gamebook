# Runbook: release de iOS a TestFlight

Pipeline **shell + Xcode CLI** (sin `fastlane`) para archivar la app iOS
(`iosApp/`), exportar un `.ipa` firmado y subirlo a TestFlight. Es un pipeline
**local, manual y de invocación explícita**: no forma parte de `./init.sh`.

> **Estado honesto:** los artefactos repo-side (scripts, firma, versionado,
> `.gitignore`) están completos y verificados sin credenciales. La ejecución
> firmada del archive/export/upload **requiere una cuenta Apple Developer
> Program** (Team ID + identidad de firma o App Store Connect API key). En un
> entorno sin esas credenciales la feature queda `blocked`; ver
> "Bloqueo y evidencia honesta".

## Prerrequisitos

- macOS con Xcode (`xcodebuild`, `xcrun altool`).
- Cuenta **Apple Developer Program** (no alcanza una cuenta gratuita).
- App ID `com.playbook.app` registrado en el portal de Apple / App Store Connect
  (bundle id **estable**, sin sufijo de Team ID).
- `TEAM_ID` (10 caracteres) y credenciales de firma, en una de estas formas:
  - **App Store Connect API key** (recomendada para el upload):
    `ASC_KEY_ID`, `ASC_ISSUER_ID` y el archivo
    `~/.appstoreconnect/private_keys/AuthKey_<KEY_ID>.p8` (fuera del repo).
  - Y/o una identidad `Apple Distribution` en el keychain
    (`security find-identity -v -p codesigning`).
- Un dispositivo físico con la app **TestFlight** y un tester interno invitado
  (para el E2E real).

## Setup una vez

1. **Team ID fuera del repo** (higiene; no es un secreto criptográfico):
   ```bash
   cp iosApp/Configuration/Local.xcconfig.example iosApp/Configuration/Local.xcconfig
   # editá TEAM_ID=<tu-team-id> en Local.xcconfig (gitignored)
   ```
   Alternativa: `export TEAM_ID=<tu-team-id>` en la shell; los scripts lo pasan
   como override a `xcodebuild`. `Config.xcconfig` versionado tiene `TEAM_ID=`
   vacío y hace `#include? "Local.xcconfig"`.

2. **App Store Connect API key**: descargá el `.p8` del portal y dejanlo en
   `~/.appstoreconnect/private_keys/` (nunca dentro del repo). Exportá:
   ```bash
   export ASC_KEY_ID=<key-id>
   export ASC_ISSUER_ID=<issuer-id>
   ```

3. **App ID**: registrá `com.playbook.app` en el portal / App Store Connect con
   el nombre `Playbook` y habilitá el bundle para firma automática.

## Versionado

- `MARKETING_VERSION` = versión de marketing (visible, p. ej. `1.0`).
- `CURRENT_PROJECT_VERSION` = build number; **debe ser único por subida**
  (TestFlight rechaza reutilizarlo). `archive.sh` los recibe por parámetro y los
  pasa a `xcodebuild` como overrides; `Info.plist` los lee vía
  `$(MARKETING_VERSION)`/`$(CURRENT_PROJECT_VERSION)`.
- El default del build number es un timestamp `YYYYMMDDHHmm`.

## Pipeline

### 0) Preflight (no archiva ni sube)

```bash
scripts/ios-release/preflight.sh
```

Verifica macOS/Xcode, el scheme compartido, `TEAM_ID`, credenciales de firma y
la herramienta de subida. Falla con `exit != 0` y un `[FAIL]` accionable por cada
prerequisito faltante.

### 1a) Archive local sin firma (verificación del pipeline)

No necesita credenciales; valida que la app compila/enlaza en `Release`.

```bash
scripts/ios-release/archive.sh --unsigned --version 1.0 --build 1
# Produce build/ios/Playbook.xcarchive (CODE_SIGNING_ALLOWED=NO)
```

### 1b) Archive firmado

```bash
scripts/ios-release/archive.sh --version 1.0 --build 202610091930
# requiere TEAM_ID; agrega firma automática (-allowProvisioningUpdates)
# y, si ASC_KEY_ID/ASC_ISSUER_ID están seteados, autenticación con la API key.
```

Internamente corre (equivalente):

```bash
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Release \
  -destination 'generic/platform=iOS' -archivePath build/ios/Playbook.xcarchive \
  -allowProvisioningUpdates \
  MARKETING_VERSION=1.0 CURRENT_PROJECT_VERSION=202610091930 archive
```

### 2) Export del `.ipa` + subida a TestFlight

```bash
scripts/ios-release/export-upload.sh
```

- Renderiza `build/ios/ExportOptions.plist` desde
  `scripts/ios-release/ExportOptions.plist.template` (método
  `app-store-connect`, `signingStyle automatic`, `teamID` desde env). El archivo
  renderizado está gitignored.
- Exporta:
  ```bash
  xcodebuild -exportArchive -archivePath build/ios/Playbook.xcarchive \
    -exportPath build/ios/export \
    -exportOptionsPlist build/ios/ExportOptions.plist \
    -allowProvisioningUpdates \
    -authenticationKeyPath ~/.appstoreconnect/private_keys/AuthKey_<ID>.p8 \
    -authenticationKeyID <ASC_KEY_ID> -authenticationKeyIssuerID <ASC_ISSUER_ID>
  ```
- Sube con el CLI de Apple (confirmado en esta Xcode: `altool` 27.0.5 (2.1)):
  ```bash
  xcrun altool --upload-app --type ios --file build/ios/export/Playbook.ipa \
    --apiKey "$ASC_KEY_ID" --apiIssuer "$ASC_ISSUER_ID"
  ```
  `altool` resuelve la key desde `~/.appstoreconnect/private_keys/`. Fallback
  declarado si `altool` no estuviera: `xcrun iTMSTransporter -m upload ...`;
  `fastlane pilot` sería el último recurso y requeriría Gemfile (no adoptado).

### 3) TestFlight

1. App Store Connect → TestFlight: el build pasa por `Processing` y queda
   `Ready to Test`.
2. Asignalo a los testers internos. La Beta App Review **no** es necesaria para
   testers internos.
3. Un tester instala desde la app TestFlight en un dispositivo real y abre
   Playbook (debe mostrar la lista de notas).

## Troubleshooting

- **"No signing certificate / requires a development team"**: falta `TEAM_ID`
  (export o `Local.xcconfig`) o no hay identidad `Apple Distribution` ni API key
  con permiso de provisioning. Corré `preflight.sh`.
- **Bundle id duplicado o inválido**: confirmá que `PRODUCT_BUNDLE_IDENTIFIER`
  es `com.playbook.app` (sin `${TEAM_ID}`) y que el App ID está registrado.
- **Build number ya usado**: TestFlight rechaza el build. Re-corré `archive.sh`
  con un `--build` nuevo.
- **`.p8` no encontrada**: `altool` la busca en
  `~/.appstoreconnect/private_keys/AuthKey_<KEY_ID>.p8`; no la muevas al repo.
- **`altool` deprecado**: sigue funcionando en Xcode 26.4/27.0; si una futura
  Xcode lo quita, usar `iTMSTransporter` o `notarytool` (upload a ASC) y
  actualizar el script + este runbook.
- **`embedAndSignAppleFrameworkForXcode` SKIPPED**: es normal en build estático
  sin firma; el framework `ComposeApp` (static) se enlaza dentro del binario.

## Evidencia a capturar

- Salida de `scripts/ios-release/preflight.sh` (con y sin credenciales, exit
  code).
- Salida de `archive.sh --unsigned` y ruta del `.xcarchive`.
- Con cuenta: `.ipa` exportado, build en TestFlight (`Processing → Ready`),
  número de marketing/build, y captura de la app instalada desde TestFlight en
  un device real.
- `git status`/`git diff` sin credenciales ni artefactos de firma.

## Seguridad

- **Nunca** commitear `.p8`, `.p12`, `.cer`, `.mobileprovision`, `.ipa`,
  `.xcarchive`, `ExportOptions.plist` renderizado ni `Local.xcconfig`. Están en
  `.gitignore`.
- El `TEAM_ID` real vive fuera del repo (`Local.xcconfig` gitignored o env).
- Los artefactos se generan en `build/ios/` (ignorado).
