#!/usr/bin/env python3
"""
Creates structured, SWE-ready tickets in Cameron's Notion database for OpenLite.
Database ID: 3f09da56-a0de-819a-ab73-fe3c30394233
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

def heading_3(text):
    return {
        "object": "block",
        "type": "heading_3",
        "heading_3": {"rich_text": [text_run(text)]}
    }

def paragraph(text):
    return {
        "object": "block",
        "type": "paragraph",
        "paragraph": {"rich_text": [text_run(text)]}
    }

def bullet(text, bold_prefix=None):
    runs = []
    if bold_prefix:
        runs.append(text_run(bold_prefix, bold=True))
    runs.append(text_run(text))
    return {
        "object": "block",
        "type": "bulleted_list_item",
        "bulleted_list_item": {"rich_text": runs}
    }

def to_do(text, checked=False):
    return {
        "object": "block",
        "type": "to_do",
        "to_do": {
            "rich_text": [text_run(text)],
            "checked": checked
        }
    }

def code_block(code_text, lang="bash"):
    return {
        "object": "block",
        "type": "code",
        "code": {
            "rich_text": [text_run(code_text)],
            "language": lang
        }
    }

def divider():
    return {"object": "block", "type": "divider", "divider": {}}

def create_ticket(title, status, priority, ticket_type, area, scope, target_files, acceptance_criteria, verification_cmd, dependencies):
    blocks = [
        heading_2("Scope"),
        paragraph(scope),
        divider(),
        heading_2("Target Files & Directories"),
    ]
    for tf in target_files:
        blocks.append(bullet(tf))
    
    blocks.append(divider())
    blocks.append(heading_2("Acceptance Criteria"))
    for ac in acceptance_criteria:
        blocks.append(to_do(ac))
        
    blocks.append(divider())
    blocks.append(heading_2("Verification Command"))
    blocks.append(code_block(verification_cmd, "bash"))
    
    blocks.append(divider())
    blocks.append(heading_2("Dependencies"))
    blocks.append(paragraph(dependencies))

    page_data = {
        "parent": {"database_id": DATABASE_ID},
        "properties": {
            "Name": {"title": [{"type": "text", "text": {"content": title}}]},
            "Status": {"select": {"name": status}},
            "Priority": {"select": {"name": priority}},
            "Type": {"select": {"name": ticket_type}},
            "Area": {"select": {"name": area}}
        },
        "children": blocks[:95]
    }
    
    res = make_request("pages", method="POST", data=page_data)
    print(f"Created ticket: {title} -> {res['id']}")
    return res

TICKETS = [
    {
        "title": "[OL-01] Scaffold Android Kotlin Project with Gradle Version Catalogs and Robolectric Test Harness",
        "status": "Ready for SWE",
        "priority": "P0 - Blocker",
        "type": "Infra",
        "area": "Core / Engine",
        "scope": "In Scope: Initialize openlite/ Android project with Gradle Kotlin DSL, gradle/libs.versions.toml version catalog, Android Gradle Plugin 8.5+, Kotlin 2.0+, and Robolectric 4.12+ for JVM-based accessibility node unit testing without emulator. Out of Scope: UI implementation, accessibility service logic.",
        "target_files": [
            "openlite/settings.gradle.kts (new)",
            "openlite/build.gradle.kts (new)",
            "openlite/gradle/libs.versions.toml (new)",
            "openlite/app/build.gradle.kts (new)",
            "openlite/app/src/main/AndroidManifest.xml (new)",
            "openlite/app/src/test/java/org/openlite/android/SmokeTest.kt (new)"
        ],
        "acceptance_criteria": [
            "./gradlew testDebugUnitTest executes in under 15 seconds on JVM without emulator.",
            "gradle/libs.versions.toml centralizes dependencies (AndroidX, Robolectric, JUnit4, Compose).",
            "Root AndroidManifest.xml compiles without android.permission.INTERNET.",
            "SmokeTest.kt asserts Robolectric runtime environment initializes successfully.",
            "Clean build passes with zero compiler warnings under -Werror."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.SmokeTest'",
        "dependencies": "None"
    },
    {
        "title": "[OL-02] Implement Instagram View State Node Parser with Robolectric Unit Tests",
        "status": "Ready for SWE",
        "priority": "P0 - Blocker",
        "type": "TDD / Test",
        "area": "Core / Engine",
        "scope": "In Scope: Pure Kotlin detector InstagramViewDetector.isReelsActive(rootNode: AccessibilityNodeInfoCompat?) returning Boolean. Inspects resource IDs (clips_video_container, clips_swipe_refresh_container) and semantic text/contentDescription 'Reels'. Fully tested via mock node trees in Robolectric. Out of Scope: Calling performGlobalAction(BACK) or service lifecycle.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/engine/InstagramViewDetector.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/engine/InstagramViewDetectorTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Returns true when rootNode contains child with viewId 'com.instagram.android:id/clips_video_container'.",
            "Returns true when rootNode contains selected tab with text 'Reels' or contentDescription 'Reels'.",
            "Returns false when rootNode is null (null safety check).",
            "Returns false when rootNode represents normal Feed (e.g. viewId 'feed_recycler_view' with no clips container).",
            "Returns false when rootNode represents Direct Messages inbox (e.g. viewId 'direct_inbox_layout').",
            "Node hierarchy traversal does not exceed 100 iterations or throw StackOverflowError on deeply nested trees."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.engine.InstagramViewDetectorTest'",
        "dependencies": "Blocked by: [OL-01] Scaffold Android Kotlin Project with Gradle Version Catalogs and Robolectric Test Harness"
    },
    {
        "title": "[OL-03] Implement OpenLiteAccessibilityService Lifecycle and Event Dispatcher",
        "status": "Ready for SWE",
        "priority": "P0 - Blocker",
        "type": "Feature",
        "area": "Accessibility",
        "scope": "In Scope: Android AccessibilityService subclass listening to TYPE_WINDOW_STATE_CHANGED and TYPE_VIEW_CLICKED for package 'com.instagram.android'. Invokes InstagramViewDetector and fires performGlobalAction(GLOBAL_ACTION_BACK) when Reels is detected. Service manifest & accessibility_service_config.xml. Out of Scope: Overlay views, persistent storage.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/service/OpenLiteAccessibilityService.kt (new)",
            "openlite/app/src/main/res/xml/accessibility_service_config.xml (new)",
            "openlite/app/src/main/AndroidManifest.xml (new)",
            "openlite/app/src/test/java/org/openlite/android/service/OpenLiteAccessibilityServiceTest.kt (new)"
        ],
        "acceptance_criteria": [
            "accessibility_service_config.xml sets feedbackType='feedbackGeneric' and eventTypes='typeWindowStateChanged|typeViewClicked'.",
            "Ignores events where event.packageName != 'com.instagram.android'.",
            "Triggers performGlobalAction(GLOBAL_ACTION_BACK) exactly once per Reels detection event.",
            "Debounces back actions within 300ms to prevent rapid double-back popping out of Instagram completely.",
            "Handles null rootInActiveWindow gracefully without throwing NullPointerException.",
            "Service unbind/destroy clears all active handler callbacks and observers."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.service.OpenLiteAccessibilityServiceTest'",
        "dependencies": "Blocked by: [OL-02] Implement Instagram View State Node Parser with Robolectric Unit Tests"
    },
    {
        "title": "[OL-04] Implement Instagram Explore Tab Gating and Search Redirection",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "Core / Engine",
        "scope": "In Scope: Detector and action logic to identify when the user activates the Instagram Explore grid (viewId 'explore_tab' or contentDescription 'Search and explore'). Configurable suppression: either bounce back to Feed or simulate tap on Search bar to allow utility search while hiding algorithmic grid. Out of Scope: Reels detection.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/engine/ExploreDetector.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/engine/ExploreDetectorTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Returns true when explore tab grid node 'com.instagram.android:id/explore_grid' is present and active.",
            "Returns false when search query input field is actively focused by user (allows typing search queries).",
            "Returns false when rootNode is null.",
            "Unit test asserts redirect or back action fires only when blockExplore preference is true.",
            "Does not trigger when user is viewing search results for a specific hashtag or username."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.engine.ExploreDetectorTest'",
        "dependencies": "Blocked by: [OL-03] Implement OpenLiteAccessibilityService Lifecycle and Event Dispatcher"
    },
    {
        "title": "[OL-05] Implement Local SQLite Telemetry Database for Intercept Metrics",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "Telemetry",
        "scope": "In Scope: On-device Room/SQLite database recording local InterceptEvent records (id, timestamp, packageName, triggerType). DAO methods: insertEvent, getTodayCount, getTotalTimeSavedMinutes(estimatedSecondsPerReel = 45). 100% on-device, zero network transmission. Out of Scope: Cloud sync, telemetry upload.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/data/TelemetryDatabase.kt (new)",
            "openlite/app/src/main/java/org/openlite/android/data/InterceptEventDao.kt (new)",
            "openlite/app/src/main/java/org/openlite/android/data/TelemetryRepository.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/data/TelemetryRepositoryTest.kt (new)"
        ],
        "acceptance_criteria": [
            "insertEvent stores timestamp in UTC epoch milliseconds.",
            "getTodayCount returns exact count of intercepts occurring between midnight today and current timestamp.",
            "getTotalTimeSavedMinutes calculates (count * 45s) / 60 rounded to nearest integer.",
            "Database operations execute asynchronously on Dispatchers.IO without blocking main thread.",
            "Database migrations schema test validates clean database creation.",
            "Zero network calls or internet permissions are referenced in data layer."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.data.TelemetryRepositoryTest'",
        "dependencies": "Blocked by: [OL-01] Scaffold Android Kotlin Project with Gradle Version Catalogs and Robolectric Test Harness"
    },
    {
        "title": "[OL-06] Implement DataStore Repository for Filter Rule Toggles",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "Core / Engine",
        "scope": "In Scope: Jetpack Preferences DataStore repository managing user preferences: blockInstagramReels (Boolean, default true), blockInstagramExplore (Boolean, default false), blockYouTubeShorts (Boolean, default true), pauseUntilTimestamp (Long, default 0L). Exposes Flow<FilterPreferences>. Out of Scope: Compose UI rendering.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/data/FilterPreferencesRepository.kt (new)",
            "openlite/app/src/main/java/org/openlite/android/data/FilterPreferences.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/data/FilterPreferencesRepositoryTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Initial state defaults: blockInstagramReels=true, blockInstagramExplore=false, blockYouTubeShorts=true, pauseUntilTimestamp=0.",
            "setInstagramReelsBlocked(false) updates DataStore and emits new state on Flow within 50ms.",
            "setPauseMinutes(15) sets pauseUntilTimestamp to currentSystemTime + (15 * 60 * 1000).",
            "isPauseActive() returns false when current time exceeds pauseUntilTimestamp.",
            "DataStore survives simulated app kill and reload in Robolectric test."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.data.FilterPreferencesRepositoryTest'",
        "dependencies": "Blocked by: [OL-01] Scaffold Android Kotlin Project with Gradle Version Catalogs and Robolectric Test Harness"
    },
    {
        "title": "[OL-07] Implement Accessibility Permission Status Observer and System Settings Intent",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "UI / Dashboard",
        "scope": "In Scope: Utility class AccessibilityPermissionHelper to check whether OpenLiteAccessibilityService is currently enabled in Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES. Intent generator opening Settings.ACTION_ACCESSIBILITY_SETTINGS with highlight extras. Out of Scope: Compose UI elements.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/util/AccessibilityPermissionHelper.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/util/AccessibilityPermissionHelperTest.kt (new)"
        ],
        "acceptance_criteria": [
            "isServiceEnabled(context) returns true when OpenLite service component name is present in enabled accessibility services string.",
            "isServiceEnabled(context) returns false when enabled accessibility string is empty, null, or contains only other third-party services.",
            "createSettingsIntent() returns Intent with action Settings.ACTION_ACCESSIBILITY_SETTINGS.",
            "Intent flags set FLAG_ACTIVITY_NEW_TASK.",
            "Unit test asserts behavior across simulated Android 10 (API 29) through Android 14 (API 34) settings strings."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.util.AccessibilityPermissionHelperTest'",
        "dependencies": "Blocked by: [OL-03] Implement OpenLiteAccessibilityService Lifecycle and Event Dispatcher"
    },
    {
        "title": "[OL-08] Implement Jetpack Compose Onboarding and Permission Wizard",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "Feature",
        "area": "UI / Dashboard",
        "scope": "In Scope: Minimalist Jetpack Compose screen OnboardingScreen walking first-time users through enabling the Accessibility permission. Shows animated/live status indicator (Red: Inactive -> Green: Active). Primary CTA button launches accessibility settings. Out of Scope: Main dashboard stats.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/ui/onboarding/OnboardingScreen.kt (new)",
            "openlite/app/src/main/java/org/openlite/android/ui/onboarding/OnboardingViewModel.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/ui/OnboardingViewModelTest.kt (new)"
        ],
        "acceptance_criteria": [
            "When accessibility service is disabled, screen renders 'Permission Required' card with 'Enable OpenLite' button.",
            "Tapping 'Enable OpenLite' fires context.startActivity with Settings.ACTION_ACCESSIBILITY_SETTINGS.",
            "When service is detected as enabled upon returning to foreground (onResume), ViewModel transitions state to OnboardingCompleted.",
            "UI follows minimalist AMOLED black/white design system with zero gradients or distraction banners.",
            "Robolectric Compose test verifies state transitions when isServiceEnabled toggles."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.ui.OnboardingViewModelTest'",
        "dependencies": "Blocked by: [OL-06] Implement DataStore Repository for Filter Rule Toggles, [OL-07] Implement Accessibility Permission Status Observer and System Settings Intent"
    },
    {
        "title": "[OL-09] Implement Jetpack Compose Main Dashboard with Live Intercept Telemetry",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "Feature",
        "area": "UI / Dashboard",
        "scope": "In Scope: DashboardScreen displaying: (1) Header with daily intercepted count and estimated time saved in minutes, (2) Toggle list for Instagram Reels, Instagram Explore, and YouTube Shorts, (3) Pause button (15m, 1h). Subscribed to TelemetryRepository and FilterPreferencesRepository. Out of Scope: Onboarding wizard.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/ui/dashboard/DashboardScreen.kt (new)",
            "openlite/app/src/main/java/org/openlite/android/ui/dashboard/DashboardViewModel.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/ui/DashboardViewModelTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Dashboard displays current day intercept count updated in real time via Flow collection.",
            "Toggling 'Block Instagram Reels' switch calls FilterPreferencesRepository.setInstagramReelsBlocked.",
            "Tapping 'Pause for 15m' updates pauseUntilTimestamp and displays remaining countdown timer in UI.",
            "If pause timer expires, UI countdown clears and status badge reverts to 'Active'.",
            "Screen renders properly in dark theme (AMOLED pure black #000000 background).",
            "Unit test asserts ViewModel handles empty database state without crash."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.ui.DashboardViewModelTest'",
        "dependencies": "Blocked by: [OL-05] Implement Local SQLite Telemetry Database for Intercept Metrics, [OL-06] Implement DataStore Repository for Filter Rule Toggles, [OL-08] Implement Jetpack Compose Onboarding and Permission Wizard"
    },
    {
        "title": "[OL-10] Implement Android Quick Settings Tile for 15-Minute Intercept Pause",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "Feature",
        "area": "Core / Engine",
        "scope": "In Scope: TileService subclass OpenLiteTileService registered in AndroidManifest.xml. Displays OpenLite status (Tile.STATE_ACTIVE or Tile.STATE_INACTIVE). Clicking tile toggles 15-minute temporary bypass window and updates subtitle. Out of Scope: UI dashboard.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/service/OpenLiteTileService.kt (new)",
            "openlite/app/src/main/AndroidManifest.xml (new)",
            "openlite/app/src/test/java/org/openlite/android/service/OpenLiteTileServiceTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Tile is declared in manifest with android.permission.BIND_QUICK_SETTINGS_TILE.",
            "onTileAdded / onStartListening reads FilterPreferencesRepository to set tile state.",
            "When OpenLite is active, tile state is STATE_ACTIVE with subtitle 'Blocking Reels'.",
            "When clicked, toggles pause state: if active, pauses for 15 minutes (state becomes STATE_INACTIVE with subtitle 'Paused'); if paused, unpauses immediately.",
            "Unit test validates tile state updates when DataStore preference emits."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.service.OpenLiteTileServiceTest'",
        "dependencies": "Blocked by: [OL-06] Implement DataStore Repository for Filter Rule Toggles"
    },
    {
        "title": "[OL-11] Implement Touch Absorber Overlay Service for Reels Bottom Navigation Tab",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "Feature",
        "area": "Overlay",
        "scope": "In Scope: Optional WindowManager overlay service using TYPE_APPLICATION_OVERLAY. Draws transparent 48x48dp touch sink positioned over the 4th navigation icon in Instagram bottom bar. Absorbs click events to prevent triggering Reels intent. Active only when com.instagram.android is foregrounded. Out of Scope: Accessibility service logic.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/overlay/TouchAbsorberOverlayService.kt (new)",
            "openlite/app/src/main/java/org/openlite/android/overlay/OverlayPositionCalculator.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/overlay/OverlayPositionCalculatorTest.kt (new)"
        ],
        "acceptance_criteria": [
            "OverlayPositionCalculator computes exact screen coordinates for bottom bar tab 4 based on DisplayMetrics.",
            "Overlay view consumes MotionEvent.ACTION_DOWN and returns true (absorbing tap).",
            "Overlay view visibility is set to GONE when Instagram is not the foreground application.",
            "Handles screen rotation (portrait vs landscape) by recalculating coordinates without crash.",
            "Does not attempt to attach overlay if Settings.canDrawOverlays(context) is false.",
            "Unit test validates coordinate math across standard 1080p, 1440p, and 720p aspect ratios."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.overlay.OverlayPositionCalculatorTest'",
        "dependencies": "Blocked by: [OL-06] Implement DataStore Repository for Filter Rule Toggles"
    },
    {
        "title": "[OL-12] Implement YouTube Shorts View Detector and Suppression Rules",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "Feature",
        "area": "Core / Engine",
        "scope": "In Scope: Modular detector YouTubeShortsDetector inspecting package 'com.google.android.youtube'. Detects Shorts container (viewId 'reel_recycler', 'shorts_container') and bottom bar Shorts tab. Invokes performGlobalAction(BACK) or navigates to Subscriptions feed. Out of Scope: Web view filtering.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/engine/YouTubeShortsDetector.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/engine/YouTubeShortsDetectorTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Returns true when rootNode contains child with viewId 'com.google.android.youtube:id/reel_recycler'.",
            "Returns true when selected bottom navigation tab has contentDescription 'Shorts'.",
            "Returns false when user is watching standard long-form YouTube video (e.g. 'watch_while_layout').",
            "Returns false when rootNode is null.",
            "Suppression fires only when FilterPreferences.blockYouTubeShorts is true.",
            "Unit test asserting simulated node hierarchy detects shorts container in <= 5ms."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.engine.YouTubeShortsDetectorTest'",
        "dependencies": "Blocked by: [OL-03] Implement OpenLiteAccessibilityService Lifecycle and Event Dispatcher, [OL-06] Implement DataStore Repository for Filter Rule Toggles"
    },
    {
        "title": "[OL-13] Audit Zero-Permission Security Posture and Configure F-Droid Release Pipeline",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Infra",
        "area": "Packaging",
        "scope": "In Scope: Final release hardening. Lint check enforcing android.permission.INTERNET is completely absent from merged manifest. ProGuard/R8 configuration optimizing APK size (< 5MB). GitHub Actions CI workflow running testDebugUnitTest and lintDebug. F-Droid metadata yaml. Out of Scope: Google Play In-App Billing (strictly non-commercial).",
        "target_files": [
            "openlite/app/proguard-rules.pro (new)",
            "openlite/.github/workflows/ci.yml (new)",
            "openlite/metadata/org.openlite.android.yml (new)",
            "openlite/app/src/test/java/org/openlite/android/SecurityManifestAuditTest.kt (new)"
        ],
        "acceptance_criteria": [
            "SecurityManifestAuditTest asserts android.permission.INTERNET is NOT present in merged manifest.",
            "SecurityManifestAuditTest asserts zero network libraries (Retrofit, OkHttp, Ktor) are present on runtime classpath.",
            "Release APK size with R8 shrinking is less than 5.0 MB.",
            "GitHub Actions CI workflow executes testDebugUnitTest and lintDebug on ubuntu-latest.",
            "F-Droid metadata file conforms to F-Droid Build Metadata specification.",
            "./gradlew assembleRelease produces unsigned release APK without errors."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.SecurityManifestAuditTest' && ./gradlew lintDebug",
        "dependencies": "Blocked by: [OL-03] Implement OpenLiteAccessibilityService Lifecycle and Event Dispatcher, [OL-09] Implement Jetpack Compose Main Dashboard with Live Intercept Telemetry, [OL-12] Implement YouTube Shorts View Detector and Suppression Rules"
    }
]

def main():
    print(f"Creating {len(TICKETS)} tickets in Notion database {DATABASE_ID}...")
    created = []
    for t in TICKETS:
        res = create_ticket(
            title=t["title"],
            status=t["status"],
            priority=t["priority"],
            ticket_type=t["type"],
            area=t["area"],
            scope=t["scope"],
            target_files=t["target_files"],
            acceptance_criteria=t["acceptance_criteria"],
            verification_cmd=t["verification_cmd"],
            dependencies=t["dependencies"]
        )
        created.append({"title": t["title"], "id": res["id"], "url": res["url"], "status": t["status"]})
    
    print("\nAll tickets created successfully!")
    with open("docs/TICKETS.json", "w") as f:
        json.dump(created, f, indent=2)
    print("Exported ticket manifest to docs/TICKETS.json")

if __name__ == "__main__":
    main()
