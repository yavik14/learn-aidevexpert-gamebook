#!/usr/bin/env bash
# Archive Release de la app iOS (host Xcode `iosApp`).
#
# - Con `--unsigned` (o `CODE_SIGNING_ALLOWED=NO`) compila/enlaza en Release sin
#   credenciales: es la verificación local del pipeline (Scenario 2).
# - Sin `--unsigned` firma el archive con `Apple Distribution` usando firma
#   automática + `-allowProvisioningUpdates` (requiere TEAM_ID; opcionalmente
#   credenciales de App Store Connect API key).
#
# NO sube nada. La subida vive en export-upload.sh.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

PROJECT="iosApp/iosApp.xcodeproj"
SCHEME="iosApp"
ARCHIVE_PATH="build/ios/Playbook.xcarchive"

VERSION="${VERSION:-1.0}"
BUILD_NUMBER="${BUILD_NUMBER:-$(date +%Y%m%d%H%M)}"
UNSIGNED=0

usage() {
  cat <<'EOF'
Uso: archive.sh [--unsigned] [--version X.Y] [--build N]

  --unsigned      compila con CODE_SIGNING_ALLOWED=NO (no necesita credenciales).
  --version X.Y   MARKETING_VERSION (default: env VERSION o 1.0).
  --build N       CURRENT_PROJECT_VERSION (default: env BUILD_NUMBER o timestamp).

Variables: VERSION, BUILD_NUMBER, TEAM_ID, ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_PATH.
Cada subida debe usar un BUILD_NUMBER nuevo (TestFlight exige unicidad).
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --unsigned) UNSIGNED=1; shift ;;
    --version)  VERSION="${2:?falta el valor de --version}"; shift 2 ;;
    --build)    BUILD_NUMBER="${2:?falta el valor de --build}"; shift 2 ;;
    -h|--help)  usage; exit 0 ;;
    *) echo "Opción desconocida: $1" >&2; usage >&2; exit 2 ;;
  esac
done

# Permitir forzar el modo sin firma vía variable de entorno.
if [ "${CODE_SIGNING_ALLOWED:-}" = "NO" ]; then
  UNSIGNED=1
fi

echo ">> Archive Release: scheme=$SCHEME marketing=$VERSION build=$BUILD_NUMBER unsigned=$UNSIGNED"
mkdir -p build/ios

ARGS=(
  -project "$PROJECT"
  -scheme "$SCHEME"
  -configuration Release
  -destination "generic/platform=iOS"
  -archivePath "$ARCHIVE_PATH"
  MARKETING_VERSION="$VERSION"
  CURRENT_PROJECT_VERSION="$BUILD_NUMBER"
)

if [ "$UNSIGNED" -eq 1 ]; then
  ARGS+=(CODE_SIGNING_ALLOWED=NO)
  echo ">> Modo sin firma (CODE_SIGNING_ALLOWED=NO): valida la compilación/enlace Release."
else
  if [ -z "${TEAM_ID:-}" ]; then
    echo "ERROR: falta TEAM_ID. Exportá TEAM_ID=<team-id> o corré 'archive.sh --unsigned'." >&2
    exit 1
  fi
  ARGS+=(-allowProvisioningUpdates DEVELOPMENT_TEAM="$TEAM_ID")
  if [ -n "${ASC_KEY_ID:-}" ] && [ -n "${ASC_ISSUER_ID:-}" ]; then
    ASC_KEY_PATH="${ASC_KEY_PATH:-$HOME/.appstoreconnect/private_keys/AuthKey_${ASC_KEY_ID}.p8}"
    if [ ! -f "$ASC_KEY_PATH" ]; then
      echo "ERROR: no existe la App Store Connect API key en $ASC_KEY_PATH." >&2
      exit 1
    fi
    ARGS+=(
      -authenticationKeyPath "$ASC_KEY_PATH"
      -authenticationKeyID "$ASC_KEY_ID"
      -authenticationKeyIssuerID "$ASC_ISSUER_ID"
    )
  fi
fi

xcodebuild "${ARGS[@]}" archive

echo ">> Archive listo: $ARCHIVE_PATH"
