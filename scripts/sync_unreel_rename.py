#!/usr/bin/env python3
"""
Syncs the renaming of OpenLite -> Unreel across Notion workspace, database, PRD, and all 17 atomic tickets.
"""

import os
import json
import urllib.request

NOTION_TOKEN = os.environ.get("NOTION_API_TOKEN")
HEADERS = {
    "Authorization": f"Bearer {NOTION_TOKEN}",
    "Notion-Version": "2022-06-28",
    "Content-Type": "application/json"
}

def patch_page(page_id, data):
    url = f"https://api.notion.com/v1/pages/{page_id}"
    req = urllib.request.Request(url, data=json.dumps(data).encode("utf-8"), headers=HEADERS, method="PATCH")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

def patch_database(database_id, title_text):
    url = f"https://api.notion.com/v1/databases/{database_id}"
    req = urllib.request.Request(url, data=json.dumps({
        "title": [{"type": "text", "text": {"content": title_text}}]
    }).encode("utf-8"), headers=HEADERS, method="PATCH")
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode())

# 1. Update Project Space Page
print("Updating Project Space Page...")
patch_page("3f09da56-a0de-8126-8bc8-fabee329995d", {
    "properties": {
        "title": {
            "title": [{"type": "text", "text": {"content": "Unreel: Open-Source Instagram Reels Eliminator"}}]
        }
    }
})

# 2. Update Database Title
print("Updating Database Title...")
patch_database("3f09da56-a0de-819a-ab73-fe3c30394233", "Unreel: Engineering Roadmap & Tickets")

# 3. Update PRD Document Title
print("Updating PRD Document Title...")
patch_page("3f09da56-a0de-8129-9f18-d92bc76395a6", {
    "properties": {
        "title": {
            "title": [{"type": "text", "text": {"content": "Product Requirements Document (PRD): Unreel v1.0"}}]
        }
    }
})

# 4. Fetch all 17 tickets and rename [OL-XX] -> [UNR-XX]
print("Fetching tickets to update titles and target paths...")
query_url = "https://api.notion.com/v1/databases/3f09da56-a0de-819a-ab73-fe3c30394233/query"
req = urllib.request.Request(query_url, data=json.dumps({}).encode("utf-8"), headers=HEADERS, method="POST")
with urllib.request.urlopen(req) as resp:
    tickets = json.loads(resp.read().decode())["results"]

for t in tickets:
    tid = t["id"]
    old_title = t["properties"]["Name"]["title"][0]["plain_text"]
    new_title = old_title.replace("[OL-", "[UNR-").replace("OpenLite", "Unreel")
    patch_page(tid, {
        "properties": {
            "Name": {"title": [{"type": "text", "text": {"content": new_title}}]}
        }
    })
    print(f"Renamed: {old_title} -> {new_title}")

print("All Notion titles updated successfully to Unreel!")
