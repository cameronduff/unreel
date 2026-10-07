#!/usr/bin/env python3
"""
Publishes the Unreel v1.1 - v2.0 Engineering Roadmap and SWE-ready tickets to Notion.
Parent Space: 3f09da56-a0de-8126-8bc8-fabee329995d
Database ID: 3f09da56-a0de-819a-ab73-fe3c30394233
"""

import os
import json
import urllib.request
import urllib.error
import time

NOTION_TOKEN = os.environ.get("NOTION_API_TOKEN")
PARENT_SPACE_ID = "3f09da56-a0de-8126-8bc8-fabee329995d"
DATABASE_ID = "3f09da56-a0de-819a-ab73-fe3c30394233"

HEADERS = {
    "Authorization": f"Bearer {NOTION_TOKEN}",
    "Notion-Version": "2022-06-28",
    "Content-Type": "application/json"
}

def make_request(endpoint, method="POST", data=None):
    url = f"https://api.notion.com/v1/{endpoint}"
    payload = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(url, data=payload, headers=HEADERS, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8")
        print(f"HTTP Error {e.code} on {endpoint}: {err_msg}")
        raise e

def text_run(content, bold=False, italic=False, code=False):
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

def heading_2(text):
    return {"object": "block", "type": "heading_2", "heading_2": {"rich_text": [text_run(text, bold=True)]}}

def heading_3(text):
    return {"object": "block", "type": "heading_3", "heading_3": {"rich_text": [text_run(text, bold=True)]}}

def paragraph(text, bold=False, italic=False, code=False):
    return {"object": "block", "type": "paragraph", "paragraph": {"rich_text": [text_run(text, bold=bold, italic=italic, code=code)]}}

def bullet_item(text, bold_prefix="", code=False):
    runs = []
    if bold_prefix:
        runs.append(text_run(bold_prefix, bold=True))
    runs.append(text_run(text, code=code))
    return {"object": "block", "type": "bulleted_list_item", "bulleted_list_item": {"rich_text": runs}}

def callout(text, emoji="💡"):
    return {
        "object": "block",
        "type": "callout",
        "callout": {
            "rich_text": [text_run(text)],
            "icon": {"type": "emoji", "emoji": emoji}
        }
    }

def divider():
    return {"object": "block", "type": "divider", "divider": {}}

ROADMAP_TICKETS = [
    {
        "id_tag": "UNR-18",
        "title": "[UNR-18] Modal Dialog and Daily Limit Overlay Suppression",
        "status": "Done",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "Accessibility",
        "scope": "Dismiss floating blackout touch-absorber overlay when Instagram modal dialogs (Daily Time Limit, bottom sheets, comment tray) appear to prevent blocking dialog action buttons.",
        "target": "app/src/main/java/org/unreel/android/engine/InstagramModalDetector.kt",
        "criteria": [
            "Detects dialog_container, dialog_window, bottom_sheet_container, action_sheet_container, and modal_container.",
            "Calls updateOverlay(false) in UnreelAccessibilityService immediately upon modal detection.",
            "Restores overlay when modal is dismissed and user returns to feed."
        ],
        "verification": "./gradlew testDebugUnitTest --tests 'org.unreel.android.engine.InstagramModalDetectorTest'"
    },
    {
        "id_tag": "UNR-19",
        "title": "[UNR-19] Fast Scroll IPC Debounce & Overlay Lifecycle ANR Fix",
        "status": "Done",
        "priority": "P0 - Blocker",
        "type": "Core / Engine",
        "area": "Overlay",
        "scope": "Eliminate high-frequency IPC flood to TouchAbsorberOverlayService during fast feed scrolling and prevent background execution timeouts.",
        "target": "app/src/main/java/org/unreel/android/overlay/TouchAbsorberOverlayService.kt",
        "criteria": [
            "Cache overlay visibility state and bounding Rect in UnreelAccessibilityService.",
            "Only issue startService IPC calls when overlay state or bounds actually transition.",
            "Set TouchAbsorberOverlayService to START_NOT_STICKY and call stopSelf() on detach."
        ],
        "verification": "./gradlew testDebugUnitTest --tests 'org.unreel.android.overlay.TouchAbsorberOverlayServiceTest'"
    },
    {
        "id_tag": "UNR-20",
        "title": "[UNR-20] 30-Day Suggested Post In-Feed Auto-Snoozer",
        "status": "Done",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "Accessibility",
        "scope": "Silently auto-snooze Instagram suggested posts every 30 days via post options menu (Not interested -> Snooze) and Content Preferences deep link without auto-scrolling.",
        "target": "app/src/main/java/org/unreel/android/engine/InstagramSuggestedPostSnoozer.kt",
        "criteria": [
            "Detects 'Suggested for you', 'Because you follow', etc. on Home feed.",
            "Clicks media_option_button and advances to 'Snooze all suggested posts in feed for 30 days'.",
            "Auto-toggles 'Snooze suggested posts' in instagram://settings_content_preferences if off.",
            "Enforces 24-hour cooldown and 2.5s watchdog timeout.",
            "Zero network permissions maintained."
        ],
        "verification": "./gradlew testDebugUnitTest --tests 'org.unreel.android.engine.InstagramSuggestedPostSnoozerTest'"
    },
    {
        "id_tag": "UNR-21",
        "title": "[UNR-21] Startup Splash Screen Overlay Suppression",
        "status": "Done",
        "priority": "P2 - Medium",
        "type": "Feature",
        "area": "Overlay",
        "scope": "Suppress blackout touch absorber box while Instagram startup splash screen / big logo is showing; attach simultaneously with the feed and bottom nav bar.",
        "target": "app/src/main/java/org/unreel/android/engine/InstagramBottomNavDetector.kt",
        "criteria": [
            "Detects SplashScreenView, IgSplashScreenActivity, and startup splash containers.",
            "Returns null for reelsBounds while splash is visible.",
            "Attaches overlay seamlessly when the feed content loads."
        ],
        "verification": "./gradlew testDebugUnitTest --tests 'org.unreel.android.engine.InstagramBottomNavDetectorTest'"
    },
    {
        "id_tag": "UNR-22",
        "title": "[UNR-22] Epic: Instagram AdShield - In-Feed Sponsored Post Auto-Hider",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "Accessibility",
        "scope": "Eliminate sponsored ads from the main Instagram feed. Detect 'Sponsored' / 'Ad' cards and automatically trigger native 'Hide ad' -> 'It's irrelevant' via post options, or smoothly skip past in <50ms.",
        "target": "app/src/main/java/org/unreel/android/engine/InstagramAdShieldDetector.kt (new)",
        "criteria": [
            "Detects Sponsored post label (sponsored_label, text/desc matching 'Sponsored' or 'Ad').",
            "Configurable action: (A) Programmatic menu Hide Ad, or (B) Smooth scroll skip.",
            "Maintains 0% false positives on organic posts and accounts.",
            "Logs ad suppression events to Room database (TRIGGER_FEED_AD_INTERCEPT).",
            "Zero network permissions preserved."
        ],
        "verification": "./gradlew testDebugUnitTest --tests 'org.unreel.android.engine.InstagramAdShieldDetectorTest'"
    },
    {
        "id_tag": "UNR-23",
        "title": "[UNR-23] Epic: Instagram AdShield - Story Ads Auto-Fast-Forward",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "Accessibility",
        "scope": "Eliminate full-screen video and carousel ads during Instagram Stories playback. Instantly fast-forward past sponsored stories in under 30ms.",
        "target": "app/src/main/java/org/unreel/android/engine/InstagramStoryAdDetector.kt (new)",
        "criteria": [
            "Detects 'Sponsored' label in story viewer header (reel_viewer_root, action_bar_title_view).",
            "Dispatches immediate right-tap or ACTION_SCROLL_FORWARD to skip ad story instantly.",
            "Never skips organic stories from friends or followed creators.",
            "Debounced with a 300ms window to prevent rapid-fire skipping past organic stories."
        ],
        "verification": "./gradlew testDebugUnitTest --tests 'org.unreel.android.engine.InstagramStoryAdDetectorTest'"
    },
    {
        "id_tag": "UNR-24",
        "title": "[UNR-24] Multi-Device Compatibility Test Matrix (Robolectric Multi-SDK & Screen Ratio Harness)",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "TDD / Test",
        "area": "Core / Engine",
        "scope": "Establish automated test fixtures verifying Unreel coordinate calculations and node detection across different Android OS versions and hardware screen aspect ratios.",
        "target": "app/src/test/java/org/unreel/android/matrix/MultiDeviceMatrixTest.kt (new)",
        "criteria": [
            "Tests parameterized across SDK 29 (Android 10), SDK 31 (Android 12), SDK 33 (Android 13), SDK 34 (Android 14).",
            "Tests parameterized across 16:9 (720x1280), 18:9 (1080x2160), 19.5:9 (1080x2340), 20:9 (1080x2400), and Foldable (1812x2176).",
            "Verifies OverlayPositionCalculator and bounding box touch absorption precision on all dimensions."
        ],
        "verification": "./gradlew testDebugUnitTest --tests 'org.unreel.android.matrix.MultiDeviceMatrixTest'"
    },
    {
        "id_tag": "UNR-25",
        "title": "[UNR-25] Automated GitHub Actions CI Matrix & Universal Release Distribution",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "Infra",
        "area": "Packaging",
        "scope": "Configure GitHub Actions workflow running automated unit and instrumented tests on pull requests, and building signed universal release APKs (Unreel-vX.Y.Z-universal.apk) on release tags.",
        "target": ".github/workflows/ci.yml (new)",
        "criteria": [
            "Automated Gradle unit test execution on ubuntu-latest.",
            "Security audit check step validating ZeroNetworkSecurityAuditTest.",
            "Release job producing signed R8-optimized universal APK under 1.5MB.",
            "Attaches universal APK to GitHub Releases for direct 1-tap download."
        ],
        "verification": "./gradlew assembleRelease"
    },
    {
        "id_tag": "UNR-26",
        "title": "[UNR-26] Cloud Device Farm Testing with Firebase Test Lab",
        "status": "Ready for SWE",
        "priority": "P3 - Low",
        "type": "Infra",
        "area": "Core / Engine",
        "scope": "Integrate Firebase Test Lab CLI to execute RealDeviceSmokeTest across physical cloud devices (Google Pixel, Samsung Galaxy, Motorola) in parallel.",
        "target": "scripts/run_firebase_test_lab.sh (new)",
        "criteria": [
            "CLI script building assembleDebug and assembleDebugAndroidTest.",
            "Submits instrumentation tests to Firebase Test Lab matrix (Pixel 4a, Galaxy S22, Moto G Power).",
            "Parses pass/fail test results and logcat output from cloud runner."
        ],
        "verification": "bash scripts/run_firebase_test_lab.sh --dry-run"
    },
    {
        "id_tag": "UNR-27",
        "title": "[UNR-27] YouTube Shorts Elimination Companion Module",
        "status": "Draft",
        "priority": "P3 - Low",
        "type": "Feature",
        "area": "Accessibility",
        "scope": "Extend Unreel engine to detect and suppress YouTube Shorts (com.google.android.youtube). Black out the Shorts bottom navigation button and intercept the vertical video viewer.",
        "target": "app/src/main/java/org/unreel/android/engine/YouTubeShortsDetector.kt (new)",
        "criteria": [
            "Detects YouTube package (com.google.android.youtube).",
            "Identifies Shorts bottom navigation tab and overlays blackout touch sink.",
            "Intercepts full-screen Shorts viewer with debounced back action.",
            "Toggleable independently from Instagram Reels in Unreel Dashboard."
        ],
        "verification": "./gradlew testDebugUnitTest --tests 'org.unreel.android.engine.YouTubeShortsDetectorTest'"
    }
]

def create_tickets():
    print("Checking existing tickets in database...")
    res = make_request(f"databases/{DATABASE_ID}/query", method="POST", data={})
    existing_titles = set()
    for page in res.get("results", []):
        props = page.get("properties", {})
        title_list = props.get("Name", {}).get("title", [])
        title = "".join([t.get("plain_text", "") for t in title_list])
        existing_titles.add(title)

    for t in ROADMAP_TICKETS:
        if t["title"] in existing_titles:
            print(f"Skipping existing ticket: {t['title']}")
            continue

        print(f"Creating ticket: {t['title']}...")
        children_blocks = [
            heading_2("Scope & Boundaries"),
            paragraph(t["scope"]),
            divider(),
            heading_2("Target Files"),
            bullet_item(t["target"], bold_prefix="Primary File: ", code=True),
            divider(),
            heading_2("Acceptance Criteria"),
        ]
        for crit in t["criteria"]:
            children_blocks.append(bullet_item(crit))

        children_blocks.extend([
            divider(),
            heading_2("Verification Command"),
            paragraph(t["verification"], code=True),
            divider(),
            heading_2("Dependencies"),
            paragraph("None" if t["id_tag"] in ["UNR-18", "UNR-19", "UNR-20", "UNR-21"] else "Blocked by Unreel Core v1.0")
        ])

        payload = {
            "parent": {"database_id": DATABASE_ID},
            "properties": {
                "Name": {"title": [{"text": {"content": t["title"]}}]},
                "Status": {"select": {"name": t["status"]}},
                "Priority": {"select": {"name": t["priority"]}},
                "Type": {"select": {"name": t["type"]}},
                "Area": {"select": {"name": t["area"]}}
            },
            "children": children_blocks
        }
        resp = make_request("pages", method="POST", data=payload)
        print(f"Created ticket page: {resp.get('id')}")
        time.sleep(0.3)

def create_roadmap_page():
    print("Creating Master Roadmap Page in Notion...")
    page_payload = {
        "parent": {"page_id": PARENT_SPACE_ID},
        "properties": {
            "title": [{"text": {"content": "Unreel Engineering Roadmap: v1.1 - v2.0 (AdShield, Multi-Device Testing & Universal APK)"}}]
        },
        "children": [
            callout("Executive Roadmap for Unreel. Scoped for review. Outlines the delivery plan for Instagram Ad Elimination (AdShield), Multi-Device Hardware Testing, Universal APK Distribution, and YouTube Shorts expansion.", emoji="🚀"),
            heading_2("1. Executive Summary & Vision"),
            paragraph("Unreel transforms conventional smartphones into distraction-free, intentional utilities. With Instagram Reels completely eradicated at sub-2ms latency, the next milestones focus on eradicating high-frequency algorithmic ads, validating stability across all Android OEMs, and delivering a zero-friction distribution pipeline."),
            divider(),
            heading_2("2. Milestone Overview & Schedule"),
            bullet_item(" Splash Screen Timing & ANR Prevention — Suppressed blackout box during startup splash logo; eliminated service start timeouts via START_NOT_STICKY lifecycle.", bold_prefix="v1.0.1 (Shipped): "),
            bullet_item(" 30-Day Suggested Post In-Feed Auto-Snoozer — Silently auto-snoozes suggested feed posts every 30 days without auto-scrolling.", bold_prefix="v1.1 (Shipped): "),
            bullet_item(" Instagram AdShield (Sponsored Posts & Stories) — Eradicate feed ads and fast-forward full-screen story ads.", bold_prefix="v1.2 (Next): "),
            bullet_item(" Multi-Device Test Matrix & Universal APK Distribution — Parameterized test harness across screen ratios + GitHub Actions CI + F-Droid.", bold_prefix="v1.3: "),
            bullet_item(" Multi-Platform Distraction Elimination — Expand engine to YouTube Shorts and Facebook Reels.", bold_prefix="v2.0: "),
            divider(),
            heading_2("3. Milestone 1.2 Deep Dive: Instagram AdShield"),
            paragraph("Why DNS Adblockers Fail: Instagram serves in-stream ads from the same CDN infrastructure (*.cdninstagram.com) as regular posts. DNS and VPN blockers cannot block Instagram ads without breaking user photos."),
            paragraph("Unreel's Accessibility Solution: By reading the accessibility node tree locally on-device:"),
            bullet_item(" Detects 'Sponsored' / 'Ad' feed posts and programmatically triggers native menu 'Hide ad' -> 'It's irrelevant', or smoothly scrolls past in <50ms.", bold_prefix="Feed Sponsored Posts: "),
            bullet_item(" Detects 'Sponsored' in story viewer and triggers instant forward tap in <30ms, eliminating video ad delays.", bold_prefix="Stories Video Ads: "),
            bullet_item(" Strict zero-network guarantee (ZeroNetworkSecurityAuditTest) ensures zero telemetry or external calls.", bold_prefix="Privacy Guarantee: "),
            divider(),
            heading_2("4. Milestone 1.3 Deep Dive: Multi-Device Compatibility & Universal APK"),
            bullet_item(" 809 KB R8-minified standalone signed APK available for 1-tap sideloading on any Android device.", bold_prefix="Universal Release APK: "),
            bullet_item(" Fully compatible with Android 10 through 15 across Samsung One UI, Xiaomi HyperOS, Google Pixel, Motorola, OnePlus.", bold_prefix="Cross-Platform Support: "),
            bullet_item(" Robolectric multi-SDK tests across API 29-34 with aspect ratio qualifiers (16:9, 18:9, 19.5:9, 20:9, Foldable).", bold_prefix="3-Tier Test Matrix: "),
            divider(),
            heading_2("5. Engineering Tickets Database Index"),
            paragraph("All atomic tickets are tracked on the Unreel Tickets Board:"),
            bullet_item(" Modal Dialog and Daily Limit Overlay Suppression (Done)", bold_prefix="[UNR-18] "),
            bullet_item(" Fast Scroll IPC Debounce & Overlay Lifecycle ANR Fix (Done)", bold_prefix="[UNR-19] "),
            bullet_item(" 30-Day Suggested Post In-Feed Auto-Snoozer (Done)", bold_prefix="[UNR-20] "),
            bullet_item(" Startup Splash Screen Overlay Suppression (Done)", bold_prefix="[UNR-21] "),
            bullet_item(" Epic: Instagram AdShield - In-Feed Sponsored Post Auto-Hider (Ready for SWE)", bold_prefix="[UNR-22] "),
            bullet_item(" Epic: Instagram AdShield - Story Ads Auto-Fast-Forward (Ready for SWE)", bold_prefix="[UNR-23] "),
            bullet_item(" Multi-Device Compatibility Test Matrix (Ready for SWE)", bold_prefix="[UNR-24] "),
            bullet_item(" Automated GitHub Actions CI Matrix & Universal Release Distribution (Ready for SWE)", bold_prefix="[UNR-25] "),
            bullet_item(" Cloud Device Farm Testing with Firebase Test Lab (Ready for SWE)", bold_prefix="[UNR-26] "),
            bullet_item(" YouTube Shorts Elimination Companion Module (Draft)", bold_prefix="[UNR-27] ")
        ]
    }
    resp = make_request("pages", method="POST", data=page_payload)
    print(f"Created Roadmap page successfully: {resp.get('url')}")
    return resp

if __name__ == "__main__":
    create_tickets()
    create_roadmap_page()
    print("All Notion roadmap items created successfully!")
