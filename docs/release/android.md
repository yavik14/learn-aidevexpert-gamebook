# Release de Android (Play internal testing)

Runbook del build firmado de Android y de la subida manual al track **internal
testing** de Play Console. La configuración de signing vive en
`composeApp/build.gradle.kts`; el keystore y las credenciales **nunca** se
versionan (`.gitignore` protege `keystore.properties`, `*.jks` y `*.keystore`).

## Alcance: qué se verifica localmente y qué es manual

| Área | Verificable en el repo | Requiere Play Console (manual) |
| --- | --- | --- |
| Signing config + precedencia env/archivo | Sí (build firmado) | — |
| APK release firmado/instalable | Sí (`apksigner`, `adb install`) | — |
| AAB firmado y verificable | Sí (`jarsigner -verify`) | — |
| Sin secrets en VCS | Sí (`git check-ignore`) | — |
| Subida al track internal testing | No | **Sí** (cuenta + app + testers) |
| Instalación desde el canal | No | **Sí** (link de testers) |

`./init.sh` **no** ejecuta tareas de release ni requiere credenciales: sigue
siendo el gate no bloqueante de debug. La verificación de release se corre a mano
con los comandos de este documento.

## Requisitos

- JDK 21 (incluye `keytool` y `jarsigner`).
- Android SDK con `build-tools` (incluye `apksigner`) y `platform-tools`
  (`adb`). Con el SDK en `local.properties` (`sdk.dir=...`) o `ANDROID_HOME`.

## 1. Generar el keystore (upload key)

El keystore de release es la **upload key** del desarrollador. Guardalo **fuera
del repo** y respaldalo en un lugar seguro (perderla exige un reset en Play
Console; ver Play App Signing más abajo).

```bash
keytool -genkeypair -v \
  -keystore ~/playbook-upload.jks \
  -alias playbook-upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` pide un password para el keystore y la key (podés usar el mismo para
ambos). **No** generes el keystore dentro del working tree del repo.

## 2. Configurar credenciales

Hay dos formas; las **variables de entorno tienen precedencia** sobre el archivo.
Alcanza con una de las dos.

### Opción A — `keystore.properties` (local, gitignored)

```bash
cp keystore.properties.example keystore.properties
# Completá storeFile/storePassword/keyAlias/keyPassword y guardá.
```

`storeFile` puede ser una ruta absoluta o relativa a la raíz del repo. El archivo
está ignorado por git.

### Opción B — variables de entorno (sin escribir secrets en disco)

```bash
export PLAYBOOK_KEYSTORE_FILE="$HOME/playbook-upload.jks"
export PLAYBOOK_KEYSTORE_PASSWORD="…"
export PLAYBOOK_KEY_ALIAS="playbook-upload"
export PLAYBOOK_KEY_PASSWORD="…"
```

Si falta **cualquiera** de las cuatro credenciales (por archivo o env), el build
de release no registra signing config y produce un APK **sin firmar** (modo
degradado). No falla ni pide secrets.

## 3. Construir los artefactos

Con signing configurado (Opción A o B):

```bash
# APK release instalable
./gradlew :composeApp:assembleRelease
# → composeApp/build/outputs/apk/release/composeApp-release.apk

# AAB (artefacto de subida a Play)
./gradlew :composeApp:bundleRelease
# → composeApp/build/outputs/bundle/release/composeApp-release.aab
```

Sin credenciales (modo degradado) el APK sale como
`composeApp-release-unsigned.apk`.

### Override de versión

Play exige incrementar `versionCode` en cada subida. No edites el script; pasá
propiedades de Gradle con los defaults actuales (`versionCode=1`,
`versionName="1.0"`):

```bash
./gradlew :composeApp:bundleRelease -Pplaybook.versionCode=2 -Pplaybook.versionName="1.1"
```

## 4. Verificar la firma

```bash
# APK (firma APK Signature Scheme)
APKSIGNER=$(ls -1 "$ANDROID_HOME"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)
"$APKSIGNER" verify --print-certs \
  composeApp/build/outputs/apk/release/composeApp-release.apk

# AAB (firma JAR)
jarsigner -verify -verbose -certs \
  composeApp/build/outputs/bundle/release/composeApp-release.aab
```

El certificado debe corresponder a tu upload key, **no** al keystore de debug de
Android.

### Instalar el APK localmente (opcional)

```bash
adb install -r composeApp/build/outputs/apk/release/composeApp-release.apk
```

## 5. Play App Signing (upload key vs app signing key)

Play usa **Play App Signing**: Google custodia la *app signing key* y vos subís el
AAB firmado con tu *upload key*. La upload key de este runbook sirve para
autenticar la subida; si se pierde o se filtra, se resetea desde Play Console
(Configuración → Integridad de la app). Respaldá el keystore fuera del repo.

## 6. Subida manual a internal testing (Play Console)

Este paso requiere una cuenta de Play Console y **no** es verificable dentro del
repo. Es manual:

1. Crear la app en Play Console (`Crear app`) con el `applicationId`
   `com.playbook.app`.
2. Completar lo mínimo obligatorio del store listing que Play exija para publicar
   en testing.
3. Ir a **Testing → Internal testing → Crear nueva versión** y subir
   `composeApp-release.aab`.
4. Agregar testers (lista de emails o Google Group) y guardar la versión.
5. Distribuir el link de internal testing (`Play Console → Testers → Copiar
   link`) e instalar el build desde Play en un dispositivo de prueba.

Si no hay cuenta de Play Console disponible, este paso se registra como **no
ejecutado** (requiere Play Console); no se fabrica evidencia.

## 7. Secretos fuera de VCS

```bash
git check-ignore -v keystore.properties   # imprime el match de .gitignore
git status --porcelain                    # no lista keystores/properties de firma
```

Regla durable: **nunca** commitear keystores ni secrets. Además de
`keystore.properties`, `.gitignore` cubre `*.jks` y `*.keystore`; igualmente,
generá el keystore **fuera** del repo.
