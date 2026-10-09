package org.unreel.android.overlay

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.unreel.android.engine.InstagramBottomNavDetector
import org.unreel.android.service.UnreelAccessibilityService
import org.unreel.android.test.MockAccessibilityNodeBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DirectThreadOverlaySuppressionTest {

    private lateinit var service: UnreelAccessibilityService
    private var overlayAttachCount = 0
    private var overlayDetachCount = 0
    private var lastOverlayState: Boolean? = null
    private var lastBounds: Rect? = null

    @Before
    fun setUp() {
        overlayAttachCount = 0
        overlayDetachCount = 0
        lastOverlayState = null
        lastBounds = null

        service = Robolectric.buildService(UnreelAccessibilityService::class.java).create().get()
        service.overlayController = { show, bounds ->
            lastOverlayState = show
            lastBounds = bounds
            if (show) {
                overlayAttachCount++
            } else {
                overlayDetachCount++
            }
        }
    }

    private fun buildDirectMessageThread(withSharedReel: Boolean = true): AccessibilityNodeInfo {
        val rootBuilder = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .setBounds(Rect(0, 0, 1080, 2340))

        val threadLayout = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/direct_thread_layout")
            .setBounds(Rect(0, 0, 1080, 2274))

        val header = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/direct_thread_header")
            .setBounds(Rect(0, 100, 1080, 250))
        threadLayout.addChild(header)

        val messageList = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/direct_message_list")
            .setBounds(Rect(0, 250, 1080, 2092))

        for (i in 0 until 10) {
            val bubble = MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/direct_text_message_text_view")
                .setText("Chat message $i")
                .setBounds(Rect(50, 300 + (i * 100), 800, 380 + (i * 100)))
            messageList.addChild(bubble)
        }

        if (withSharedReel) {
            // Shared Reel bubble placed right near the bottom where the Reels tab would normally be
            val sharedReelNode = MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/direct_reel_share_message")
                .setContentDescription("Reels")
                .setText("Watch this Reel: funniest pet clips")
                .setBounds(Rect(216, 2142, 432, 2274))
                .setVisibleToUser(true)
            messageList.addChild(sharedReelNode)
        }
        threadLayout.addChild(messageList)

        val composerBar = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/message_composer_bar")
            .setBounds(Rect(0, 2092, 1080, 2274))

        val composerContainer = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/row_thread_composer_container")
            .setBounds(Rect(22, 2114, 1058, 2252))

        val editText = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/row_thread_composer_edittext")
            .setText("Message...")
            .setBounds(Rect(166, 2114, 599, 2252))
            .setVisibleToUser(true)
        composerContainer.addChild(editText)
        composerBar.addChild(composerContainer)
        threadLayout.addChild(composerBar)

        rootBuilder.addChild(threadLayout)
        return rootBuilder.build().unwrap() as AccessibilityNodeInfo
    }

    private fun buildMainFeedWithTabBar(): AccessibilityNodeInfo {
        val rootBuilder = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .setBounds(Rect(0, 0, 1080, 2340))

        val feedRecycler = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/feed_recycler_view")
            .setBounds(Rect(0, 200, 1080, 2142))
        rootBuilder.addChild(feedRecycler)

        val tabBar = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/tab_bar")
            .setBounds(Rect(0, 2142, 1080, 2274))

        tabBar.addChild(
            MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/feed_tab")
                .setContentDescription("Home")
                .setSelected(true)
                .setBounds(Rect(0, 2142, 216, 2274))
                .setVisibleToUser(true)
        )
        tabBar.addChild(
            MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/clips_tab")
                .setContentDescription("Reels")
                .setSelected(false)
                .setBounds(Rect(216, 2142, 432, 2274))
                .setVisibleToUser(true)
        )
        tabBar.addChild(
            MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/profile_tab")
                .setContentDescription("Profile")
                .setSelected(false)
                .setBounds(Rect(864, 2142, 1080, 2274))
                .setVisibleToUser(true)
        )
        rootBuilder.addChild(tabBar)

        return rootBuilder.build().unwrap() as AccessibilityNodeInfo
    }

    @Test
    fun testDirectMessageWithSharedReelDoesNotAttachOverlay() {
        val dmRoot = buildDirectMessageThread(withSharedReel = true)
        val compat = androidx.core.view.accessibility.AccessibilityNodeInfoCompat.wrap(dmRoot)

        // 1. Direct thread must be recognized as active
        assertTrue(InstagramBottomNavDetector.isDirectThreadActive(compat))

        // 2. Tab bounds must be null despite "Reels" contentDescription in message list
        val bounds = InstagramBottomNavDetector.findReelsTabBounds(compat)
        assertNull("Shared Reel in DM message list must NEVER match as bottom nav tab", bounds)

        // 3. Accessibility service processing must detach / suppress overlay
        service.rootNodeProvider = { dmRoot }
        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        service.onAccessibilityEvent(event)

        assertEquals(0, overlayAttachCount)
        assertEquals(false, lastOverlayState)
    }

    @Test
    fun testRapidScrollingInDirectMessageThreadKeepsOverlayDetached() {
        val dmRoot = buildDirectMessageThread(withSharedReel = true)
        service.rootNodeProvider = { dmRoot }

        // Send 50 rapid scroll events simulating fast scrolling through chat history
        val scrollEvents = 50
        for (i in 0 until scrollEvents) {
            val event = AccessibilityEvent.obtain().apply {
                packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED
            }
            service.onAccessibilityEvent(event)
        }

        // Overlay should have 0 attaches across all 50 scroll events
        assertEquals(0, overlayAttachCount)
        assertEquals(false, lastOverlayState)
    }

    @Test
    fun testTransitionFromFeedToDirectMessageImmediatelyDetachesOverlay() {
        // Step 1: User is on main feed -> Overlay attaches over Reels tab
        val feedRoot = buildMainFeedWithTabBar()
        service.rootNodeProvider = { feedRoot }

        val feedEvent = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        service.onAccessibilityEvent(feedEvent)

        assertEquals(1, overlayAttachCount)
        assertEquals(true, lastOverlayState)
        assertEquals(Rect(216, 2142, 432, 2274), lastBounds)

        // Step 2: User taps into a DM thread -> Overlay must immediately detach
        val dmRoot = buildDirectMessageThread(withSharedReel = false)
        service.rootNodeProvider = { dmRoot }

        val dmEvent = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }
        service.onAccessibilityEvent(dmEvent)

        assertEquals(false, lastOverlayState)
        assertEquals(1, overlayDetachCount)

        // Benchmark steady-state event dispatch duration with JIT warmup
        repeat(5) {
            service.onAccessibilityEvent(dmEvent)
        }
        val iterations = 10
        val startNs = System.nanoTime()
        repeat(iterations) {
            service.onAccessibilityEvent(dmEvent)
        }
        val avgElapsedMs = ((System.nanoTime() - startNs) / 1_000_000.0) / iterations

        assertTrue("Steady-state latency ($avgElapsedMs ms) must be well within frame budget (<= 16ms)", avgElapsedMs <= 16.0)
    }

    @Test
    fun testNonTabBarNodeMatchingReelsRejected() {
        // Root node without tab bar containing a random button or list item titled "Reels"
        val exploreRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/explore_container")
            .setBounds(Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/search_result_text")
                    .setText("Reels")
                    .setContentDescription("Reels")
                    .setBounds(Rect(100, 2200, 500, 2300))
                    .setVisibleToUser(true)
            )
            .build()

        val bounds = InstagramBottomNavDetector.findReelsTabBounds(exploreRoot)
        assertNull("Node outside verified bottom navigation bar container must not match", bounds)
    }

    @Test
    fun testBottomNavBarContainerRequiresValidZoneOrMultipleTabs() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .setBounds(Rect(0, 0, 1080, 2340))
            .build()

        assertNull(InstagramBottomNavDetector.findBottomNavBarContainer(root))
        assertNull(InstagramBottomNavDetector.findBottomNavBarContainer(null))
    }
}
