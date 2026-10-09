# Unreel Engineering Roadmap: v1.1 - v2.0

> **Parent Notion Space:** [Unreel Project Space](https://app.notion.com/p/Unreel-Open-Source-Distraction-Reels-Eliminator-3f09da56a0de81268bc8fabee329995d)  
> **Master Notion Roadmap:** [Unreel Roadmap Document](https://app.notion.com/p/Unreel-Engineering-Roadmap-v1-1-v2-0-AdShield-Multi-Device-Testing-Universal-APK-3f29da56a0de81388230f2516ffb4df7)  
> **Engineering Tickets Database:** [Unreel Tickets Board](https://app.notion.com/p/3f09da56a0de819aab73fe3c30394233)

---

## 1. Executive Summary & Vision

Unreel transforms conventional smartphones into distraction-free, intentional utilities. With Instagram Reels completely eradicated at sub-2ms latency, the next milestones focus on eradicating high-frequency algorithmic ads, validating stability across all Android OEMs, and delivering a zero-friction distribution pipeline.

---

## 2. Milestone Overview & Schedule

| Version | Milestone Focus | Status | Key Deliverables |
| :--- | :--- | :--- | :--- |
| **v1.0.1** | **Splash Screen Timing & ANR Prevention** | **Shipped** | Suppressed blackout box during startup splash logo; eliminated service start timeouts via `START_NOT_STICKY` lifecycle and state caching. |
| **v1.1** | **30-Day Suggested Post In-Feed Auto-Snoozer** | **Shipped** | Silently auto-snoozes suggested feed posts every 30 days without auto-scrolling via post options menu and Content Preferences toggle. |
| **v1.2** | **Instagram AdShield (Sponsored Posts & Stories)** | **Ready for SWE** | Eradicate feed sponsored ads via programmatic hide/skip; auto-fast-forward story video ads in <30ms. |
| **v1.3** | **Multi-Device Test Matrix & Universal Release Distribution** | **Ready for SWE** | Parameterized Robolectric test harness across screen ratios + GitHub Actions CI + F-Droid package. |
| **v2.0** | **Multi-Platform Distraction Elimination** | **Draft** | Expand engine to YouTube Shorts and Facebook Reels. |

---

## 3. Milestone 1.2 Deep Dive: Instagram AdShield

### 3.1 Why DNS Adblockers Fail
Instagram serves in-stream ads from the same CDN infrastructure (`*.cdninstagram.com`) as regular user photos and video posts. DNS and VPN blockers (Pi-hole, AdGuard, NextDNS) cannot distinguish between ad media and organic photos without breaking image loading across the entire app.

### 3.2 Unreel's Local Accessibility Solution
By reading the accessibility node hierarchy locally on-device:
- **Feed Sponsored Posts ([UNR-22])**: Detects `"Sponsored"` / `"Ad"` labels and programmatically triggers native menu `"Hide ad"` $\rightarrow$ `"It's irrelevant"`, or smoothly scrolls past in <50ms.
- **Stories Video Ads ([UNR-23])**: Detects `"Sponsored"` in story viewer and triggers an instant forward tap in <30ms, eliminating video ad delays.
- **Privacy Guarantee**: Strict zero-network guarantee (`ZeroNetworkSecurityAuditTest`) ensures zero telemetry, zero proxying, and zero external network calls.

---

## 4. Milestone 1.3 Deep Dive: Multi-Device Compatibility & Universal APK

- **Universal Release APK ([UNR-25])**: 809 KB R8-minified standalone signed APK available for 1-tap sideloading on any Android device (`app/build/outputs/apk/release/app-release.apk`).
- **Cross-Platform Support**: Fully compatible with Android 10 through 15 across Samsung One UI, Xiaomi HyperOS, Google Pixel, Motorola, and OnePlus.
- **3-Tier Test Matrix ([UNR-24], [UNR-26])**:
  - *Tier 1:* Robolectric multi-SDK tests across API 29-34 with aspect ratio qualifiers (16:9, 18:9, 19.5:9, 20:9, Foldable).
  - *Tier 2:* GitHub Actions CI matrix running headless Android emulators.
  - *Tier 3:* Cloud hardware testing via Firebase Test Lab.

---

## 5. Engineering Tickets Database Index

| Ticket ID | Title | Status | Priority | Area |
| :--- | :--- | :--- | :--- | :--- |
| **[UNR-18]** | Modal Dialog and Daily Limit Overlay Suppression | `Done` | P1 - High | Accessibility |
| **[UNR-19]** | Fast Scroll IPC Debounce & Overlay Lifecycle ANR Fix | `Done` | P0 - Blocker | Overlay |
| **[UNR-20]** | 30-Day Suggested Post In-Feed Auto-Snoozer | `Done` | P1 - High | Accessibility |
| **[UNR-21]** | Startup Splash Screen Overlay Suppression | `Done` | P2 - Medium | Overlay |
| **[UNR-22]** | Epic: Instagram AdShield - In-Feed Sponsored Post Auto-Hider | `Done` | P1 - High | Accessibility |
| **[UNR-23]** | Epic: Instagram AdShield - Story Ads Auto-Fast-Forward | `Done` | P1 - High | Accessibility |
| **[UNR-24]** | Multi-Device Compatibility Test Matrix (Robolectric Multi-SDK & Screen Ratio Harness) | `Done` | P2 - Medium | Core / Engine |
| **[UNR-25]** | Automated GitHub Actions CI Matrix & Universal Release Distribution | `Done` | P2 - Medium | Packaging |
| **[UNR-26]** | Cloud Device Farm Testing with Firebase Test Lab | `Done` | P3 - Low | Core / Engine |
| **[UNR-27]** | YouTube Shorts Elimination Companion Module | `Draft` | P3 - Low | Accessibility |
| **[UNR-28]** | Overlay Precision & DM Chat Suppression Engine Fix | `Done` | P0 - Blocker | Overlay |
| **[UNR-29]** | Adaptive AMOLED & Monochromatic Launcher App Icon | `Done` | P1 - High | UI / Dashboard |
| **[UNR-30]** | Adversarial Overlay Flashing & Direct Thread Validation Test Suite | `Done` | P1 - High | Overlay |
