# Android Dumbphone Engine: Architecture, PoC Trade-offs & Engineering Directives

> **System Target:** Google Pixel 4a (and generic Android 10+ devices)  
> **Repository Nature:** Active **Proof of Concept (PoC)** — a sandbox for rapid experimentation across launchers, debloat strategies, system settings, and UX friction models.  
> **Philosophy:** Intentional digital minimalism, distraction eradication, and single-purpose utility.  
> **Primary Control Plane:** Android Debug Bridge (`adb`) via WSL2 Linux interop (`/mnt/d/platform-tools/adb.exe` / `~/.local/bin/adb`).  
> **Repository Purpose:** Automated provisioning, debloating, aesthetic minimalism, and state management for transforming consumer smartphones into distraction-free devices.

---

## 1. PoC Architecture & Trade-Off Analysis (Pros & Cons)

Because this repository is an active Proof of Concept, multiple approaches are actively tested. The following matrix details the pros and cons of each architectural approach trialed.

### 1.1 Debloating Strategy: User-Space (`pm uninstall --user 0`) vs. Root / Custom ROM

| Approach | Pros (+) | Cons (-) | PoC Verdict |
| :--- | :--- | :--- | :--- |
| **User-Space Debloating (`pm uninstall -k --user 0`)** | • 100% reversible via `cmd package install-existing`<br>• Zero bootloader unlocking or tripping SafetyNet/Play Integrity<br>• Fast test iterations (no full device wipe needed)<br>• Survives soft reboots | • Package APKs remain on `/system` or `/product` partitions (does not free raw flash storage)<br>• Occasional system background services can still poll status unless disabled via `pm disable-user` | **Adopted as primary PoC standard** |
| **Rooting / Magisk / KernelSU** | • Total system partition read-write access<br>• Ability to permanently erase APKs and inject custom kernel modules | • High bricking risk during rapid trials<br>• Breaks banking/authenticator apps if SafetyNet fails<br>• Requires unlocked bootloader wipe | *Deferred / Rejected for PoC phase* |
| **Custom Privacy ROM (GrapheneOS / LineageOS)** | • Strips Google Play Services at OS compile level<br>• Per-app network and sensor permission toggles | • High setup friction for quick ADB tweaks<br>• Pixel 4a (sunfish) is legacy AOSP (Android 13 max official support)<br>• Difficult to revert in under 60 seconds | *Reserved for dedicated hardware phase* |

### 1.2 Display Aesthetic: System Daltonizer Grayscale vs. Bedtime Mode vs. E-Ink / Overlays

| Approach | Pros (+) | Cons (-) | PoC Verdict |
| :--- | :--- | :--- | :--- |
| **Monochromacy Daltonizer (`accessibility_display_daltonizer 0`)** | • Hardware GPU-level color matrix conversion<br>• Zero battery overhead<br>• Instantly kills visual dopamine triggers across all apps, web pages, and video<br>• Works uniformly across entire OS | • Makes color-coded transit maps (London Underground, Google Maps traffic lines) difficult to read<br>• Camera viewfinder displays in B&W (captured photos remain full color)<br>• QR code contrast can occasionally be degraded | **Adopted as primary PoC standard** |
| **Digital Wellbeing Bedtime Mode** | • Retains color during scheduled daylight hours<br>• Native UI quick settings tile | • Easy to bypass or turn off when experiencing cravings<br>• Intertwined with Google Digital Wellbeing telemetry packages | *Rejected (too easy to bypass)* |
| **Third-Party Screen Filter Overlays** | • Configurable tint and contrast levels | • High battery draw (draws over other apps)<br>• Accessibility service can be killed by Android battery manager | *Rejected* |

### 1.3 Minimalist Launcher: Text-Based (Olauncher) vs. Stripped Stock Pixel Launcher

