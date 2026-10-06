#!/usr/bin/env python3
"""
Populates 17 ultra-atomic, hyper-detailed SWE-ready tickets in Cameron's Notion database.
Database ID: 3f09da56-a0de-819a-ab73-fe3c30394233
"""

import os
import json
import urllib.request
import urllib.error

NOTION_TOKEN = os.environ.get("NOTION_API_TOKEN")
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

def clear_existing_tickets():
    print("Fetching existing pages to clean...")
    res = make_request(f"databases/{DATABASE_ID}/query", method="POST", data={})
    for p in res.get("results", []):
        pid = p["id"]
        make_request(f"blocks/{pid}", method="DELETE")
        print(f"Archived old page: {pid}")

def text_run(content, bold=False, code=False):
    return {
        "type": "text",
        "text": {"content": content},
        "annotations": {
            "bold": bold,
            "italic": False,
            "strikethrough": False,
            "underline": False,
            "code": code,
            "color": "default"
        }
    }

def heading_2(text):
    return {"object": "block", "type": "heading_2", "heading_2": {"rich_text": [text_run(text)]}}

def paragraph(text):
    return {"object": "block", "type": "paragraph", "paragraph": {"rich_text": [text_run(text)]}}

def bullet(text):
    return {"object": "block", "type": "bulleted_list_item", "bulleted_list_item": {"rich_text": [text_run(text)]}}

def to_do(text):
    return {"object": "block", "type": "to_do", "to_do": {"rich_text": [text_run(text)], "checked": False}}

def code_block(code_text, lang="bash"):
    return {"object": "block", "type": "code", "code": {"rich_text": [text_run(code_text)], "language": lang}}

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
    blocks.append(heading_2("Acceptance Criteria"),)
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
    print(f"Created atomic ticket: {title} -> {res['id']}")
    return res

