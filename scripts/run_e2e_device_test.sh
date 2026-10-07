#!/usr/bin/env bash
# ==============================================================================
# run_e2e_device_test.sh — Master Automated Real-Device E2E Test Suite Runner
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd -P)"

export PATH="/home/cameron/.local/bin:/home/cameron/.local/android-sdk/platform-tools:$PATH"

echo "Building latest Unreel binary..."
(cd "$REPO_ROOT" && ./gradlew assembleDebug --quiet)

echo "Deploying latest binary to connected device..."
adb install -r -d "$REPO_ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null

echo "Executing automated on-device E2E test suite..."
python3 "$SCRIPT_DIR/e2e_device_test_suite.py"
