package org.unreel.android.overlay

import android.graphics.Rect

object OverlayPositionCalculator {

    const val DEFAULT_TOTAL_TABS = 5
    const val DEFAULT_REELS_TAB_INDEX = 1

    /**
     * Calculates the screen bounds (Rect) for the Reels tab overlay touch absorber.
     *
     * @param screenWidthPx Total screen width in pixels.
     * @param screenHeightPx Total screen height in pixels.
     * @param navBarHeightPx Height of the bottom navigation bar in pixels.
     * @param totalTabs Total number of tabs in the bottom navigation bar (defaults to 5).
     * @param tabIndex 0-indexed position of the Reels tab (defaults to 3).
     * @return Rect representing the bounding box of the target tab.
     */
    fun calculateTabBounds(
        screenWidthPx: Int,
        screenHeightPx: Int,
        navBarHeightPx: Int,
        totalTabs: Int = DEFAULT_TOTAL_TABS,
        tabIndex: Int = DEFAULT_REELS_TAB_INDEX
    ): Rect {
        if (screenWidthPx <= 0) {
            throw IllegalArgumentException("screenWidthPx must be > 0, got $screenWidthPx")
        }
        if (screenHeightPx <= 0) {
            throw IllegalArgumentException("screenHeightPx must be > 0, got $screenHeightPx")
        }
        if (navBarHeightPx <= 0) {
            throw IllegalArgumentException("navBarHeightPx must be > 0, got $navBarHeightPx")
        }
        if (totalTabs <= 0) {
            throw IllegalArgumentException("totalTabs must be > 0, got $totalTabs")
        }
        if (tabIndex < 0 || tabIndex >= totalTabs) {
            throw IllegalArgumentException("tabIndex must be in range [0, $totalTabs), got $tabIndex")
        }

        val tabWidth = screenWidthPx / totalTabs
        val left = tabWidth * tabIndex
        val right = tabWidth * (tabIndex + 1)
        val top = screenHeightPx - navBarHeightPx
        val bottom = screenHeightPx

        return Rect(left, top, right, bottom)
    }
}
