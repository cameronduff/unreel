#!/usr/bin/env python3
import os
import json
import urllib.request

token = os.environ.get("NOTION_API_TOKEN")
headers = {
    "Authorization": f"Bearer {token}",
    "Notion-Version": "2022-06-28",
    "Content-Type": "application/json"
}

def update_blocks(page_id, blocks):
    get_url = f"https://api.notion.com/v1/blocks/{page_id}/children"
    req = urllib.request.Request(get_url, headers=headers)
    with urllib.request.urlopen(req) as resp:
        children = json.loads(resp.read().decode())["results"]
    
    for c in children:
        del_url = f"https://api.notion.com/v1/blocks/{c['id']}"
        del_req = urllib.request.Request(del_url, headers=headers, method="DELETE")
        try:
            urllib.request.urlopen(del_req)
        except Exception:
            pass

    post_req = urllib.request.Request(get_url, data=json.dumps({"children": blocks}).encode("utf-8"), headers=headers, method="PATCH")
    with urllib.request.urlopen(post_req) as resp:
        return json.loads(resp.read().decode())

def text_run(content, bold=False):
    return {"type": "text", "text": {"content": content}, "annotations": {"bold": bold}}

def heading_2(text):
    return {"object": "block", "type": "heading_2", "heading_2": {"rich_text": [text_run(text)]}}

def paragraph(text):
    return {"object": "block", "type": "paragraph", "paragraph": {"rich_text": [text_run(text)]}}

def bullet(text):
    return {"object": "block", "type": "bulleted_list_item", "bulleted_list_item": {"rich_text": [text_run(text)]}}

def to_do(text):
    return {"object": "block", "type": "to_do", "to_do": {"rich_text": [text_run(text)], "checked": False}}

def code_block(code_text):
    return {"object": "block", "type": "code", "code": {"rich_text": [text_run(code_text)], "language": "bash"}}

def divider():
    return {"object": "block", "type": "divider", "divider": {}}

# Update [OL-06]
ol06_blocks = [
    heading_2("Scope"),
    paragraph("In Scope: Jetpack Preferences DataStore repository managing core filtering state strictly for Instagram Reels: blockInstagramReels (Boolean, default true) and pauseUntilTimestamp (Long, default 0L). Exposes Flow<FilterPreferences>. Out of Scope: YouTube Shorts, Explore gating, Compose UI rendering."),
    divider(),
    heading_2("Target Files & Directories"),
    bullet("openlite/app/src/main/java/org/openlite/android/data/FilterPreferencesRepository.kt (new)"),
    bullet("openlite/app/src/main/java/org/openlite/android/data/FilterPreferences.kt (new)"),
    bullet("openlite/app/src/test/java/org/openlite/android/data/FilterPreferencesRepositoryTest.kt (new)"),
    divider(),
    heading_2("Acceptance Criteria"),
    to_do("Initial state defaults: blockInstagramReels=true, pauseUntilTimestamp=0L."),
    to_do("setInstagramReelsBlocked(false) updates DataStore and emits new state on Flow within 50ms."),
    to_do("setPauseMinutes(15) sets pauseUntilTimestamp to currentSystemTime + (15 * 60 * 1000)."),
    to_do("isPauseActive() returns false when current time exceeds pauseUntilTimestamp."),
    to_do("DataStore survives simulated app kill and reload in Robolectric test."),
    divider(),
    heading_2("Verification Command"),
    code_block("cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.data.FilterPreferencesRepositoryTest'"),
    divider(),
    heading_2("Dependencies"),
    paragraph("Blocked by: [OL-01] Scaffold Android Kotlin Project with Gradle Version Catalogs and Robolectric Test Harness")
]
update_blocks("3f09da56-a0de-81ad-bd2a-c420de4c9e36", ol06_blocks)
print("Updated [OL-06] body")

