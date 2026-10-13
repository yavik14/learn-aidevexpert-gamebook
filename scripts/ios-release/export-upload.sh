#!/usr/bin/env bash
# Exporta el archive Release a un .ipa firmado y lo sube a TestFlight.
#
# Requiere: un archive firmado (archive.sh sin --unsigned), TEAM_ID y una App
# Store Connect API key (ASC_KEY_ID/ASC_ISSUER_ID + .p8 en
# ~/.appstoreconnect/private_keys/). Renderiza ExportOptions.plist en
# build/ios/ (gitignored) a partir de la plantilla versionada.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

ARCHIVE_PATH="${ARCHIVE_PATH:-build/ios/Playbook.xcarchive}"
EXPORT_PATH="${EXPORT_PATH:-build/ios/export}"
TEMPLATE="scripts/ios-release/ExportOptions.plist.template"
EXPORT_OPTIONS="build/ios/ExportOptions.plist"
KEY_DIR="${HOME}/.appstoreconnect/private_keys"

# --- Prerequisitos ---------------------------------------------------------
if [ ! -d "$ARCHIVE_PATH" ]; then
  echo "ERROR: no existe el archive $ARCHIVE_PATH. Corré archive.sh (firmado) primero." >&2
  exit 1
fi
if [ -z "${TEAM_ID:-}" ]; then
  echo "ERROR: falta TEAM_ID. Exportá TEAM_ID=<team-id>." >&2
  exit 1
fi
if [ -z "${ASC_KEY_ID:-}" ] || [ -z "${ASC_ISSUER_ID:-}" ]; then
  echo "ERROR: faltan ASC_KEY_ID/ASC_ISSUER_ID (App Store Connect API key)." >&2
  exit 1
fi
ASC_KEY_PATH="${ASC_KEY_PATH:-$KEY_DIR/AuthKey_${ASC_KEY_ID}.p8}"
if [ ! -f "$ASC_KEY_PATH" ]; then
  echo "ERROR: no existe la App Store Connect API key en $ASC_KEY_PATH." >&2
  exit 1
fi

# --- Render de ExportOptions (sin valores reales versionados) --------------
mkdir -p build/ios "$EXPORT_PATH"
sed "s/__TEAM_ID__/${TEAM_ID}/g" "$TEMPLATE" > "$EXPORT_OPTIONS"

# --- Export del .ipa -------------------------------------------------------
xcodebuild -exportArchive \
  -archivePath "$ARCHIVE_PATH" \
  -exportPath "$EXPORT_PATH" \
  -exportOptionsPlist "$EXPORT_OPTIONS" \
  -allowProvisioningUpdates \
  -authenticationKeyPath "$ASC_KEY_PATH" \
  -authenticationKeyID "$ASC_KEY_ID" \
  -authenticationKeyIssuerID "$ASC_ISSUER_ID"

IPA="$(find "$EXPORT_PATH" -maxdepth 1 -name '*.ipa' -print -quit)"
if [ -z "$IPA" ]; then
  echo "ERROR: no se generó ningún .ipa en $EXPORT_PATH." >&2
  exit 1
fi
echo ">> IPA exportado: $IPA"

# --- Subida a App Store Connect / TestFlight -------------------------------
# `altool` resuelve la key desde ~/.appstoreconnect/private_keys/AuthKey_<id>.p8.
xcrun altool --upload-app --type ios --file "$IPA" \
  --apiKey "$ASC_KEY_ID" --apiIssuer "$ASC_ISSUER_ID"

echo ">> Subida enviada a App Store Connect. Revisá App Store Connect → TestFlight."
echo ">> Nota: usar un CURRENT_PROJECT_VERSION nuevo en cada subida (TestFlight exige unicidad)."
