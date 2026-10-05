# Android Dumbphone Engine: Architecture, ADB Tooling & Engineering Directives

> **System Target:** Google Pixel 4a (and generic Android 10+ devices)  
> **Philosophy:** Intentional digital minimalism, distraction eradication, and single-purpose utility.  
> **Primary Control Plane:** Android Debug Bridge (`adb`) via WSL2 Linux interop (`/mnt/d/platform-tools/adb.exe` / `~/.local/bin/adb`).  
> **Repository Purpose:** Automated provisioning, debloating, aesthetic minimalism, and state management for transforming consumer smartphones into distraction-free devices.

---

## 1. Safety Controls & Operating Policies

> [!IMPORTANT]
> **Safe Package Removal vs. Permanent Deletion Policy:**
> - **NEVER** use irreversible system wipes or hard deletion of system partitions without an explicit fallback.
> - **Always use `pm uninstall -k --user 0 <package>`**: The `-k` flag retains app cache/data, and `--user 0` only removes the app for the primary user while preserving the APK in `/system` or `/product`.
> - **Any stripped system app can be instantly restored** with zero data loss or factory resets using:
>   ```bash
>   adb shell cmd package install-existing <package>
>   ```

> [!IMPORTANT]
> **Preserved Essentials Whitelist (Never Strip):**
> - **Phone / Telephony:** Core dialer, SIM toolkit, telephony providers (`com.google.android.dialer`, `com.android.phone`).
> - **Messaging / SMS:** Default SMS carrier apps and emergency broadcasts.
> - **Navigation / Maps:** Google Maps (`com.google.android.apps.maps`) preserved for safe transport.
> - **Audio / Music:** Spotify (`com.spotify.music`) preserved for audiobooks, podcasts, and offline listening.
> - **Camera & Photos:** Preserved for utility capture (`com.google.android.apps.photos`, `com.google.android.GoogleCamera`).
> - **Web Rendering Infrastructure:** Android System WebView (`com.google.android.webview`) and Chrome (`com.android.chrome`) must remain enabled for auth popups and core system components.

> [!IMPORTANT]
> **Recursive Documentation Directive:**
> - **Any modification to phone settings, package lists, scripts, or ADB workflows MUST be recursively recorded in this document.**
> - Whenever a new package is identified as bloat or essential, or a system UI setting is tuned, agents and developers must append the change to Section 8 ("Recursive Modification Ledger") or use the helper utility:
>   ```bash
>   ./scripts/add_tweak.sh package --pkg <id> --action <remove|preserve> --category <cat> --reason <why>
>   ./scripts/add_tweak.sh setting --ns <system|secure|global> --key <key> --val <val> --desc <desc>
>   ./scripts/add_tweak.sh note --text "<engineering observation>"
>   ```

---

## 2. ADB Tooling & WSL Environment Setup

### 2.1 Binary Resolution & Aliases
The development workstation runs **WSL2 (Ubuntu Linux)** on top of a Windows host. The official Android SDK platform-tools are located on the host drive:
* **Host Platform Tools:** `D:\platform-tools\adb.exe`
* **WSL Mounted Path:** `/mnt/d/platform-tools/adb.exe`
* **Bash Alias (`~/.bashrc`):** `alias adb='/mnt/d/platform-tools/adb.exe'`
* **Global CLI Wrapper (`~/.local/bin/adb`):** Wrapper script ensuring `adb` functions seamlessly in non-interactive subshells, build scripts, and automated agents.

### 2.2 Device Connection & Verification
* **USB Debugging:** Must be enabled on device via *Settings > About Phone > Tap 'Build Number' 7 times > Developer Options > USB Debugging*.
* **Checking Connection:**
  ```bash
  adb devices
  adb wait-for-device
  adb get-state  # Expect 'device'
  ```
* **Wireless ADB (Optional):**
  ```bash
  # Pair device over Wi-Fi (Android 11+):
  adb pair <ip>:<port> <pairing_code>
  adb connect <ip>:<port>
  ```

---

## 3. Repository Architecture & Script Inventory

```
dumb-phone/
├── AGENTS.md                  # Master architecture, ADB reference & recursive ledger
├── agents.md                  # Symlink to AGENTS.md for universal agent indexing
├── .gitignore                 # Excludes temp databases, crash logs, and binary caches
├── provision_dumbphone.sh     # Primary automated provisioning & debloating engine
├── revert_dumbphone.sh        # Complete rollback script restoring stock smartphone state
├── scripts/
│   └── add_tweak.sh           # Interactive CLI to recursively record tweaks to AGENTS.md
├── dumb_launcher.apk          # Minimal launcher binary (Olauncher / Minimalist UI)
└── olauncher.apk              # Upstream Olauncher APK reference
```

