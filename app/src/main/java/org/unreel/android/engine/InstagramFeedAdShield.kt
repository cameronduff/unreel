package org.unreel.android.engine

import android.graphics.Rect
import android.util.Log
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.unreel.android.data.ReelsInterceptEntity
import java.util.ArrayDeque
import java.util.regex.Pattern

/**
 * Eradicates sponsored advertising posts in the Instagram feed by detecting
 * "Sponsored" labels and programmatically navigating the options menu to hide the ad.
 *
 * Runs strictly within frame budgets (<3ms per pass) and operates as a non-blocking
 * state machine with watchdog timeout guards.
 */
class InstagramFeedAdShield(
    var watchdogTimeoutMs: Long = 2500L,
    var actionCooldownMs: Long = 5000L
) {

    enum class State {
        IDLE,
        AWAITING_OPTIONS_MENU,
        AWAITING_REASON_MENU
    }

    sealed class ActionResult {
        object None : ActionResult()
        data class ClickedOptions(val bounds: Rect) : ActionResult()
        data class ClickedHideAd(val text: String) : ActionResult()
        data class AdHidden(val trigger: String) : ActionResult()
        object TimeoutReset : ActionResult()
    }

    var state: State = State.IDLE
        internal set

    var lastActionEpochMs: Long = 0L
    var sequenceStartEpochMs: Long = 0L
        internal set

    private val processedAdPositions = mutableMapOf<Int, Long>()

    fun resetState() {
        state = State.IDLE
        sequenceStartEpochMs = 0L
    }

    fun isCooldownActive(currentEpochMs: Long = System.currentTimeMillis()): Boolean {
        return lastActionEpochMs > 0L && currentEpochMs < lastActionEpochMs + actionCooldownMs
    }

    fun processHierarchy(
        rootNode: AccessibilityNodeInfoCompat?,
        currentEpochMs: Long = System.currentTimeMillis()
    ): ActionResult {
        if (rootNode == null) return ActionResult.None

        // Check watchdog timeout for active multi-step menu interactions
        if (state != State.IDLE) {
            if (currentEpochMs - sequenceStartEpochMs > watchdogTimeoutMs) {
                Log.w(TAG, "Watchdog timeout ($watchdogTimeoutMs ms) in state $state. Resetting to IDLE.")
                resetState()
                lastActionEpochMs = currentEpochMs
                return ActionResult.TimeoutReset
            }
        }

        // Clean stale processed ad records (> 60 seconds old)
        processedAdPositions.entries.removeIf { currentEpochMs - it.value > 60_000L }

        return when (state) {
            State.IDLE -> handleIdleState(rootNode, currentEpochMs)
            State.AWAITING_OPTIONS_MENU -> handleAwaitingOptionsMenu(rootNode, currentEpochMs)
            State.AWAITING_REASON_MENU -> handleAwaitingReasonMenu(rootNode, currentEpochMs)
        }
    }

    private fun handleIdleState(
        rootNode: AccessibilityNodeInfoCompat,
        currentEpochMs: Long
    ): ActionResult {
        if (isCooldownActive(currentEpochMs)) return ActionResult.None

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        var sponsoredLabelBounds: Rect? = null
        val optionButtonCandidates = mutableListOf<Pair<AccessibilityNodeInfoCompat, Rect>>()

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val viewId = current.viewIdResourceName
            if (viewId != null) {
                // If clips or modal is present, abort immediately
                if (viewId.contains("clips_video_container") ||
                    viewId.contains("dialog_container") ||
                    viewId.contains("row_thread_composer") ||
                    viewId.contains("message_composer")
                ) {
                    return ActionResult.None
                }
            }

            val text = current.text?.toString()
            val desc = current.contentDescription?.toString()

            if (sponsoredLabelBounds == null) {
                val isSponsored = (text != null && SPONSORED_PATTERN.matcher(text).find()) ||
                    (desc != null && SPONSORED_PATTERN.matcher(desc).find())
                if (isSponsored && current.isVisibleToUser) {
                    val rect = Rect()
                    current.getBoundsInScreen(rect)
                    // Check if we haven't already processed an ad at this approximate Y coordinate recently
                    val approxY = rect.centerY() / 50
                    if (!processedAdPositions.containsKey(approxY)) {
                        sponsoredLabelBounds = rect
                    }
                }
            }

            val isOptionButton = (viewId != null && OPTIONS_BUTTON_IDS.contains(viewId)) ||
                (desc != null && OPTIONS_BUTTON_DESC_PATTERN.matcher(desc).find())
            if (isOptionButton && current.isVisibleToUser) {
                val rect = Rect()
                current.getBoundsInScreen(rect)
                optionButtonCandidates.add(Pair(current, rect))
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        val labelRect = sponsoredLabelBounds ?: return ActionResult.None
        if (optionButtonCandidates.isEmpty()) return ActionResult.None

        // Find the options button vertically closest to the Sponsored label header
        val closestButtonPair = optionButtonCandidates.minByOrNull { (_, rect) ->
            Math.abs(rect.centerY() - labelRect.centerY())
        } ?: return ActionResult.None

        val (buttonNode, buttonBounds) = closestButtonPair
        // Require options button to be in close vertical proximity (header row)
        if (Math.abs(buttonBounds.centerY() - labelRect.centerY()) > 350) {
            return ActionResult.None
        }

        Log.i(TAG, "Sponsored ad detected at $labelRect. Triggering options button at $buttonBounds")
        val clicked = performClick(buttonNode)
        if (clicked) {
            state = State.AWAITING_OPTIONS_MENU
            sequenceStartEpochMs = currentEpochMs
            val approxY = labelRect.centerY() / 50
            processedAdPositions[approxY] = currentEpochMs
            return ActionResult.ClickedOptions(buttonBounds)
        }

        return ActionResult.None
    }

    private fun handleAwaitingOptionsMenu(
        rootNode: AccessibilityNodeInfoCompat,
        currentEpochMs: Long
    ): ActionResult {
        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        var hideAdNode: AccessibilityNodeInfoCompat? = null

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val text = current.text?.toString()
            val desc = current.contentDescription?.toString()

            val isHideAd = (text != null && HIDE_AD_PATTERN.matcher(text).find()) ||
                (desc != null && HIDE_AD_PATTERN.matcher(desc).find())
            if (isHideAd && current.isVisibleToUser) {
                hideAdNode = current
                break
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        if (hideAdNode != null) {
            val label = hideAdNode.text?.toString() ?: hideAdNode.contentDescription?.toString() ?: "Hide ad"
            Log.i(TAG, "Matched '$label' in options menu. Clicking to select hide reason...")
            val clicked = performClick(hideAdNode)
            if (clicked) {
                state = State.AWAITING_REASON_MENU
                sequenceStartEpochMs = currentEpochMs
                return ActionResult.ClickedHideAd(label)
            }
        }

        return ActionResult.None
    }

    private fun handleAwaitingReasonMenu(
        rootNode: AccessibilityNodeInfoCompat,
        currentEpochMs: Long
    ): ActionResult {
        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val text = current.text?.toString()
            val desc = current.contentDescription?.toString()

            val isReason = (text != null && REASON_PATTERN.matcher(text).find()) ||
                (desc != null && REASON_PATTERN.matcher(desc).find())
            if (isReason && current.isVisibleToUser) {
                Log.i(TAG, "Matched hide reason '${text ?: desc}'. Finalizing ad elimination...")
                val clicked = performClick(current)
                if (clicked) {
                    resetState()
                    lastActionEpochMs = currentEpochMs
                    return ActionResult.AdHidden(ReelsInterceptEntity.TRIGGER_FEED_AD_SHIELD)
                }
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        return ActionResult.None
    }

    private fun performClick(node: AccessibilityNodeInfoCompat?): Boolean {
        if (node == null) return false
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        }
        var current: AccessibilityNodeInfoCompat? = node.parent
        var depth = 0
        while (current != null && depth < 3) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return node.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
    }

    companion object {
        const val TAG = "InstagramFeedAdShield"
        private const val MAX_NODE_TRAVERSAL_LIMIT = 150

        val SPONSORED_PATTERN = Pattern.compile(
            "(?i)^sponsored$|^paid partnership$|\\bsponsored\\b"
        )
        val OPTIONS_BUTTON_IDS = setOf(
            "com.instagram.android:id/media_option_button",
            "com.instagram.android:id/row_feed_button_options"
        )
        val OPTIONS_BUTTON_DESC_PATTERN = Pattern.compile(
            "(?i)(more actions for this post|options|more options)"
        )
        val HIDE_AD_PATTERN = Pattern.compile(
            "(?i)\\bhide ad\\b|\\bhide this ad\\b"
        )
        val REASON_PATTERN = Pattern.compile(
            "(?i)it['’]?s irrelevant|i see it too often|it['’]?s inappropriate"
        )
    }
}
