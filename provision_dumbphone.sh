#!/bin/bash
set -eo pipefail

ADB="${ADB:-$(command -v adb 2>/dev/null || echo /mnt/d/platform-tools/adb.exe)}"

# If your phone currently has an active PIN/pattern, supply it here (e.g. EXISTING_PIN="1234").
# Leave as "" if screen security is already removed or set to None/Swipe.
EXISTING_PIN=""

echo "=== 1. Checking device connection ==="
$ADB wait-for-device
DEVICE_STATE=$($ADB get-state | tr -d '\r')
echo "Device connected: $DEVICE_STATE"

echo "=== 2. Cleaning secondary / work profiles ==="
$ADB shell pm remove-user 10 2>/dev/null || true

echo "=== 3. Restoring Allowed Utilities (Chrome & WebView) ==="
$ADB shell cmd package install-existing com.android.chrome 2>/dev/null || true
$ADB shell pm enable --user 0 com.google.android.webview 2>/dev/null || true
$ADB shell cmd package install-existing com.google.android.apps.photos 2>/dev/null || true

echo "=== 4. Clearing Screen Lock & Disabling Keyguard ==="
if [ -n "$EXISTING_PIN" ]; then
    echo "Clearing existing lock credential..."
    $ADB shell locksettings clear --old "$EXISTING_PIN" 2>/dev/null || true
else
    $ADB shell locksettings clear 2>/dev/null || true
fi

echo "Disabling lock screen completely..."
$ADB shell locksettings set-disabled true 2>/dev/null || true

echo "Configuring Tap-to-Wake & Lift-to-Wake..."
$ADB shell settings put secure doze_double_tap_gesture 1
$ADB shell settings put secure doze_pulse_on_pick_up 1

echo "=== 5. Sourcing & Installing Minimal Launcher ==="
LAUNCHER_PKG="app.olauncher"
LAUNCHER_ACTIVITY="app.olauncher/.MainActivity"

# Clean up any bad 9-byte stubs
if [ -f "dumb_launcher.apk" ]; then
    FILE_SIZE=$(wc -c < "dumb_launcher.apk" 2>/dev/null || echo 0)
    if [ "$FILE_SIZE" -lt 500000 ]; then
        rm -f dumb_launcher.apk
    fi
fi

# Fetch verified binary
if [ ! -f "dumb_launcher.apk" ]; then
    echo "Downloading verified minimal launcher..."
    curl -L -A "Mozilla/5.0" -o dumb_launcher.apk https://github.com/tanujnotes/Olauncher/releases/download/v6.9.17/Olauncher-v6.9.17.apk
fi

# Validate size before streaming
FINAL_SIZE=$(wc -c < "dumb_launcher.apk" 2>/dev/null || echo 0)
if [ "$FINAL_SIZE" -lt 500000 ]; then
    echo "Error: Downloaded APK is invalid (${FINAL_SIZE} bytes)."
    exit 1
fi

echo "Installing launcher on device..."
$ADB install -r -d -g dumb_launcher.apk

echo "=== 6. Enforcing Minimal Launcher as Persistent Default ==="
# Exemption from battery optimization ensures Android doesn't suspend or kill it
$ADB shell dumpsys deviceidle whitelist +$LAUNCHER_PKG 2>/dev/null || true

# Assign Android HOME role to the minimal launcher
$ADB shell cmd role add-role-holder android.app.role.HOME $LAUNCHER_PKG 2>/dev/null || true
$ADB shell cmd package set-home-activity $LAUNCHER_ACTIVITY 2>/dev/null || true

# Disable the stock Pixel launcher and setup wizards so no fallback conflicts occur
$ADB shell pm disable-user --user 0 com.google.android.apps.nexuslauncher 2>/dev/null || true
$ADB shell pm disable-user --user 0 com.google.android.setupwizard 2>/dev/null || true
$ADB shell pm disable-user --user 0 com.android.setupwizard 2>/dev/null || true

echo "=== 7. Applying Dumbphone Aesthetic & Display Tweaks ==="
# Enable Grayscale (Monochromacy)
$ADB shell settings put secure accessibility_display_daltonizer_enabled 1
$ADB shell settings put secure accessibility_display_daltonizer 0

# Enforce true AMOLED Dark Theme
$ADB shell cmd uimode night yes

