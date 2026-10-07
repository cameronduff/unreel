package org.unreel.android.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.unreel.android.test.MockAccessibilityNodeBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InstagramBottomNavDetectorTest {

    @Test
    fun testSelectedContentDescriptionReelsReturnsTrue() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/navigation_bar")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setContentDescription("Reels")
                    .setSelected(true)
            )
            .build()

        assertTrue(InstagramBottomNavDetector.isReelsTabSelected(root))
    }

    @Test
    fun testSelectedTextReelsReturnsTrue() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/navigation_bar")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setText("Reels")
                    .setSelected(true)
            )
            .build()

        assertTrue(InstagramBottomNavDetector.isReelsTabSelected(root))
    }

    @Test
    fun testUnselectedContentDescriptionReelsReturnsFalse() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/navigation_bar")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setContentDescription("Reels")
                    .setSelected(false)
            )
            .build()

        assertFalse(InstagramBottomNavDetector.isReelsTabSelected(root))
    }

    @Test
    fun testHomeOrProfileSelectedReturnsFalse() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/navigation_bar")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setContentDescription("Home")
                    .setSelected(true)
            )
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setContentDescription("Profile")
                    .setSelected(false)
            )
            .build()

        assertFalse(InstagramBottomNavDetector.isReelsTabSelected(root))
    }

    @Test
    fun testReturnsFalseOnNullRoot() {
        assertFalse(InstagramBottomNavDetector.isReelsTabSelected(null))
    }

    @Test
    fun testCaseInsensitiveMatching() {
        val rootLower = MockAccessibilityNodeBuilder()
            .setContentDescription("reels")
            .setSelected(true)
            .build()
        assertTrue(InstagramBottomNavDetector.isReelsTabSelected(rootLower))

        val rootUpper = MockAccessibilityNodeBuilder()
            .setContentDescription("REELS")
            .setSelected(true)
            .build()
        assertTrue(InstagramBottomNavDetector.isReelsTabSelected(rootUpper))
    }

    @Test
    fun testExecutionSpeedUnder3MsAcross50Nodes() {
        val rootBuilder = MockAccessibilityNodeBuilder().setViewId("com.instagram.android:id/root")
        val navBar = MockAccessibilityNodeBuilder().setViewId("com.instagram.android:id/tab_bar")
        navBar.addChild(MockAccessibilityNodeBuilder().setContentDescription("Home").setSelected(false))
        navBar.addChild(MockAccessibilityNodeBuilder().setContentDescription("Search").setSelected(false))
        navBar.addChild(MockAccessibilityNodeBuilder().setContentDescription("Post").setSelected(false))
        navBar.addChild(MockAccessibilityNodeBuilder().setContentDescription("Reels").setSelected(true))
        navBar.addChild(MockAccessibilityNodeBuilder().setContentDescription("Profile").setSelected(false))

        val content = MockAccessibilityNodeBuilder().setViewId("com.instagram.android:id/content")
        for (i in 0 until 45) {
            content.addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("node_$i")
                    .setContentDescription("Content item $i")
            )
        }

        rootBuilder.addChild(navBar)
        rootBuilder.addChild(content)
        val root = rootBuilder.build()

        // Warmup JVM / Robolectric reflection caches
        repeat(5) {
            InstagramBottomNavDetector.isReelsTabSelected(root)
        }

        val iterations = 20
        val start = System.nanoTime()
        repeat(iterations) {
            assertTrue(InstagramBottomNavDetector.isReelsTabSelected(root))
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        val avgDurationMs = elapsedMs / iterations

        assertTrue("Average execution duration ($avgDurationMs ms) should be <= 3ms", avgDurationMs <= 3L)
    }

    @Test
    fun testSplashScreenDetected() {
        val splashRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/splash_screen_container")
            .setClassName("android.window.SplashScreenView")
            .setVisibleToUser(true)
            .build()

        assertTrue(InstagramBottomNavDetector.isSplashScreenShowing(splashRoot))
    }

    @Test
    fun testSplashScreenNotPresentOnNormalFeed() {
        val feedRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_feed")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/title_logo")
                    .setVisibleToUser(true)
            )
            .build()

        assertFalse(InstagramBottomNavDetector.isSplashScreenShowing(feedRoot))
        assertFalse(InstagramBottomNavDetector.isSplashScreenShowing(null))
    }
}
