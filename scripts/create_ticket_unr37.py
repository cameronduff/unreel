#!/usr/bin/env python3
"""
Creates SWE-ready ticket [UNR-37] in Notion for ignoring dormant/invisible hierarchy nodes.
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
    "id": "UNR-37",
    "title": "[UNR-37] Ignore Dormant / Invisible Hierarchy Nodes in Subscreen & Direct Thread Detection",
    "priority": "P0 - Blocker",
    "type": "Feature",
    "area": "Core / Engine",
    "status": "In Progress",
    "scope": (
        "In Instagram's single-activity architecture (InstagramMainActivity), previously visited subscreen/fragment "
        "views (such as thread_fragment_container, direct_thread, header_left_button, action_bar_button_back) remain "
        "in the view hierarchy in memory with isVisibleToUser = false after popping back to Home feed. "
        "InstagramHierarchyScanner must explicitly check current.isVisibleToUser before flagging isDirect, isModal, "
        "or back button matches so dormant invisible nodes do not permanently suppress the overlay on Home feed."
    ),
    "target_files": [
        "app/src/main/java/org/unreel/android/engine/InstagramHierarchyScanner.kt",
        "app/src/test/java/org/unreel/android/engine/InstagramHierarchyScannerTest.kt"
    ],
    "criteria": [
        "Invisible/dormant subscreen and direct thread nodes (isVisibleToUser == false) do not flag isDirectThreadActive = true.",
        "Invisible/dormant modal nodes (isVisibleToUser == false) do not flag isModalOpen = true.",
        "Real-device Home feed retains overlay at Rect(216, 2142 - 432, 2274) even after navigating into and returning from direct message threads or settings.",
        "All Robolectric unit and hierarchy tests pass cleanly."
    ],
    "verification": "./gradlew testDebugUnitTest --tests \"org.unreel.android.engine.InstagramHierarchyScannerTest\"",
    "dependencies": "Blocked by: [UNR-33]"
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
            callout(f"Ticket ID: {ticket['id']} — Ignore dormant/invisible hierarchy nodes to prevent overlay suppression on Home feed."),
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
