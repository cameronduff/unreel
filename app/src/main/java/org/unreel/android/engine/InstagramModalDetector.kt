package org.unreel.android.engine

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.ArrayDeque

/**
 * Detects whether Instagram is currently displaying a modal dialog, bottom sheet,
 * daily time limit prompt, comment tray, story viewer, or message composer.
 *
 * When any of these are active, the bottom tab bar is either obscured or inactive,
 * and any floating overlay must be immediately dismissed to avoid blocking dialog
 * action buttons or input fields.
 */
object InstagramModalDetector {

    val MODAL_CONTAINER_VIEW_IDS = setOf(
        "com.instagram.android:id/dialog_container",
        "com.instagram.android:id/dialog_window",
        "com.instagram.android:id/bottom_sheet_container",
        "com.instagram.android:id/action_sheet_container",
        "com.instagram.android:id/design_bottom_sheet",
        "com.instagram.android:id/igds_headline_headline",
        "com.instagram.android:id/comment_composer_container",
        "com.instagram.android:id/direct_share_sheet",
        "com.instagram.android:id/reel_viewer_root",
        "com.instagram.android:id/row_thread_composer",
        "com.instagram.android:id/quick_capture_fragment_container"
    )

    private const val MAX_NODE_TRAVERSAL_LIMIT = 80

    /**
     * Traverses the node hierarchy to detect if an active modal dialog, bottom sheet,
     * or daily limit prompt is currently present on screen.
     */
    fun isModalOrDialogPresent(rootNode: AccessibilityNodeInfoCompat?): Boolean {
        if (rootNode == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val viewId = current.viewIdResourceName
            if (viewId != null) {
                if (MODAL_CONTAINER_VIEW_IDS.contains(viewId) ||
                    viewId.contains("dialog") ||
                    viewId.contains("bottom_sheet") ||
                    viewId.contains("action_sheet")
                ) {
                    if (current.isVisibleToUser && (current.childCount > 0 || current.text != null)) {
                        android.util.Log.d("UnreelModalDetector", "Matched modal container viewId='$viewId'")
                        return true
                    }
                }

                // modal_container with active children
                if (viewId == "com.instagram.android:id/modal_container" && current.childCount > 0 && current.isVisibleToUser) {
                    android.util.Log.d("UnreelModalDetector", "Matched populated modal_container")
                    return true
                }
            }

            val className = current.className?.toString()
            if (className != null && (className.contains("Dialog", ignoreCase = true) || className.contains("BottomSheet", ignoreCase = true))) {
                if (current.isVisibleToUser) {
                    android.util.Log.d("UnreelModalDetector", "Matched modal className='$className'")
                    return true
                }
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        return false
    }
}
