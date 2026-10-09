#!/usr/bin/env python3
"""
Creates SWE-ready tickets [UNR-32] to [UNR-36] in Notion for resolving overlay delay on non-feed screens.
"""
import os
import json
import urllib.request
import urllib.error

NOTION_TOKEN = os.environ.get("NOTION_API_TOKEN")
DATABASE_ID = "3f09da56-a0de-819a-ab73-fe3c30394233"
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
    return {
        "object": "block",
        "type": "heading_2",
        "heading_2": {"rich_text": [text_run(text)]}
    }

def paragraph(text):
    return {
        "object": "block",
        "type": "paragraph",
        "paragraph": {"rich_text": [text_run(text)]}
    }

def to_do_item(text, checked=False):
    return {
        "object": "block",
        "type": "to_do",
        "to_do": {
            "rich_text": [text_run(text)],
            "checked": checked
        }
    }

def code_block(code_str, language="bash"):
    return {
        "object": "block",
        "type": "code",
        "code": {
            "rich_text": [text_run(code_str)],
            "language": language
        }
    }

def callout(text, emoji="⚡"):
    return {
        "object": "block",
        "type": "callout",
        "callout": {
            "rich_text": [text_run(text)],
            "icon": {"type": "emoji", "emoji": emoji}
        }
    }

TICKETS = [
    {
        "id": "UNR-32",
        "title": "[UNR-32] Fast-Path Detection for Home-Bar-Free Screens & Re-Attachment Fix",
        "priority": "P0 - Blocker",
        "type": "Feature",
        "area": "Accessibility",
        "status": "Ready for SWE",
        "scope": (
            "Eliminate laggy overlay lingering and accidental re-attachment when navigating to non-feed "
            "screens. Fix Fast-Path 2 in UnreelAccessibilityService to stop blindly re-attaching on generic "
            "InstagramMainActivity state changes without verifying feed presence. Add fast-path detection "
            "for back-button action bars (action_bar_button_back) and non-feed containers for instant 0ms detachment."
        ),
        "target_files": [
            "app/src/main/java/org/unreel/android/service/UnreelAccessibilityService.kt",
            "app/src/test/java/org/unreel/android/service/UnreelAccessibilityServiceTest.kt"
        ],
        "criteria": [
            "Fast-Path 2 does not blindly re-attach overlay when className is InstagramMainActivity unless feed presence is verified.",
            "Navigating to any screen with action_bar_button_back or direct inbox triggers instant overlay detachment.",
            "cachedTabBounds is preserved for subsequent true return to Home feed.",
            "All unit tests pass without regressions."
        ],
        "verification": "./gradlew testDebugUnitTest --tests \"org.unreel.android.service.UnreelAccessibilityServiceTest\"",
        "dependencies": "None"
    },
    {
        "id": "UNR-33",
        "title": "[UNR-33] Bottom-Up Target-Directed Navigation Bar Scanner",
        "priority": "P0 - Blocker",
        "type": "Feature",
        "area": "Core / Engine",
        "status": "Ready for SWE",
        "scope": (
            "Refactor InstagramHierarchyScanner to prioritize inspecting the lower portion of the screen (bottom 15%) "
            "and direct children of layout_container_main_panel in reverse order. Prevent exhausting the 70-node BFS traversal "
            "on screens without a bottom navigation bar (Settings, message threads, search results). Recognize Settings and "
            "inbox headers to short-circuit immediately."
        ),
        "target_files": [
            "app/src/main/java/org/unreel/android/engine/InstagramHierarchyScanner.kt",
            "app/src/test/java/org/unreel/android/engine/InstagramHierarchyScannerTest.kt"
        ],
        "criteria": [
            "Resolves tab_bar in <= 10 node visits on feed screens instead of traversing deep into feed contents.",
            "Detects absence of tab_bar in <= 15 nodes on non-feed screens like Settings or messages.",
            "Recognizes direct_inbox_action_bar and settings action bars as isDirectThreadActive / non-feed screens.",
            "All hierarchy scanner tests pass with 100% accuracy."
        ],
        "verification": "./gradlew testDebugUnitTest --tests \"org.unreel.android.engine.InstagramHierarchyScannerTest\"",
        "dependencies": "Blocked by: [UNR-32]"
    },
    {
        "id": "UNR-34",
        "title": "[UNR-34] Guard Secondary Engines (AdShield, Snoozer & StoryAd) on Non-Feed Screens",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "Core / Engine",
        "status": "Ready for SWE",
        "scope": (
            "Prevent secondary scanning engines (InstagramSuggestedPostSnoozer, InstagramFeedAdShield, "
            "InstagramStoryAdDetector) from executing expensive BFS traversals (up to 180 Binder IPCs) "
            "when reelsTabBounds is null or the user is not in the home feed. Eliminates UI thread lag during screen transitions."
        ),
        "target_files": [
            "app/src/main/java/org/unreel/android/service/UnreelAccessibilityService.kt",
            "app/src/test/java/org/unreel/android/service/UnreelAccessibilityServiceTest.kt"
        ],
        "criteria": [
            "InstagramSuggestedPostSnoozer is bypassed entirely when scan.reelsTabBounds == null.",
            "InstagramFeedAdShield is bypassed entirely when scan.reelsTabBounds == null.",
            "InstagramStoryAdDetector only runs when story viewer container is detected.",
            "Zero secondary traversal overhead occurs while user is in Settings or Direct Messages."
        ],
        "verification": "./gradlew testDebugUnitTest --tests \"org.unreel.android.service.UnreelAccessibilityServiceTest\"",
        "dependencies": "Blocked by: [UNR-33]"
    },
    {
        "id": "UNR-35",
        "title": "[UNR-35] Instant Visual Invisibility (View.GONE) on TouchAbsorber Overlay Detachment",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "Overlay",
        "status": "Ready for SWE",
        "scope": (
            "Ensure the TouchAbsorber blackout box vanishes immediately from the user screen without waiting "
            "for WindowManagerService to execute window detachment IPC. In TouchAbsorberOverlayService.detachOverlay(), "
            "synchronously set overlayView.visibility = View.GONE before removing from WindowManager."
        ),
        "target_files": [
            "app/src/main/java/org/unreel/android/overlay/TouchAbsorberOverlayService.kt",
            "app/src/test/java/org/unreel/android/overlay/TouchAbsorberOverlayServiceTest.kt"
        ],
        "criteria": [
            "overlayView visibility is set to View.GONE immediately upon detachOverlay invocation.",
            "WindowManager.removeView is safely executed without NPE or window leak.",
            "When re-attaching, overlayView visibility is ensured to be View.VISIBLE.",
            "Zero visual box ghosting during rapid attach/detach sequences."
        ],
        "verification": "./gradlew testDebugUnitTest --tests \"org.unreel.android.overlay.TouchAbsorberOverlayServiceTest\"",
        "dependencies": "Blocked by: [UNR-32]"
    },
    {
        "id": "UNR-36",
        "title": "[UNR-36] Real-Device Transition Latency Benchmark & Verification Suite",
        "priority": "P0 - Blocker",
        "type": "TDD / Test",
        "area": "Accessibility",
        "status": "Ready for SWE",
        "scope": (
            "Validate that overlay disappearance latency on real hardware (Pixel 4a) is reduced from "
            "1,200ms-5,500ms down to < 100ms when transitioning to Settings and Direct Messages. Create "
            "an automated test verifying instantaneous overlay removal."
        ),
        "target_files": [
            "scripts/verify_real_device.sh",
            "scripts/e2e_device_test_suite.py"
        ],
        "criteria": [
            "Live transition from Feed to Settings detaches overlay in < 100ms.",
            "Live transition from Feed to Direct Messages detaches overlay in < 100ms.",
            "Re-entry from Settings / DMs back to Home feed restores blackout box accurately over Reels tab.",
            "All unit and real-device automated tests pass cleanly."
        ],
        "verification": "./scripts/run_e2e_device_test.sh",
        "dependencies": "Blocked by: [UNR-32], [UNR-33], [UNR-34], [UNR-35]"
    }
]

