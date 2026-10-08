#!/usr/bin/env bash
set -euo pipefail

echo "Repository: $(pwd)"
echo "Harness status: bootstrapped (Kotlin Multiplatform + Compose Multiplatform + SQLDelight)"
echo ""

# --- Requisitos del entorno -----------------------------------------------
missing=0

if ! command -v java >/dev/null 2>&1; then
  echo "ERROR: no se encontró 'java'. Instalá un JDK 17+ (recomendado JBR/OpenJDK 21) y probá de nuevo." >&2
  missing=1
else
  echo "JDK: $(java -version 2>&1 | head -1)"
fi

if [ -z "${ANDROID_HOME:-}" ] && [ ! -f local.properties ]; then
  echo "ERROR: falta el Android SDK. Seteá ANDROID_HOME o creá local.properties con 'sdk.dir=/ruta/al/Android/sdk'." >&2
  missing=1
else
  echo "Android SDK: ${ANDROID_HOME:-$(grep -E '^sdk\.dir=' local.properties | cut -d= -f2-)}"
fi

if [ "$(uname -s)" = "Darwin" ]; then
  if ! command -v xcodebuild >/dev/null 2>&1; then
    echo "ERROR: falta Xcode. Instalalo desde la App Store y ejecutá 'xcode-select --install'." >&2
    missing=1
  else
    echo "Xcode: $(xcodebuild -version 2>/dev/null | head -1)"
  fi
fi

if [ "$missing" -ne 0 ]; then
  echo "" >&2
  echo "init.sh abortado: faltan requisitos del entorno (ver arriba)." >&2
  exit 1
fi

# --- Gate no bloqueante: compilación Android + tests del core -------------
echo ""
echo ">> Compilando Android y corriendo tests del core..."
./gradlew :composeApp:assembleDebug :core:testDebugUnitTest --console=plain

# --- Compilación del framework iOS (solo en macOS) ------------------------
if [ "$(uname -s)" = "Darwin" ]; then
  if [ "$(uname -m)" = "arm64" ]; then
    IOS_LINK_TASK=":composeApp:linkDebugFrameworkIosSimulatorArm64"
    IOS_CORE_TEST_TASK=":core:iosSimulatorArm64Test"
  else
    IOS_LINK_TASK=":composeApp:linkDebugFrameworkIosX64"
    IOS_CORE_TEST_TASK=":core:iosX64Test"
  fi
  echo ""
  echo ">> Compilando el framework iOS ($IOS_LINK_TASK)..."
  ./gradlew "$IOS_LINK_TASK" --console=plain
  echo ""
  echo ">> Corriendo tests del core en iOS ($IOS_CORE_TEST_TASK)..."
  ./gradlew "$IOS_CORE_TEST_TASK" --console=plain
fi

# --- Comandos manuales (no se ejecutan aquí) ------------------------------
echo ""
echo "Checks OK. Comandos manuales para lanzar la app:"
echo "  Android (emulador/device conectado):"
echo "    ./gradlew :composeApp:installDebug"
echo "    adb shell am start -n com.playbook.app/.MainActivity"
echo "  iOS (simulador):"
echo "    xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \\"
echo "      -configuration Debug -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' build"
echo "    (o abrí iosApp/iosApp.xcodeproj en Xcode y presioná Run)"
echo ""
echo "Nota: este script NO inicia dev servers ni sesiones de emulador/simulador."
