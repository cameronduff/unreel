package org.unreel.android.engine

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.ArrayDeque
import java.util.regex.Pattern

object InstagramBottomNavDetector {

    private val REELS_WORD_PATTERN = Pattern.compile("(?i)\\breels?\\b")
    private const val MAX_NODE_TRAVERSAL_LIMIT = 100

    /**
     * Inspects the view hierarchy root node to determine if the bottom navigation
     * Reels tab is currently selected.
     *
     * Short-circuits on isSelected boolean check and inspects children eagerly.
     */
    fun isReelsTabSelected(rootNode: AccessibilityNodeInfoCompat?): Boolean {
        if (rootNode == null) return false

        val rootBounds = android.graphics.Rect()
        rootNode.getBoundsInScreen(rootBounds)

        if (isNodeReels(rootNode, rootBounds)) return true

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val childCount = current.childCount
            for (i in 0 until childCount) {
                val child = current.getChild(i) ?: continue
                if (isNodeReels(child, rootBounds)) {
                    return true
                }
                if (child.childCount > 0) {
                    queue.add(child)
                }
            }
        }

        return false
    }

    private fun isNodeReels(node: AccessibilityNodeInfoCompat, rootBounds: android.graphics.Rect): Boolean {
        if (!node.isSelected) return false

        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        // If root bounds are valid on a real screen, ensure this node is in the bottom navigation zone (lower 35%)
        if (rootBounds.height() > 500 && rect.bottom > 0) {
            val minBottomY = rootBounds.bottom - (rootBounds.height() * 0.35)
            if (rect.bottom < minBottomY) {
                return false
            }
        }

        val desc = node.contentDescription?.toString()
        if (desc != null && REELS_WORD_PATTERN.matcher(desc).find()) {
            android.util.Log.w("UnreelNavDetector", "Matched by desc='$desc', bounds=$rect, viewId='${node.viewIdResourceName}', class='${node.className}'")
            return true
        }

        val text = node.text?.toString()
        if (text != null && REELS_WORD_PATTERN.matcher(text).find()) {
            android.util.Log.w("UnreelNavDetector", "Matched by text='$text', bounds=$rect, viewId='${node.viewIdResourceName}', class='${node.className}'")
            return true
        }

        val viewId = node.viewIdResourceName
        if (viewId != null && (viewId.contains("reels_tab", ignoreCase = true) || viewId.contains("clips_tab", ignoreCase = true))) {
            android.util.Log.w("UnreelNavDetector", "Matched by viewId='$viewId', bounds=$rect, desc='$desc', class='${node.className}'")
            return true
        }

        return false
    }

    /**
     * Inspects the view hierarchy to determine if the user is inside a direct message
     * chat / conversation thread, where the bottom navigation bar is absent and replaced
     * by the message composer.
     */
    fun isDirectThreadActive(rootNode: AccessibilityNodeInfoCompat?): Boolean {
        if (rootNode == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val viewId = current.viewIdResourceName
            if (viewId != null && (
                viewId.contains("row_thread_composer", ignoreCase = true) ||
                viewId.contains("message_composer", ignoreCase = true) ||
                viewId.contains("direct_thread", ignoreCase = true) ||
                viewId.contains("thread_fragment", ignoreCase = true) ||
                viewId.contains("direct_message_list", ignoreCase = true) ||
                viewId.contains("direct_text_message", ignoreCase = true) ||
                viewId.contains("message_thread_container", ignoreCase = true) ||
                viewId.contains("message_list", ignoreCase = true) ||
                viewId.contains("direct_command_picker", ignoreCase = true) ||
                viewId.contains("meta_ai_voice_container", ignoreCase = true) ||
                viewId.contains("blurred_text_area_background", ignoreCase = true) ||
                viewId.contains("row_thread", ignoreCase = true)
            )) {
                return true
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                val child = current.getChild(i) ?: continue
                queue.add(child)
            }
        }

        return false
    }

    /**
     * Inspects the view hierarchy to locate a verified bottom navigation bar container.
     * The container must be in the bottom navigation zone and have a tab_bar resource ID
     * or contain multiple navigation tab children.
     */
    fun findBottomNavBarContainer(rootNode: AccessibilityNodeInfoCompat?): AccessibilityNodeInfoCompat? {
        if (rootNode == null) return null

        val rootBounds = android.graphics.Rect()
        rootNode.getBoundsInScreen(rootBounds)

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        val minBottomY = if (rootBounds.height() > 500) {
            rootBounds.bottom - (rootBounds.height() * 0.25)
        } else {
            0.0
        }

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            if (isNavBarNode(current, rootBounds, minBottomY)) {
                return current
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                val child = current.getChild(i) ?: continue
                queue.add(child)
            }
        }

        return null
    }

    private fun isNavBarNode(
        node: AccessibilityNodeInfoCompat,
        rootBounds: android.graphics.Rect,
        minBottomY: Double
    ): Boolean {
        val viewId = node.viewIdResourceName
        val isNavBarId = viewId != null && (
            viewId.endsWith(":id/tab_bar") ||
            viewId.endsWith(":id/navigation_bar") ||
            (viewId.contains("tab_bar", ignoreCase = true) && !viewId.contains("shadow", ignoreCase = true)) ||
            viewId.contains("bottom_navigation", ignoreCase = true)
        )

        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)

        val inBottomZone = rootBounds.height() == 0 || rect.bottom >= minBottomY

        if (isNavBarId && inBottomZone && node.childCount >= 1) {
            return true
        }

        // Check if container holds multiple distinct navigation tabs
        if (node.childCount >= 2 && inBottomZone) {
            var tabMatches = 0
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val cId = child.viewIdResourceName ?: ""
                val cDesc = child.contentDescription?.toString() ?: ""
                val cText = child.text?.toString() ?: ""
                if (cId.contains("feed_tab", ignoreCase = true) ||
                    cId.contains("search_tab", ignoreCase = true) ||
                    cId.contains("clips_tab", ignoreCase = true) ||
                    cId.contains("reels_tab", ignoreCase = true) ||
                    cId.contains("profile_tab", ignoreCase = true) ||
                    cId.contains("direct_tab", ignoreCase = true) ||
                    cDesc.equals("Home", ignoreCase = true) ||
                    cDesc.equals("Search", ignoreCase = true) ||
                    cDesc.equals("Reels", ignoreCase = true) ||
                    cDesc.equals("Profile", ignoreCase = true) ||
                    cText.equals("Home", ignoreCase = true) ||
                    cText.equals("Search", ignoreCase = true) ||
                    cText.equals("Reels", ignoreCase = true) ||
                    cText.equals("Profile", ignoreCase = true)
                ) {
                    tabMatches++
                }
            }
            if (tabMatches >= 2) {
                return true
            }
        }

        return false
    }

    /**
     * Inspects the view hierarchy root node to locate the screen bounds of the Reels
     * bottom navigation tab (whether selected or not), to allow the touch absorber
     * overlay to position itself with pixel precision over the button.
     *
     * Returns null immediately if inside a direct message thread, or if the bottom
     * navigation bar container is not present.
     */
    fun findReelsTabBounds(rootNode: AccessibilityNodeInfoCompat?): android.graphics.Rect? {
        if (rootNode == null) return null
        if (isDirectThreadActive(rootNode)) return null

        val navContainer = findBottomNavBarContainer(rootNode) ?: return null

        // Inspect children of the verified navigation bar container
        for (i in 0 until navContainer.childCount) {
            val child = navContainer.getChild(i) ?: continue
            val viewId = child.viewIdResourceName
            val desc = child.contentDescription?.toString()
            val text = child.text?.toString()

            val isMatch = (viewId != null && (viewId.contains("clips_tab", ignoreCase = true) || viewId.contains("reels_tab", ignoreCase = true))) ||
                (desc != null && desc.equals("Reels", ignoreCase = true)) ||
                (text != null && text.equals("Reels", ignoreCase = true))

            if (isMatch && child.isVisibleToUser) {
                val rect = android.graphics.Rect()
                child.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    return rect
                }
            }

            // Inspect grand-children if tabs are nested inside FrameLayouts
            for (j in 0 until child.childCount) {
                val grandChild = child.getChild(j) ?: continue
                val gId = grandChild.viewIdResourceName
                val gDesc = grandChild.contentDescription?.toString()
                val gText = grandChild.text?.toString()

                val gMatch = (gId != null && (gId.contains("clips_tab", ignoreCase = true) || gId.contains("reels_tab", ignoreCase = true))) ||
                    (gDesc != null && gDesc.equals("Reels", ignoreCase = true)) ||
                    (gText != null && gText.equals("Reels", ignoreCase = true))

                if (gMatch && grandChild.isVisibleToUser) {
                    val rect = android.graphics.Rect()
                    grandChild.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        return rect
                    }
                }
            }
        }

        return null
    }

    /**
     * Inspects the view hierarchy to check if the main Feed / Home tab is currently selected.
     */
    fun isFeedTabActive(rootNode: AccessibilityNodeInfoCompat?): Boolean {
        if (rootNode == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val viewId = current.viewIdResourceName
            if (viewId != null && (
                viewId.contains("row_thread_composer", ignoreCase = true) ||
                viewId.contains("message_composer", ignoreCase = true) ||
                viewId.contains("direct_thread", ignoreCase = true)
            )) {
                return false
            }

            val desc = current.contentDescription?.toString()
            val text = current.text?.toString()

            val isFeedTab = (viewId != null && viewId.contains("feed_tab", ignoreCase = true)) ||
                (desc != null && desc.equals("Home", ignoreCase = true)) ||
                (text != null && text.equals("Home", ignoreCase = true))

            if (isFeedTab) {
                return current.isSelected
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                val child = current.getChild(i) ?: continue
                queue.add(child)
            }
        }

        // If bottom tab bar is not found, fallback to true
        return true
    }

    /**
     * Inspects the view hierarchy to check if Instagram is still displaying its startup
     * splash screen / big logo, preventing premature blackout overlay attachment.
     */
    fun isSplashScreenShowing(rootNode: AccessibilityNodeInfoCompat?): Boolean {
        if (rootNode == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val viewId = current.viewIdResourceName
            val className = current.className?.toString()

            if (viewId != null && (viewId.contains("splash", ignoreCase = true) || viewId.contains("loading", ignoreCase = true))) {
                if (current.isVisibleToUser) {
                    return true
                }
            }

            if (className != null && (className.contains("SplashScreen", ignoreCase = true) || className.contains("IgSplashScreen", ignoreCase = true))) {
                return true
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                val child = current.getChild(i) ?: continue
                queue.add(child)
            }
        }

        return false
    }
}
