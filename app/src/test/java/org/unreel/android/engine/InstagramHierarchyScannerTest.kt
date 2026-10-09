package org.unreel.android.engine

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.unreel.android.test.MockAccessibilityNodeBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InstagramHierarchyScannerTest {

    @Test
    fun testClipsDetectedInSinglePass() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/clips_video_container")
            )
            .build()

        val result = InstagramHierarchyScanner.scan(root)
        assertTrue(result.isClipsVisible)
    }

    @Test
    fun testDirectThreadDetectedInSinglePass() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/direct_thread_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/row_thread_composer_container")
            )
            .build()

        val result = InstagramHierarchyScanner.scan(root)
        assertTrue(result.isDirectThreadActive)
        assertNull(result.reelsTabBounds)
    }

    @Test
    fun testModalDetectedInSinglePass() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/dialog_container")
            )
            .build()

        val result = InstagramHierarchyScanner.scan(root)
        assertTrue(result.isModalOpen)
    }

    @Test
    fun testBottomNavBarTabBoundsResolvedInSinglePass() {
        val rootBuilder = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .setBounds(Rect(0, 0, 1080, 2340))

        val tabBar = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/tab_bar")
            .setBounds(Rect(0, 2142, 1080, 2274))

        tabBar.addChild(
            MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/feed_tab")
                .setContentDescription("Home")
                .setBounds(Rect(0, 2142, 216, 2274))
                .setVisibleToUser(true)
        )
        tabBar.addChild(
            MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/clips_tab")
                .setContentDescription("Reels")
                .setBounds(Rect(216, 2142, 432, 2274))
                .setVisibleToUser(true)
        )
        rootBuilder.addChild(tabBar)

        val result = InstagramHierarchyScanner.scan(rootBuilder.build())
        assertFalse(result.isClipsVisible)
        assertFalse(result.isDirectThreadActive)
        assertFalse(result.isModalOpen)
        assertFalse(result.isSplashScreenShowing)
        assertEquals(Rect(216, 2142, 432, 2274), result.reelsTabBounds)
    }

    @Test
    fun testSplashScreenDetectedWhenNoTabBar() {
        val splashRoot = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/splash_screen_container")
            .setClassName("android.window.SplashScreenView")
            .setVisibleToUser(true)
            .build()

        val result = InstagramHierarchyScanner.scan(splashRoot)
        assertTrue(result.isSplashScreenShowing)
        assertNull(result.reelsTabBounds)
    }

    @Test
    fun testReelsTabSelectedDetectedInSinglePass() {
        val rootBuilder = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .setBounds(Rect(0, 0, 1080, 2340))

        val tabBar = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/tab_bar")
            .setBounds(Rect(0, 2142, 1080, 2274))

        tabBar.addChild(
            MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/clips_tab")
                .setContentDescription("Reels")
                .setBounds(Rect(216, 2142, 432, 2274))
                .setSelected(true)
                .setVisibleToUser(true)
        )
        rootBuilder.addChild(tabBar)

        val result = InstagramHierarchyScanner.scan(rootBuilder.build())
        assertTrue(result.isReelsTabSelected)
    }

    @Test
    fun testSinglePassPerformanceWithin60FpsFrameBudgetAcross50Nodes() {
        val rootBuilder = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/root")
            .setBounds(Rect(0, 0, 1080, 2340))
        val navBar = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/tab_bar")
            .setBounds(Rect(0, 2140, 1080, 2300))
        navBar.addChild(MockAccessibilityNodeBuilder().setContentDescription("Home").setSelected(false))
        navBar.addChild(MockAccessibilityNodeBuilder().setContentDescription("Search").setSelected(false))
        navBar.addChild(MockAccessibilityNodeBuilder().setContentDescription("Post").setSelected(false))
        navBar.addChild(MockAccessibilityNodeBuilder().setContentDescription("Reels").setSelected(false).setVisibleToUser(true).setBounds(Rect(200, 2100, 400, 2300)))
        navBar.addChild(MockAccessibilityNodeBuilder().setContentDescription("Profile").setSelected(false))

        val content = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/content")
            .setBounds(Rect(0, 150, 1080, 2140))
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

        // JIT Warmup
        repeat(20) {
            InstagramHierarchyScanner.scan(root)
        }

        val iterations = 50
        val start = System.nanoTime()
        repeat(iterations) {
            val res = InstagramHierarchyScanner.scan(root)
            assertNotNull(res.reelsTabBounds)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0
        val avgDurationMs = elapsedMs / iterations

        assertTrue("Single-pass execution ($avgDurationMs ms) must be within 16.6ms frame budget", avgDurationMs <= 16.6)
    }
}
