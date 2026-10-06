package org.unreel.android.engine

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.ArrayDeque

object InstagramBottomNavDetector {

    private const val TARGET_REELS_LABEL = "reels"
    private const val MAX_NODE_TRAVERSAL_LIMIT = 80

    /**
     * Inspects the view hierarchy root node to determine if the bottom navigation
     * Reels tab is currently selected.
     *
     * Short-circuits on isSelected boolean check and inspects children eagerly.
     */
    fun isReelsTabSelected(rootNode: AccessibilityNodeInfoCompat?): Boolean {
        if (rootNode == null) return false

        if (isNodeReels(rootNode)) return true

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val childCount = current.childCount
            for (i in 0 until childCount) {
                val child = current.getChild(i) ?: continue
                if (isNodeReels(child)) {
                    return true
                }
                if (child.childCount > 0) {
                    queue.add(child)
                }
            }
        }

        return false
    }

    private fun isNodeReels(node: AccessibilityNodeInfoCompat): Boolean {
        if (!node.isSelected) return false
        val desc = node.contentDescription
        if (desc != null && desc.toString().equals(TARGET_REELS_LABEL, ignoreCase = true)) {
            return true
        }
        val text = node.text
        if (text != null && text.toString().equals(TARGET_REELS_LABEL, ignoreCase = true)) {
            return true
        }
        return false
    }
}
