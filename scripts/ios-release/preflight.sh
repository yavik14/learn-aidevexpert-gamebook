#!/usr/bin/env bash
# Preflight del pipeline de release de iOS a TestFlight.
#
# Valida prerequisitos (macOS, Xcode, scheme, TEAM_ID, credenciales de firma y
# herramienta de subida) y termina con exit != 0 si falta alguno. NO archiva,
# NO exporta y NO sube nada. Correlo antes de archive.sh / export-upload.sh.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

PROJECT="iosApp/iosApp.xcodeproj"
SCHEME="iosApp"
SCHEME_FILE="$PROJECT/xcshareddata/xcschemes/$SCHEME.xcscheme"
LOCAL_XCCONFIG="iosApp/Configuration/Local.xcconfig"
KEY_DIR="${HOME}/.appstoreconnect/private_keys"

fail=0
ok()   { printf '  [OK]   %s\n' "$*"; }
bad()  { printf '  [FAIL] %s\n' "$*" >&2; fail=$((fail + 1)); }

echo "== Preflight: iOS release (TestFlight) =="
echo "Repo: $REPO_ROOT"
echo ""

# 1. macOS (firmar/archivar iOS requiere macOS).
if [ "$(uname -s)" = "Darwin" ]; then
  ok "macOS: $(uname -s) $(uname -r)"
else
  bad "Se requiere macOS para firmar y archivar iOS (detectado: $(uname -s))."
fi

# 2. Xcode / xcodebuild.
if command -v xcodebuild >/dev/null 2>&1; then
  ok "Xcode: $(xcodebuild -version 2>/dev/null | tr '\n' ' ')"
else
  bad "Falta Xcode/xcodebuild. Instalá Xcode desde la App Store y corré 'xcode-select --install'."
fi

# 3. Scheme compartido (invocaciones CLI deterministas).
if [ -f "$SCHEME_FILE" ]; then
  ok "Scheme compartido: $SCHEME_FILE"
else
  bad "Falta el scheme compartido $SCHEME_FILE (necesario para archive/export por CLI)."
fi

# 4. TEAM_ID: env o Local.xcconfig (gitignored).
TEAM_ID_RESOLVED="${TEAM_ID:-}"
TEAM_SOURCE="env TEAM_ID"
if [ -z "$TEAM_ID_RESOLVED" ] && [ -f "$LOCAL_XCCONFIG" ]; then
  TEAM_ID_RESOLVED="$(grep -E '^[[:space:]]*TEAM_ID[[:space:]]*=' "$LOCAL_XCCONFIG" | tail -1 | cut -d= -f2- | tr -d '[:space:]' || true)"
  TEAM_SOURCE="$LOCAL_XCCONFIG"
fi
if [ -n "$TEAM_ID_RESOLVED" ] && [ "$TEAM_ID_RESOLVED" != "YOUR_TEAM_ID" ]; then
  ok "TEAM_ID: $TEAM_ID_RESOLVED (fuente: $TEAM_SOURCE)"
else
  bad "TEAM_ID no definido. Exportá TEAM_ID=<team-id> o creá $LOCAL_XCCONFIG desde Local.xcconfig.example."
fi

# 5. Credenciales de firma: identidad de codesigning o App Store Connect API key.
IDENTITIES="$(security find-identity -v -p codesigning 2>/dev/null | grep -E '"[^"]*(Apple Distribution|Apple Development)[^"]*"' || true)"
ASC_KEY_PATH="${ASC_KEY_PATH:-$KEY_DIR/AuthKey_${ASC_KEY_ID:-}.p8}"
if [ -n "$IDENTITIES" ]; then
  ok "Identidad de firma: $(printf '%s\n' "$IDENTITIES" | head -1 | sed 's/^[[:space:]]*//')"
elif [ -n "${ASC_KEY_ID:-}" ] && [ -n "${ASC_ISSUER_ID:-}" ] && [ -f "$ASC_KEY_PATH" ]; then
  ok "App Store Connect API key: ASC_KEY_ID=$ASC_KEY_ID (key: $ASC_KEY_PATH)"
else
  bad "Sin credenciales de firma: 0 identidades de codesigning y sin App Store Connect API key (ASC_KEY_ID/ASC_ISSUER_ID + $KEY_DIR/AuthKey_<KEY_ID>.p8)."
fi

# 6. Herramienta de subida disponible en la Xcode instalada.
UPLOAD_TOOL=""
UPLOAD_CMD=""
if command -v xcrun >/dev/null 2>&1 && xcrun --find altool >/dev/null 2>&1; then
  UPLOAD_TOOL="altool"; UPLOAD_CMD="xcrun altool --upload-app --type ios --file <ipa> --apiKey <key_id> --apiIssuer <issuer_id>"
elif command -v xcrun >/dev/null 2>&1 && xcrun --find iTMSTransporter >/dev/null 2>&1; then
  UPLOAD_TOOL="iTMSTransporter"; UPLOAD_CMD="xcrun iTMSTransporter -m upload ..."
fi
if [ -n "$UPLOAD_TOOL" ]; then
  ok "Herramienta de subida: $UPLOAD_TOOL ($UPLOAD_CMD)"
else
  bad "No se encontró 'xcrun altool' ni 'xcrun iTMSTransporter' en la Xcode instalada."
fi

echo ""
if [ "$fail" -ne 0 ]; then
  echo "Preflight FALLÓ: faltan $fail prerequisito(s). Corregí los [FAIL] de arriba; no se archivó ni subió nada." >&2
  exit 1
fi
echo "Preflight OK: prerequisitos listos. Corré scripts/ios-release/archive.sh y luego scripts/ios-release/export-upload.sh."