### 3.1 `provision_dumbphone.sh` Pipeline
The provisioning script executes a sequenced 9-stage pipeline:
1. **Device Handshake:** Waits for ADB connection and verifies authorization state.
2. **Work Profile Removal:** Eliminates secondary or work user profiles (`pm remove-user 10`).
3. **Core Utility Assurance:** Force-enables WebView and Chrome.
4. **Keyguard & Lock Bypass:** Clears active PIN/pattern credential and disables lock screen completely (`locksettings clear`, `locksettings set-disabled true`), enabling instant tap-to-wake.
5. **Minimal Launcher Installation:** Deploys lightweight launcher APK (`dumb_launcher.apk`).
6. **Default Home Role Enforcement:** Whitelists launcher against battery optimization, assigns `android.app.role.HOME`, sets home activity, and disables stock Pixel launcher (`com.google.android.apps.nexuslauncher`) and Google setup wizards.
7. **Dumbphone Aesthetic Engine:**
   - Enables Monochromacy / Grayscale (`accessibility_display_daltonizer_enabled 1`, `accessibility_display_daltonizer 0`).
   - Forces System AMOLED Dark Mode (`cmd uimode night yes`).
   - Suppresses heads-up banner notifications (`heads_up_notifications_enabled 0`).
   - Removes battery percentage clutter (`status_bar_show_battery_percent 0`).
   - Blacklists status bar clutter icons (`icon_blacklist wifi,bluetooth,alarm_clock,volume`).
   - Scales typography for hardware feel (`font_scale 1.12`).
   - Hides navigation pill bar (`hide_gesture_line 1`).
8. **Categorized Debloat:** Iteratively uninstalls 60+ distraction, social media, shopping, delivery, gaming, and telemetry packages for user 0.
9. **Verification & Soft Reboot:** Validates retained third-party packages and reboots into the minimalist environment.

### 3.2 `revert_dumbphone.sh` Pipeline
Restores full factory stock smartphone functionality in under 30 seconds:
1. Disables grayscale daltonizer (restores full color display).
2. Re-enables stock Pixel Launcher (`com.google.android.apps.nexuslauncher`) and sets it as default home activity.
3. Re-installs core Google packages: Google Play Store (`com.android.vending`), Google Wallet (`com.google.android.apps.walletnfcrel`), Google Photos, Google App/Search, and YouTube.
4. Resets system UI flags (restores heads-up notifications and battery percentage indicator).

---

## 4. Android Subsystem & ADB Command Reference

### 4.1 Package Management (`pm` & `cmd package`)
* **List installed packages:**
  ```bash
  adb shell pm list packages -3        # Third-party only
  adb shell pm list packages -d        # Disabled packages only
  adb shell pm list packages -e        # Enabled packages only
  adb shell pm list packages -s        # System packages only
  adb shell pm list packages | grep <term>
  ```
* **Debloat (user-level uninstallation):**
  ```bash
  adb shell pm uninstall -k --user 0 <package_name>
  ```
* **Restore uninstalled system package:**
  ```bash
  adb shell cmd package install-existing <package_name>
  ```
* **Disable / Enable package without uninstalling:**
  ```bash
  adb shell pm disable-user --user 0 <package_name>
  adb shell pm enable --user 0 <package_name>
  ```
* **Query app path / APK location:**
  ```bash
  adb shell pm path <package_name>
  ```

### 4.2 Settings Subsystem (`settings`)
Android maintains three settings namespaces: `system`, `secure`, and `global`.
* **Read a setting:**
  ```bash
  adb shell settings get <system|secure|global> <key>
  ```
* **Write a setting:**
  ```bash
  adb shell settings put <system|secure|global> <key> <value>
  ```
* **Delete / Reset a setting:**
  ```bash
  adb shell settings delete <system|secure|global> <key>
  ```

