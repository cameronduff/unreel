package org.unreel.android.engine

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.ArrayDeque

object InstagramClipsDetector {

    val TARGET_VIEW_IDS = setOf(
        "com.instagram.android:id/clips_video_container",
        "com.instagram.android:id/clips_swipe_refresh_container",
        "com.instagram.android:id/clips_viewer_view_pager"
    )

    private const val MAX_NODE_TRAVERSAL_LIMIT = 80

    /**
     * Inspects the view hierarchy root node to detect if the user has navigated into
     * Instagram's full-screen vertical Reels viewer.
     *
     * Short-circuits immediately upon the first matching view ID.
     */
    fun isClipsContainerVisible(rootNode: AccessibilityNodeInfoCompat?): Boolean {
        if (rootNode == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val viewId = current.viewIdResourceName
            if (viewId != null && TARGET_VIEW_IDS.contains(viewId)) {
                return true
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        return false
    }
}
