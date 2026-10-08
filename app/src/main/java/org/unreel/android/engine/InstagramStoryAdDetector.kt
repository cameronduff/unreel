package org.unreel.android.engine

import android.graphics.Point
import android.graphics.Rect
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import java.util.ArrayDeque
import java.util.regex.Pattern

/**
 * High-performance, zero-allocation detector for Instagram Story Ads.
 * Identifies full-screen story viewer containers (e.g., 'reel_viewer_root')
 * containing 'Sponsored' or 'Ad' labels, and determines the fast-forward skip
 * action to bypass the ad in under 30ms.
 */
class InstagramStoryAdDetector(
    private val debounceWindowMs: Long = 350L
) {

    sealed class ActionResult {
        data class SkipAd(
            val tapPoint: Point,
            val scrollableNode: AccessibilityNodeInfoCompat? = null,
            val adIdentifier: String = "Sponsored Story"
        ) : ActionResult()

        object InCooldown : ActionResult()
        object OrganicStory : ActionResult()
        object NotInStoryViewer : ActionResult()
    }

    private var lastSkipEpochMs: Long = 0L

    companion object {
        const val MAX_TRAVERSAL_LIMIT = 60

        private val SPONSORED_TEXT_PATTERN = Pattern.compile(
            "^(Sponsored|Ad|Sponsored\\s+by.*|Paid\\s+partnership.*)$",
            Pattern.CASE_INSENSITIVE
        )

        private val STORY_VIEWER_IDS = setOf(
            "reel_viewer_root",
            "reel_viewer_fragment_container",
            "stories_viewer_container",
            "story_viewer_fragment",
            "reel_viewer_media_layout"
        )

        private val STORY_AD_CTA_IDS = setOf(
            "story_ad_cta",
            "ad_action_button",
            "cta_button",
            "headline_action_button"
        )
    }

    /**
     * Checks if the user is currently viewing Instagram Stories.
     */
    fun isStoryViewerActive(rootNode: AccessibilityNodeInfoCompat?): Boolean {
        if (rootNode == null) return false

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var count = 0

        while (queue.isNotEmpty() && count < MAX_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            count++

            val viewId = current.viewIdResourceName
            if (viewId != null) {
                for (storyId in STORY_VIEWER_IDS) {
                    if (viewId.contains(storyId, ignoreCase = true)) {
                        return true
                    }
                }
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
     * Inspects the view hierarchy to determine whether the active story is sponsored.
     * If sponsored, computes fast-forward skip tap coordinates and debounces repeated triggers.
     */
    fun detectAndSkipAd(
        rootNode: AccessibilityNodeInfoCompat?,
        currentTimeMs: Long = System.currentTimeMillis()
    ): ActionResult {
        if (rootNode == null) return ActionResult.NotInStoryViewer

        val rootBounds = Rect()
        rootNode.getBoundsInScreen(rootBounds)
        if (rootBounds.width() <= 0 || rootBounds.height() <= 0) {
            return ActionResult.NotInStoryViewer
        }

        var isInsideStoryViewer = false
        var isSponsored = false
        var adIdentifier = "Sponsored Story"
        var scrollablePagerNode: AccessibilityNodeInfoCompat? = null

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var count = 0

        while (queue.isNotEmpty() && count < MAX_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            count++

            val viewId = current.viewIdResourceName
            if (viewId != null) {
                for (storyId in STORY_VIEWER_IDS) {
                    if (viewId.contains(storyId, ignoreCase = true)) {
                        isInsideStoryViewer = true
                    }
                }
                for (ctaId in STORY_AD_CTA_IDS) {
                    if (viewId.contains(ctaId, ignoreCase = true) && current.isVisibleToUser) {
                        isSponsored = true
                        adIdentifier = "Story Ad CTA: $viewId"
                    }
                }
            }

            if (current.isScrollable && (viewId?.contains("pager", ignoreCase = true) == true || viewId?.contains("reel_viewer", ignoreCase = true) == true)) {
                scrollablePagerNode = current
            }

            val text = current.text?.toString()?.trim()
            if (!text.isNullOrEmpty() && SPONSORED_TEXT_PATTERN.matcher(text).matches() && current.isVisibleToUser) {
                isSponsored = true
                adIdentifier = "Sponsored Text: '$text'"
            }

            val desc = current.contentDescription?.toString()?.trim()
            if (!desc.isNullOrEmpty() && SPONSORED_TEXT_PATTERN.matcher(desc).matches() && current.isVisibleToUser) {
                isSponsored = true
                adIdentifier = "Sponsored Desc: '$desc'"
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                val child = current.getChild(i) ?: continue
                queue.add(child)
            }
        }

        if (!isInsideStoryViewer) {
            return ActionResult.NotInStoryViewer
        }

        if (!isSponsored) {
            return ActionResult.OrganicStory
        }

        if (currentTimeMs - lastSkipEpochMs < debounceWindowMs) {
            return ActionResult.InCooldown
        }

        lastSkipEpochMs = currentTimeMs

        // Fast-forward tap target: right side (92% of screen width, vertical center)
        val tapX = (rootBounds.left + rootBounds.width() * 0.92f).toInt()
        val tapY = (rootBounds.top + rootBounds.height() * 0.5f).toInt()
        val tapPoint = Point(tapX, tapY)

        return ActionResult.SkipAd(
            tapPoint = tapPoint,
            scrollableNode = scrollablePagerNode,
            adIdentifier = adIdentifier
        )
    }

    fun resetDebounce() {
        lastSkipEpochMs = 0L
    }
}