| Approach | Pros (+) | Cons (-) | PoC Verdict |
| :--- | :--- | :--- | :--- |
| **Text-Based Launcher (`app.olauncher` / Minimalist UI)** | • Eliminates all app icons, colors, and badge notification dots<br>• Pure typographic search enforces intentional launch (must type app name)<br>• Instant home screen load time (~2MB binary footprint)<br>• Built-in daily minimal wallpaper support | • Android gesture navigation (swiping home) can exhibit minor animation jitter compared to OEM launcher<br>• Requires manually granting `android.app.role.HOME` via ADB | **Adopted as primary PoC standard** |
| **Stripped Stock Pixel Launcher (`nexuslauncher`)** | • Flawless native gesture fluid animations and recents integration | • Always displays Google search bar dock and "At a Glance" widget<br>• Visual app grid still tempts subconscious icon-tapping | *Relegated to fallback/revert only* |
| **Dedicated Hardware E-Ink Device (Light Phone / Mudita)** | • Absolute hardware distraction barrier | • Costly proprietary hardware ($300+)<br>• Lacks essential utilities (e.g. Spotify offline podcasts, Google Maps navigation) | *Benchmarked against this PoC* |

### 1.4 Screen Security: Keyguard Elimination vs. Biometric / PIN Lock

| Approach | Pros (+) | Cons (-) | PoC Verdict |
| :--- | :--- | :--- | :--- |
| **Lock Screen Elimination (`locksettings set-disabled true`)** | • Instant tap-to-wake / lift-to-wake directly into launcher<br>• Phone behaves like a physical notebook, pager, or calculator<br>• Eliminates lock screen notification grazing | • Zero physical theft or privacy protection if lost<br>• Accidental pocket dialing / screen wakes if sensor triggers<br>• Invalidates Google Wallet / contactless payment cards | **Adopted for dedicated dumbphone mode** |
| **Standard Biometric / PIN Lock** | • Secures device data and credentials | • Introduces unlocking friction; lock screen becomes a passive notification display surface | *Configurable via `EXISTING_PIN` in script* |

### 1.5 Telemetry & Services: Wholesale GMS Removal vs. Selective App Debloat

| Approach | Pros (+) | Cons (-) | PoC Verdict |
| :--- | :--- | :--- | :--- |
| **Selective App Debloat (Preserve GMS Core)** | • Push notifications continue to work via FCM (Firebase Cloud Messaging)<br>• Google Maps retains high-accuracy fused GPS location<br>• Android System WebView and Chrome remain functional for OAuth logins<br>• Play Store (`com.android.vending`) is removed to prevent installing new distractions | • Background Google sync services still consume small telemetry bandwidth | **Adopted as primary PoC standard** |
| **Total GMS Deprivation (Removing Play Services)** | • Zero Google telemetry or data harvesting | • Breaks push notifications for communication apps<br>• Breaks Maps geocoding and location tracking<br>• Severe system crash loops on stock Pixel firmware | *Rejected due to stability failures* |

### 1.6 Notification UI: Banner Suppression & Status Bar Blacklisting

| Approach | Pros (+) | Cons (-) | PoC Verdict |
| :--- | :--- | :--- | :--- |
| **Heads-Up Banner Suppression (`heads_up_notifications_enabled 0`)** | • Eliminates invasive top pop-down banners while viewing content<br>• Pulls user out of reactive mode (messages wait in notification shade until intentional pull-down) | • High-priority time-sensitive alerts (e.g. delivery driver arriving, 2FA SMS code) require manual shade pull | **Adopted as primary PoC standard** |
| **Status Bar Icon Blacklisting (`icon_blacklist`)** | • Hides clutter icons (Wi-Fi, Bluetooth, alarm, volume, battery %)<br>• Reduces subconscious battery anxiety and visual noise | • User cannot instantly see if Bluetooth headphones are connected without opening Quick Settings | **Adopted as primary PoC standard** |

---

## 2. Safety Controls & Operating Policies

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
> **Recursive Documentation Directive (Self-Updating Directive):**
> - **Any modification to phone settings, package lists, scripts, or ADB workflows MUST be recursively recorded in this document.**
> - Whenever a new trial is attempted, a package is identified as bloat or essential, or a system UI setting is tuned, agents and developers must append the change to Section 8 ("Recursive Modification Ledger") or use the helper utility:
>   ```bash
>   ./scripts/add_tweak.sh poc --name "<title>" --pros "<pros>" --cons "<cons>" --verdict "<verdict>"
>   ./scripts/add_tweak.sh package --pkg <id> --action <remove|preserve> --category <cat> --reason <why>
>   ./scripts/add_tweak.sh setting --ns <system|secure|global> --key <key> --val <val> --desc <desc>
>   ./scripts/add_tweak.sh note --text "<engineering observation>"
>   ```