# Update [OL-09]
ol09_blocks = [
    heading_2("Scope"),
    paragraph("In Scope: DashboardScreen displaying: (1) Header with daily intercepted Instagram Reels count and estimated minutes saved, (2) Dedicated master toggle switch for Instagram Reels suppression, (3) Temporary pause button (15m, 1h). Subscribed to TelemetryRepository and FilterPreferencesRepository. Out of Scope: Other platforms, Explore feed."),
    divider(),
    heading_2("Target Files & Directories"),
    bullet("openlite/app/src/main/java/org/openlite/android/ui/dashboard/DashboardScreen.kt (new)"),
    bullet("openlite/app/src/main/java/org/openlite/android/ui/dashboard/DashboardViewModel.kt (new)"),
    bullet("openlite/app/src/test/java/org/openlite/android/ui/DashboardViewModelTest.kt (new)"),
    divider(),
    heading_2("Acceptance Criteria"),
    to_do("Dashboard displays current day Instagram Reels count updated in real time via Flow collection."),
    to_do("Toggling 'Block Instagram Reels' switch calls FilterPreferencesRepository.setInstagramReelsBlocked."),
    to_do("Tapping 'Pause for 15m' updates pauseUntilTimestamp and displays remaining countdown timer in UI."),
    to_do("If pause timer expires, UI countdown clears and status badge reverts to 'Active'."),
    to_do("Screen renders cleanly in dark theme (AMOLED pure black #000000 background)."),
    to_do("Unit test asserts ViewModel handles empty database state without crash."),
    divider(),
    heading_2("Verification Command"),
    code_block("cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.ui.DashboardViewModelTest'"),
    divider(),
    heading_2("Dependencies"),
    paragraph("Blocked by: [OL-05] Implement Local SQLite Telemetry Database for Intercept Metrics, [OL-06] Implement DataStore Repository for Instagram Reels Filter and Pause State, [OL-08] Implement Jetpack Compose Onboarding and Permission Wizard")
]
update_blocks("3f09da56-a0de-81c9-bd9b-f6a5786d16c2", ol09_blocks)
print("Updated [OL-09] body")

# Update [OL-13]
ol13_blocks = [
    heading_2("Scope"),
    paragraph("In Scope: Final release hardening for Instagram Reels suppressor. Lint check enforcing android.permission.INTERNET is completely absent from merged manifest. ProGuard/R8 configuration optimizing APK size (< 4MB). GitHub Actions CI workflow running testDebugUnitTest and lintDebug. F-Droid metadata yaml. Out of Scope: Other platforms, Google Play In-App Billing."),
    divider(),
    heading_2("Target Files & Directories"),
    bullet("openlite/app/proguard-rules.pro (new)"),
    bullet("openlite/.github/workflows/ci.yml (new)"),
    bullet("openlite/metadata/org.openlite.android.yml (new)"),
    bullet("openlite/app/src/test/java/org/openlite/android/SecurityManifestAuditTest.kt (new)"),
    divider(),
    heading_2("Acceptance Criteria"),
    to_do("SecurityManifestAuditTest asserts android.permission.INTERNET is NOT present in merged manifest."),
    to_do("SecurityManifestAuditTest asserts zero network libraries (Retrofit, OkHttp, Ktor) are present on runtime classpath."),
    to_do("Release APK size with R8 shrinking is less than 4.0 MB."),
    to_do("GitHub Actions CI workflow executes testDebugUnitTest and lintDebug on ubuntu-latest."),
    to_do("F-Droid metadata file conforms to F-Droid Build Metadata specification."),
    to_do("./gradlew assembleRelease produces unsigned release APK without errors."),
    divider(),
    heading_2("Verification Command"),
    code_block("cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.SecurityManifestAuditTest' && ./gradlew lintDebug"),
    divider(),
    heading_2("Dependencies"),
    paragraph("Blocked by: [OL-03] Implement OpenLiteAccessibilityService Lifecycle and Event Dispatcher, [OL-09] Implement Jetpack Compose Main Dashboard with Reels Intercept Telemetry")
]
update_blocks("3f09da56-a0de-813a-ad03-c721834cb517", ol13_blocks)
print("Updated [OL-13] body")
