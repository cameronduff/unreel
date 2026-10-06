package org.unreel.android.overlay

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OverlayPositionCalculatorTest {

    @Test
    fun testFHDPlusCoordinates() {
        val width = 1080
        val height = 2400
        val navBarHeight = 150
        val bounds = OverlayPositionCalculator.calculateTabBounds(
            screenWidthPx = width,
            screenHeightPx = height,
            navBarHeightPx = navBarHeight,
            totalTabs = 5,
            tabIndex = 3
        )

        val tabWidth = width / 5 // 216
        val expected = Rect(
            216 * 3, // 648
            2400 - 150, // 2250
            216 * 4, // 864
            2400
        )
        assertEquals(expected, bounds)
    }

    @Test
    fun testQHDPlusCoordinates() {
        val width = 1440
        val height = 3120
        val navBarHeight = 200
        val bounds = OverlayPositionCalculator.calculateTabBounds(
            screenWidthPx = width,
            screenHeightPx = height,
            navBarHeightPx = navBarHeight,
            totalTabs = 5,
            tabIndex = 3
        )

        val tabWidth = width / 5 // 288
        val expected = Rect(
            288 * 3, // 864
            3120 - 200, // 2920
            288 * 4, // 1152
            3120
        )
        assertEquals(expected, bounds)
    }

    @Test
    fun testHDPlusCoordinates() {
        val width = 720
        val height = 1600
        val navBarHeight = 120
        val bounds = OverlayPositionCalculator.calculateTabBounds(
            screenWidthPx = width,
            screenHeightPx = height,
            navBarHeightPx = navBarHeight,
            totalTabs = 5,
            tabIndex = 3
        )

        val tabWidth = width / 5 // 144
        val expected = Rect(
            144 * 3, // 432
            1600 - 120, // 1480
            144 * 4, // 576
            1600
        )
        assertEquals(expected, bounds)
    }

    @Test
    fun testInvalidScreenWidthThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            OverlayPositionCalculator.calculateTabBounds(
                screenWidthPx = 0,
                screenHeightPx = 2400,
                navBarHeightPx = 150
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OverlayPositionCalculator.calculateTabBounds(
                screenWidthPx = -1080,
                screenHeightPx = 2400,
                navBarHeightPx = 150
            )
        }
    }

    @Test
    fun testInvalidTotalTabsThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            OverlayPositionCalculator.calculateTabBounds(
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                navBarHeightPx = 150,
                totalTabs = 0
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OverlayPositionCalculator.calculateTabBounds(
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                navBarHeightPx = 150,
                totalTabs = -5
            )
        }
    }

    @Test
    fun testInvalidScreenHeightOrNavBarThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            OverlayPositionCalculator.calculateTabBounds(
                screenWidthPx = 1080,
                screenHeightPx = 0,
                navBarHeightPx = 150
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OverlayPositionCalculator.calculateTabBounds(
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                navBarHeightPx = 0
            )
        }
    }

    @Test
    fun testInvalidTabIndexThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            OverlayPositionCalculator.calculateTabBounds(
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                navBarHeightPx = 150,
                totalTabs = 5,
                tabIndex = -1
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OverlayPositionCalculator.calculateTabBounds(
                screenWidthPx = 1080,
                screenHeightPx = 2400,
                navBarHeightPx = 150,
                totalTabs = 5,
                tabIndex = 5
            )
        }
    }
}
