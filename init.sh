#!/usr/bin/env bash
set -euo pipefail

echo "Repository: $(pwd)"
echo "Harness status: pre-bootstrap"
echo "Application stack is not initialized yet (KMP + Compose Multiplatform pending)."
echo "Next step: pick the first ready feature from feature_list.json (kmp-project-bootstrap) and perform technical bootstrap."
echo ""
echo "Expected future commands after bootstrap (not available yet):"
echo "  - ./gradlew :composeApp:assembleDebug"
echo "  - ./gradlew :composeApp:testDebugUnitTest   # core tests, command may change after bootstrap"
echo "  - Android: run on emulator/device; iOS: open in Xcode and run on simulator"
