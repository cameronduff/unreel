#!/usr/bin/env python3
"""
Updates ticket status in Notion.
"""
import os
import sys
import json
import urllib.request
import urllib.error

NOTION_TOKEN = os.environ.get("NOTION_API_TOKEN")
NOTION_API_URL = "https://api.notion.com/v1"

HEADERS = {
    "Authorization": f"Bearer {NOTION_TOKEN}",
    "Notion-Version": "2022-06-28",
    "Content-Type": "application/json"
}

def update_status(page_id, status="Done"):
    url = f"{NOTION_API_URL}/pages/{page_id}"
    payload = json.dumps({
        "properties": {
            "Status": {
                "select": {"name": status}
            }
        }
    }).encode("utf-8")
    req = urllib.request.Request(url, data=payload, headers=HEADERS, method="PATCH")
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print(f"Successfully updated page {page_id} to status: {status}")
            return data
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode("utf-8")
        print(f"HTTP Error {e.code}: {err_msg}")
        raise e

if __name__ == "__main__":
    page_id = sys.argv[1] if len(sys.argv) > 1 else "3f49da56-a0de-812d-91f6-ef269026fce6"
    status = sys.argv[2] if len(sys.argv) > 2 else "Done"
    update_status(page_id, status)