---

## 3. ADB Tooling & WSL Environment Setup

### 3.1 Binary Resolution & Aliases
The development workstation runs **WSL2 (Ubuntu Linux)** on top of a Windows host. The official Android SDK platform-tools are located on the host drive:
* **Host Platform Tools:** `D:\platform-tools\adb.exe`
* **WSL Mounted Path:** `/mnt/d/platform-tools/adb.exe`
* **Bash Alias (`~/.bashrc`):** `alias adb='/mnt/d/platform-tools/adb.exe'`
* **Global CLI Wrapper (`~/.local/bin/adb`):** Wrapper script ensuring `adb` functions seamlessly in non-interactive subshells, build scripts, and automated agents.

### 3.2 Device Connection & Verification
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

## 4. Repository Architecture & Script Inventory

```
dumb-phone/
├── AGENTS.md                  # Master architecture, PoC trade-offs, ADB reference & ledger
├── .gitignore                 # Excludes temp databases, crash logs, and binary caches
├── provision_dumbphone.sh     # Primary automated provisioning & debloating engine
├── revert_dumbphone.sh        # Complete rollback script restoring stock smartphone state
├── scripts/
│   └── add_tweak.sh           # Interactive CLI to recursively record tweaks & PoC trials to AGENTS.md
├── dumb_launcher.apk          # Minimal launcher binary (Olauncher / Minimalist UI)
└── olauncher.apk              # Upstream Olauncher APK reference
```

### 4.1 `provision_dumbphone.sh` Pipeline
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

### 4.2 `revert_dumbphone.sh` Pipeline
Restores full factory stock smartphone functionality in under 30 seconds:
1. Disables grayscale daltonizer (restores full color display).
2. Re-enables stock Pixel Launcher (`com.google.android.apps.nexuslauncher`) and sets it as default home activity.
3. Re-installs core Google packages: Google Play Store (`com.android.vending`), Google Wallet (`com.google.android.apps.walletnfcrel`), Google Photos, Google App/Search, and YouTube.
4. Resets system UI flags (restores heads-up notifications and battery percentage indicator).

---

## 5. Android Subsystem & ADB Command Reference

### 5.1 Package Management (`pm` & `cmd package`)
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

### 5.2 Settings Subsystem (`settings`)
* **Read setting:** `adb shell settings get <system|secure|global> <key>`
* **Write setting:** `adb shell settings put <system|secure|global> <key> <value>`
* **Delete setting:** `adb shell settings delete <system|secure|global> <key>`

---

## 6. Bloatware & Distraction Package Catalog

The following categorized domains are targeted for removal during dumbphone provisioning:

### 6.1 App Stores & Download Gateways
* `com.android.vending` — Google Play Store
* `com.google.android.feedback` — Play Store feedback agent

### 6.2 Banking, Wallet & Trading
* `com.google.android.apps.walletnfcrel` — Google Wallet
* `uk.co.santander.santanderUK` — Santander UK
* `co.uk.getmondo` — Monzo
* `com.americanexpress.android.acctsvcs.uk` — Amex UK
* `com.avuscapital.trading212` — Trading 212
* `uk.gov.hmrc.ptcalc` — HMRC Tax Calculator
* `com.gigasure.gigasureapp` — GigaSure

### 6.3 Social Media, News & Feeds
* `com.zhiliaoapp.musically` — TikTok
* `com.instagram.android` — Instagram
* `com.facebook.katana` — Facebook
* `com.linkedin.android` — LinkedIn
* `com.google.android.googlequicksearchbox` — Google App & Discover Feed

