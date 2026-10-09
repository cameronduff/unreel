package org.unreel.android.engine

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.ArrayDeque
import java.util.regex.Pattern

object InstagramClipsDetector {

    val TARGET_VIEW_IDS = setOf(
        "com.instagram.android:id/clips_video_container",
        "com.instagram.android:id/clips_swipe_refresh_container",
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/clips_viewer_container",
        "com.instagram.android:id/clips_viewer_fragment_container",
        "com.instagram.android:id/reel_viewer_container",
        "com.instagram.android:id/clips_video_player",
        "com.instagram.android:id/clips_media_item",
        "com.instagram.android:id/reels_viewer",
        "com.instagram.android:id/watch_reels_button",
        "com.instagram.android:id/clips_item_container",
        "com.instagram.android:id/clips_viewer_root",
        "com.instagram.android:id/clips_viewer_content",
        "com.instagram.android:id/clips_author_name",
        "com.instagram.android:id/clips_viewer_debug_container"
    )

    private val REELS_DESC_PATTERN = Pattern.compile("(?i)\\b(reels?|clips?)\\b.*(video|viewer|by\\s*@)")
    private const val MAX_NODE_TRAVERSAL_LIMIT = 150

    /**
     * Inspects the view hierarchy root node to detect if the user has navigated into
     * Instagram's full-screen vertical Reels viewer.
     *
     * Short-circuits immediately upon the first matching view ID or heuristic.
     */
    fun isClipsContainerVisible(rootNode: AccessibilityNodeInfoCompat?): Boolean {
        if (rootNode == null) return false

        val rootBounds = android.graphics.Rect()
        rootNode.getBoundsInScreen(rootBounds)

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            if (isNodeClips(current, rootBounds)) {
                return true
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        return false
    }

    /**
     * Checks if a single node matches fullscreen Reels / Clips criteria.
     */
    fun isNodeClips(node: AccessibilityNodeInfoCompat, rootBounds: android.graphics.Rect): Boolean {
        // 1. Direct view ID match or pattern match
        val viewId = node.viewIdResourceName
        if (viewId != null) {
            if (TARGET_VIEW_IDS.contains(viewId) ||
                viewId.contains("clips_viewer") ||
                viewId.contains("reels_viewer")
            ) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                // If root bounds available, ensure the container is actually visible to the user
                // and spans fullscreen height (>50%) to prevent invisible 0-height stubs or inline cards
                if (rootBounds.height() == 0 || (node.isVisibleToUser && rect.height() >= (rootBounds.height() * 0.5))) {
                    android.util.Log.w("UnreelClipsDetector", "Matched TARGET_VIEW_ID: '$viewId', bounds=$rect, visible=${node.isVisibleToUser}")
                    return true
                }
            }
        }

        // 2. Class name heuristic match (e.g. ClipsViewerFragment container)
        val className = node.className?.toString()
        if (className != null && (className.contains("ClipsViewer", ignoreCase = true) || className.contains("ReelsViewer", ignoreCase = true))) {
            android.util.Log.w("UnreelClipsDetector", "Matched className: '$className'")
            return true
        }

        // 3. Top header title match (fullscreen viewer "Reels" header title)
        val text = node.text?.toString()
        if (text.equals("Reels", ignoreCase = true) || text.equals("Clips", ignoreCase = true)) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (rect.top in 1..400) {
                android.util.Log.w("UnreelClipsDetector", "Matched top header title: '$text', bounds=$rect")
                return true
            }
        }

        // 4. Content description heuristic for full screen video clips
        val desc = node.contentDescription?.toString()
        if (desc != null && REELS_DESC_PATTERN.matcher(desc).find()) {
            val rect = android.graphics.Rect()
            node.getBoundsInScreen(rect)
            if (rootBounds.height() == 0 || rect.height() >= (rootBounds.height() * 0.7)) {
                android.util.Log.w("UnreelClipsDetector", "Matched desc: '$desc', bounds=$rect")
                return true
            }
        }

        return false
    }
}
