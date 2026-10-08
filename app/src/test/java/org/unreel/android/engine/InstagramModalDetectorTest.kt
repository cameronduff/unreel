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
class InstagramModalDetectorTest {

    @Test
    fun testNullRootReturnsFalse() {
        assertFalse(InstagramModalDetector.isModalOrDialogPresent(null))
    }

    @Test
    fun testNormalFeedReturnsFalse() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/tab_bar")
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/feed_tab")
                    )
            )
            .build()

        assertFalse(InstagramModalDetector.isModalOrDialogPresent(root))
    }

    @Test
    fun testDailyLimitDialogReturnsTrue() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/dialog_container")
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/igds_headline_headline")
                            .setText("You've reached your daily limit")
                    )
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/primary_button")
                            .setText("OK")
                    )
            )
            .build()

        assertTrue(InstagramModalDetector.isModalOrDialogPresent(root))
    }

    @Test
    fun testModalContainerWithChildrenReturnsTrue() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/modal_container")
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/dialog_window")
                    )
            )
            .build()

        assertTrue(InstagramModalDetector.isModalOrDialogPresent(root))
    }

    @Test
    fun testEmptyModalContainerReturnsFalse() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/modal_container")
            )
            .build()

        assertFalse(InstagramModalDetector.isModalOrDialogPresent(root))
    }

    @Test
    fun testBottomSheetCommentsTrayReturnsTrue() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/bottom_sheet_container")
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/comment_composer_container")
                    )
            )
            .build()

        assertTrue(InstagramModalDetector.isModalOrDialogPresent(root))
    }

    @Test
    fun testDirectMessageComposerReturnsTrue() {
        val root = MockAccessibilityNodeBuilder()
            .setViewId("com.instagram.android:id/main_layout")
            .addChild(
                MockAccessibilityNodeBuilder()
                    .setViewId("com.instagram.android:id/message_composer_bar")
                    .addChild(
                        MockAccessibilityNodeBuilder()
                            .setViewId("com.instagram.android:id/row_thread_composer_container")
                            .addChild(
                                MockAccessibilityNodeBuilder()
                                    .setViewId("com.instagram.android:id/row_thread_composer_edittext")
                                    .setText("Message...")
                            )
                    )
            )
            .build()

        assertTrue(InstagramModalDetector.isModalOrDialogPresent(root))
    }
}
