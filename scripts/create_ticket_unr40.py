#!/usr/bin/env python3
"""
Creates SWE-ready ticket [UNR-40] in Notion for Persistent Zero-Jitter Overlay Stability & Accurate Subscreen Navigation Gating.
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

def callout(text, emoji="🎯"):
    return {
        "object": "block",
        "type": "callout",
        "callout": {
            "rich_text": [text_run(text)],
            "icon": {"type": "emoji", "emoji": emoji}
        }
    }

TICKET = {
    "id": "UNR-40",
    "title": "[UNR-40] Persistent Zero-Jitter Overlay Stability & Accurate Subscreen Navigation Gating",
    "priority": "P0 - Blocker",
    "type": "Feature",
    "area": "Core / Engine",
    "status": "In Progress",
    "scope": (
        "Completely eliminate carousel/feed swipe blinking, WindowManager layer churn, and event lag. "
        "Remove FLAG_WATCH_OUTSIDE_TOUCH and all outside-touch overlay suppression so that normal user "
        "interactions (swiping carousels, scrolling the feed, media taps) never cause the blackout overlay "
        "to detach or flicker. On all bottom-navigation screens (Feed, Explore, Inbox, Profile), the overlay "
        "must remain rock-solid and static, appearing fully baked into the app. "
        "Refactor isNavigationalClick to only match explicit navigation triggers (Options button, DM conversation rows, "
        "Comments, Back/Close) rather than defaulting to true on every feed click. Ensure clean, deterministic detachment "
        "when entering screens without a bottom navigation bar."
    ),
    "target_files": [
        "app/src/main/java/org/unreel/android/overlay/TouchAbsorberOverlayService.kt",
        "app/src/main/java/org/unreel/android/service/UnreelAccessibilityService.kt",
        "app/src/test/java/org/unreel/android/overlay/TouchAbsorberOverlayServiceTest.kt",
        "app/src/test/java/org/unreel/android/service/UnreelAccessibilityServiceTest.kt"
    ],
    "criteria": [
        "Remove FLAG_WATCH_OUTSIDE_TOUCH and handleOutsideTouch from TouchAbsorberOverlayService.",
        "Ensure swiping post carousels and vertical scrolling causes zero overlay detachments or visibility toggles.",
        "Refactor isNavigationalClick in UnreelAccessibilityService: only return true for explicit subscreen navigation triggers (Options, DM thread rows, Comments, Back/Close); default to false for feed content clicks.",
        "Maintain rock-solid overlay placement on all bottom-bar tabs (Feed, Search, Direct Inbox, Profile) without WindowManager thrashing.",
        "Ensure clean detachment when opening Direct message conversations, Settings, or Comments.",
        "All unit tests pass cleanly in Gradle (./gradlew testDebugUnitTest).",
        "Verify on physical Pixel 4a that swiping carousels has 0ms jitter and moving to DMs/Settings detaches cleanly."
    ],
    "verification": "./gradlew testDebugUnitTest",
    "dependencies": "Blocked by: [UNR-39]"
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
            callout(f"Ticket ID: {ticket['id']} — Native baked-in overlay stability, zero carousel jitter, and precise subscreen gating."),
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
