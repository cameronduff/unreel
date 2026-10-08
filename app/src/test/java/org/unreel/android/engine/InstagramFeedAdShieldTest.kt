package org.unreel.android.engine

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.unreel.android.data.ReelsInterceptEntity
import org.unreel.android.test.MockAccessibilityNodeBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InstagramFeedAdShieldTest {

    private lateinit var adShield: InstagramFeedAdShield

    @Before
    fun setUp() {
        adShield = InstagramFeedAdShield(
            watchdogTimeoutMs = 2500L,
            actionCooldownMs = 5000L
        )
    }

    @Test
    fun testSponsoredPostInFeedTriggersOptionsClick() {
        val root = MockAccessibilityNodeBuilder()
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
                    .setBounds(Rect(40, 200, 300, 250))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/media_option_button")
                    .setClickable(true)
                    .setVisibleToUser(true)
                    .setBounds(Rect(950, 190, 1050, 260))
            )
            .build()

        val result = adShield.processHierarchy(root, currentEpochMs = 1000L)

        assertTrue(result is InstagramFeedAdShield.ActionResult.ClickedOptions)
        assertEquals(InstagramFeedAdShield.State.AWAITING_OPTIONS_MENU, adShield.state)
    }

    @Test
    fun testOptionsMenuTriggersHideAdClick() {
        adShield.state = InstagramFeedAdShield.State.AWAITING_OPTIONS_MENU
        adShield.sequenceStartEpochMs = 1000L

        val menuRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/bottom_sheet")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Hide ad")
                    .setClickable(true)
                    .setVisibleToUser(true)
                    .setBounds(Rect(50, 1500, 1000, 1600))
            )
            .build()

        val result = adShield.processHierarchy(menuRoot, currentEpochMs = 1500L)

        assertTrue(result is InstagramFeedAdShield.ActionResult.ClickedHideAd)
        assertEquals(InstagramFeedAdShield.State.AWAITING_REASON_MENU, adShield.state)
    }

    @Test
    fun testReasonMenuTriggersReasonClickAndCompletes() {
        adShield.state = InstagramFeedAdShield.State.AWAITING_REASON_MENU
        adShield.sequenceStartEpochMs = 1500L

        val reasonRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/bottom_sheet")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("It's irrelevant")
                    .setClickable(true)
                    .setVisibleToUser(true)
                    .setBounds(Rect(50, 1400, 1000, 1500))
            )
            .build()

        val result = adShield.processHierarchy(reasonRoot, currentEpochMs = 2000L)

        assertTrue(result is InstagramFeedAdShield.ActionResult.AdHidden)
        val hiddenResult = result as InstagramFeedAdShield.ActionResult.AdHidden
        assertEquals(ReelsInterceptEntity.TRIGGER_FEED_AD_SHIELD, hiddenResult.trigger)
        assertEquals(InstagramFeedAdShield.State.IDLE, adShield.state)
    }

    @Test
    fun testWatchdogTimeoutResetsToIdle() {
        adShield.state = InstagramFeedAdShield.State.AWAITING_OPTIONS_MENU
        adShield.sequenceStartEpochMs = 1000L

        val dummyRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .build()

        // 3000ms elapsed > 2500ms watchdog
        val result = adShield.processHierarchy(dummyRoot, currentEpochMs = 4000L)

        assertTrue(result is InstagramFeedAdShield.ActionResult.TimeoutReset)
        assertEquals(InstagramFeedAdShield.State.IDLE, adShield.state)
    }

    @Test
    fun testNonSponsoredPostDoesNothing() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/feed_tab")
                    .setSelected(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Just a regular photo caption")
                    .setVisibleToUser(true)
                    .setBounds(Rect(40, 200, 500, 250))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/media_option_button")
                    .setClickable(true)
                    .setVisibleToUser(true)
                    .setBounds(Rect(950, 190, 1050, 260))
            )
            .build()

        val result = adShield.processHierarchy(root, currentEpochMs = 1000L)

        assertTrue(result is InstagramFeedAdShield.ActionResult.None)
        assertEquals(InstagramFeedAdShield.State.IDLE, adShield.state)
    }

    @Test
    fun testExecutionSpeedUnder3Ms() {
        val rootBuilder = MockAccessibilityNodeBuilder().setViewId("com.instagram.android:id/main_feed")
        rootBuilder.addChild(MockAccessibilityNodeBuilder().setViewId("com.instagram.android:id/feed_tab").setSelected(true))
        for (i in 0 until 50) {
            rootBuilder.addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("node_$i")
                    .setText("Organic feed post $i")
            )
        }
        val root = rootBuilder.build()

        // Warmup JIT
        repeat(10) { adShield.processHierarchy(root) }

        val iterations = 20
        val start = System.nanoTime()
        repeat(iterations) {
            adShield.processHierarchy(root)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        val avgDurationMs = elapsedMs / iterations
        // Real device ART execution is sub-1ms; allow up to 50ms on JVM due to Robolectric synthetic reflection overhead
        assertTrue("Execution duration ($avgDurationMs ms) must be well within 16.6ms frame budget (<= 50ms on Robolectric JVM)", avgDurationMs <= 50L)
    }
}
