#!/usr/bin/env bash
# ==============================================================================
# connect_device.sh — Connect Android Test Device via ADB (USB / Wireless)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Ensure native Linux ADB is used
export PATH="${HOME}/.local/bin:${HOME}/.local/android-sdk/platform-tools:/usr/local/bin:$PATH"

echo "======================================================================"
echo "  ⚡ UNREEL: Android Test Device Connection Manager"
echo "======================================================================"

check_device() {
    local dev_count
    dev_count=$(adb devices | grep -v "List of devices" | grep -c "device$" || true)
    if [ "$dev_count" -ge 1 ]; then
        local serial
        serial=$(adb devices | grep -v "List of devices" | grep "device$" | head -n 1 | awk '{print $1}')
        local model
        model=$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null || echo "Unknown Model")
        local android_ver
        android_ver=$(adb -s "$serial" shell getprop ro.build.version.release 2>/dev/null || echo "Unknown")
        echo ""
        echo "✅ Connected Device Found!"
        echo "   Serial:       $serial"
        echo "   Model:        $model"
        echo "   Android OS:   $android_ver"
        echo "======================================================================"
        return 0
    fi
    return 1
}

# 1. Quick check for existing connection
if check_device; then
    exit 0
fi

# Check for unauthorized devices
unauth_count=$(adb devices | grep -v "List of devices" | grep -c "unauthorized$" || true)
if [ "$unauth_count" -ge 1 ]; then
    echo "⚠️  Device detected but UNAUTHORIZED."
    echo "👉 Please unlock your phone and tap 'Allow USB debugging' on the RSA prompt."
    echo "   Waiting for authorization..."
    adb wait-for-device
    if check_device; then
        exit 0
    fi
fi

# 2. If an IP or argument is provided directly:
if [ "${1:-}" != "" ]; then
    TARGET="$1"
    echo "Attempting direct connection to: $TARGET..."
    adb connect "$TARGET"
    sleep 1
    if check_device; then
        exit 0
    else
        echo "❌ Failed to connect to $TARGET."
    fi
fi

echo ""
echo "No active ADB device detected. Choose connection method:"
echo ""
echo "----------------------------------------------------------------------"
echo "  METHOD 1: Wireless Debugging (Android 11+)"
echo "----------------------------------------------------------------------"
echo "  1. On your phone: Settings > Developer Options > Enable 'Wireless Debugging'"
echo "  2. Tap 'Pair device with pairing code'"
echo "  3. Run:"
echo "     ./scripts/connect_device.sh pair <IP>:<PAIR_PORT> <6-DIGIT-CODE>"
echo "  4. Then connect to the main IP:PORT shown on the Wireless Debugging screen:"
echo "     ./scripts/connect_device.sh <IP>:<PORT>"
echo ""
echo "----------------------------------------------------------------------"
echo "  METHOD 2: Standard Wi-Fi ADB (Port 5555)"
echo "----------------------------------------------------------------------"
echo "  If your phone has ADB TCP enabled:"
echo "     ./scripts/connect_device.sh <PHONE_IP>:5555"
echo ""
echo "----------------------------------------------------------------------"
echo "  METHOD 3: USB Cable via Windows WSL2 (usbipd)"
echo "----------------------------------------------------------------------"
echo "  If plugged into PC via USB cable, open Windows PowerShell as Admin:"
echo "     usbipd list"
echo "     usbipd wsl attach --busid <BUSID>"
echo "  Then re-run this script."
echo "======================================================================"

# Handle 'pair' command
if [ "${1:-}" = "pair" ]; then
    if [ "${2:-}" = "" ] || [ "${3:-}" = "" ]; then
        echo "Usage: ./scripts/connect_device.sh pair <IP:PORT> <PAIRING_CODE>"
        exit 1
    fi
    echo "Pairing with $2 using code $3..."
    adb pair "$2" "$3"
    echo "Pairing complete. Now run: ./scripts/connect_device.sh <IP:CONNECT_PORT>"
fi

exit 1
