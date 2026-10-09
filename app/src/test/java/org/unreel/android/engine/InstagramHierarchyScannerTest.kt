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

    @Test
    fun testDirectInboxDetectedAsNonFeedScreen() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/direct_inbox_action_bar")
            )
            .build()

        val result = InstagramHierarchyScanner.scan(root)
        assertTrue("Direct inbox must be detected as active direct thread/non-feed screen", result.isDirectThreadActive)
        assertNull("reelsTabBounds must be null on Direct Inbox", result.reelsTabBounds)
    }

    @Test
    fun testSettingsComposeViewDetectedAsNonFeedScreen() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setClassName("androidx.compose.ui.platform.ComposeView")
            )
            .build()

        val result = InstagramHierarchyScanner.scan(root)
        assertTrue("ComposeView must be detected as active direct thread/non-feed screen", result.isDirectThreadActive)
        assertNull("reelsTabBounds must be null on ComposeView settings", result.reelsTabBounds)
    }

    @Test
    fun testActionBarBackButtonDetectedAsNonFeedScreen() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/action_bar_button_back")
            )
            .build()

        val result = InstagramHierarchyScanner.scan(root)
        assertTrue("Back button in action bar must flag screen as non-feed screen", result.isDirectThreadActive)
        assertNull("reelsTabBounds must be null on subscreen with back button", result.reelsTabBounds)
    }

    @Test
    fun testBottomNavBarShortCircuitsImmediatelyWithLowNodeCount() {
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
        assertNotNull(result.reelsTabBounds)
        assertTrue("Short-circuit must keep nodesVisited <= 10 (was ${result.nodesVisited})", result.nodesVisited <= 10)
    }

    @Test
    fun testTopBarBackButtonWithContentDescriptionDetectedAsSubscreen() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .setBounds(Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setClassName("android.widget.Button")
                    .setContentDescription("Back")
                    .setBounds(Rect(11, 147, 143, 279))
            )
            .build()

        val result = InstagramHierarchyScanner.scan(root)
        assertTrue("Top-bar Back button by contentDescription must flag screen as subscreen", result.isDirectThreadActive)
        assertNull("reelsTabBounds must be null on subscreen with Back button", result.reelsTabBounds)
    }

    @Test
    fun testTopBarHeaderLeftButtonDetectedAsSubscreen() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .setBounds(Rect(0, 0, 1080, 2340))
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/header_left_button")
                    .setContentDescription("Back")
                    .setBounds(Rect(0, 136, 154, 290))
            )
            .build()

        val result = InstagramHierarchyScanner.scan(root)
        assertTrue("header_left_button must flag screen as subscreen", result.isDirectThreadActive)
        assertNull("reelsTabBounds must be null on subscreen with header_left_button", result.reelsTabBounds)
    }

    @Test
    fun testModalScreenNullifiesReelsTabBoundsEvenIfTabBarPresent() {
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
                .setVisibleToUser(true)
        )
        rootBuilder.addChild(tabBar)

        val dialog = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/dialog_container")
            .setBounds(Rect(100, 500, 980, 1800))
        rootBuilder.addChild(dialog)

        val result = InstagramHierarchyScanner.scan(rootBuilder.build())
        assertTrue("Modal must be detected as open", result.isModalOpen)
        assertNull("reelsTabBounds must be null when modal is open even if tab bar is in tree", result.reelsTabBounds)
    }

    @Test
    fun testDormantDirectThreadNodesIgnoredWhenNotVisibleToUser() {
        val rootBuilder = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .setBounds(Rect(0, 0, 1080, 2340))

        // Dormant header_left_button in memory with isVisibleToUser = false
        val dormantHeader = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/header_left_button")
            .setContentDescription("Back")
            .setBounds(Rect(0, 136, 154, 290))
            .setVisibleToUser(false)
        rootBuilder.addChild(dormantHeader)

        // Dormant thread_fragment_container with isVisibleToUser = false
        val dormantThread = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/thread_fragment_container")
            .setVisibleToUser(false)
        rootBuilder.addChild(dormantThread)

        // Active bottom navigation bar
        val tabBar = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/tab_bar")
            .setBounds(Rect(0, 2142, 1080, 2274))
            .setVisibleToUser(true)

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
        assertFalse("Dormant direct thread nodes must NOT trigger isDirectThreadActive", result.isDirectThreadActive)
        assertEquals(Rect(216, 2142, 432, 2274), result.reelsTabBounds)
    }

    @Test
    fun testDormantModalNodesIgnoredWhenNotVisibleToUser() {
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
                .setVisibleToUser(true)
        )
        rootBuilder.addChild(tabBar)

        // Dormant dialog container with isVisibleToUser = false
        val dormantDialog = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/dialog_container")
            .setBounds(Rect(100, 500, 980, 1800))
            .setVisibleToUser(false)
        rootBuilder.addChild(dormantDialog)

        val result = InstagramHierarchyScanner.scan(rootBuilder.build())
        assertFalse("Dormant dialog must NOT be detected as open", result.isModalOpen)
        assertEquals(Rect(216, 2142, 432, 2274), result.reelsTabBounds)
    }

    @Test
    fun testMessagesTabWithTabBarRetainsOverlay() {
        val rootBuilder = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .setBounds(Rect(0, 0, 1080, 2340))

        val tabBar = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/tab_bar")
            .setBounds(Rect(0, 2142, 1080, 2274))
            .setVisibleToUser(true)

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
        tabBar.addChild(
            MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/direct_tab")
                .setContentDescription("Message")
                .setBounds(Rect(432, 2142, 648, 2274))
                .setSelected(true)
                .setVisibleToUser(true)
        )
        rootBuilder.addChild(tabBar)

        val inboxActionBar = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/direct_inbox_action_bar")
            .setVisibleToUser(true)
        rootBuilder.addChild(inboxActionBar)

        val result = InstagramHierarchyScanner.scan(rootBuilder.build())
        assertTrue("Direct inbox tab should be recognized", result.isDirectThreadActive)
        assertEquals("Reels tab must be covered when tab bar is present on messages tab", Rect(216, 2142, 432, 2274), result.reelsTabBounds)
    }

    @Test
    fun testActiveSubscreenBackButtonSuppressesOverlayEvenIfTabBarInTree() {
        val rootBuilder = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .setBounds(Rect(0, 0, 1080, 2340))

        val tabBar = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/tab_bar")
            .setBounds(Rect(0, 2142, 1080, 2274))
            .setVisibleToUser(true)
        tabBar.addChild(
            MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/clips_tab")
                .setContentDescription("Reels")
                .setBounds(Rect(216, 2142, 432, 2274))
                .setVisibleToUser(true)
        )
        rootBuilder.addChild(tabBar)

        val backButton = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/action_bar_button_back")
            .setContentDescription("Back")
            .setBounds(Rect(50, 150, 150, 250))
            .setVisibleToUser(true)
        rootBuilder.addChild(backButton)

        val result = InstagramHierarchyScanner.scan(rootBuilder.build())
        assertTrue("Subscreen back button must flag active subscreen", result.isDirectThreadActive)
        assertNull("Overlay must be suppressed on active subscreen with back button", result.reelsTabBounds)
    }

    @Test
    fun testDirectThreadComposerSuppressesOverlayEvenIfTabBarInTree() {
        val rootBuilder = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .setBounds(Rect(0, 0, 1080, 2340))

        val tabBar = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/tab_bar")
            .setBounds(Rect(0, 2142, 1080, 2274))
            .setVisibleToUser(true)
        tabBar.addChild(
            MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/clips_tab")
                .setContentDescription("Reels")
                .setBounds(Rect(216, 2142, 432, 2274))
                .setVisibleToUser(true)
        )
        rootBuilder.addChild(tabBar)

        val composer = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/row_thread_composer")
            .setVisibleToUser(true)
        rootBuilder.addChild(composer)

        val result = InstagramHierarchyScanner.scan(rootBuilder.build())
        assertTrue("Thread composer must flag active direct thread", result.isDirectThreadActive)
        assertNull("Overlay must be suppressed on active direct thread composer", result.reelsTabBounds)
    }
}