### 6.4 Video & Entertainment Streaming
* `com.google.android.youtube` — YouTube
* `com.google.android.apps.youtube.music` — YouTube Music
* `com.google.android.videos` — Google TV
* `com.netflix.mediaclient` — Netflix
* `com.disney.disneyplus` — Disney+
* `bbc.iplayer.android` — BBC iPlayer
* `com.bskyb.skyservice` — Sky
* `com.shazam.android` — Shazam

### 6.5 Games & Casual Distractions
* `com.supercell.clashroyale` — Clash Royale
* `com.miniclip.plagueinc` — Plague Inc
* `com.moonfrog.ludo.club` — Ludo Club

### 6.6 Food Delivery & Fast Commerce
* `com.ubercab` — Uber
* `com.deliveroo.orderapp` — Deliveroo
* `com.amazon.mShop.android.shopping` — Amazon Shopping
* `com.tesco.grocery.view` — Tesco Grocery
* `com.asda.android` — Asda
* `com.lidl.eci.lidlplus` — Lidl Plus
* `nandos.android.app` — Nando's
* `com.stonegate.mixr` — Mixr Bar Loyalty
* `com.ga.loyalty.android.nectar.activities` — Nectar Loyalty

### 6.7 Travel Booking & Real Estate
* `com.thetrainline` — Trainline
* `com.ryanair.cheapflights` — Ryanair
* `net.skyscanner.android.main` — Skyscanner
* `com.booking` — Booking.com
* `com.airbnb.android` — Airbnb
* `com.ni.discovercars` — Discover Cars
* `com.ba.mobile` — British Airways
* `com.rightmove.android` — Rightmove

### 6.8 Telemetry, Assistant & Background Sync
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

## 7. Recursive Documentation Protocol (Self-Updating Directive)

To ensure this PoC knowledge base never becomes stale, all changes to this repository and connected Android devices must follow the **Recursive Update Cycle**:

```
[1. Formulate Hypothesis / Trial] ──> [2. Execute ADB Tweak on Device]
                 │                                    │
                 ▼                                    ▼
[4. Sync Provision/Revert Scripts] <── [3. Document Pros, Cons & Verdict in Ledger]
```

### 7.1 Protocol Guidelines
1. **Rule 1: Document all Pros & Cons.** When trying a new approach (e.g. testing a different launcher, killing a new background daemon, changing DPI), document the trade-offs in Section 8 using `./scripts/add_tweak.sh poc`.
2. **Rule 2: Symmetric Reversibility.** For every provisioning tweak or package removal tested, an inverse recovery command must exist in `revert_dumbphone.sh`.
3. **Rule 3: Periodic Auditing.** Run `./scripts/add_tweak.sh audit` when reconnecting the phone to detect newly introduced third-party apps.

---

## 8. Recursive Modification Ledger

*All newly discovered packages, UI adjustments, PoC experiment trials, and trade-offs must be logged below chronologically.*

- **[2026-10-04 17:40:00] Initial Baseline Engine**: Established Pixel 4a dumbphone provisioning scripts (`provision_dumbphone.sh` and `revert_dumbphone.sh`).
- **[2026-10-04 17:43:00] Minimal Launcher Binary Validation**: Verified Olauncher v6.9.17 release binary download with size sanity check (>500KB) to prevent 9-byte stub corruption.
- **[2026-10-05 20:25:00] WSL ADB CLI Integration**: Configured Windows platform-tools bridge (`/mnt/d/platform-tools/adb.exe`) with global wrapper at `~/.local/bin/adb` and `.bashrc` alias.
- **[2026-10-05 20:28:00] Recursive Ledger Utility**: Created `scripts/add_tweak.sh` to automate appending package modifications, system settings, and engineering notes directly to this documentation.
- **[2026-10-05 20:28:53] Discovery / Engineering Note**:
  - Verified wrapper in ~/.local/bin/adb for non-interactive WSL shell execution.
- **[2026-10-05 20:30:00] PoC Trade-Off Formalization**: Formatted comprehensive Pros & Cons analysis matrix across debloating strategies, display grayscale modes, minimal launchers, and screen lock policies.

- **[2026-10-05 20:43:11] Discovery / Engineering Note**:
  - Researched SocialLite & architected Unreel native Android Reels-killer companion. Published PRD to Notion and docs/PRD.md.