def create_ticket(ticket):
    page_data = {
        "parent": {"database_id": DATABASE_ID},
        "properties": {
            "Name": {
                "title": [{"text": {"content": ticket["title"]}}]
            },
            "Status": {
                "select": {"name": ticket["status"]}
            },
            "Priority": {
                "select": {"name": ticket["priority"]}
            },
            "Type": {
                "select": {"name": ticket["type"]}
            },
            "Area": {
                "select": {"name": ticket["area"]}
            }
        },
        "children": [
            callout(f"Ticket ID: {ticket['id']} — Scoped for zero-latency overlay detachment on non-feed screens."),
            heading_2("Scope"),
            paragraph(ticket["scope"]),
            heading_2("Target Files & Directories"),
            *[paragraph(f"• {f}") for f in ticket["target_files"]],
            heading_2("Acceptance Criteria"),
            *[to_do_item(crit) for crit in ticket["criteria"]],
            heading_2("Verification Command"),
            code_block(ticket["verification"], language="bash"),
            heading_2("Dependencies"),
            paragraph(ticket["dependencies"])
        ]
    }
    res = make_request("pages", method="POST", data=page_data)
    print(f"✅ Created ticket {ticket['id']}: {ticket['title']} (Notion Page ID: {res['id']})")
    return res["id"]

def main():
    print(f"Creating {len(TICKETS)} tickets in Notion database {DATABASE_ID}...")
    for t in TICKETS:
        create_ticket(t)
    print("\nAll tickets created successfully in Notion!")

if __name__ == "__main__":
    main()
