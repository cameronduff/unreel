#!/usr/bin/env python3
"""
OpenLite PRD Generator and Notion Publisher
Creates docs/PRD.md and publishes a rich-text PRD page to Cameron's Notion workspace.
"""

import os
import json
import urllib.request
import urllib.error

NOTION_TOKEN = os.environ.get("NOTION_API_TOKEN")
PARENT_SPACE_ID = "3f09da56-a0de-8126-8bc8-fabee329995d"  # OpenLite Project Space

NOTION_API_URL = "https://api.notion.com/v1"
HEADERS = {
    "Authorization": f"Bearer {NOTION_TOKEN}",
    "Notion-Version": "2022-06-28",
    "Content-Type": "application/json"
}

def make_request(endpoint, method="POST", data=None):
    url = f"{NOTION_API_URL}/{endpoint}"
    payload = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=payload, headers=HEADERS, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8")
        print(f"HTTP Error {e.code} on {endpoint}: {err_msg}")
        raise e

def text_block(content, bold=False, italic=False, code=False):
    return {
        "type": "text",
        "text": {"content": content},
        "annotations": {
            "bold": bold,
            "italic": italic,
            "strikethrough": False,
            "underline": False,
            "code": code,
            "color": "default"
        }
    }

def heading_1(text):
    return {
        "object": "block",
        "type": "heading_1",
        "heading_1": {"rich_text": [text_block(text)]}
    }

def heading_2(text):
    return {
        "object": "block",
        "type": "heading_2",
        "heading_2": {"rich_text": [text_block(text)]}
    }

def heading_3(text):
    return {
        "object": "block",
        "type": "heading_3",
        "heading_3": {"rich_text": [text_block(text)]}
    }

def paragraph(text_runs):
    if isinstance(text_runs, str):
        text_runs = [text_block(text_runs)]
    return {
        "object": "block",
        "type": "paragraph",
        "paragraph": {"rich_text": text_runs}
    }

def bullet(text_runs):
    if isinstance(text_runs, str):
        text_runs = [text_block(text_runs)]
    return {
        "object": "block",
        "type": "bulleted_list_item",
        "bulleted_list_item": {"rich_text": text_runs}
    }

def callout(text, emoji="💡"):
    return {
        "object": "block",
        "type": "callout",
        "callout": {
            "icon": {"type": "emoji", "emoji": emoji},
            "rich_text": [text_block(text)]
        }
    }

def code_block(code_text, language="kotlin"):
    return {
        "object": "block",
        "type": "code",
        "code": {
            "rich_text": [text_block(code_text)],
            "language": language
        }
    }

def divider():
    return {"object": "block", "type": "divider", "divider": {}}

