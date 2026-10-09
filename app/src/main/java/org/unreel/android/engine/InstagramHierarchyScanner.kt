package org.unreel.android.engine

import android.graphics.Rect
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.ArrayDeque
import java.util.regex.Pattern

object InstagramHierarchyScanner {

    private val REELS_WORD_PATTERN = Pattern.compile("(?i)\\breels?\\b")
    private const val MAX_NODE_TRAVERSAL_LIMIT = 70

    data class ScanResult(
        val isClipsVisible: Boolean = false,
        val isReelsTabSelected: Boolean = false,
        val isDirectThreadActive: Boolean = false,
        val isModalOpen: Boolean = false,
        val isSplashScreenShowing: Boolean = false,
        val reelsTabBounds: Rect? = null,
        val nodesVisited: Int = 0
    )

    /**
     * Executes a unified single-pass breadth-first scan across the accessibility hierarchy.
     * Consolidates Clips detection, Reels tab selection, Direct thread detection, Modal presence,
     * Splash screen checks, and Reels tab coordinate resolution into one fast traversal.
     */
    fun scan(rootNode: AccessibilityNodeInfoCompat?): ScanResult {
        if (rootNode == null) return ScanResult()

        val rootBounds = Rect()
        rootNode.getBoundsInScreen(rootBounds)

        val minBottomY = if (rootBounds.height() > 500) {
            rootBounds.bottom - (rootBounds.height() * 0.25)
        } else {
            0.0
        }

        var isClips = false
        var isReelsTabSel = false
        var isDirect = false
        var isModal = false
        var isSplash = false
        var tabBounds: Rect? = null

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val viewId = current.viewIdResourceName
            val className = current.className?.toString()
            val desc = current.contentDescription?.toString()
            val text = current.text?.toString()

            // 1. Fullscreen Clips Detection
            if (!isClips && InstagramClipsDetector.isNodeClips(current, rootBounds)) {
                isClips = true
            }

            // 2. Direct Thread / Message Composer Detection
            if (!isDirect && viewId != null && (
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
                isDirect = true
            }

            // 3. Modal / Dialog Detection
            if (!isModal) {
                if (viewId != null && (
                    viewId.contains("dialog_container", ignoreCase = true) ||
                    viewId.contains("igds_modal", ignoreCase = true) ||
                    viewId.contains("action_sheet", ignoreCase = true) ||
                    viewId.contains("bottom_sheet_container", ignoreCase = true) ||
                    viewId.contains("headline_container", ignoreCase = true)
                )) {
                    isModal = true
                } else if (className != null && (
                    className.contains("Dialog", ignoreCase = true) ||
                    className.contains("BottomSheet", ignoreCase = true)
                )) {
                    isModal = true
                }
            }

            // 4. Splash Screen Detection
            if (!isSplash && tabBounds == null) {
                if (viewId != null && (
                    viewId.contains("splash", ignoreCase = true) ||
                    viewId.contains("loading", ignoreCase = true)
                )) {
                    if (current.isVisibleToUser) {
                        isSplash = true
                    }
                } else if (className != null && (
                    className.contains("SplashScreen", ignoreCase = true) ||
                    className.contains("IgSplashScreen", ignoreCase = true)
                )) {
                    isSplash = true
                }
            }

            // 5. Verified Bottom Navigation Bar Container & Reels Tab Bounds
            if (tabBounds == null && !isDirect) {
                val isNavBarContainer = isNavBarNode(current, rootBounds, minBottomY)
                if (isNavBarContainer) {
                    val bounds = resolveReelsTabFromContainer(current, rootBounds)
                    if (bounds != null) {
                        tabBounds = bounds
                        isSplash = false // If bottom nav bar is present, splash screen is definitely gone
                    }
                }
            }

            // 1b. Reels Tab Selected Detection
            if (!isReelsTabSel && current.isSelected) {
                val isReelsNode = (viewId != null && (viewId.contains("reels_tab", ignoreCase = true) || viewId.contains("clips_tab", ignoreCase = true))) ||
                    (desc != null && REELS_WORD_PATTERN.matcher(desc).find()) ||
                    (text != null && REELS_WORD_PATTERN.matcher(text).find())
                if (isReelsNode) {
                    val rect = Rect()
                    current.getBoundsInScreen(rect)
                    val inBottomZone = rootBounds.height() == 0 || rect.bottom >= minBottomY
                    if (inBottomZone) {
                        isReelsTabSel = true
                    }
                }
            }

            // Short-circuit: If fullscreen clips or reels tab selection detected, priority 0 back action is needed immediately
            if (isClips || isReelsTabSel) {
                return ScanResult(
                    isClipsVisible = isClips,
                    isReelsTabSelected = isReelsTabSel,
                    isDirectThreadActive = isDirect,
                    isModalOpen = isModal,
                    isSplashScreenShowing = isSplash,
                    reelsTabBounds = tabBounds,
                    nodesVisited = visitedCount
                )
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                val child = current.getChild(i) ?: continue
                queue.add(child)
            }
        }

        return ScanResult(
            isClipsVisible = isClips,
            isReelsTabSelected = isReelsTabSel,
            isDirectThreadActive = isDirect,
            isModalOpen = isModal,
            isSplashScreenShowing = isSplash,
            reelsTabBounds = if (isDirect) null else tabBounds,
            nodesVisited = visitedCount
        )
    }

