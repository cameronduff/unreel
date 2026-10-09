#!/usr/bin/env python3
"""
Creates SWE-ready ticket [UNR-38] in Notion for instant pre-emptive overlay detachment on navigation transitions.
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
    "id": "UNR-38",
    "title": "[UNR-38] Instant Pre-Emptive Overlay Detachment on Navigation & Screen Transitions",
    "priority": "P0 - Blocker",
    "type": "Feature",
    "area": "Core / Engine",
    "status": "In Progress",
    "scope": (
        "When users navigate away from screens with a bottom navigation bar into sub-pages without one "
        "(direct message threads, settings, profile details, comments), Android fragment animations take "
        "200-400ms during which the blackout overlay statically lingers over input fields and content. "
        "Unreel must immediately detach the overlay on TYPE_VIEW_CLICKED for non-bottom-bar navigation actions, "
        "set notificationTimeout to 0ms in accessibility config, prioritize top action bar back-button checks, "
        "and correct direct_inbox false-positives so screen transitions feel completely snappy with zero visible delay."
    ),
    "target_files": [
        "app/src/main/res/xml/accessibility_service_config.xml",
        "app/src/main/java/org/unreel/android/service/UnreelAccessibilityService.kt",
        "app/src/main/java/org/unreel/android/engine/InstagramHierarchyScanner.kt",
        "app/src/test/java/org/unreel/android/engine/InstagramHierarchyScannerTest.kt",
        "app/src/test/java/org/unreel/android/service/UnreelAccessibilityServiceTest.kt"
    ],
    "criteria": [
        "Pre-emptively hide overlay immediately upon receiving TYPE_VIEW_CLICKED on navigation elements, list items, search, or back buttons outside bottom bar.",
        "Set android:notificationTimeout='0' in accessibility_service_config.xml to eliminate IPC dispatch queuing delays.",
        "Remove direct_inbox from isDirectThreadActive in InstagramHierarchyScanner to prevent incorrect overlay suppression on inbox tab.",
        "Prioritize top action bar and back-button detection in InstagramHierarchyScanner to short-circuit before checking bottom navigation bar.",
        "Verify on physical device that transitioning into message threads and settings has zero visual box lingering on screen.",
        "All unit tests pass cleanly in Gradle."
    ],
    "verification": "./gradlew testDebugUnitTest",
    "dependencies": "Blocked by: [UNR-37]"
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
            callout(f"Ticket ID: {ticket['id']} — Instant pre-emptive overlay detachment on navigation transitions to eradicate box lingering."),
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
