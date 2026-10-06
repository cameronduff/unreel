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