    private fun isNavBarNode(
        node: AccessibilityNodeInfoCompat,
        rootBounds: Rect,
        minBottomY: Double
    ): Boolean {
        val viewId = node.viewIdResourceName
        val isNavBarId = viewId != null && (
            viewId.endsWith(":id/tab_bar") ||
            viewId.endsWith(":id/navigation_bar") ||
            (viewId.contains("tab_bar", ignoreCase = true) && !viewId.contains("shadow", ignoreCase = true)) ||
            viewId.contains("bottom_navigation", ignoreCase = true)
        )

        val rect = Rect()
        node.getBoundsInScreen(rect)

        val inBottomZone = rootBounds.height() == 0 || rect.bottom >= minBottomY

        if (isNavBarId && inBottomZone && node.childCount >= 1) {
            return true
        }

        if (node.childCount in 2..7 && inBottomZone) {
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

    private fun resolveReelsTabFromContainer(
        container: AccessibilityNodeInfoCompat,
        rootBounds: Rect
    ): Rect? {
        for (i in 0 until container.childCount) {
            val child = container.getChild(i) ?: continue
            val viewId = child.viewIdResourceName
            val desc = child.contentDescription?.toString()
            val text = child.text?.toString()

            val isMatch = (viewId != null && (viewId.contains("clips_tab", ignoreCase = true) || viewId.contains("reels_tab", ignoreCase = true))) ||
                (desc != null && desc.equals("Reels", ignoreCase = true)) ||
                (text != null && text.equals("Reels", ignoreCase = true))

            if (isMatch && child.isVisibleToUser) {
                val rect = Rect()
                child.getBoundsInScreen(rect)
                if (rect.width() > 0 && rect.height() > 0) {
                    return rect
                }
            }

            for (j in 0 until child.childCount) {
                val grandChild = child.getChild(j) ?: continue
                val gId = grandChild.viewIdResourceName
                val gDesc = grandChild.contentDescription?.toString()
                val gText = grandChild.text?.toString()

                val gMatch = (gId != null && (gId.contains("clips_tab", ignoreCase = true) || gId.contains("reels_tab", ignoreCase = true))) ||
                    (gDesc != null && gDesc.equals("Reels", ignoreCase = true)) ||
                    (gText != null && gText.equals("Reels", ignoreCase = true))

                if (gMatch && grandChild.isVisibleToUser) {
                    val rect = Rect()
                    grandChild.getBoundsInScreen(rect)
                    if (rect.width() > 0 && rect.height() > 0) {
                        return rect
                    }
                }
            }
        }

        return null
    }
}
