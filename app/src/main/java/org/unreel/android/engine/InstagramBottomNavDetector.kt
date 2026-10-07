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
}