### 4.3 Key Settings Reference for Minimalism
| Namespace | Key | Target Value | Effect |
| :--- | :--- | :--- | :--- |
| `secure` | `accessibility_display_daltonizer_enabled` | `1` | Enables color filter engine |
| `secure` | `accessibility_display_daltonizer` | `0` | Sets filter mode to Monochromacy (Grayscale) |
| `secure` | `icon_blacklist` | `wifi,bluetooth,alarm_clock,volume` | Cleans status bar icon clutter |
| `secure` | `doze_double_tap_gesture` | `1` | Double-tap screen to wake |
| `secure` | `doze_pulse_on_pick_up` | `1` | Lift phone to wake screen |
| `global` | `heads_up_notifications_enabled` | `0` | Disables invasive pop-down notification banners |
| `global` | `hide_gesture_line` | `1` | Hides the bottom navigation gesture indicator |
| `system` | `status_bar_show_battery_percent` | `0` | Hides battery percentage number |
| `system` | `font_scale` | `1.12` | Slightly enlarged tactile typography |

### 4.4 Home Launcher & Role Management
* **Assign Home Role to Launcher:**
  ```bash
  adb shell cmd role add-role-holder android.app.role.HOME <launcher_package>
  adb shell cmd package set-home-activity <launcher_package>/<activity>
  ```
* **Exempt from Battery Optimization (prevent process kill):**
  ```bash
  adb shell dumpsys deviceidle whitelist +<launcher_package>
  ```

### 4.5 Security & Lockscreen Control
* **Clear Screen Lock Credential:**
  ```bash
  adb shell locksettings clear --old "<PIN>" # If PIN set
  adb shell locksettings clear               # If no PIN
  ```
* **Disable Keyguard / Lock Screen Entirely:**
  ```bash
  adb shell locksettings set-disabled true
  ```

---

## 5. Bloatware & Distraction Package Catalog

The following categorized domains are targeted for removal during dumbphone provisioning:

### 5.1 App Stores & Download Gateways
* `com.android.vending` — Google Play Store
* `com.google.android.feedback` — Play Store feedback agent

### 5.2 Banking, Wallet & Trading
* `com.google.android.apps.walletnfcrel` — Google Wallet
* `uk.co.santander.santanderUK` — Santander UK
* `co.uk.getmondo` — Monzo
* `com.americanexpress.android.acctsvcs.uk` — Amex UK
* `com.avuscapital.trading212` — Trading 212
* `uk.gov.hmrc.ptcalc` — HMRC Tax Calculator
* `com.gigasure.gigasureapp` — GigaSure

### 5.3 Social Media, News & Feeds
* `com.zhiliaoapp.musically` — TikTok
* `com.instagram.android` — Instagram
* `com.facebook.katana` — Facebook
* `com.linkedin.android` — LinkedIn
* `com.google.android.googlequicksearchbox` — Google App & Discover Feed

### 5.4 Video & Entertainment Streaming
* `com.google.android.youtube` — YouTube
* `com.google.android.apps.youtube.music` — YouTube Music
* `com.google.android.videos` — Google TV
* `com.netflix.mediaclient` — Netflix
* `com.disney.disneyplus` — Disney+
* `bbc.iplayer.android` — BBC iPlayer
* `com.bskyb.skyservice` — Sky
* `com.shazam.android` — Shazam

### 5.5 Games & Casual Distractions
* `com.supercell.clashroyale` — Clash Royale
* `com.miniclip.plagueinc` — Plague Inc
* `com.moonfrog.ludo.club` — Ludo Club

### 5.6 Food Delivery & Fast Commerce
* `com.ubercab` — Uber
* `com.deliveroo.orderapp` — Deliveroo
* `com.amazon.mShop.android.shopping` — Amazon Shopping
* `com.tesco.grocery.view` — Tesco Grocery
* `com.asda.android` — Asda
* `com.lidl.eci.lidlplus` — Lidl Plus
* `nandos.android.app` — Nando's
* `com.stonegate.mixr` — Mixr Bar Loyalty
* `com.ga.loyalty.android.nectar.activities` — Nectar Loyalty

### 5.7 Travel Booking & Real Estate
* `com.thetrainline` — Trainline
* `com.ryanair.cheapflights` — Ryanair
* `net.skyscanner.android.main` — Skyscanner
* `com.booking` — Booking.com
* `com.airbnb.android` — Airbnb
* `com.ni.discovercars` — Discover Cars
* `com.ba.mobile` — British Airways
* `com.rightmove.android` — Rightmove

