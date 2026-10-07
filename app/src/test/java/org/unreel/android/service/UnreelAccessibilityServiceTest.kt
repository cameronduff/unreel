package org.unreel.android.service

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

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UnreelAccessibilityServiceTest {

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
    fun testIgnoresNonInstagramEvents() {
        val event = AccessibilityEvent.obtain().apply {
            packageName = "com.android.chrome"
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }

        service.onAccessibilityEvent(event)
        assertEquals(0, backActionCount)
    }

    @Test
    fun testNullRootDoesNotCrash() {
        service.rootNodeProvider = { null }
        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }

        service.onAccessibilityEvent(event)
        assertEquals(0, backActionCount)
    }

    @Test
    fun testClipsContainerTriggersDebouncedBack() {
        var interceptedCallbackFired = false
        service.onReelsIntercepted = {
            interceptedCallbackFired = true
        }

        val clipsRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/clips_video_container")
            )
            .build()

        service.rootNodeProvider = { clipsRoot.unwrap() as AccessibilityNodeInfo }

        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }

        service.onAccessibilityEvent(event)
        assertEquals(1, backActionCount)
        assertTrue(interceptedCallbackFired)
    }

    @Test
    fun testReelsTabSelectedTriggersDebouncedBack() {
        val tabRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/tab_bar")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setContentDescription("Reels")
                    .setSelected(true)
            )
            .build()

        service.rootNodeProvider = { tabRoot.unwrap() as AccessibilityNodeInfo }

        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_VIEW_CLICKED
        }

        service.onAccessibilityEvent(event)
        assertEquals(1, backActionCount)
    }

    @Test
    fun testNormalFeedDoesNotTriggerBack() {
        val feedRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/feed_recycler_view")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setContentDescription("Home")
                    .setSelected(true)
            )
            .build()

        service.rootNodeProvider = { feedRoot.unwrap() as AccessibilityNodeInfo }

        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }

        service.onAccessibilityEvent(event)
        assertEquals(0, backActionCount)
    }

    @Test
    fun testWhenFilteringDisabledDoesNotTriggerBack() {
        service.isFilterActiveProvider = { false }

        val clipsRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/clips_video_container")
            )
            .build()

        service.rootNodeProvider = { clipsRoot.unwrap() as AccessibilityNodeInfo }

        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }

        service.onAccessibilityEvent(event)
        assertEquals(0, backActionCount)
    }

    @Test
    fun testInstagramEventShowsOverlayWhenReelsTabFound() {
        var lastOverlayShow: Boolean? = null
        var lastBounds: android.graphics.Rect? = null
        service.overlayController = { show, bounds ->
            lastOverlayShow = show
            lastBounds = bounds
        }

        val tabRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/tab_bar")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/clips_tab")
                    .setContentDescription("Reels")
                    .setBounds(android.graphics.Rect(216, 2142, 432, 2274))
            )
            .build()

        service.rootNodeProvider = { tabRoot.unwrap() as AccessibilityNodeInfo }

        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }

        service.onAccessibilityEvent(event)
        assertEquals(true, lastOverlayShow)
        assertEquals(android.graphics.Rect(216, 2142, 432, 2274), lastBounds)
    }

    @Test
    fun testNonInstagramWindowStateEventHidesOverlay() {
        var lastOverlayShow: Boolean? = null
        service.overlayController = { show, _ ->
            lastOverlayShow = show
        }

        val event = AccessibilityEvent.obtain().apply {
            packageName = "com.google.android.apps.nexuslauncher"
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }

        service.onAccessibilityEvent(event)
        assertEquals(false, lastOverlayShow)
    }

    @Test
    fun testWhenFilteringDisabledHidesOverlay() {
        var lastOverlayShow: Boolean? = null
        service.overlayController = { show, _ ->
            lastOverlayShow = show
        }
        service.isFilterActiveProvider = { false }

        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        }

        service.onAccessibilityEvent(event)
        assertEquals(false, lastOverlayShow)
    }
}
