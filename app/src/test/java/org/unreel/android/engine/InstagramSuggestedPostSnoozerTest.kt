package org.unreel.android.engine

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.unreel.android.data.ReelsInterceptEntity
import org.unreel.android.test.MockAccessibilityNodeBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InstagramSuggestedPostSnoozerTest {

    private lateinit var snoozer: InstagramSuggestedPostSnoozer

    @Before
    fun setUp() {
        snoozer = InstagramSuggestedPostSnoozer(
            cooldownDurationMs = 24 * 60 * 60 * 1000L,
            backoffDurationMs = 5 * 60 * 1000L,
            watchdogTimeoutMs = 2500L
        )
    }

    @Test
    fun testInitialStateIsIdle() {
        assertEquals(InstagramSuggestedPostSnoozer.State.IDLE, snoozer.state)
        assertFalse(snoozer.isCooldownActive(1000L))
    }

    @Test
    fun testDetectSuggestedPostAndClickOptionsButton() {
        val rootNode = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/feed_tab")
                    .setContentDescription("Home")
                    .setSelected(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Suggested for you")
                    .setBounds(Rect(40, 200, 300, 250))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/media_option_button")
                    .setContentDescription("More actions for this post")
                    .setClickable(true)
                    .setBounds(Rect(950, 190, 1050, 260))
            )
            .build()

        val result = snoozer.processHierarchy(rootNode, currentEpochMs = 1000L)

        assertTrue(result is InstagramSuggestedPostSnoozer.ActionResult.ClickedMoreOptions)
        val clickedResult = result as InstagramSuggestedPostSnoozer.ActionResult.ClickedMoreOptions
        assertEquals(Rect(950, 190, 1050, 260), clickedResult.bounds)
        assertEquals(InstagramSuggestedPostSnoozer.State.AWAITING_MENU, snoozer.state)
        assertEquals(1000L, snoozer.sequenceStartEpochMs)
    }

    @Test
    fun testDetectBecauseYouFollowAndClickOptionsButton() {
        val rootNode = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/feed_tab")
                    .setSelected(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setContentDescription("Because you follow travel")
                    .setBounds(Rect(40, 500, 400, 550))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/media_option_button")
                    .setClickable(true)
                    .setBounds(Rect(950, 490, 1050, 560))
            )
            .build()

        val result = snoozer.processHierarchy(rootNode, currentEpochMs = 2000L)
        assertTrue(result is InstagramSuggestedPostSnoozer.ActionResult.ClickedMoreOptions)
        assertEquals(InstagramSuggestedPostSnoozer.State.AWAITING_MENU, snoozer.state)
    }

    @Test
    fun testOptionsButtonTooFarAwayIsIgnored() {
        val rootNode = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/feed_tab")
                    .setSelected(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Suggested for you")
                    .setBounds(Rect(40, 200, 300, 250))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/media_option_button")
                    .setClickable(true)
                    .setBounds(Rect(950, 1000, 1050, 1070)) // >450px vertical distance
            )
            .build()

        val result = snoozer.processHierarchy(rootNode, currentEpochMs = 1000L)
        assertEquals(InstagramSuggestedPostSnoozer.ActionResult.None, result)
        assertEquals(InstagramSuggestedPostSnoozer.State.IDLE, snoozer.state)
    }

    @Test
    fun testAwaitingMenuWithDirectSnoozeCompletesSnooze() {
        snoozer.state = InstagramSuggestedPostSnoozer.State.AWAITING_MENU
        snoozer.sequenceStartEpochMs = 1000L

        val menuRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/bottom_sheet")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Snooze all suggested posts in feed for 30 days")
                    .setClickable(true)
                    .setBounds(Rect(50, 1500, 1000, 1600))
            )
            .build()

        val result = snoozer.processHierarchy(menuRoot, currentEpochMs = 1200L)

        assertTrue(result is InstagramSuggestedPostSnoozer.ActionResult.SnoozeCompleted)
        val completedResult = result as InstagramSuggestedPostSnoozer.ActionResult.SnoozeCompleted
        assertEquals(ReelsInterceptEntity.TRIGGER_FEED_AUTO_SNOOZE, completedResult.trigger)
        assertEquals(InstagramSuggestedPostSnoozer.State.IDLE, snoozer.state)
        assertEquals(1200L, snoozer.lastSuccessfulSnoozeEpochMs)
        assertTrue(snoozer.isCooldownActive(currentEpochMs = 1300L))
    }

    @Test
    fun testAwaitingMenuWithNotInterestedAdvancesToConfirmation() {
        snoozer.state = InstagramSuggestedPostSnoozer.State.AWAITING_MENU
        snoozer.sequenceStartEpochMs = 1000L

        val menuRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/bottom_sheet")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Not interested")
                    .setClickable(true)
                    .setBounds(Rect(50, 1400, 500, 1480))
            )
            .build()

        val result = snoozer.processHierarchy(menuRoot, currentEpochMs = 1200L)

        assertTrue(result is InstagramSuggestedPostSnoozer.ActionResult.ClickedNotInterested)
        val notInterestedResult = result as InstagramSuggestedPostSnoozer.ActionResult.ClickedNotInterested
        assertEquals("Not interested", notInterestedResult.text)
        assertEquals(InstagramSuggestedPostSnoozer.State.AWAITING_CONFIRMATION, snoozer.state)
        assertEquals(1200L, snoozer.sequenceStartEpochMs)
    }

    @Test
    fun testAwaitingConfirmationWithSnoozeOptionCompletesSnooze() {
        snoozer.state = InstagramSuggestedPostSnoozer.State.AWAITING_CONFIRMATION
        snoozer.sequenceStartEpochMs = 1200L

        val confirmationRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/confirmation_container")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Snooze all suggested posts in feed for 30 days")
                    .setClickable(true)
                    .setBounds(Rect(50, 1200, 900, 1300))
            )
            .build()

        val result = snoozer.processHierarchy(confirmationRoot, currentEpochMs = 1400L)

        assertTrue(result is InstagramSuggestedPostSnoozer.ActionResult.SnoozeCompleted)
        val completedResult = result as InstagramSuggestedPostSnoozer.ActionResult.SnoozeCompleted
        assertEquals(ReelsInterceptEntity.TRIGGER_FEED_AUTO_SNOOZE, completedResult.trigger)
        assertEquals(InstagramSuggestedPostSnoozer.State.IDLE, snoozer.state)
        assertEquals(1400L, snoozer.lastSuccessfulSnoozeEpochMs)
    }

    @Test
    fun testWatchdogTimeoutResetsToIdleWithBackoff() {
        snoozer.state = InstagramSuggestedPostSnoozer.State.AWAITING_MENU
        snoozer.sequenceStartEpochMs = 1000L

        val emptyRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/empty_container")
            .build()

        // 2600ms later (> 2500ms watchdog timeout)
        val result = snoozer.processHierarchy(emptyRoot, currentEpochMs = 3600L)

        assertEquals(InstagramSuggestedPostSnoozer.ActionResult.TimeoutReset, result)
        assertEquals(InstagramSuggestedPostSnoozer.State.IDLE, snoozer.state)
        // Backoff active for 5 minutes
        assertTrue(snoozer.isCooldownActive(currentEpochMs = 3700L))
        assertEquals(3600L + (5 * 60 * 1000L), snoozer.backoffUntilEpochMs)
    }

    @Test
    fun testCooldownSuppressesFeedDetection() {
        snoozer.lastSuccessfulSnoozeEpochMs = 1000L

        val rootNode = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/feed_tab")
                    .setSelected(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Suggested for you")
                    .setBounds(Rect(40, 200, 300, 250))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/media_option_button")
                    .setClickable(true)
                    .setBounds(Rect(950, 190, 1050, 260))
            )
            .build()

        // Check 1 hour later (within 24h cooldown)
        val oneHourLater = 1000L + (3600 * 1000L)
        val result = snoozer.processHierarchy(rootNode, currentEpochMs = oneHourLater)

        assertEquals(InstagramSuggestedPostSnoozer.ActionResult.None, result)
        assertEquals(InstagramSuggestedPostSnoozer.State.IDLE, snoozer.state)

        // Check 25 hours later (cooldown expired)
        val twentyFiveHoursLater = 1000L + (25 * 3600 * 1000L)
        val afterCooldownResult = snoozer.processHierarchy(rootNode, currentEpochMs = twentyFiveHoursLater)

        assertTrue(afterCooldownResult is InstagramSuggestedPostSnoozer.ActionResult.ClickedMoreOptions)
        assertEquals(InstagramSuggestedPostSnoozer.State.AWAITING_MENU, snoozer.state)
    }

    @Test
    fun testSettingsContentPreferencesToggle() {
        val settingsRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/action_bar_root")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Snooze suggested posts")
                    .setBounds(Rect(44, 1115, 608, 1172))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setClassName("android.widget.ToggleButton")
                    .setCheckable(true)
                    .setChecked(false) // Toggle is currently OFF
                    .setClickable(true)
                    .setBounds(Rect(893, 1155, 1036, 1243))
            )
            .build()

        val result = snoozer.processHierarchy(settingsRoot, currentEpochMs = 5000L)

        assertTrue(result is InstagramSuggestedPostSnoozer.ActionResult.SnoozeCompleted)
        val completedResult = result as InstagramSuggestedPostSnoozer.ActionResult.SnoozeCompleted
        assertEquals(ReelsInterceptEntity.TRIGGER_SETTINGS_AUTO_SNOOZE, completedResult.trigger)
        assertEquals(InstagramSuggestedPostSnoozer.State.IDLE, snoozer.state)
        assertEquals(5000L, snoozer.lastSuccessfulSnoozeEpochMs)
    }

    @Test
    fun testSettingsContentPreferencesAlreadyCheckedDoesNothing() {
        val settingsRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/action_bar_root")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Snooze suggested posts")
                    .setBounds(Rect(44, 1115, 608, 1172))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setClassName("android.widget.ToggleButton")
                    .setCheckable(true)
                    .setChecked(true) // Already ON
                    .setClickable(true)
                    .setBounds(Rect(893, 1155, 1036, 1243))
            )
            .build()

        val result = snoozer.processHierarchy(settingsRoot, currentEpochMs = 5000L)
        assertEquals(InstagramSuggestedPostSnoozer.ActionResult.None, result)
    }

    @Test
    fun testFeedTabInactiveSuppressesDetection() {
        val searchTabRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/feed_tab")
                    .setSelected(false) // Feed tab NOT selected
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Suggested for you")
                    .setBounds(Rect(40, 200, 300, 250))
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/media_option_button")
                    .setClickable(true)
                    .setBounds(Rect(950, 190, 1050, 260))
            )
            .build()

        val result = snoozer.processHierarchy(searchTabRoot, currentEpochMs = 1000L)
        assertEquals(InstagramSuggestedPostSnoozer.ActionResult.None, result)
    }

    @Test
    fun testModalSuppressesFeedDetection() {
        val modalRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/dialog_container")
                    .setText("Daily limit reached")
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Suggested for you")
                    .setBounds(Rect(40, 200, 300, 250))
            )
            .build()

        val result = snoozer.processHierarchy(modalRoot, currentEpochMs = 1000L)
        assertEquals(InstagramSuggestedPostSnoozer.ActionResult.None, result)
    }
}