def build_prd_blocks():
    blocks = []

    # Callout overview
    blocks.append(callout(
        "OpenLite is an open-source, subscription-free, native Android utility engineered to eradicate Instagram Reels, YouTube Shorts, and algorithmic doomscrolling while preserving 100% native 120Hz speed, camera fidelity, and instant messaging.",
        "⚡"
    ))
    blocks.append(divider())

    # Section 1
    blocks.append(heading_1("1. Executive Summary & Vision"))
    blocks.append(paragraph([
        text_block("Commercial tools like SocialLite demonstrate strong demand for digital minimalism, but lock users into $50–$80/year subscriptions for sluggish WebKit/WebView containers that break push notifications and degrade device responsiveness. "),
        text_block("OpenLite", bold=True),
        text_block(" is an open-source, client-side Android companion that operates directly alongside the official native Instagram app without modifying APK binaries or proxying private credentials.")
    ]))
    blocks.append(bullet([text_block("Mission: ", bold=True), text_block("Free users from algorithmic slot machines while keeping genuine human connection (DMs, event planning, photo sharing) blazing fast and zero-friction.")]))
    blocks.append(bullet([text_block("License: ", bold=True), text_block("GPL-3.0 / MIT (100% free, community-governed, privacy-verifiable).")]))
    blocks.append(bullet([text_block("Target Platform: ", bold=True), text_block("Android 8.0+ (API 26) up to modern Android 15+.")]))

    # Section 2
    blocks.append(heading_1("2. Problem Statement & Market Opportunity"))
    blocks.append(paragraph("Modern digital minimalism faces three fatal compromises:"))
    blocks.append(bullet([text_block("The Webview Sluggishness: ", bold=True), text_block("Web wrapper apps have high touch latency, dropped frames, missing gestures, and unreliable push notifications for urgent DMs.")]))
    blocks.append(bullet([text_block("The APK Patching Fragility: ", bold=True), text_block("Bytecode patchers (ReVanced, Instander) break weekly as Meta pushes obfuscated updates, and distributing modified binaries triggers DMCA takedowns.")]))
    blocks.append(bullet([text_block("The Subscription Moat: ", bold=True), text_block("Paywalling basic DOM filters or accessibility gates behind recurring monthly subscriptions exploits users seeking mental peace.")]))
    blocks.append(callout("Opportunity: An open-source, zero-network-permission Accessibility Service companion that provides sub-16ms suppression on the official native app.", "🎯"))

    # Section 3
    blocks.append(heading_1("3. User Personas & Use Cases"))
    blocks.append(bullet([
        text_block("Persona A — The Recovering Doomscroller: ", bold=True),
        text_block("Wants to use Instagram purely to reply to group chats and friend stories, but subconsciously gets sucked into 90-minute Reels rabbit holes.")
    ]))
    blocks.append(bullet([
        text_block("Persona B — The Dumbphone Minimalist: ", bold=True),
        text_block("Carries a minimalist Android phone (e.g. Pixel 4a with Olauncher) and needs essential communication tools without modern algorithmic triggers.")
    ]))
    blocks.append(bullet([
        text_block("Persona C — The Privacy & FOSS Purist: ", bold=True),
        text_block("Refuses to input credentials into closed-source modded APKs or pay recurring subscriptions for client-side functionality.")
    ]))

    # Section 4
    blocks.append(heading_1("4. Technical Architecture"))
    blocks.append(paragraph("OpenLite is structured into three decoupled native Android subsystems:"))

    blocks.append(heading_2("4.1 Sub-16ms Event Interception Engine"))
    blocks.append(paragraph("Operates as a high-priority Android AccessibilityService with fine-tuned event listeners:"))
    blocks.append(code_block(
"""class OpenLiteAccessibilityService : AccessibilityService() {
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
}""", "kotlin"))

    blocks.append(heading_2("4.2 Zero-Network-Permission Trust Model"))
    blocks.append(paragraph("To guarantee total user privacy and eliminate security fears:"))
    blocks.append(bullet([text_block("No INTERNET Permission: ", bold=True), text_block("android.permission.INTERNET is completely omitted from AndroidManifest.xml. The OS physically prevents the app from sending any data off the device.")]))
    blocks.append(bullet([text_block("Zero Credential Handling: ", bold=True), text_block("Users remain logged into the official Google Play Instagram app. OpenLite never sees usernames, passwords, or tokens.")]))

    blocks.append(heading_2("4.3 Touch Absorber Overlay (Optional Guard)"))
    blocks.append(paragraph([
        text_block("An optional lightweight floating touch sink (using "),
        text_block("SYSTEM_ALERT_WINDOW", code=True),
        text_block(") positioned precisely over the Reels navigation icon. Accidental thumb taps are absorbed as a harmless no-op or routed to Direct Messages.")
    ]))

    # Section 5
    blocks.append(heading_1("5. Functional Requirements"))
    blocks.append(bullet([text_block("FR-1: Instant Reels Suppression: ", bold=True), text_block("Detect and terminate Reels viewer within <= 16ms of launch (before video playback starts).")]))
    blocks.append(bullet([text_block("FR-2: Explore Feed Gating: ", bold=True), text_block("Configurable toggle to either suppress the Explore tab completely or redirect it directly to Search.")]))
    blocks.append(bullet([text_block("FR-3: Quick Settings Tile: ", bold=True), text_block("Allow users to quickly pause OpenLite for 5, 15, or 60 minutes via an Android Quick Settings tile.")]))
    blocks.append(bullet([text_block("FR-4: Local Telemetry & Time Saved: ", bold=True), text_block("Track daily intercept count and estimated screen time saved in local on-device SQLite database.")]))
    blocks.append(bullet([text_block("FR-5: Multi-Platform Extensibility: ", bold=True), text_block("Modular engine supporting YouTube Shorts, Facebook Watch, and TikTok through unified plugin architecture.")]))

    # Section 6
    blocks.append(heading_1("6. Non-Functional Requirements"))
    blocks.append(bullet([text_block("Performance: ", bold=True), text_block("Suppression action latency < 16ms (1 display frame at 60Hz). Zero stutter on feed scroll.")]))
    blocks.append(bullet([text_block("Battery Overhead: ", bold=True), text_block("< 0.3% battery consumption over a 24-hour cycle. 100% reactive, zero background polling.")]))
    blocks.append(bullet([text_block("Binary Footprint: ", bold=True), text_block("< 5 MB APK size built with Jetpack Compose and R8 tree-shaking.")]))

    # Section 7
    blocks.append(heading_1("7. Milestone Roadmap"))
    blocks.append(bullet([text_block("Phase 1 (MVP — 2 Weeks): ", bold=True), text_block("Scaffold Kotlin repo, AccessibilityService core, Instagram Reels suppression, Jetpack Compose setup wizard.")]))
    blocks.append(bullet([text_block("Phase 2 (Polish & Hardening — 2 Weeks): ", bold=True), text_block("Quick Settings tile, Explore tab gating, Touch Absorber overlay, local stats dashboard.")]))
    blocks.append(bullet([text_block("Phase 3 (Multi-App & Distribution — 3 Weeks): ", bold=True), text_block("YouTube Shorts support, F-Droid metadata package, Google Play Store compliance, GitHub v1.0 release.")]))

    return blocks

