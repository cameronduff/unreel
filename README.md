# ⚡ Unreel

> **High-Performance, Native Android Open-Source Instagram Reels Eliminator**  
> *Keep your DMs, camera, and friends. Eradicate the doomscroll.*

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![Zero Network Permission](https://img.shields.io/badge/Network-0%20Permissions-brightgreen.svg)](#privacy--trust)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-orange.svg)](#requirements)

---

## 🎯 The Mission

Modern digital minimalism faces a frustrating dilemma:
- **Dumbphones & Total Abstinence:** Deleting Instagram or switching to a feature phone breaks group chats, event planning, and DMs.
- **Web Wrappers (SocialLite, PWAs):** Sluggish web containers suffer from touch lag, dropped frames, degraded camera capture, missing gestures, and unreliable push notifications—often behind $50–$80/year subscriptions.
- **Modded APKs (Instander, ReVanced):** Bytecode patches break weekly with Meta updates, risk account bans, and cannot be safely distributed open-source.

**Unreel** solves this with a **native companion engine**:
It runs alongside the **official, unmodified Google Play Store Instagram app**. Through a high-priority Android `AccessibilityService`, it detects when the Reels video player or navigation tab is mounted and bounces you back to your inbox or feed in **under 16 milliseconds** (within 1 display frame).

---

## 🛡️ Privacy & Zero-Network Trust Model

Unreel operates with a **zero-trust security architecture**:
- **No Internet Permission:** `android.permission.INTERNET` is completely omitted from the manifest. The Android operating system physically prevents Unreel from sending any data off your phone.
- **Zero Credential Access:** You log into the official Instagram app as usual. Unreel never sees, intercepts, or stores passwords, tokens, or private messages.
- **100% On-Device:** All intercept metrics and telemetry are stored in a local, encrypted SQLite database on your device.

---

## 🏗️ Architecture & Core Components

```
                   [ User taps Instagram App ]
                               │
                               ▼
        ┌──────────────────────────────────────────────┐
        │        Official Instagram Native APK         │
        └──────────────────────┬───────────────────────┘
                               │ UI Window & View Events
                               ▼
        ┌──────────────────────────────────────────────┐
        │           Unreel Accessibility Engine        │
        │  • ClipsContainerDetector (sub-16ms)         │
        │  • BottomNavReelsDetector                    │
        │  • DebouncedBackDispatcher                   │
        └──────────────────────┬───────────────────────┘
                               │
            ┌──────────────────┴──────────────────┐
            ▼                                     ▼
   [ Reels Detected? ]                    [ Normal Feed / DM? ]
            │                                     │
    Yes: Fires Back Action                 No: Unreel is Idle
    (Redirects to Feed/Inbox)             (0% CPU, 0% Overhead)
```

1. **Sub-16ms Detection Engine (`InstagramClipsDetector`, `InstagramBottomNavDetector`):** Detects view hierarchies in memory without slowing down the rendering pipeline.
2. **Debounced Back Dispatcher (`DebouncedBackDispatcher`):** Prevents consecutive rapid-fire back-presses from exiting Instagram entirely.
3. **Local Telemetry (`UnreelDatabase`):** Tracks daily blocked reels and estimated screen time reclaimed.
4. **Touch Absorber (`TouchAbsorberOverlayService`):** An optional floating touch sink that absorbs accidental thumb taps on the Reels tab.
5. **Quick Settings Tile (`UnreelTileService`):** 1-tap pause toggle for 15 minutes of uninterrupted access when needed.

---

## 🛠️ Building & Verification

### Prerequisites
- JDK 17+
- Android SDK Platform 34 & Build-Tools 34.0.0
- Gradle 8.9 (via included `./gradlew`)

### Build Commands
```bash
# Compile debug APK
./gradlew assembleDebug

# Run unit tests on JVM (powered by Robolectric in-memory node mocking)
./gradlew testDebugUnitTest

# Run zero-network security manifest audit
./gradlew testDebugUnitTest --tests "org.unreel.android.security.ZeroNetworkSecurityAuditTest"

# Run Android lint
./gradlew lintDebug
```

---

## 🗺️ Roadmap & Tracking

Project roadmap and atomic task specifications are tracked in Notion:
* 📋 **Notion Board:** [Unreel Engineering Roadmap & Tickets](https://app.notion.com/p/3f09da56a0de819aab73fe3c30394233)
* 📄 **Product Requirements Document:** [docs/PRD.md](docs/PRD.md)
* 📑 **Ticket Manifest:** [docs/TICKETS.json](docs/TICKETS.json)

---

## 📄 License

GPL-3.0 License. 100% Free and Open Source.
