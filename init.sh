#!/usr/bin/env bash
set -euo pipefail

echo "Repository: $(pwd)"
echo "Harness status: bootstrapped (Kotlin Multiplatform + Compose Multiplatform + SQLDelight)"
echo "Platform scope: Android-first (iOS congelado; ver docs/adr/0003-ios-congelado-mvp.md)"
echo ""

# --- Requisitos del entorno (Android-only) --------------------------------
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

if [ "$missing" -ne 0 ]; then
  echo "" >&2
  echo "init.sh abortado: faltan requisitos del entorno (ver arriba)." >&2
  exit 1
fi

# --- Gate no bloqueante: compilación Android + tests del core -------------
echo ""
echo ">> Compilando Android y corriendo tests del core..."
./gradlew :composeApp:assembleDebug :core:testDebugUnitTest --console=plain

# --- Comandos manuales (no se ejecutan aquí) ------------------------------
echo ""
echo "Checks OK. Comandos manuales para lanzar la app:"
echo "  Android (emulador/device conectado):"
echo "    ./gradlew :composeApp:installDebug"
echo "    adb shell am start -n com.playbook.app/.MainActivity"
echo ""
echo "Nota: este script NO inicia dev servers ni sesiones de emulador/simulador."
echo "Nota: iOS está congelado; el gate valida solo Android. El proyecto iOS se"
echo "conserva y debe seguir compilando (los nuevos expect aportan un actual mínimo)."
