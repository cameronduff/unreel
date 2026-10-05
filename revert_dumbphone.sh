#!/bin/bash
set -eo pipefail

ADB="${ADB:-$(command -v adb 2>/dev/null || echo /mnt/d/platform-tools/adb.exe)}"

echo "=== 1. Checking device connection ==="
$ADB wait-for-device
DEVICE_STATE=$($ADB get-state | tr -d '\r')
echo "Device connected in state: $DEVICE_STATE"

echo "=== 2. Disabling Grayscale (Restoring Color) ==="
$ADB shell settings put secure accessibility_display_daltonizer_enabled 0
echo "Color display restored."

echo "=== 3. Restoring Stock Pixel Launcher ==="
$ADB shell pm enable --user 0 com.google.android.apps.nexuslauncher 2>/dev/null || true
$ADB shell cmd package set-home-activity com.google.android.apps.nexuslauncher/.NexusLauncherActivity 2>/dev/null || true
echo "Stock launcher restored."

echo "=== 4. Restoring Core Google Services ==="
CORE_PACKAGES_TO_RESTORE=(
    "com.android.vending"
    "com.google.android.apps.walletnfcrel"
    "com.google.android.apps.photos"
    "com.google.android.googlequicksearchbox"
    "com.google.android.youtube"
)

for pkg in "${CORE_PACKAGES_TO_RESTORE[@]}"; do
    echo "Restoring $pkg..."
    $ADB shell cmd package install-existing "$pkg" >/dev/null 2>&1 || true
done

echo "=== 5. Resetting System UI Flags ==="
$ADB shell settings put global heads_up_notifications_enabled 1
$ADB shell settings put system status_bar_show_battery_percent 1

echo ""
echo "=== Revert Complete! ==="
echo "The Pixel 4a has been returned to its standard smartphone configuration."
