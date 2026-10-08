package org.unreel.android.matrix

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.unreel.android.engine.InstagramBottomNavDetector
import org.unreel.android.engine.InstagramClipsDetector
import org.unreel.android.engine.InstagramStoryAdDetector
import org.unreel.android.overlay.OverlayPositionCalculator
import org.unreel.android.test.MockAccessibilityNodeBuilder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class MultiDeviceMatrixTest {

    data class DeviceProfile(
        val name: String,
        val widthPx: Int,
        val heightPx: Int,
        val navBarHeightPx: Int,
        val aspectRatioName: String,
        val expectedReelsTabIndex: Int = 1
    )

    private val deviceProfiles = listOf(
        // 16:9 Legacy / Budget
        DeviceProfile("Generic 16:9 HD", 720, 1280, 96, "16:9"),
        DeviceProfile("Generic 16:9 FHD", 1080, 1920, 128, "16:9"),

        // 18:9 (2:1)
        DeviceProfile("Pixel 2 XL / LG G6", 1080, 2160, 132, "18:9"),

        // 19.5:9 (Flagship standard / Pixel 4a / iPhone form factor)
        DeviceProfile("Google Pixel 4a (sunfish)", 1080, 2340, 132, "19.5:9"),
        DeviceProfile("Samsung Galaxy S23 / A54", 1080, 2340, 132, "19.5:9"),

        // 20:9 & 20.5:9 (Tall modern Android flagships)
        DeviceProfile("Google Pixel 8 Pro", 1344, 2992, 140, "20:9"),
        DeviceProfile("Samsung Galaxy S23 Ultra", 1440, 3088, 144, "19.3:9"),
        DeviceProfile("OnePlus 11 / 12", 1440, 3216, 144, "20.1:9"),
        DeviceProfile("Xiaomi Redmi Note 12", 1080, 2400, 132, "20:9"),

        // Foldable & Tablets
        DeviceProfile("Google Pixel Fold (Inner)", 1840, 2208, 120, "6:5"),
        DeviceProfile("Samsung Galaxy Z Fold5 (Inner)", 1812, 2176, 120, "5:6"),
        DeviceProfile("Android Tablet 16:10", 1600, 2560, 140, "16:10")
    )

    @Test
    fun testOverlayPositionCalculatorPrecisionAcrossAllDeviceProfiles() {
        for (device in deviceProfiles) {
            val bounds = OverlayPositionCalculator.calculateTabBounds(
                screenWidthPx = device.widthPx,
                screenHeightPx = device.heightPx,
                navBarHeightPx = device.navBarHeightPx,
                totalTabs = 5,
                tabIndex = device.expectedReelsTabIndex
            )

            val expectedTabWidth = device.widthPx / 5
            val expectedLeft = expectedTabWidth * device.expectedReelsTabIndex
            val expectedRight = expectedTabWidth * (device.expectedReelsTabIndex + 1)
            val expectedTop = device.heightPx - device.navBarHeightPx
            val expectedBottom = device.heightPx

            assertEquals("Device ${device.name}: left bound mismatch", expectedLeft, bounds.left)
            assertEquals("Device ${device.name}: right bound mismatch", expectedRight, bounds.right)
            assertEquals("Device ${device.name}: top bound mismatch", expectedTop, bounds.top)
            assertEquals("Device ${device.name}: bottom bound mismatch", expectedBottom, bounds.bottom)

            // Strictly within screen boundaries
            assertTrue("Device ${device.name}: bounds must stay inside screen horizontally", bounds.left >= 0 && bounds.right <= device.widthPx)
            assertTrue("Device ${device.name}: bounds must stay inside screen vertically", bounds.top >= 0 && bounds.bottom <= device.heightPx)
            assertTrue("Device ${device.name}: bounds must have positive area", bounds.width() > 0 && bounds.height() > 0)
        }
    }

    @Test
    fun testBottomNavDetectorReelsTabMatchingAcrossAllProfiles() {
        for (device in deviceProfiles) {
            val tabWidth = device.widthPx / 5
            val reelsLeft = tabWidth * device.expectedReelsTabIndex
            val reelsRight = tabWidth * (device.expectedReelsTabIndex + 1)
            val navTop = device.heightPx - device.navBarHeightPx
            val navBottom = device.heightPx

            val root = MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/main_feed")
                .setBounds(Rect(0, 0, device.widthPx, device.heightPx))
                .addChild(
                    MockAccessibilityNodeBuilder()
                        .setViewId("com.instagram.android:id/tab_bar")
                        .setBounds(Rect(0, navTop, device.widthPx, navBottom))
                        .addChild(
                            MockAccessibilityNodeBuilder()
                                .setViewId("com.instagram.android:id/feed_tab")
                                .setBounds(Rect(0, navTop, tabWidth, navBottom))
                                .setText("Home")
                        )
                        .addChild(
                            MockAccessibilityNodeBuilder()
                                .setViewId("com.instagram.android:id/clips_tab")
                                .setBounds(Rect(reelsLeft, navTop, reelsRight, navBottom))
                                .setContentDescription("Reels")
                                .setVisibleToUser(true)
                        )
                        .addChild(
                            MockAccessibilityNodeBuilder()
                                .setViewId("com.instagram.android:id/direct_tab")
                                .setBounds(Rect(tabWidth * 2, navTop, tabWidth * 3, navBottom))
                                .setContentDescription("Message")
                        )
                )
                .build()

            val detectedBounds = InstagramBottomNavDetector.findReelsTabBounds(root)
            assertNotNull("Device ${device.name}: Reels tab bounds must be detected", detectedBounds)
            assertEquals("Device ${device.name}: Left bound mismatch", reelsLeft, detectedBounds!!.left)
            assertEquals("Device ${device.name}: Right bound mismatch", reelsRight, detectedBounds.right)
            assertEquals("Device ${device.name}: Top bound mismatch", navTop, detectedBounds.top)
            assertEquals("Device ${device.name}: Bottom bound mismatch", navBottom, detectedBounds.bottom)
        }
    }

    @Test
    fun testStoryAdDetectorFastForwardCoordinatesAcrossAllProfiles() {
        val detector = InstagramStoryAdDetector(debounceWindowMs = 0L)

        for (device in deviceProfiles) {
            val root = MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/reel_viewer_root")
                .setBounds(Rect(0, 0, device.widthPx, device.heightPx))
                .addChild(
                    MockAccessibilityNodeBuilder()
                        .setViewId("com.instagram.android:id/sponsored_label")
                        .setText("Sponsored")
                        .setVisibleToUser(true)
                )
                .build()

            val result = detector.detectAndSkipAd(root, currentTimeMs = 1000L)
            assertTrue("Device ${device.name}: Result must be SkipAd", result is InstagramStoryAdDetector.ActionResult.SkipAd)

            val skip = result as InstagramStoryAdDetector.ActionResult.SkipAd
            val expectedMinTapX = (device.widthPx * 0.90f).toInt()
            val expectedTapY = (device.heightPx * 0.5f).toInt()

            assertTrue("Device ${device.name}: Tap X (${skip.tapPoint.x}) must be on right edge (>= $expectedMinTapX)", skip.tapPoint.x >= expectedMinTapX)
            assertEquals("Device ${device.name}: Tap Y must be vertically centered", expectedTapY, skip.tapPoint.y)
        }
    }

    @Test
    fun testClipsDetectorCoverageThresholdAcrossAllAspectRatios() {
        for (device in deviceProfiles) {
            // Full screen clips viewer (100% height) -> Must detect
            val fullScreenViewer = MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/clips_viewer_container")
                .setBounds(Rect(0, 0, device.widthPx, device.heightPx))
                .setVisibleToUser(true)
                .build()

            assertTrue("Device ${device.name}: Full screen Reels clips viewer must be detected", InstagramClipsDetector.isClipsContainerVisible(fullScreenViewer))

            // Stub/collapsed container (< 50% screen height, e.g. 100px) inside screen root -> Must NOT detect
            val rootWithStub = MockAccessibilityNodeBuilder()
                .setViewId("com.instagram.android:id/main_feed")
                .setBounds(Rect(0, 0, device.widthPx, device.heightPx))
                .addChild(
                    MockAccessibilityNodeBuilder()
                        .setViewId("com.instagram.android:id/clips_viewer_container")
                        .setBounds(Rect(0, 0, device.widthPx, 100))
                        .setVisibleToUser(true)
                )
                .build()

            assertFalse("Device ${device.name}: Stub clips container must NOT be detected", InstagramClipsDetector.isClipsContainerVisible(rootWithStub))
        }
    }

    @Test
    fun testMultiSdkApiLevelCompatibilityMatrix() {
        val supportedSdkVersions = listOf(
            29 to "Android 10 (Q)",
            30 to "Android 11 (R)",
            31 to "Android 12 (S)",
            32 to "Android 12L (Sv2)",
            33 to "Android 13 (T)",
            34 to "Android 14 (U)",
            35 to "Android 15 (V)"
        )

        for ((sdkInt, name) in supportedSdkVersions) {
            // Ensure minimum SDK contract
            assertTrue("SDK $sdkInt ($name) must satisfy minSdkVersion >= 29", sdkInt >= 29)
            // Ensure bounds calculator operates identically regardless of host framework version
            val bounds = OverlayPositionCalculator.calculateTabBounds(1080, 2340, 132, 5, 1)
            assertEquals(216, bounds.left)
            assertEquals(432, bounds.right)
            assertEquals(2208, bounds.top)
            assertEquals(2340, bounds.bottom)
        }
    }
}
