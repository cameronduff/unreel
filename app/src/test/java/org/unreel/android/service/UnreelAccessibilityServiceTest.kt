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
import org.unreel.android.engine.InstagramFeedAdShield
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

    @Test
    fun testWhenModalDialogPresentHidesOverlay() {
        var lastOverlayShow: Boolean? = null
        service.overlayController = { show, _ ->
            lastOverlayShow = show
        }

        val modalRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/dialog_container")
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/primary_button")
                            .setText("OK")
                    )
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/tab_bar")
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/clips_tab")
                            .setContentDescription("Reels")
                            .setBounds(android.graphics.Rect(216, 2142, 432, 2274))
                    )
            )
            .build()

        service.rootNodeProvider = { modalRoot.unwrap() as AccessibilityNodeInfo }

        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }

        service.onAccessibilityEvent(event)
        assertEquals(false, lastOverlayShow)
    }

    @Test
    fun testWhenDirectMessageThreadPresentHidesOverlay() {
        var lastOverlayShow: Boolean? = null
        service.overlayController = { show, _ ->
            lastOverlayShow = show
        }

        val dmThreadRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/message_composer_bar")
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/row_thread_composer_container")
                    )
            )
            .build()

        service.rootNodeProvider = { dmThreadRoot.unwrap() as AccessibilityNodeInfo }

        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }

        service.onAccessibilityEvent(event)
        assertEquals(false, lastOverlayShow)
    }

    @Test
    fun testAutoSnoozeSuggestedPostSequence() {
        var recordedTrigger: String? = null
        val fakeDao = object : org.unreel.android.data.ReelsInterceptDao {
            override suspend fun insertIntercept(entity: org.unreel.android.data.ReelsInterceptEntity): Long {
                recordedTrigger = entity.triggerType
                return 1L
            }
            override fun getCountSince(startEpochMs: Long): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
            override fun getTotalCount(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
            override fun getAllIntercepts(): kotlinx.coroutines.flow.Flow<List<org.unreel.android.data.ReelsInterceptEntity>> = kotlinx.coroutines.flow.emptyFlow()
        }
        service.daoProvider = { fakeDao }

        // Step 1: Feed with suggested post
        val feedRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/feed_tab")
                    .setSelected(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Suggested for you")
                    .setBounds(android.graphics.Rect(40, 200, 300, 250))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/media_option_button")
                    .setClickable(true)
                    .setBounds(android.graphics.Rect(950, 190, 1050, 260))
            )
            .build()

        service.rootNodeProvider = { feedRoot.unwrap() as AccessibilityNodeInfo }

        val feedEvent = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        service.onAccessibilityEvent(feedEvent)

        assertEquals(org.unreel.android.engine.InstagramSuggestedPostSnoozer.State.AWAITING_MENU, service.suggestedPostSnoozer.state)

        // Step 2: Menu with direct snooze
        val menuRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/bottom_sheet")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Snooze all suggested posts in feed for 30 days")
                    .setClickable(true)
                    .setBounds(android.graphics.Rect(50, 1500, 1000, 1600))
            )
            .build()

        service.rootNodeProvider = { menuRoot.unwrap() as AccessibilityNodeInfo }

        val menuEvent = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        service.onAccessibilityEvent(menuEvent)

        assertEquals(org.unreel.android.engine.InstagramSuggestedPostSnoozer.State.IDLE, service.suggestedPostSnoozer.state)
        // Telemetry recorded via DAO
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        assertEquals(org.unreel.android.data.ReelsInterceptEntity.TRIGGER_FEED_AUTO_SNOOZE, recordedTrigger)
    }

    @Test
    fun testFeedAdShieldAutoHidesSponsoredPostSequence() {
        var recordedTrigger: String? = null
        val fakeDao = object : org.unreel.android.data.ReelsInterceptDao {
            override suspend fun insertIntercept(entity: org.unreel.android.data.ReelsInterceptEntity): Long {
                recordedTrigger = entity.triggerType
                return 1L
            }
            override fun getCountSince(startEpochMs: Long): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
            override fun getTotalCount(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
            override fun getAllIntercepts(): kotlinx.coroutines.flow.Flow<List<org.unreel.android.data.ReelsInterceptEntity>> = kotlinx.coroutines.flow.emptyFlow()
        }
        service.daoProvider = { fakeDao }

        // Step 1: Sponsored post detected in feed
        val feedRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/feed_tab")
                    .setSelected(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Sponsored")
                    .setVisibleToUser(true)
                    .setBounds(android.graphics.Rect(40, 200, 300, 250))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/media_option_button")
                    .setClickable(true)
                    .setVisibleToUser(true)
                    .setBounds(android.graphics.Rect(950, 190, 1050, 260))
            )
            .build()

        service.rootNodeProvider = { feedRoot.unwrap() as AccessibilityNodeInfo }

        val event1 = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        service.onAccessibilityEvent(event1)

        assertEquals(InstagramFeedAdShield.State.AWAITING_OPTIONS_MENU, service.feedAdShield.state)

        // Step 2: Options menu with "Hide ad"
        val menuRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/bottom_sheet")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Hide ad")
                    .setClickable(true)
                    .setVisibleToUser(true)
                    .setBounds(android.graphics.Rect(50, 1500, 1000, 1600))
            )
            .build()

        service.rootNodeProvider = { menuRoot.unwrap() as AccessibilityNodeInfo }

        val event2 = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        service.onAccessibilityEvent(event2)

        assertEquals(InstagramFeedAdShield.State.AWAITING_REASON_MENU, service.feedAdShield.state)

        // Step 3: Reason menu with "It's irrelevant"
        val reasonRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/bottom_sheet")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("It's irrelevant")
                    .setClickable(true)
                    .setVisibleToUser(true)
                    .setBounds(android.graphics.Rect(50, 1400, 1000, 1500))
            )
            .build()

        service.rootNodeProvider = { reasonRoot.unwrap() as AccessibilityNodeInfo }

        val event3 = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        service.onAccessibilityEvent(event3)

        assertEquals(InstagramFeedAdShield.State.IDLE, service.feedAdShield.state)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        Thread.sleep(100)
        assertEquals(org.unreel.android.data.ReelsInterceptEntity.TRIGGER_FEED_AD_SHIELD, recordedTrigger)
    }

    @Test
    fun testStoryAdShieldAutoSkipsSponsoredStory() {
        var recordedTrigger: String? = null
        var dispatchedTapX: Float? = null
        var dispatchedTapY: Float? = null

        val fakeDao = object : org.unreel.android.data.ReelsInterceptDao {
            override suspend fun insertIntercept(entity: org.unreel.android.data.ReelsInterceptEntity): Long {
                recordedTrigger = entity.triggerType
                return 1L
            }
            override fun getCountSince(startEpochMs: Long): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
            override fun getTotalCount(): kotlinx.coroutines.flow.Flow<Int> = kotlinx.coroutines.flow.emptyFlow()
            override fun getAllIntercepts(): kotlinx.coroutines.flow.Flow<List<org.unreel.android.data.ReelsInterceptEntity>> = kotlinx.coroutines.flow.emptyFlow()
        }
        service.daoProvider = { fakeDao }
        service.gestureDispatcher = { x, y ->
            dispatchedTapX = x
            dispatchedTapY = y
            true
        }

        val storyAdRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/reel_viewer_root")
            .setBounds(android.graphics.Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/action_bar_title_view")
                    .setText("Sponsored Brand")
                    .setVisibleToUser(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/sponsored_label")
                    .setText("Sponsored")
                    .setVisibleToUser(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/story_ad_cta")
                    .setText("Install Now")
                    .setVisibleToUser(true)
            )
            .build()

        service.rootNodeProvider = { storyAdRoot.unwrap() as AccessibilityNodeInfo }

        val event = AccessibilityEvent.obtain().apply {
            packageName = UnreelAccessibilityService.TARGET_INSTAGRAM_PACKAGE
            eventType = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        }
        service.onAccessibilityEvent(event)

        assertTrue("Tap X must be >= 90% of screen width", (dispatchedTapX ?: 0f) >= 970f)
        assertEquals(1170f, dispatchedTapY ?: 0f, 0.1f)

        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        Thread.sleep(100)
        assertEquals(org.unreel.android.data.ReelsInterceptEntity.TRIGGER_STORY_AD_SHIELD, recordedTrigger)
    }
}

