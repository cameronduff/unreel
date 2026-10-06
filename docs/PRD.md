# Product Requirements Document (PRD): Unreel v1.0

**Project Name:** Unreel  
**Tagline:** High-Performance, Native Android Open-Source Instagram Reels Eliminator  
**Status:** Approved / Planning  
**License:** GPL-3.0 / MIT  
**Parent Notion Space:** [Unreel Project Space](https://app.notion.com/p/Unreel-Open-Source-Distraction-Reels-Eliminator-3f09da56a0de81268bc8fabee329995d)  
**Notion PRD Document:** [Unreel PRD](https://app.notion.com/p/Product-Requirements-Document-PRD-Unreel-v1-0-3f09da56a0de81299f18d92bc76395a6)  
**Notion Tickets Board:** [Unreel Engineering Roadmap & Tickets](https://app.notion.com/p/3f09da56a0de819aab73fe3c30394233)

---

## 1. Executive Summary & Vision

Unreel is an open-source, subscription-free, native Android utility engineered to eradicate **Instagram Reels** while preserving 100% native 120Hz speed, camera fidelity, and instant messaging.

Commercial tools like SocialLite demonstrate strong consumer demand for digital minimalism, but lock users into $50–$80/year subscriptions for sluggish WebKit/WebView containers that break push notifications and degrade device responsiveness. **Unreel** is a client-side Android companion operating directly alongside the official native Instagram app without modifying APK binaries or proxying private credentials.

* **Core Mission (v1.0):** Eradicate Instagram Reels completely (both full-screen clips viewer and bottom navigation tab).
* **Out of Scope (Deferred to v2):** YouTube Shorts, TikTok, and Instagram Explore feed gating are explicitly deferred.
* **License:** GPL-3.0 / MIT (100% free, community-governed, privacy-verifiable).
* **Target Platform:** Android 8.0+ (API 26) up to modern Android 15+.

---

## 2. Problem Statement & Market Opportunity

Modern digital minimalism faces three fatal compromises:
1. **The Webview Sluggishness:** Web wrapper apps have high touch latency, dropped frames, missing gestures, and unreliable push notifications for urgent DMs.
2. **The APK Patching Fragility:** Bytecode patchers (ReVanced, Instander) break weekly as Meta pushes obfuscated updates, and distributing modified binaries triggers DMCA takedowns.
3. **The Subscription Moat:** Paywalling basic DOM filters or accessibility gates behind recurring monthly subscriptions exploits users seeking mental peace.

**The Open-Source Opportunity:** An open-source, zero-network-permission Accessibility Service companion that provides sub-16ms suppression on the official native app.

---

## 3. User Personas & Use Cases

* **Persona A — The Recovering Doomscroller:** Wants to use Instagram purely to reply to group chats and friend stories, but subconsciously gets sucked into 90-minute Reels rabbit holes.
* **Persona B — The Dumbphone Minimalist:** Carries a minimalist Android phone (e.g. Pixel 4a with Olauncher) and needs essential communication tools without modern algorithmic triggers.
* **Persona C — The Privacy & FOSS Purist:** Refuses to input credentials into closed-source modded APKs or pay recurring subscriptions for client-side functionality.

---

## 4. Technical Architecture

### 4.1 Sub-16ms Event Interception Engine
Operates as a high-priority Android `AccessibilityService` with fine-tuned event listeners:

```kotlin
class UnreelAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.packageName != "com.instagram.android") return

        val rootNode = rootInActiveWindow ?: return

        // 1. Instant kill if full-screen Reels container mounts
        val reelsContainers = rootNode.findAccessibilityNodeInfosByViewId(
            "com.instagram.android:id/clips_video_container"
        )
        if (reelsContainers.isNotEmpty()) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            TelemetryTracker.recordIntercept("reels_fullscreen")
            return
        }

        // 2. Intercept active selection of bottom navigation Reels tab
        val reelsTabs = rootNode.findAccessibilityNodeInfosByText("Reels")
        for (node in reelsTabs) {
            if (node.isSelected) {
                performGlobalAction(GLOBAL_ACTION_BACK)
                TelemetryTracker.recordIntercept("reels_tab")
                break
            }
        }
    }

    override fun onInterrupt() {}
}
```

### 4.2 Zero-Network-Permission Trust Model
To guarantee total user privacy and eliminate security fears:
* **No INTERNET Permission:** `android.permission.INTERNET` is completely omitted from `AndroidManifest.xml`. The OS physically prevents the app from sending any data off the device.
* **Zero Credential Handling:** Users remain logged into the official Google Play Instagram app. Unreel never sees usernames, passwords, or tokens.

### 4.3 Touch Absorber Overlay (Optional Guard)
An optional lightweight floating touch sink (using `SYSTEM_ALERT_WINDOW`) positioned precisely over the Reels navigation icon. Accidental thumb taps are absorbed as a harmless no-op or routed to Direct Messages.

---

## 5. Functional Requirements (v1.0 Focus)

* **FR-1: Instant Reels Suppression:** Detect and terminate Reels viewer within <= 16ms of launch (before video playback starts).
* **FR-2: Bottom Nav Reels Tab Interception:** Intercept selection of the Reels tab and immediately bounce back to Feed or Inbox.
* **FR-3: Quick Settings Tile:** Allow users to quickly pause Unreel for 15 minutes via an Android Quick Settings tile.
* **FR-4: Local Telemetry & Time Saved:** Track daily intercept count and estimated screen time saved in local on-device SQLite database.
* **FR-5: Minimalist AMOLED Dashboard:** Clean toggle and statistics screen built with Jetpack Compose.

---

## 6. Non-Functional Requirements

* **Performance:** Suppression action latency < 16ms (1 display frame at 60Hz). Zero stutter on feed scroll.
* **Battery Overhead:** < 0.3% battery consumption over a 24-hour cycle. 100% reactive, zero background polling.
* **Binary Footprint:** < 4 MB APK size built with Jetpack Compose and R8 tree-shaking.

---

## 7. Milestone Roadmap (v1.0)

* **Phase 1 (Core Engine & TDD — 1 Week):** Scaffold Kotlin repo with Robolectric, implement `InstagramViewDetector`, `UnreelAccessibilityService` back bounce.
* **Phase 2 (Data & UI — 1 Week):** Room database for metrics, DataStore preferences, Jetpack Compose dashboard & onboarding wizard.
* **Phase 3 (Quick Settings & Hardening — 1 Week):** Quick Settings pause tile, Touch Absorber overlay, zero-permission lint verification, F-Droid metadata package.