ATOMIC_TICKETS = [
    {
        "title": "[OL-01] Initialize Android Gradle Project Structure with Version Catalogs",
        "status": "Ready for SWE",
        "priority": "P0 - Blocker",
        "type": "Infra",
        "area": "Core / Engine",
        "scope": "In Scope: Create root openlite/ directory with Gradle Kotlin DSL, gradle/libs.versions.toml version catalog, compileSdk=34, minSdk=26, targetSdk=34, and Kotlin 2.0. Configure app module with applicationId 'org.openlite.android'. Zero permissions declared in AndroidManifest.xml. Out of Scope: Robolectric setup, UI libraries, feature code.",
        "target_files": [
            "openlite/settings.gradle.kts (new)",
            "openlite/build.gradle.kts (new)",
            "openlite/gradle/libs.versions.toml (new)",
            "openlite/app/build.gradle.kts (new)",
            "openlite/app/src/main/AndroidManifest.xml (new)"
        ],
        "acceptance_criteria": [
            "./gradlew assembleDebug compiles successfully with exit code 0.",
            "gradle/libs.versions.toml defines all dependency versions (AGP 8.5+, Kotlin 2.0+, AndroidX Core).",
            "Root AndroidManifest.xml explicitly omits android.permission.INTERNET.",
            "minSdkVersion is set to 26 (Android 8.0 Oreo) and targetSdkVersion is 34 (Android 14).",
            "clean build produces zero compiler warnings under -Werror flag."
        ],
        "verification_cmd": "cd openlite && ./gradlew assembleDebug --quiet",
        "dependencies": "None"
    },
    {
        "title": "[OL-02] Configure Robolectric Test Runner and Accessibility Node Mocking Harness",
        "status": "Ready for SWE",
        "priority": "P0 - Blocker",
        "type": "Infra",
        "area": "Core / Engine",
        "scope": "In Scope: Add Robolectric 4.12+ and JUnit4 test dependencies to openlite/app/build.gradle.kts. Create reusable test helper MockAccessibilityNodeBuilder to mock AccessibilityNodeInfoCompat trees with custom viewIds, texts, contentDescriptions, and selection states in memory. Out of Scope: Instagram detection logic.",
        "target_files": [
            "openlite/gradle/libs.versions.toml (new)",
            "openlite/app/build.gradle.kts (new)",
            "openlite/app/src/test/java/org/openlite/android/test/MockAccessibilityNodeBuilder.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/test/RobolectricHarnessTest.kt (new)"
        ],
        "acceptance_criteria": [
            "./gradlew testDebugUnitTest executes successfully on JVM without emulator or physical device.",
            "MockAccessibilityNodeBuilder allows building parent-child hierarchy with arbitrary viewIdResourceName.",
            "MockAccessibilityNodeBuilder allows setting text, contentDescription, and isSelected flags.",
            "RobolectricHarnessTest validates mock tree traversal depth-first and breadth-first without throwing NPE.",
            "Test suite execution completes in under 10 seconds."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.test.RobolectricHarnessTest'",
        "dependencies": "Blocked by: [OL-01] Initialize Android Gradle Project Structure with Version Catalogs"
    },
    {
        "title": "[OL-03] Implement Full-Screen Reels Clips Container Detector",
        "status": "Ready for SWE",
        "priority": "P0 - Blocker",
        "type": "TDD / Test",
        "area": "Core / Engine",
        "scope": "In Scope: Implement pure Kotlin function InstagramClipsDetector.isClipsContainerVisible(rootNode: AccessibilityNodeInfoCompat?): Boolean. Detects full-screen vertical Reels viewer by matching resource IDs 'com.instagram.android:id/clips_video_container', 'clips_swipe_refresh_container', and 'clips_viewer_view_pager'. Unit test using MockAccessibilityNodeBuilder. Out of Scope: Bottom nav tab detection, back-action dispatching.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/engine/InstagramClipsDetector.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/engine/InstagramClipsDetectorTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Returns true when rootNode contains child with viewId 'com.instagram.android:id/clips_video_container'.",
            "Returns true when rootNode contains child with viewId 'com.instagram.android:id/clips_swipe_refresh_container'.",
            "Returns true when rootNode contains child with viewId 'com.instagram.android:id/clips_viewer_view_pager'.",
            "Returns false when rootNode is null (null safety).",
            "Returns false when rootNode contains feed recycler 'com.instagram.android:id/feed_recycler_view' without clips containers.",
            "Returns false when rootNode is Direct Messages inbox 'com.instagram.android:id/direct_inbox_layout'.",
            "Traversal stops immediately upon first match (short-circuit evaluation) in <= 5ms."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.engine.InstagramClipsDetectorTest'",
        "dependencies": "Blocked by: [OL-02] Configure Robolectric Test Runner and Accessibility Node Mocking Harness"
    },
    {
        "title": "[OL-04] Implement Bottom Navigation Reels Tab Detector",
        "status": "Ready for SWE",
        "priority": "P0 - Blocker",
        "type": "TDD / Test",
        "area": "Core / Engine",
        "scope": "In Scope: Implement pure Kotlin function InstagramBottomNavDetector.isReelsTabSelected(rootNode: AccessibilityNodeInfoCompat?): Boolean. Inspects bottom navigation bar nodes, matching contentDescription or text equals 'Reels' where isSelected == true. Unit tested with MockAccessibilityNodeBuilder. Out of Scope: Clips video container, back action dispatch.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/engine/InstagramBottomNavDetector.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/engine/InstagramBottomNavDetectorTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Returns true when node with contentDescription 'Reels' has isSelected == true.",
            "Returns true when node with text 'Reels' has isSelected == true.",
            "Returns false when node with contentDescription 'Reels' exists but has isSelected == false.",
            "Returns false when Home tab (contentDescription 'Home') or Profile tab is selected.",
            "Returns false when rootNode is null.",
            "Handles case-insensitive matching for 'reels' and 'Reels'.",
            "Execution completes in <= 3ms across simulated 50-node layout tree."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.engine.InstagramBottomNavDetectorTest'",
        "dependencies": "Blocked by: [OL-02] Configure Robolectric Test Runner and Accessibility Node Mocking Harness"
    },
    {
        "title": "[OL-05] Implement Debounced Global Back Action Dispatcher",
        "status": "Ready for SWE",
        "priority": "P0 - Blocker",
        "type": "Core / Engine",
        "area": "Core / Engine",
        "scope": "In Scope: Create DebouncedBackDispatcher class wrapping AccessibilityService.performGlobalAction(GLOBAL_ACTION_BACK). Enforces a configurable debounce threshold (default 300ms) to prevent consecutive rapid back-presses from exiting Instagram completely when multiple window events fire in rapid succession. Out of Scope: Accessibility service registration.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/engine/DebouncedBackDispatcher.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/engine/DebouncedBackDispatcherTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Invokes performGlobalAction(GLOBAL_ACTION_BACK) when dispatchBack() is called for the first time.",
            "Ignores calls to dispatchBack() if called within 300ms of the preceding successful dispatch.",
            "Allows dispatchBack() to fire again once 300ms has elapsed since the previous action.",
            "Allows setting custom debounce threshold via constructor (e.g. 500ms).",
            "Returns boolean indicating whether the back action was dispatched (true) or suppressed by debounce (false).",
            "Thread-safe execution under concurrent dispatch calls from main and worker threads."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.engine.DebouncedBackDispatcherTest'",
        "dependencies": "Blocked by: [OL-02] Configure Robolectric Test Runner and Accessibility Node Mocking Harness"
    },
    {
        "title": "[OL-06] Implement OpenLiteAccessibilityService Event Loop",
        "status": "Ready for SWE",
        "priority": "P0 - Blocker",
        "type": "Feature",
        "area": "Accessibility",
        "scope": "In Scope: Implement OpenLiteAccessibilityService extending android.accessibilityservice.AccessibilityService. Configure XML metadata accessibility_service_config.xml. In onAccessibilityEvent, filter for package 'com.instagram.android', invoke InstagramClipsDetector and InstagramBottomNavDetector, and delegate to DebouncedBackDispatcher. Out of Scope: Telemetry storage, overlay views.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/service/OpenLiteAccessibilityService.kt (new)",
            "openlite/app/src/main/res/xml/accessibility_service_config.xml (new)",
            "openlite/app/src/main/AndroidManifest.xml (new)",
            "openlite/app/src/test/java/org/openlite/android/service/OpenLiteAccessibilityServiceTest.kt (new)"
        ],
        "acceptance_criteria": [
            "accessibility_service_config.xml specifies eventTypes='typeWindowStateChanged|typeViewClicked' and packageNames='com.instagram.android'.",
            "service is declared in AndroidManifest.xml with android.permission.BIND_ACCESSIBILITY_SERVICE.",
            "onAccessibilityEvent ignores any event where event.packageName != 'com.instagram.android'.",
            "Triggers DebouncedBackDispatcher.dispatchBack() when either clips container or reels tab is detected.",
            "Handles null rootInActiveWindow gracefully without throwing NullPointerException.",
            "Unit test in Robolectric simulates window transition into Instagram and verifies back dispatch."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.service.OpenLiteAccessibilityServiceTest'",
        "dependencies": "Blocked by: [OL-03] Implement Full-Screen Reels Clips Container Detector, [OL-04] Implement Bottom Navigation Reels Tab Detector, [OL-05] Implement Debounced Global Back Action Dispatcher"
    },
    {
        "title": "[OL-07] Implement Room Database and DAO for Local Intercept Telemetry",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "Telemetry",
        "scope": "In Scope: Configure Room Database with entity ReelsInterceptEntity(id: Long, timestampEpochMs: Long, triggerType: String). Implement ReelsInterceptDao with insertIntercept(entity), getCountSince(startOfDayEpochMs): Flow<Int>, and getTotalCount(): Flow<Int>. 100% on-device SQLite. Out of Scope: Remote sync, UI presentation.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/data/ReelsInterceptEntity.kt (new)",
            "openlite/app/src/main/java/org/openlite/android/data/ReelsInterceptDao.kt (new)",
            "openlite/app/src/main/java/org/openlite/android/data/OpenLiteDatabase.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/data/ReelsInterceptDaoTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Room database compiles with KSP and exports schema to app/schemas.",
            "insertIntercept correctly persists entity with autogenerated primary key.",
            "getCountSince(timestamp) returns only records inserted with timestampEpochMs >= parameter.",
            "Flow emits new count automatically when a new record is inserted.",
            "In-memory Room database test verifies CRUD operations without disk I/O.",
            "Zero network dependencies or external telemetry SDKs are included."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.data.ReelsInterceptDaoTest'",
        "dependencies": "Blocked by: [OL-01] Initialize Android Gradle Project Structure with Version Catalogs"
    },
    {
        "title": "[OL-08] Implement Preferences DataStore Repository for Reels Filter State",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "Core / Engine",
        "scope": "In Scope: Create FilterPreferencesRepository using Jetpack Preferences DataStore. Manages boolean isReelsFilterEnabled (default true) and long pauseUntilEpochMs (default 0L). Methods: setFilterEnabled(Boolean), pauseFiltering(durationMinutes: Int), resumeFiltering(), and isPauseActive(currentEpochMs: Long): Boolean. Out of Scope: Compose UI screens.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/data/FilterPreferencesRepository.kt (new)",
            "openlite/app/src/main/java/org/openlite/android/data/FilterPreferences.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/data/FilterPreferencesRepositoryTest.kt (new)"
        ],
        "acceptance_criteria": [
            "isReelsFilterEnabled Flow emits true by default on fresh installation.",
            "setFilterEnabled(false) persists false and emits to all active collectors within 50ms.",
            "pauseFiltering(15) sets pauseUntilEpochMs to currentSystemTime + 900,000ms.",
            "isPauseActive(currentEpochMs) returns true when currentEpochMs < pauseUntilEpochMs, and false when elapsed.",
            "resumeFiltering() resets pauseUntilEpochMs immediately to 0L.",
            "DataStore survives simulated process restart in Robolectric test."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.data.FilterPreferencesRepositoryTest'",
        "dependencies": "Blocked by: [OL-01] Initialize Android Gradle Project Structure with Version Catalogs"
    },
    {
        "title": "[OL-09] Implement Accessibility Service Status Checker",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "UI / Dashboard",
        "scope": "In Scope: Implement utility object AccessibilityStatusHelper with method isServiceEnabled(context: Context): Boolean. Reads Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, parses the colon-separated component names, and checks if OpenLiteAccessibilityService component is present. Out of Scope: Settings intent creation, UI widgets.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/util/AccessibilityStatusHelper.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/util/AccessibilityStatusHelperTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Returns true when Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES contains 'org.openlite.android/.service.OpenLiteAccessibilityService'.",
            "Returns false when Settings string is empty string or null.",
            "Returns false when Settings string contains other packages (e.g. 'com.google.android.marvin.talkback/.TalkBackService') but not OpenLite.",
            "Handles colon-separated string with multiple packages without regex failure.",
            "Unit test asserts correct parsing across simulated Android 10 through 14 test cases."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.util.AccessibilityStatusHelperTest'",
        "dependencies": "Blocked by: [OL-06] Implement OpenLiteAccessibilityService Event Loop"
    },
    {
        "title": "[OL-10] Implement System Accessibility Settings Intent Factory",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Feature",
        "area": "UI / Dashboard",
        "scope": "In Scope: Implement utility object AccessibilityIntentFactory with method createOpenSettingsIntent(): Intent. Returns Intent configured with Settings.ACTION_ACCESSIBILITY_SETTINGS, Intent.FLAG_ACTIVITY_NEW_TASK, and Android 11+ extra ':settings:fragment_args_key' highlighting OpenLite. Out of Scope: Launching the intent, Compose UI buttons.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/util/AccessibilityIntentFactory.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/util/AccessibilityIntentFactoryTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Returned intent has action Settings.ACTION_ACCESSIBILITY_SETTINGS.",
            "Returned intent has flag Intent.FLAG_ACTIVITY_NEW_TASK set.",
            "Returned intent includes extra ':settings:fragment_args_key' matching OpenLiteAccessibilityService component name.",
            "createOpenSettingsIntent() produces non-null intent with zero external dependencies.",
            "Unit test validates intent flags and action on JVM."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.util.AccessibilityIntentFactoryTest'",
        "dependencies": "Blocked by: [OL-01] Initialize Android Gradle Project Structure with Version Catalogs"
    },
    {
        "title": "[OL-11] Implement Onboarding Screen for Accessibility Permission Grant",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "Feature",
        "area": "UI / Dashboard",
        "scope": "In Scope: Create OnboardingViewModel and Jetpack Compose OnboardingScreen. Observes AccessibilityStatusHelper. If permission is missing, renders step-by-step card with 'Enable OpenLite in Settings' button linking to AccessibilityIntentFactory. Transitions to completed state when permission is detected. Out of Scope: Main dashboard stats.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/ui/onboarding/OnboardingScreen.kt (new)",
            "openlite/app/src/main/java/org/openlite/android/ui/onboarding/OnboardingViewModel.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/ui/OnboardingViewModelTest.kt (new)"
        ],
        "acceptance_criteria": [
            "When isServiceEnabled is false, UI renders 'Permission Required' banner and CTA button.",
            "Tapping CTA button invokes intent launcher with AccessibilityIntentFactory intent.",
            "When onResume fires and isServiceEnabled becomes true, ViewModel emits State.PermissionGranted.",
            "UI follows minimalist monochromatic palette (Black, White, Gray) with zero decorative illustrations.",
            "Robolectric Compose test verifies state change when permission status transitions."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.ui.OnboardingViewModelTest'",
        "dependencies": "Blocked by: [OL-09] Implement Accessibility Service Status Checker, [OL-10] Implement System Accessibility Settings Intent Factory"
    },
    {
        "title": "[OL-12] Implement Dashboard Screen with Live Reels Counter and Pause Timer",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "Feature",
        "area": "UI / Dashboard",
        "scope": "In Scope: Implement DashboardViewModel and Jetpack Compose DashboardScreen. Displays: (1) Today's blocked Reels counter from ReelsInterceptDao, (2) Master toggle switch for Instagram Reels filtering via FilterPreferencesRepository, (3) Pause button (15 minutes) with active countdown timer. Out of Scope: Onboarding screen.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/ui/dashboard/DashboardScreen.kt (new)",
            "openlite/app/src/main/java/org/openlite/android/ui/dashboard/DashboardViewModel.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/ui/DashboardViewModelTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Dashboard collects today intercept count from ReelsInterceptDao and renders count in real time.",
            "Toggling 'Block Instagram Reels' switch calls FilterPreferencesRepository.setFilterEnabled.",
            "Tapping 'Pause for 15m' updates pauseUntilEpochMs and initiates countdown timer in UI.",
            "Countdown timer displays remaining minutes:seconds and clears automatically upon expiration.",
            "Pure AMOLED dark theme with zero colored badge distractions.",
            "Unit test validates ViewModel correctly maps repository flows to UI state."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.ui.DashboardViewModelTest'",
        "dependencies": "Blocked by: [OL-07] Implement Room Database and DAO for Local Intercept Telemetry, [OL-08] Implement Preferences DataStore Repository for Reels Filter State, [OL-11] Implement Onboarding Screen for Accessibility Permission Grant"
    },
    {
        "title": "[OL-13] Implement Quick Settings Tile for 15-Minute Pause Toggle",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "Feature",
        "area": "Core / Engine",
        "scope": "In Scope: Implement OpenLiteTileService extending android.service.quicksettings.TileService. Registered in AndroidManifest.xml with android.permission.BIND_QUICK_SETTINGS_TILE. Reads FilterPreferencesRepository. If active, clicking pauses filtering for 15m (Tile.STATE_INACTIVE, subtitle 'Paused'). If paused, clicking resumes immediately (Tile.STATE_ACTIVE, subtitle 'Active'). Out of Scope: Compose UI.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/service/OpenLiteTileService.kt (new)",
            "openlite/app/src/main/AndroidManifest.xml (new)",
            "openlite/app/src/test/java/org/openlite/android/service/OpenLiteTileServiceTest.kt (new)"
        ],
        "acceptance_criteria": [
            "TileService is declared in manifest with BIND_QUICK_SETTINGS_TILE permission.",
            "onStartListening updates qsTile.state to STATE_ACTIVE when isPauseActive is false.",
            "onStartListening updates qsTile.state to STATE_INACTIVE when isPauseActive is true.",
            "onClick toggles pause state via FilterPreferencesRepository and calls qsTile.updateTile().",
            "Tile subtitle displays 'Blocking Reels' when active and 'Paused (15m)' when paused.",
            "Robolectric test verifies tile state updates upon simulated click."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.service.OpenLiteTileServiceTest'",
        "dependencies": "Blocked by: [OL-08] Implement Preferences DataStore Repository for Reels Filter State"
    },
    {
        "title": "[OL-14] Implement Touch Absorber Screen Coordinate Calculator",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "Core / Engine",
        "area": "Overlay",
        "scope": "In Scope: Implement pure Kotlin class OverlayPositionCalculator with method calculateBottomTabBounds(screenWidthPx: Int, screenHeightPx: Int, navBarHeightPx: Int, tabIndex: Int = 3, totalTabs: Int = 5): Rect. Computes exact pixel bounding box for the Reels tab (index 3 of 5). Out of Scope: WindowManager overlay service.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/overlay/OverlayPositionCalculator.kt (new)",
            "openlite/app/src/test/java/org/openlite/android/overlay/OverlayPositionCalculatorTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Divides screenWidthPx equally by totalTabs (5) to determine tab width.",
            "Calculates left coordinate for tab 3 as width * 3, and right coordinate as width * 4.",
            "Sets top coordinate as screenHeightPx - navBarHeightPx, and bottom coordinate as screenHeightPx.",
            "Throws IllegalArgumentException if screenWidthPx <= 0 or totalTabs <= 0.",
            "Unit test asserts exact Rect boundaries across 1080x2400 (FHD+), 1440x3120 (QHD+), and 720x1600 (HD+)."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.overlay.OverlayPositionCalculatorTest'",
        "dependencies": "Blocked by: [OL-01] Initialize Android Gradle Project Structure with Version Catalogs"
    },
    {
        "title": "[OL-15] Implement Touch Absorber Floating Window Overlay Service",
        "status": "Ready for SWE",
        "priority": "P2 - Medium",
        "type": "Feature",
        "area": "Overlay",
        "scope": "In Scope: Implement TouchAbsorberOverlayService extending android.app.Service. Uses WindowManager with LayoutParams.TYPE_APPLICATION_OVERLAY to mount a transparent touch sink View at bounds computed by OverlayPositionCalculator. Consumes MotionEvent.ACTION_DOWN and returns true (absorbing taps). Out of Scope: Accessibility service logic.",
        "target_files": [
            "openlite/app/src/main/java/org/openlite/android/overlay/TouchAbsorberOverlayService.kt (new)",
            "openlite/app/src/main/AndroidManifest.xml (new)",
            "openlite/app/src/test/java/org/openlite/android/overlay/TouchAbsorberOverlayServiceTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Service checks Settings.canDrawOverlays(context) before attempting to attach window.",
            "Uses WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE to prevent stealing keyboard focus.",
            "Touch sink view onTouch listener consumes ACTION_DOWN and returns true, preventing event reaching Instagram.",
            "onDestroy removes view from WindowManager to prevent window leaks.",
            "Robolectric test verifies window layout params and touch consumption."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.overlay.TouchAbsorberOverlayServiceTest'",
        "dependencies": "Blocked by: [OL-08] Implement Preferences DataStore Repository for Reels Filter State, [OL-14] Implement Touch Absorber Screen Coordinate Calculator"
    },
    {
        "title": "[OL-16] Implement Security Audit Test Enforcing Zero Network Permissions",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "TDD / Test",
        "area": "Packaging",
        "scope": "In Scope: Implement JUnit test ZeroNetworkSecurityAuditTest. Parses merged AndroidManifest.xml from build/intermediates/merged_manifests and asserts android.permission.INTERNET is completely absent. Inspects runtime classpath and asserts zero networking classes (OkHttp, Retrofit, Ktor, Apache HTTP) are bundled. Out of Scope: ProGuard packaging.",
        "target_files": [
            "openlite/app/src/test/java/org/openlite/android/security/ZeroNetworkSecurityAuditTest.kt (new)"
        ],
        "acceptance_criteria": [
            "Test fails if android.permission.INTERNET is present anywhere in merged manifest.",
            "Test fails if any package starting with 'okhttp3', 'retrofit2', or 'io.ktor' is found on runtime classpath.",
            "Test passes when manifest contains only BIND_ACCESSIBILITY_SERVICE and optional SYSTEM_ALERT_WINDOW.",
            "Test executes in under 2 seconds on JVM.",
            "Added to pre-commit and CI verification gate."
        ],
        "verification_cmd": "cd openlite && ./gradlew testDebugUnitTest --tests 'org.openlite.android.security.ZeroNetworkSecurityAuditTest'",
        "dependencies": "Blocked by: [OL-01] Initialize Android Gradle Project Structure with Version Catalogs"
    },
    {
        "title": "[OL-17] Configure Release Build Packaging and F-Droid Metadata",
        "status": "Ready for SWE",
        "priority": "P1 - High",
        "type": "Infra",
        "area": "Packaging",
        "scope": "In Scope: Configure app/proguard-rules.pro with aggressive R8 minification rules. Ensure unsigned release APK size is < 3.5 MB. Create F-Droid build metadata YAML file metadata/org.openlite.android.yml conforming to F-Droid specs. Configure GitHub Actions CI .github/workflows/ci.yml running testDebugUnitTest and lintDebug on push. Out of Scope: Google Play billing.",
        "target_files": [
            "openlite/app/proguard-rules.pro (new)",
            "openlite/.github/workflows/ci.yml (new)",
            "openlite/metadata/org.openlite.android.yml (new)"
        ],
        "acceptance_criteria": [
            "./gradlew assembleRelease produces unsigned release APK under 3.5 MB in size.",
            "R8 shrinks unused Kotlin standard library and Compose runtime code.",
            "GitHub Actions workflow runs './gradlew testDebugUnitTest' and './gradlew lintDebug' on ubuntu-latest.",
            "F-Droid metadata YAML file passes lint verification against F-Droid metadata schema.",
            "Release APK compiles without R8 missing class warnings."
        ],
        "verification_cmd": "cd openlite && ./gradlew assembleRelease lintDebug",
        "dependencies": "Blocked by: [OL-06] Implement OpenLiteAccessibilityService Event Loop, [OL-12] Implement Dashboard Screen with Live Reels Counter and Pause Timer, [OL-16] Implement Security Audit Test Enforcing Zero Network Permissions"
    }
]

def main():
    clear_existing_tickets()
    print(f"\nCreating {len(ATOMIC_TICKETS)} ultra-atomic tickets in database {DATABASE_ID}...")
    manifest = []
    for t in ATOMIC_TICKETS:
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
        manifest.append({
            "id": res["id"],
            "url": res["url"],
            "title": t["title"],
            "status": t["status"],
            "priority": t["priority"],
            "area": t["area"],
            "type": t["type"],
            "dependencies": t["dependencies"]
        })
    
    with open("docs/TICKETS.json", "w") as f:
        json.dump(manifest, f, indent=2)
    print("Exported updated atomic tickets to docs/TICKETS.json")

if __name__ == "__main__":
    main()