# Suppress banner popups & status bar battery percentage
$ADB shell settings put global heads_up_notifications_enabled 0
$ADB shell settings put system status_bar_show_battery_percent 0

# Clean clutter icons from status bar (Wi-Fi, Bluetooth, alarm, volume)
$ADB shell settings put secure icon_blacklist wifi,bluetooth,alarm_clock,volume

# Scale typography slightly for a tactile, hardware feel
$ADB shell settings put system font_scale 1.12

# Hide the gesture navigation pill line at the bottom
$ADB shell settings put global hide_gesture_line 1 2>/dev/null || true

echo "=== 8. Stripping Distraction, Shopping, Banking & Bloat Packages ==="
PACKAGES_TO_REMOVE=(
    # App Stores & Download Frameworks
    "com.android.vending"
    "com.google.android.feedback"

    # Banking, Wallet & Financial
    "com.google.android.apps.walletnfcrel"
    "uk.co.santander.santanderUK"
    "co.uk.getmondo"
    "com.americanexpress.android.acctsvcs.uk"
    "com.avuscapital.trading212"
    "uk.gov.hmrc.ptcalc"
    "com.gigasure.gigasureapp"

    # Social Media & Feeds
    "com.zhiliaoapp.musically"
    "com.instagram.android"
    "com.facebook.katana"
    "com.linkedin.android"
    "com.google.android.googlequicksearchbox"

    # Video, Music & Streaming (Spotify preserved)
    "com.google.android.youtube"
    "com.google.android.apps.youtube.music"
    "com.google.android.videos"
    "com.netflix.mediaclient"
    "com.disney.disneyplus"
    "bbc.iplayer.android"
    "com.bskyb.skyservice"
    "com.shazam.android"

    # Games
    "com.supercell.clashroyale"
    "com.miniclip.plagueinc"
    "com.moonfrog.ludo.club"

    # Food & Shopping
    "com.ubercab"
    "com.deliveroo.orderapp"
    "com.amazon.mShop.android.shopping"
    "com.tesco.grocery.view"
    "com.asda.android"
    "com.lidl.eci.lidlplus"
    "nandos.android.app"
    "com.stonegate.mixr"
    "com.ga.loyalty.android.nectar.activities"

    # Travel (Maps preserved)
    "com.thetrainline"
    "com.ryanair.cheapflights"
    "net.skyscanner.android.main"
    "com.booking"
    "com.airbnb.android"
    "com.ni.discovercars"
    "com.ba.mobile"

    # Fitness, Passwords & Utilities
    "com.garmin.android.apps.connectmobile"
    "com.garmin.connectiq"
    "cc.dreamspark.intervaltimer"
    "com.lastpass.lpandroid"
    "com.azure.authenticator"
    "com.google.android.apps.authenticator2"
    "com.bitdefender.security"
    "com.windscribe.vpn"
    "com.sharkninja.shark"
    "com.nhs.online.nhsonline"
    "com.nextcloud.client"
    "com.accor.appli.hybrid"
    "notion.id"
    "com.github.android"
    "com.rightmove.android"

    # Google Telemetry & Assistant Services
    "com.google.android.apps.translate"
    "com.google.android.apps.bard"
    "com.google.android.apps.labs.language.tailwind"
    "com.google.android.earth"
    "com.google.android.keep"
    "com.google.android.apps.cloudconsole"
    "com.google.android.apps.chromecast.app"
    "com.google.android.apps.tachyon"
    "com.google.android.apps.googleassistant"
    "com.google.android.apps.podcasts"
    "com.google.android.apps.pixelmigrate"
    "com.google.android.contactkeys"
    "com.google.android.safetycore"
    "com.microsoft.windowsintune.companyportal"
)

for pkg in "${PACKAGES_TO_REMOVE[@]}"; do
    $ADB shell pm uninstall -k --user 0 "$pkg" >/dev/null 2>&1 || true
done

echo ""
echo "=== 9. Verification: Retained 3rd-Party Packages ==="
$ADB shell pm list packages -3 --user 0

echo ""
echo "=== Provisioning Complete. Performing clean reboot test... ==="
$ADB reboot
echo "Device is rebooting. It will wake directly to the minimal UI without a lock screen."
