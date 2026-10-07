package org.unreel.android.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.unreel.android.engine.DebouncedBackDispatcher
import org.unreel.android.test.MockAccessibilityNodeBuilder
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FastScrollEdgeCaseTest {

    private lateinit var service: UnreelAccessibilityService
    private var backActionCount = 0

    @Before
    fun setUp() {
        backActionCount = 0
        service = Robolectric.buildService(UnreelAccessibilityService::class.java).create().get()
        service.backDispatcher = DebouncedBackDispatcher(
            backActionExecutor = {
                backActionCount++
                true
            },
            debounceWindowMs = 300L
        )
    }

    @Test
    fun testRapidScrollEventsDoNotTriggerFalseReelsInterception() {
        // Build typical feed post card hierarchy
        val feedRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .setBounds(Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/feed_recycler_view")
                    .setBounds(Rect(0, 136, 1080, 2142))
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/row_feed_photo_profile_imageview")
                    )
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/row_feed_caption")
                            .setText("Sunday vibes #weekend")
                    )
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/tab_bar")
                    .setBounds(Rect(0, 2142, 1080, 2274))
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/clips_tab")
                            .setContentDescription("Reels")
                            .setBounds(Rect(216, 2142, 432, 2274))
                    )
            )
            .build()

        service.rootNodeProvider = { feedRoot.unwrap() as AccessibilityNodeInfo }

        var overlayCallCount = 0
        service.overlayController = { show, _ ->
            if (show) overlayCallCount++
        }

        // Fire 50 rapid scroll events simulating high-speed flings
        for (i in 0 until 50) {
            val event = AccessibilityEvent.obtain().apply {
                packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
                eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED
            }
            service.onAccessibilityEvent(event)
        }

        // Zero false back actions must be dispatched during scrolling
        assertEquals("No false back actions should be dispatched during fast scrolling", 0, backActionCount)
        assertEquals("Overlay should be requested on the first event", 1, overlayCallCount)
    }

    @Test
    fun testReelsDetectionPreemptsOverlayMaintenance() {
        // Setup node where Clips container is active
        val clipsRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/clips_viewer_container")
            .setBounds(Rect(0, 136, 1080, 2340))
            .setVisibleToUser(true)
            .build()

        service.rootNodeProvider = { clipsRoot.unwrap() as AccessibilityNodeInfo }

        var overlayCalled = false
        service.overlayController = { _, _ ->
            overlayCalled = true
        }

        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }

        service.onAccessibilityEvent(event)

        // Reels should immediately trigger Back
        assertEquals(1, backActionCount)
        // Overlay maintenance should be bypassed entirely on Reels detection
        assertFalse("Overlay maintenance must be bypassed when Reels is detected", overlayCalled)
    }

    @Test
    fun testHighFrequencyBurstLatencyBudget() {
        val feedRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .setBounds(Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/feed_recycler_view")
                    .setBounds(Rect(0, 136, 1080, 2142))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/tab_bar")
                    .setBounds(Rect(0, 2142, 1080, 2274))
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/clips_tab")
                            .setContentDescription("Reels")
                            .setBounds(Rect(216, 2142, 432, 2274))
                    )
            )
            .build()

        service.rootNodeProvider = { feedRoot.unwrap() as AccessibilityNodeInfo }
        service.overlayController = { _, _ -> }

        val totalMs = measureTimeMillis {
            for (i in 0 until 100) {
                val event = AccessibilityEvent.obtain().apply {
                    packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
                    eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED
                }
                service.onAccessibilityEvent(event)
            }
        }

        val avgMs = totalMs.toDouble() / 100.0
        println("100 scroll events processed in $totalMs ms (avg: $avgMs ms/event)")
        assertTrue("Average processing latency ($avgMs ms) must be well within 16.6ms frame budget (<= 8.0ms on Robolectric JVM)", avgMs <= 8.0)
    }

    @Test
    fun testDebouncedBackWindowAllowsRecoveryAfter300Ms() {
        val dispatcher = DebouncedBackDispatcher(
            backActionExecutor = {
                backActionCount++
                true
            },
            debounceWindowMs = 300L
        )

        // First dispatch at t=1000
        assertTrue("First dispatch should succeed", dispatcher.dispatchBack(1000L))
        assertEquals(1, backActionCount)

        // Second dispatch at t=1150 (150ms later) should be suppressed
        assertFalse("Dispatch within 300ms window should be suppressed", dispatcher.dispatchBack(1150L))
        assertEquals(1, backActionCount)

        // Third dispatch at t=1350 (350ms later) should succeed
        assertTrue("Dispatch after 300ms window should succeed", dispatcher.dispatchBack(1350L))
        assertEquals(2, backActionCount)
    }

    @Test
    fun testFeedVideoCardNotMistakenForFullscreenClips() {
        // In-feed video card taking up only 30% of screen height
        val inFeedVideoRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .setBounds(Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/clips_video_container")
                    .setBounds(Rect(0, 500, 1080, 1100)) // height: 600px < 50% of 2340
                    .setVisibleToUser(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/tab_bar")
                    .setBounds(Rect(0, 2142, 1080, 2274))
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/clips_tab")
                            .setContentDescription("Reels")
                            .setBounds(Rect(216, 2142, 432, 2274))
                    )
            )
            .build()

        service.rootNodeProvider = { inFeedVideoRoot.unwrap() as AccessibilityNodeInfo }
        service.overlayController = { _, _ -> }

        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_VIEW_SCROLLED
        }

        service.onAccessibilityEvent(event)

        assertEquals("Inline feed video card (<50% height) must not trigger back action", 0, backActionCount)
    }
}
