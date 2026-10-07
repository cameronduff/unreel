package org.unreel.android.engine

import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.unreel.android.test.MockAccessibilityNodeBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AdversarialReelsE2ETest {

    private lateinit var dispatcher: DebouncedBackDispatcher
    private var backActionCount = 0

    @Before
    fun setUp() {
        backActionCount = 0
        dispatcher = DebouncedBackDispatcher(
            backActionExecutor = {
                backActionCount++
                true
            },
            debounceWindowMs = 250L
        )
    }

    // =========================================================================
    // 1. PERFORMANCE & SUB-16ms FRAME BUDGET BENCHMARK
    // =========================================================================

    @Test
    fun testTraversalPerformanceIsWellUnder16msBudget() {
        // In real Instagram, the clips video container is a top-level surface container
        // mounted directly under the window content frame.
        val clipsRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/content")
            .setClassName("android.widget.FrameLayout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/clips_video_container")
                    .setClassName("android.view.ViewGroup")
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/action_bar_container")
                    .setClassName("android.widget.LinearLayout")
            )
            .build()

        // Warm up JVM JIT compiler
        for (w in 0..50) {
            InstagramClipsDetector.isClipsContainerVisible(clipsRoot)
        }

        // Benchmark 100 consecutive scans
        val iterations = 100
        val startTimeNs = System.nanoTime()
        var detections = 0
        for (it in 0 until iterations) {
            if (InstagramClipsDetector.isClipsContainerVisible(clipsRoot)) {
                detections++
            }
        }
        val elapsedMs = (System.nanoTime() - startTimeNs) / 1_000_000.0
        val avgLatencyMs = elapsedMs / iterations

        assertEquals(iterations, detections)

        // Sub-16ms enforcement: Average latency must be < 2.0 ms (well within 120Hz 8.33ms / 60Hz 16.6ms budget)
        println("⚡ BENCHMARK RESULT: $iterations iterations took ${elapsedMs}ms (Average: ${avgLatencyMs}ms per frame)")
        assertTrue(
            "Average detection latency ($avgLatencyMs ms) must be well under 16ms frame budget",
            avgLatencyMs < 2.0
        )
    }

    // =========================================================================
    // 2. ADVERSARIAL REELS ATTACK VECTORS & ENTRY POINTS
    // =========================================================================

    @Test
    fun testAllKnownClipsViewIdsAreIntercepted() {
        val testIds = listOf(
            "com.instagram.android:id/clips_video_container",
            "com.instagram.android:id/clips_swipe_refresh_container",
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_container",
            "com.instagram.android:id/reel_viewer_container",
            "com.instagram.android:id/clips_video_player",
            "com.instagram.android:id/clips_media_item",
            "com.instagram.android:id/reels_viewer",
            "com.instagram.android:id/watch_reels_button"
        )

        for (viewId in testIds) {
            val root = MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/root")
                .addChild(MockAccessibilityNodeBuilder().setViewId(viewId))
                .build()

            val detected = InstagramClipsDetector.isClipsContainerVisible(root)
            assertTrue("View ID $viewId MUST be detected as Reels", detected)
        }
    }

    @Test
    fun testObfuscatedTreeClassAndContentDescriptionHeuristics() {
        // Case A: Class name contains ClipsViewerFragment
        val classNode = MockAccessibilityNodeBuilder()
            .setClassName("com.instagram.clips.viewer.ClipsViewerFragment")
            .build()
        assertTrue(
            "Class name heuristic must catch ClipsViewerFragment",
            InstagramClipsDetector.isClipsContainerVisible(classNode)
        )

        // Case B: Content description contains 'Reel by @creator'
        val descNode = MockAccessibilityNodeBuilder()
            .setContentDescription("Reel by @influencer • 1.2M views")
            .build()
        assertTrue(
            "Content description heuristic must catch 'Reel by @'",
            InstagramClipsDetector.isClipsContainerVisible(descNode)
        )

        // Case C: Content description 'Watch Reels video'
        val watchReelsNode = MockAccessibilityNodeBuilder()
            .setContentDescription("Watch Reels video")
            .build()
        assertTrue(
            "Content description heuristic must catch 'Watch Reels video'",
            InstagramClipsDetector.isClipsContainerVisible(watchReelsNode)
        )
    }

    @Test
    fun testAllBottomNavReelsTabVariantsAreIntercepted() {
        // Case A: Exact 'Reels' selected
        val tabExact = MockAccessibilityNodeBuilder()
            .setContentDescription("Reels")
            .setSelected(true)
            .build()
        assertTrue(InstagramBottomNavDetector.isReelsTabSelected(tabExact))

        // Case B: Singular 'Reel' selected
        val tabSingular = MockAccessibilityNodeBuilder()
            .setContentDescription("Reel")
            .setSelected(true)
            .build()
        assertTrue(InstagramBottomNavDetector.isReelsTabSelected(tabSingular))

        // Case C: Composite content description 'Reels, tab 3 of 5'
        val tabComposite = MockAccessibilityNodeBuilder()
            .setContentDescription("Reels, tab 3 of 5")
            .setSelected(true)
            .build()
        assertTrue(InstagramBottomNavDetector.isReelsTabSelected(tabComposite))

        // Case D: Text element 'Reels' selected
        val tabText = MockAccessibilityNodeBuilder()
            .setText("Reels")
            .setSelected(true)
            .build()
        assertTrue(InstagramBottomNavDetector.isReelsTabSelected(tabText))

        // Case E: View ID reels_tab selected
        val tabViewId = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/tab_bar_reels_tab")
            .setSelected(true)
            .build()
        assertTrue(InstagramBottomNavDetector.isReelsTabSelected(tabViewId))
    }

    // =========================================================================
    // 3. ZERO FALSE POSITIVES (NORMAL INSTAGRAM USE PRESERVED)
    // =========================================================================

    @Test
    fun testNormalInstagramSurfacesAreNeverBlocked() {
        // Feed post with photos
        val homeFeed = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/feed_recycler_view")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/row_feed_photo_profile_imageview")
            )
            .build()
        assertFalse("Home feed must NOT be blocked", InstagramClipsDetector.isClipsContainerVisible(homeFeed))
        assertFalse("Home feed must NOT trigger nav detector", InstagramBottomNavDetector.isReelsTabSelected(homeFeed))

        // Direct Messages inbox
        val dms = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/direct_inbox_tray")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Direct Messages (3 unread)")
            )
            .build()
        assertFalse("DMs must NOT be blocked", InstagramClipsDetector.isClipsContainerVisible(dms))

        // Camera / Story Creator
        val camera = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/camera_shutter_button")
            .build()
        assertFalse("Camera must NOT be blocked", InstagramClipsDetector.isClipsContainerVisible(camera))

        // Other bottom nav tabs selected (Home, Search, Profile)
        val homeTab = MockAccessibilityNodeBuilder()
            .setContentDescription("Home, tab 1 of 5")
            .setSelected(true)
            .build()
        assertFalse("Home tab must NOT be blocked", InstagramBottomNavDetector.isReelsTabSelected(homeTab))

        val searchTab = MockAccessibilityNodeBuilder()
            .setContentDescription("Search and explore, tab 2 of 5")
            .setSelected(true)
            .build()
        assertFalse("Search tab must NOT be blocked", InstagramBottomNavDetector.isReelsTabSelected(searchTab))

        val profileTab = MockAccessibilityNodeBuilder()
            .setContentDescription("Profile, tab 5 of 5")
            .setSelected(true)
            .build()
        assertFalse("Profile tab must NOT be blocked", InstagramBottomNavDetector.isReelsTabSelected(profileTab))
    }

    // =========================================================================
    // 4. DOOMSCROLL STRESS TEST & DEBOUNCER INTEGRITY
    // =========================================================================

    @Test
    fun testRapidFireDoomscrollSimulationFiresCleanly() {
        val clipsRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/clips_video_container")
            .build()

        var simulatedTimeMs = 1000L

        // Simulate 50 rapid events in 100ms (hyperactive swiping)
        for (i in 0 until 50) {
            val detected = InstagramClipsDetector.isClipsContainerVisible(clipsRoot)
            assertTrue(detected)

            // Attempt back action with debouncing
            dispatcher.dispatchBack(currentEpochMs = simulatedTimeMs)
            simulatedTimeMs += 2L // 2ms apart
        }

        // Only 1 back action should have fired during the first 250ms window!
        assertEquals(1, backActionCount)

        // Advance past debounce window (300ms later)
        simulatedTimeMs += 300L
        dispatcher.dispatchBack(currentEpochMs = simulatedTimeMs)
        assertEquals(2, backActionCount)
    }
}