def main():
    print("Building OpenLite PRD...")
    blocks = build_prd_blocks()
    
    # 1. Create PRD page in Notion under PARENT_SPACE_ID
    print(f"Creating Notion page under parent space {PARENT_SPACE_ID}...")
    page_data = {
        "parent": {"page_id": PARENT_SPACE_ID},
        "icon": {"type": "emoji", "emoji": "📄"},
        "properties": {
            "title": {
                "title": [{"type": "text", "text": {"content": "Product Requirements Document (PRD): OpenLite v1.0"}}]
            }
        },
        # Notion allows max 100 blocks on page creation
        "children": blocks[:95]
    }
    
    res = make_request("pages", method="POST", data=page_data)
    page_id = res["id"]
    page_url = res["url"]
    print(f"Successfully created Notion PRD Page: {page_id}")
    print(f"Notion URL: {page_url}")

    # Append remaining blocks if any
    if len(blocks) > 95:
        print(f"Appending remaining {len(blocks) - 95} blocks...")
        make_request(f"blocks/{page_id}/children", method="PATCH", data={"children": blocks[95:]})
        print("All blocks appended.")

    # Also save local markdown
    print("Writing docs/PRD.md...")
    with open("docs/PRD.md", "w") as f:
        f.write("""# Product Requirements Document (PRD): OpenLite v1.0
**Project Name:** OpenLite  
**Tagline:** High-Performance, Native Android Open-Source Distraction & Reels Eliminator  
**Status:** Approved / Planning  
**License:** GPL-3.0 / MIT  
**Parent Notion Space:** [OpenLite Project Space](https://app.notion.com/p/OpenLite-Open-Source-Distraction-Reels-Eliminator-3f09da56a0de81268bc8fabee329995d)  
**Notion PRD Document:** [OpenLite PRD](%s)

---

## 1. Executive Summary & Vision
OpenLite is an open-source, subscription-free, native Android utility engineered to eradicate Instagram Reels, YouTube Shorts, and algorithmic doomscrolling while preserving 100% native 120Hz speed, camera fidelity, and instant messaging.

Commercial tools like SocialLite demonstrate strong demand for digital minimalism, but lock users into $50–$80/year subscriptions for sluggish WebKit/WebView containers that break push notifications and degrade device responsiveness. **OpenLite** is a client-side Android companion operating directly alongside the official native Instagram app without modifying APK binaries or proxying private credentials.

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
class OpenLiteAccessibilityService : AccessibilityService() {
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
}
```

### 4.2 Zero-Network-Permission Trust Model
To guarantee total user privacy and eliminate security fears:
* **No INTERNET Permission:** `android.permission.INTERNET` is completely omitted from `AndroidManifest.xml`. The OS physically prevents the app from sending any data off the device.
* **Zero Credential Handling:** Users remain logged into the official Google Play Instagram app. OpenLite never sees usernames, passwords, or tokens.

### 4.3 Touch Absorber Overlay (Optional Guard)
An optional lightweight floating touch sink (using `SYSTEM_ALERT_WINDOW`) positioned precisely over the Reels navigation icon. Accidental thumb taps are absorbed as a harmless no-op or routed to Direct Messages.

---

## 5. Functional Requirements
* **FR-1: Instant Reels Suppression:** Detect and terminate Reels viewer within <= 16ms of launch (before video playback starts).
* **FR-2: Explore Feed Gating:** Configurable toggle to either suppress the Explore tab completely or redirect it directly to Search.
* **FR-3: Quick Settings Tile:** Allow users to quickly pause OpenLite for 5, 15, or 60 minutes via an Android Quick Settings tile.
* **FR-4: Local Telemetry & Time Saved:** Track daily intercept count and estimated screen time saved in local on-device SQLite database.
* **FR-5: Multi-Platform Extensibility:** Modular engine supporting YouTube Shorts, Facebook Watch, and TikTok through unified plugin architecture.

---

## 6. Non-Functional Requirements
* **Performance:** Suppression action latency < 16ms (1 display frame at 60Hz). Zero stutter on feed scroll.
* **Battery Overhead:** < 0.3%% battery consumption over a 24-hour cycle. 100%% reactive, zero background polling.
* **Binary Footprint:** < 5 MB APK size built with Jetpack Compose and R8 tree-shaking.

---

## 7. Milestone Roadmap
* **Phase 1 (MVP — 2 Weeks):** Scaffold Kotlin repo, AccessibilityService core, Instagram Reels suppression, Jetpack Compose setup wizard.
* **Phase 2 (Polish & Hardening — 2 Weeks):** Quick Settings tile, Explore tab gating, Touch Absorber overlay, local stats dashboard.
* **Phase 3 (Multi-App & Distribution — 3 Weeks):** YouTube Shorts support, F-Droid metadata package, Google Play Store compliance, GitHub v1.0 release.
""" % page_url)
    print("Successfully wrote docs/PRD.md!")

if __name__ == "__main__":
    main()
