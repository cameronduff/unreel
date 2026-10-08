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
import org.unreel.android.test.MockAccessibilityNodeBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class InstagramStoryAdDetectorTest {

    private lateinit var detector: InstagramStoryAdDetector

    @Before
    fun setUp() {
        detector = InstagramStoryAdDetector(debounceWindowMs = 350L)
    }

    @Test
    fun testNullRootReturnsNotInStoryViewer() {
        val result = detector.detectAndSkipAd(null)
        assertTrue(result is InstagramStoryAdDetector.ActionResult.NotInStoryViewer)
        assertFalse(detector.isStoryViewerActive(null))
    }

    @Test
    fun testFeedHierarchyReturnsNotInStoryViewer() {
        val feedRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .setBounds(Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/feed_tab")
                    .setText("Home")
            )
            .build()

        val result = detector.detectAndSkipAd(feedRoot)
        assertTrue(result is InstagramStoryAdDetector.ActionResult.NotInStoryViewer)
        assertFalse(detector.isStoryViewerActive(feedRoot))
    }

    @Test
    fun testOrganicStoryReturnsOrganicStory() {
        val organicStoryRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/reel_viewer_root")
            .setBounds(Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/action_bar_title_view")
                    .setText("elisha_zara")
                    .setVisibleToUser(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/toolbar_container")
                    .setText("3h")
                    .setVisibleToUser(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/reel_viewer_media_layout")
                    .setVisibleToUser(true)
            )
            .build()

        assertTrue(detector.isStoryViewerActive(organicStoryRoot))
        val result = detector.detectAndSkipAd(organicStoryRoot)
        assertTrue(result is InstagramStoryAdDetector.ActionResult.OrganicStory)
    }

    @Test
    fun testSponsoredStoryWithTextLabelTriggersSkip() {
        val adStoryRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/reel_viewer_root")
            .setBounds(Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/action_bar_title_view")
                    .setText("gymshark")
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
                    .setText("Shop Now")
                    .setVisibleToUser(true)
            )
            .build()

        assertTrue(detector.isStoryViewerActive(adStoryRoot))
        val result = detector.detectAndSkipAd(adStoryRoot, currentTimeMs = 1000L)
        assertTrue("Result must be SkipAd", result is InstagramStoryAdDetector.ActionResult.SkipAd)

        val skip = result as InstagramStoryAdDetector.ActionResult.SkipAd
        assertTrue("Tap X must be >= 90% of screen width (1080 * 0.9 = 972)", skip.tapPoint.x >= 970)
        assertEquals(1170, skip.tapPoint.y) // Centered vertically in 2340px height
    }

    @Test
    fun testSponsoredStoryWithContentDescriptionTriggersSkip() {
        val adStoryRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/stories_viewer_container")
            .setBounds(Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/action_bar_title_view")
                    .setContentDescription("Sponsored post by audi")
                    .setText("Sponsored")
                    .setVisibleToUser(true)
            )
            .build()

        val result = detector.detectAndSkipAd(adStoryRoot, currentTimeMs = 1000L)
        assertTrue("Result must be SkipAd", result is InstagramStoryAdDetector.ActionResult.SkipAd)
    }

    @Test
    fun testDebouncePreventsRapidFireSkipping() {
        val adStoryRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/reel_viewer_root")
            .setBounds(Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Sponsored")
                    .setVisibleToUser(true)
            )
            .build()

        // First trigger at t=1000ms -> SkipAd
        val res1 = detector.detectAndSkipAd(adStoryRoot, currentTimeMs = 1000L)
        assertTrue(res1 is InstagramStoryAdDetector.ActionResult.SkipAd)

        // Second trigger at t=1100ms (< 350ms window) -> InCooldown
        val res2 = detector.detectAndSkipAd(adStoryRoot, currentTimeMs = 1100L)
        assertTrue(res2 is InstagramStoryAdDetector.ActionResult.InCooldown)

        // Third trigger at t=1400ms (> 350ms window) -> SkipAd
        val res3 = detector.detectAndSkipAd(adStoryRoot, currentTimeMs = 1400L)
        assertTrue(res3 is InstagramStoryAdDetector.ActionResult.SkipAd)
    }

    @Test
    fun testExecutionSpeedUnderFrameBudget() {
        val rootBuilder = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/reel_viewer_root")
            .setBounds(Rect(0, 0, 1080, 2340))

        for (i in 0 until 40) {
            rootBuilder.addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("element_$i")
                    .setText("Story element $i")
            )
        }
        val root = rootBuilder.build()

        // Warmup
        repeat(5) { detector.detectAndSkipAd(root) }

        val iterations = 25
        val start = System.nanoTime()
        repeat(iterations) {
            detector.detectAndSkipAd(root)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        val avgDurationMs = elapsedMs / iterations

        assertTrue("Execution duration ($avgDurationMs ms) must be well within frame budget (<= 50ms on Robolectric JVM)", avgDurationMs <= 50L)
    }
}
