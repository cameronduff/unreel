package org.unreel.android.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.unreel.android.test.MockAccessibilityNodeBuilder
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class InstagramClipsDetectorTest {

    @Test
    fun testDetectsClipsVideoContainer() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_container")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/clips_video_container")
            )
            .build()

        assertTrue(InstagramClipsDetector.isClipsContainerVisible(root))
    }

    @Test
    fun testDetectsClipsSwipeRefreshContainer() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_container")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/clips_swipe_refresh_container")
            )
            .build()

        assertTrue(InstagramClipsDetector.isClipsContainerVisible(root))
    }

    @Test
    fun testDetectsClipsViewerViewPager() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_container")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/clips_viewer_view_pager")
            )
            .build()

        assertTrue(InstagramClipsDetector.isClipsContainerVisible(root))
    }

    @Test
    fun testReturnsFalseOnNullRoot() {
        assertFalse(InstagramClipsDetector.isClipsContainerVisible(null))
    }

    @Test
    fun testReturnsFalseOnNormalFeed() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/feed_recycler_view")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/row_feed_photo_profile_imageview")
            )
            .build()

        assertFalse(InstagramClipsDetector.isClipsContainerVisible(root))
    }

    @Test
    fun testReturnsFalseOnDirectMessagesInbox() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/direct_inbox_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/direct_thread_row")
            )
            .build()

        assertFalse(InstagramClipsDetector.isClipsContainerVisible(root))
    }

    @Test
    fun testShortCircuitsInUnder5Ms() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/clips_video_container")
            .build()

        val duration = measureTimeMillis {
            assertTrue(InstagramClipsDetector.isClipsContainerVisible(root))
        }

        assertTrue("Execution duration ($duration ms) should be <= 5ms", duration <= 5L)
    }
}
