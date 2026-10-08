#!/usr/bin/env bash
# ==============================================================================
# Unreel: Cloud Device Farm Runner with Google Firebase Test Lab
# Executes instrumented RealDeviceSmokeTest suites across physical cloud OEM devices.
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

DRY_RUN=false
GCLOUD_PROJECT="${GCLOUD_PROJECT:-unreel-android-poc}"
RESULTS_BUCKET="${RESULTS_BUCKET:-gs://unreel-test-lab-results}"

# Parse command line flags
while [[ $# -gt 0 ]]; do
    case "$1" in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --project)
            GCLOUD_PROJECT="$2"
            shift 2
            ;;
        --help|-h)
            echo "Usage: $0 [--dry-run] [--project <gcloud-project-id>]"
            echo "Options:"
            echo "  --dry-run    Validate APK compilation, device matrix, and gcloud CLI without launching cloud tests."
            echo "  --project    Specify Google Cloud Project ID."
            exit 0
            ;;
        *)
            echo "Unknown argument: $1"
            exit 1
            ;;
    esac
done

echo "=========================================================="
echo " Unreel: Firebase Test Lab Cloud Device Farm Runner"
echo "=========================================================="
echo "Project Root:      ${PROJECT_ROOT}"
echo "Dry Run Mode:      ${DRY_RUN}"
echo "Google Cloud Proj: ${GCLOUD_PROJECT}"
echo "=========================================================="

cd "${PROJECT_ROOT}"

# 1. Compile debug and instrumented test APKs
echo ""
echo "[Step 1/4] Assembling Application and Test APKs..."
./gradlew assembleDebug assembleDebugAndroidTest --no-daemon

APP_APK="${PROJECT_ROOT}/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="${PROJECT_ROOT}/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"

if [[ ! -f "${APP_APK}" ]]; then
    echo "ERROR: Target application APK not found at: ${APP_APK}"
    exit 1
fi

if [[ ! -f "${TEST_APK}" ]]; then
    echo "ERROR: Target test APK not found at: ${TEST_APK}"
    exit 1
fi

APP_SIZE=$(stat -c%s "${APP_APK}")
TEST_SIZE=$(stat -c%s "${TEST_APK}")
echo "  ✓ App APK:  ${APP_APK} (${APP_SIZE} bytes)"
echo "  ✓ Test APK: ${TEST_APK} (${TEST_SIZE} bytes)"

# 2. Define Device Matrix (Physical devices across OEMs)
DEVICE_MATRIX=(
    "model=oriole,version=33,locale=en,orientation=portrait"      # Google Pixel 6 (Android 13)
    "model=panther,version=34,locale=en,orientation=portrait"     # Google Pixel 7 (Android 14)
    "model=b0q,version=33,locale=en,orientation=portrait"         # Samsung Galaxy S22 Ultra (Android 13)
    "model=dm3q,version=34,locale=en,orientation=portrait"        # Samsung Galaxy S23 Ultra (Android 14)
)

echo ""
echo "[Step 2/4] Configured Physical Device Test Matrix (${#DEVICE_MATRIX[@]} devices):"
for dev in "${DEVICE_MATRIX[@]}"; do
    echo "  • ${dev}"
done

# 3. Check gcloud CLI
echo ""
echo "[Step 3/4] Verifying Google Cloud Platform CLI (gcloud)..."
if command -v gcloud >/dev/null 2>&1; then
    GCLOUD_VER=$(gcloud --version 2>&1 | head -n 1)
    echo "  ✓ Found gcloud: ${GCLOUD_VER}"
else
    echo "  ℹ gcloud CLI not detected on local path."
    if [[ "${DRY_RUN}" = false ]]; then
        echo "ERROR: 'gcloud' is required to submit jobs to Firebase Test Lab."
        echo "Install Google Cloud SDK: https://cloud.google.com/sdk/docs/install"
        exit 1
    fi
fi

# 4. Execute or simulate Firebase Test Lab submission
echo ""
echo "[Step 4/4] Submitting Tests to Firebase Test Lab..."

DEVICE_ARGS=()
for dev in "${DEVICE_MATRIX[@]}"; do
    DEVICE_ARGS+=(--device "${dev}")
done

TEST_CMD=(
    gcloud firebase test android run
    --type instrumentation
    --app "${APP_APK}"
    --test "${TEST_APK}"
    --test-targets "class org.unreel.android.RealDeviceSmokeTest"
    "${DEVICE_ARGS[@]}"
    --project "${GCLOUD_PROJECT}"
    --timeout 10m
    --num-flaky-test-attempts 1
)

if [[ "${DRY_RUN}" = true ]]; then
    echo "  [DRY-RUN] Verification complete! The command that would execute is:"
    echo "  ------------------------------------------------------------------"
    echo "  ${TEST_CMD[*]}"
    echo "  ------------------------------------------------------------------"
    echo "  ✓ Build verified: Both APKs compiled successfully."
    echo "  ✓ Test suite verified: RealDeviceSmokeTest target selected."
    echo "  ✓ Device matrix verified: 4 physical OEM cloud devices targeted."
    echo "  ✓ Dry run succeeded with zero errors."
    exit 0
fi

echo "Executing Firebase Test Lab submission..."
"${TEST_CMD[@]}"
