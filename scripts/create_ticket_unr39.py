#!/usr/bin/env python3
"""
Creates SWE-ready ticket [UNR-39] in Notion for Zero-Delay Pre-Emptive Input Detection (FLAG_WATCH_OUTSIDE_TOUCH) & WindowManager Teardown.
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

TICKET = {
    "id": "UNR-39",
    "title": "[UNR-39] Zero-Delay Pre-Emptive Input Detection (FLAG_WATCH_OUTSIDE_TOUCH) & WindowManager Teardown",
    "priority": "P0 - Blocker",
    "type": "Feature",
    "area": "Core / Engine",
    "status": "In Progress",
    "scope": (
        "Eradicate the 300ms transition lag where the blackout overlay lingers during screen transitions "
        "(direct message threads, settings, comments, profile subpages). Android OS intentionally delays "
        "accessibility event delivery during activity and fragment animations (~300ms), and custom touch handlers "
        "or Jetpack Compose components bypass TYPE_VIEW_CLICKED events. "
        "Equip TouchAbsorberOverlayService with FLAG_WATCH_OUTSIDE_TOUCH to intercept hardware-level touch downs (0ms) "
        "outside the bottom bar, immediately suppressing overlay visibility on frame 0 of navigation transitions. "
        "Enforce strict singleton window lifecycle to prevent duplicate WindowManager instances, and fix "
        "isNavigationalClick node wrapping."
    ),
    "target_files": [
        "app/src/main/java/org/unreel/android/overlay/TouchAbsorberOverlayService.kt",
        "app/src/main/java/org/unreel/android/service/UnreelAccessibilityService.kt",
        "app/src/test/java/org/unreel/android/overlay/TouchAbsorberOverlayServiceTest.kt",
        "app/src/test/java/org/unreel/android/service/UnreelAccessibilityServiceTest.kt"
    ],
    "criteria": [
        "Add FLAG_WATCH_OUTSIDE_TOUCH to TouchAbsorberOverlayService WindowManager.LayoutParams.",
        "Implement handleOutsideTouch: if touch is in content area (y < tabTop), immediately set overlayView.visibility = View.GONE (0ms response).",
        "Preserve overlay visibility when touch is within the bottom navigation bar zone (y >= tabTop) to keep Reels covered during bottom tab switches.",
        "Fix isNavigationalClick in UnreelAccessibilityService to reject whole-window root fallback bounds.",
        "Ensure robust singleton lifecycle with removeViewImmediate/removeView safety against duplicate window leaks.",
        "All unit tests pass cleanly in Gradle.",
        "Verify on physical Pixel 4a with high-speed recording that entering DMs and Settings exhibits 0ms box lingering."
    ],
    "verification": "./gradlew testDebugUnitTest",
    "dependencies": "Blocked by: [UNR-38]"
}

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
            callout(f"Ticket ID: {ticket['id']} — Zero-delay input-level overlay suppression and window lifecycle management."),
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

if __name__ == "__main__":
    create_ticket(TICKET)