### 5.8 Telemetry, Assistant & Background Sync
* `com.google.android.apps.bard` — Google Gemini
* `com.google.android.apps.googleassistant` — Google Assistant
* `com.google.android.apps.translate` — Google Translate
* `com.google.android.apps.podcasts` — Google Podcasts
* `com.google.android.apps.chromecast.app` — Google Home
* `com.google.android.apps.cloudconsole` — Google Cloud Console
* `com.google.android.keep` — Google Keep
* `com.google.android.earth` — Google Earth
* `com.google.android.apps.pixelmigrate` — Pixel Data Transfer
* `com.google.android.contactkeys` — Contact Keys
* `com.google.android.safetycore` — Safety Core
* `com.microsoft.windowsintune.companyportal` — Intune Portal

---

## 6. Recursive Documentation Protocol (Self-Updating Directive)

To ensure this knowledge base never becomes stale, all changes to this repository and connected Android devices must follow the **Recursive Update Cycle**:

```
[1. Discover / Test Tweak] ──> [2. Validate Device State]
            │                               │
            ▼                               ▼
[4. Sync Provisioning Scripts] <── [3. Record in AGENTS.md Ledger]
```

### 6.1 Recursive Rules for AI Agents & Developers
1. **Rule 1: Never edit without documenting.** If a package is added to the debloat list, removed from exclusion, or a new system setting is discovered, immediately record it in Section 8 ("Recursive Modification Ledger").
2. **Rule 2: Symmetric Reversibility.** For every provisioning tweak or package removal added, the inverse recovery action must be added to `revert_dumbphone.sh`.
3. **Rule 3: Use the CLI Helper.** You can invoke `scripts/add_tweak.sh` from any terminal or agent to append changes directly without manual formatting:
   ```bash
   ./scripts/add_tweak.sh package --pkg com.example.distraction --action remove --category "Social" --reason "Infinite feed"
   ./scripts/add_tweak.sh setting --ns secure --key display_density_forced --val 400 --desc "Increase UI density"
   ./scripts/add_tweak.sh note --text "Verified that Pixel 4a Android 13 preserves adb authorization across reboots."
   ```
4. **Rule 4: Automated Auditing.** Periodically run `./scripts/add_tweak.sh audit` to query the connected device for newly installed third-party apps that have not yet been evaluated.

---

## 7. Troubleshooting & Recovery FAQ

* **Device says "unauthorized" in `adb devices`:**
  - Check the phone screen. A prompt will ask "Allow USB debugging?". Check "Always allow from this computer" and tap OK.
  - If prompt doesn't appear: `adb kill-server && adb start-server`.
* **WSL cannot see ADB device:**
  - Verify device is recognized on Windows host via `cmd.exe /c adb devices`.
  - Ensure Windows ADB server is running on default port `5037`.
* **Accidentally uninstalled an essential utility (e.g. Camera or Messages):**
  - Execute: `adb shell cmd package install-existing <package_name>`
* **Locked out of Launcher or black screen:**
  - Re-enable the stock Pixel launcher:
    ```bash
    adb shell pm enable --user 0 com.google.android.apps.nexuslauncher
    adb shell cmd package set-home-activity com.google.android.apps.nexuslauncher/.NexusLauncherActivity
    ```
* **Color display recovery:**
  ```bash
  adb shell settings put secure accessibility_display_daltonizer_enabled 0
  ```

---

## 8. Recursive Modification Ledger

*All newly discovered packages, UI adjustments, and hardware behavioral observations must be logged below chronologically.*

- **[2026-10-04 17:40:00] Initial Baseline Engine**: Established Pixel 4a dumbphone provisioning scripts (`provision_dumbphone.sh` and `revert_dumbphone.sh`).
- **[2026-10-04 17:43:00] Minimal Launcher Binary Validation**: Verified Olauncher v6.9.17 release binary download with size sanity check (>500KB) to prevent 9-byte stub corruption.
- **[2026-10-05 20:25:00] WSL ADB CLI Integration**: Configured Windows platform-tools bridge (`/mnt/d/platform-tools/adb.exe`) with global wrapper at `~/.local/bin/adb` and `.bashrc` alias.
- **[2026-10-05 20:28:00] Recursive Ledger Utility**: Created `scripts/add_tweak.sh` to automate appending package modifications, system settings, and engineering notes directly to this documentation.

- **[2026-10-05 20:28:53] Discovery / Engineering Note**:
  - Verified wrapper in ~/.local/bin/adb for non-interactive WSL shell execution.