---

## 9. Unreel Project: Notion Database, Ticket Schema & Status Lifecycle

### 9.1 Notion Workspace & Database Configuration
* **Project Space Page:** [Unreel Project Space](https://app.notion.com/p/Unreel-Open-Source-Distraction-Reels-Eliminator-3f09da56a0de81268bc8fabee329995d) (`3f09da56-a0de-8126-8bc8-fabee329995d`)
* **PRD Document:** [Product Requirements Document (PRD): Unreel v1.0](https://app.notion.com/p/Product-Requirements-Document-PRD-Unreel-v1-0-3f09da56a0de81299f18d92bc76395a6) (`3f09da56-a0de-8129-9f18-d92bc76395a6`)
* **Engineering Roadmap & Tickets Database ID:** `3f09da56-a0de-819a-ab73-fe3c30394233`
* **Engineering Roadmap & Tickets URL:** [Unreel Tickets Board](https://app.notion.com/p/3f09da56a0de819aab73fe3c30394233)

### 9.2 Ticket Status Lifecycle
* **`Draft`**: Requirements under formulation, unresolved architectural questions, or missing verification commands.
* **`Ready for SWE`**: Strictly gated. All required fields are verified, acceptance criteria are concrete and testable, unit/integration verification command is defined, and dependencies are resolved.
* **`In Progress`**: Active implementation in branch.
* **`Done`**: All acceptance criteria pass, regression tests verified via `./gradlew test` or instrumented test runner.

### 9.3 Ticket Schema & Required Fields
Every ticket created in the database MUST contain:
1. **Title:** Imperative, specific, prefixed with ID: `[OL-XX] <Title>`.
2. **Properties:**
   - `Name`: String title.
   - `Status`: `Ready for SWE` (or `Draft`).
   - `Priority`: `P0 - Blocker` | `P1 - High` | `P2 - Medium` | `P3 - Low`.
   - `Type`: `Epic` | `Feature` | `TDD / Test` | `Infra`.
   - `Area`: `Core / Engine` | `Accessibility` | `Overlay` | `UI / Dashboard` | `Telemetry` | `Packaging`.
3. **Page Body Content:**
   - **Scope:** In-scope and out-of-scope boundaries (under 250 words).
   - **Target files/directories:** Verified repo paths; mark new paths as `(new)`.
   - **Acceptance Criteria:** Pass/fail checklist including null/empty, boundary, and edge conditions.
   - **Verification command:** Exact CLI command to prove completion (e.g. `./gradlew testDebugUnitTest --tests "...""`).
   - **Dependencies:** Explicit list ("Blocked by: [OL-YY]" or "None").

- **[2026-10-05 20:47:50] Discovery / Engineering Note**:
  - Created Unreel tickets database in Notion (3f09da56-a0de-819a-ab73-fe3c30394233) with 11 Ready for SWE tickets and 2 Draft tickets, scoped strictly to Instagram Reels elimination.

- **[2026-10-05 20:55:11] Discovery / Engineering Note**:
  - Refactored Unreel tickets into 17 ultra-atomic, single-concern, SWE-ready tickets in Notion database (3f09da56-a0de-819a-ab73-fe3c30394233) strictly scoped to Instagram Reels eradication.

- **[2026-10-05 20:56:16] Discovery / Engineering Note**:
  - Synchronized repository base and Notion workspace to 'Unreel' (cameronduff/unreel.git) across 17 atomic tickets, PRD, and database.

- **[2026-10-05 21:43:30] Discovery / Engineering Note**:
  - Implemented tickets [UNR-01] through [UNR-10]: Scaffolding, Robolectric harness, Reels detectors, debounced back dispatcher, AccessibilityService event loop, Room database, DataStore repository, and accessibility intent/status helpers. All 42 unit/integration tests passing (100%).

- **[2026-10-05 22:24:00] Discovery / Engineering Note**:
  - Completed all 17 atomic tickets [UNR-01] through [UNR-17] for Unreel v1.0. Implemented OnboardingScreen & ViewModel, Dashboard AMOLED screen with countdown pause timer, UnreelTileService Quick Settings 15m toggle, TouchAbsorber OverlayPositionCalculator & TouchAbsorberOverlayService floating touch sink, ZeroNetworkSecurityAuditTest enforcing zero network permissions, and R8-shrunk release APK packaging (769 KB, well under 3.5 MB limit) with F-Droid YAML metadata and GitHub Actions CI workflow. All 66 unit/integration/security tests passing (100%), lint clean.

- **[2026-10-07 16:28:00] Discovery / Engineering Note**:
  - Configured native Linux ADB binary bridge in ~/.local/bin/adb to avoid WSL2 Windows-interop hangs. Implemented scripts/connect_device.sh supporting Wireless Pairing (Android 11+), TCP/IP port 5555, and USB interop. Implemented scripts/verify_real_device.sh for live end-to-end device verification: APK installation, automated AccessibilityService binding via settings put secure, Instagram Reels intent launch, sub-16ms suppression logcat audit, and Room telemetry persistence validation. Added on-hardware RealDeviceSmokeTest.kt instrumented test suite.



- **[2026-10-07 18:17:33] Discovery / Engineering Note**:
  - Engineered automated on-device E2E performance test suite (scripts/e2e_device_test_suite.py) evaluating physical Pixel 4a under 16.6ms frame budget (1.33ms avg latency), debouncing doomscroll bursts (650ms transition window), and verifying 0% false positives on feed, search, DMs, and profile. Validated 183 real SQLite Room telemetry records. All 76 local unit/Robolectric tests and 100% of real-device E2E tests passing.

- **[2026-10-07 19:08:00] Discovery / Engineering Note**:
  - Resolved cold-launch false positive kick in `InstagramClipsDetector.kt`: Instagram attaches an invisible 0-height stub container (`clips_viewer_debug_container`, 0x0 px) on startup that previously triggered the Back dispatcher. Enforced `isVisibleToUser && rect.height() >= rootBounds.height() * 0.5`. Verified on physical Pixel 4a: Instagram now launches and browses smoothly. Automated on-device E2E test suite (`run_e2e_device_test.sh`) passes 100% across all 9 gates (10.5ms avg suppression latency, zero false positives, 233 telemetry events).

- **[2026-10-07 20:10:00] Discovery / Engineering Note**:
  - Addressed Instagram Daily Limit / Modal dialog overlay obstruction. Engineered `InstagramModalDetector.kt` to traverse accessibility node hierarchies and detect dialog containers (`dialog_container`, `dialog_window`, `bottom_sheet_container`, `action_sheet_container`, `igds_headline_headline`, `comment_composer_container`, `direct_share_sheet`, `reel_viewer_root`, `row_thread_composer`, `quick_capture_fragment_container`). When active, `UnreelAccessibilityService` immediately dismisses the floating blackout overlay (`updateOverlay(false)`), ensuring Daily Time Limit buttons, comments, story replies, and DMs remain 100% interactive. Mapped all 9 primary Instagram customer journeys in `docs/CUSTOMER_JOURNEYS.md`. Verified on physical Pixel 4a: 89 unit tests pass (100%), automated on-device E2E test suite passes 100%.

- **[2026-10-07 21:15:00] Discovery / Engineering Note**:
  - Fixed fast scroll ANR and IPC spam in `UnreelAccessibilityService.kt`: Added state and bounds caching (`lastOverlayState`, `lastOverlayBounds`) to ensure `TouchAbsorberOverlayService.show/hide()` is only called on state transitions rather than on every high-frequency scroll event.

- **[2026-10-07 21:35:00] Discovery / Engineering Note**:
  - Implemented Option 1: 30-day silent auto-snoozing of feed suggested posts via `InstagramSuggestedPostSnoozer.kt`. Traverses accessibility hierarchy to catch suggested post indicators ("Suggested for you", "Because you follow", etc.) on the Home feed, clicks post options (`media_option_button`), and programmatically triggers "Not interested" -> "Snooze all suggested posts in feed for 30 days" (or direct snooze menu item). Also detects `instagram://settings_content_preferences` and auto-toggles "Snooze suggested posts" if off. Backed by 24h cooldown gating (zero overhead during regular feed browsing), 2.5s watchdog timeout, DataStore persistence, Room telemetry (`TRIGGER_FEED_AUTO_SNOOZE`), and dashboard toggle UI. Verified across 106 unit/integration tests (100% pass) and physical Pixel 4a E2E performance suite (100% pass, 2.0ms latency).




- **[2026-10-07 22:03:01] Discovery / Engineering Note**:
  - Investigated brief visual pop-up before Reels interception. Identified priority inversion where splash screen, modal checks, and overlay maintenance were executing before Reels clips detection, adding unnecessary BFS traversals and IPC latency. Reordered UnreelAccessibilityService.kt so P0 Reels detection executes first and short-circuits in <2ms. Removed stopSelf() from TouchAbsorberOverlayService to eliminate cold-start latency when returning to feed. Reduced accessibility_service_config.xml notificationTimeout from 50ms to 10ms and DebouncedBackDispatcher window to 300ms. Created scripts/test_scroll_speeds.py and benchmarked across slow (800ms), medium (350ms), fast (100ms), and rapid multi-fling (50ms) scroll speeds (0 detaches, 0 false triggers, 0 ANRs). Added FastScrollEdgeCaseTest.kt covering rapid scroll bursts, priority preemption, and inline feed video discriminators. All 111 unit tests and 100% of real-device E2E tests passing on Pixel 4a.

- **[2026-10-08 17:15:12] Discovery / Engineering Note**:
  - Updated project license to GNU General Public License v3.0 (GPL-3.0) with Section 7(b) mandatory attribution and citation terms requiring Cameron Duff to be cited as an original creator and contributor upon any reuse or redistribution. Created LICENSE and CITATION.cff, updated README.md and metadata/org.unreel.android.yml.

- **[2026-10-08 17:48:00] Discovery / Engineering Note**:
  - Fixed Direct Messages overlay persistence and implemented [UNR-22] In-Feed Sponsored Post Auto-Hider:
    1. Replaced asynchronous IPC `startService()` in `TouchAbsorberOverlayService` with in-process `@Volatile` singleton direct attach/detach methods (<0.1ms dispatch).
    2. Updated `InstagramModalDetector` and `InstagramBottomNavDetector` to detect direct message view hierarchies (`row_thread_composer`, `message_composer_bar`, `direct_thread`, `thread_fragment_container`), suppressing the overlay immediately when in chat threads or when keyboard opens. Verified on Pixel 4a with screenshots.
    3. Engineered `InstagramFeedAdShield.kt` state machine detecting Sponsored/Ad labels in the main feed and programmatically triggering post options (`media_option_button`) -> "Hide ad" -> "It's irrelevant". Verified across 124 unit tests (100% pass) and deployed to physical Pixel 4a.

- **[2026-10-08 17:54:00] Discovery / Engineering Note**:
  - Implemented [UNR-23] Instagram AdShield - Story Ads Auto-Fast-Forward:
    1. Engineered `InstagramStoryAdDetector.kt` identifying full-screen story viewer containers (`reel_viewer_root`, `stories_viewer_container`) displaying Sponsored/Ad labels or ad CTA buttons (`story_ad_cta`, `ad_action_button`).
    2. Programmatically fast-forwards sponsored stories in <30ms via `ACTION_SCROLL_FORWARD` or instant right-edge tap gesture dispatch (`dispatchTapGesture(x = 92% width, y = center)`), preserving organic friend stories with a 350ms debounce window.
    3. Enabled `android:canPerformGestures="true"` in accessibility service config. Logged intercept telemetry to Room database (`TRIGGER_STORY_AD_SHIELD`).
    4. Verified across 131 unit tests (100% pass rate).

- **[2026-10-08 17:58:00] Discovery / Engineering Note**:
  - Implemented [UNR-24] Multi-Device Compatibility Test Matrix:
    1. Engineered `MultiDeviceMatrixTest.kt` verifying coordinate calculations, bottom navigation tab detection, story ad fast-forward coordinates, and full-screen clips coverage across 11 device profiles.
    2. Covered aspect ratios: 16:9 (720x1280, 1080x1920), 18:9 (1080x2160), 19.5:9 (1080x2340 Pixel 4a / Galaxy S23), 20:9 (1344x2992 Pixel 8 Pro, 1440x3088 S23 Ultra, 1080x2400 Redmi), and Foldables/Tablets (1840x2208 Pixel Fold, 1812x2176 Z Fold5, 1600x2560 Tablet).
    3. Verified API level compatibility across Android 10 (API 29) through Android 15 (API 35).
    4. All 136 unit tests passing (100% pass rate).

- **[2026-10-08 18:00:00] Discovery / Engineering Note**:
  - Implemented [UNR-25] Automated GitHub Actions CI Matrix & Universal Release Distribution:
    1. Engineered multi-job CI workflow (`.github/workflows/ci.yml`) triggering on `main` push, PRs, and `v*` release tags.
    2. Gated on discrete stages: Zero Network Security Audit (`ZeroNetworkSecurityAuditTest`), Multi-Device Matrix Test (`MultiDeviceMatrixTest`), full test suite, and Android Lint.
    3. Configured packaging job building R8-optimized universal APK (`Unreel-universal.apk`), enforcing binary size budget (< 1.5MB), uploading workflow artifact, and publishing GitHub Releases.

- **[2026-10-08 18:03:00] Discovery / Engineering Note**:
  - Implemented [UNR-26] Cloud Device Farm Testing with Firebase Test Lab:
    1. Engineered `scripts/run_firebase_test_lab.sh` targeting physical cloud device matrix: Pixel 6 (Android 13), Pixel 7 (Android 14), Galaxy S22 Ultra (Android 13), and Galaxy S23 Ultra (Android 14).
    2. Automated compilation of `app-debug.apk` and `app-debug-androidTest.apk` running `RealDeviceSmokeTest`.
    3. Verified with `--dry-run` validating prerequisites, CLI tooling (`gcloud`), and build outputs with 0 errors.






- **[2026-10-09 08:52:55] Discovery / Engineering Note**:
  - Implemented [UNR-28], [UNR-29], and [UNR-30]:
- [UNR-28] Overlay Precision & DM Chat Suppression Engine Fix:
  1. Identified root causes of overlay flashing and DM leakage: (a) flawed TYPE_VIEW_SCROLLED bypass retaining overlay during chat scrolling; (b) false positive tab matching on shared Reel messages in DM threads due to unconstrained search; (c) tab_bar_shadow matching ahead of tab_bar; (d) fallback coordinates calculation in TouchAbsorberOverlayService.
  2. Implemented strict bottom navigation bar container verification (findBottomNavBarContainer requiring tab_bar and childCount >= 1 with shadow exclusion).
  3. Added top-priority isDirectThreadActive check and short-circuited feed/story scanners in chat threads.
- [UNR-29] Adaptive AMOLED & Monochromatic Launcher App Icon:
  1. Designed vector assets: ic_launcher_background.xml (#0D1014 AMOLED black), ic_launcher_foreground.xml (electric cyan outer focus aperture + white geometric 'U' glyph + precision reticle), and ic_launcher_monochrome.xml (Pixel dynamic theming).
  2. Configured adaptive icons in mipmap-anydpi-v26/ and mipmap/ with application and activity links in AndroidManifest.xml.
  3. Created LauncherIconTest verifying 100% inflation integrity.
- [UNR-30] Adversarial Overlay Flashing & Direct Thread Validation Test Suite:
  1. Engineered DirectThreadOverlaySuppressionTest verifying shared Reel messages in DM do not attach overlay, high-frequency chat scrolling produces 0 overlay attachments, and feed-to-DM transition latency is sub-16ms.
  2. All 146 unit tests passing (100% pass rate).
- Real Device Testing Directive:
  Always use 'elisha zara kunalan duff' (clickable bounds [0, 989]-[1080, 1198]) as the target live test conversation thread on device. Verified overlay is cleanly suppressed in chat and immediately attaches upon returning to inbox.
