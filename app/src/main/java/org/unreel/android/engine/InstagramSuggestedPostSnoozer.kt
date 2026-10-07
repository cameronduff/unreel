package org.unreel.android.engine

import android.graphics.Rect
import android.util.Log
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.unreel.android.data.ReelsInterceptEntity
import java.util.ArrayDeque
import java.util.regex.Pattern

class InstagramSuggestedPostSnoozer(
    var cooldownDurationMs: Long = 24 * 60 * 60 * 1000L, // 24 hours
    var backoffDurationMs: Long = 5 * 60 * 1000L,       // 5 minutes on abort/timeout
    var watchdogTimeoutMs: Long = 2500L                 // 2.5 seconds to complete menu interaction
) {

    enum class State {
        IDLE,
        AWAITING_MENU,
        AWAITING_CONFIRMATION
    }

    sealed class ActionResult {
        object None : ActionResult()
        data class ClickedMoreOptions(val bounds: Rect) : ActionResult()
        data class ClickedNotInterested(val text: String) : ActionResult()
        data class SnoozeCompleted(val trigger: String) : ActionResult()
        object TimeoutReset : ActionResult()
    }

    var state: State = State.IDLE
        internal set

    var lastSuccessfulSnoozeEpochMs: Long = 0L
    var sequenceStartEpochMs: Long = 0L
        internal set
    var backoffUntilEpochMs: Long = 0L
        internal set

    fun isCooldownActive(currentEpochMs: Long = System.currentTimeMillis()): Boolean {
        if (currentEpochMs < backoffUntilEpochMs) return true
        if (lastSuccessfulSnoozeEpochMs > 0L && currentEpochMs < lastSuccessfulSnoozeEpochMs + cooldownDurationMs) {
            return true
        }
        return false
    }

    fun resetState() {
        state = State.IDLE
        sequenceStartEpochMs = 0L
    }

    fun processHierarchy(
        rootNode: AccessibilityNodeInfoCompat?,
        currentEpochMs: Long = System.currentTimeMillis()
    ): ActionResult {
        if (rootNode == null) return ActionResult.None

        // Check watchdog timeout
        if (state != State.IDLE) {
            if (currentEpochMs - sequenceStartEpochMs > watchdogTimeoutMs) {
                Log.w(TAG, "Watchdog timeout exceeded ($watchdogTimeoutMs ms) in state $state. Resetting to IDLE.")
                resetState()
                backoffUntilEpochMs = currentEpochMs + backoffDurationMs
                return ActionResult.TimeoutReset
            }
        }

        // 1. If currently inside Settings > Content preferences, check if we can toggle snooze directly
        val settingsResult = checkAndToggleSettingsSnooze(rootNode, currentEpochMs)
        if (settingsResult is ActionResult.SnoozeCompleted) {
            return settingsResult
        }

        // 2. If in IDLE and cooldown is active, skip traversal immediately (zero performance overhead)
        if (state == State.IDLE && isCooldownActive(currentEpochMs)) {
            return ActionResult.None
        }

        return when (state) {
            State.IDLE -> handleIdleState(rootNode, currentEpochMs)
            State.AWAITING_MENU -> handleAwaitingMenuState(rootNode, currentEpochMs)
            State.AWAITING_CONFIRMATION -> handleAwaitingConfirmationState(rootNode, currentEpochMs)
        }
    }

    private fun handleIdleState(
        rootNode: AccessibilityNodeInfoCompat,
        currentEpochMs: Long
    ): ActionResult {
        // Only run when viewing main feed
        if (InstagramModalDetector.isModalOrDialogPresent(rootNode)) return ActionResult.None
        if (InstagramClipsDetector.isClipsContainerVisible(rootNode)) return ActionResult.None
        if (!InstagramBottomNavDetector.isFeedTabActive(rootNode)) return ActionResult.None

        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        var suggestedLabelBounds: Rect? = null
        val optionButtonCandidates = mutableListOf<Pair<AccessibilityNodeInfoCompat, Rect>>()

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val text = current.text?.toString()
            val desc = current.contentDescription?.toString()
            val viewId = current.viewIdResourceName

            // Check if this node is a suggested post indicator
            if (suggestedLabelBounds == null) {
                val isSuggested = (text != null && SUGGESTED_LABEL_PATTERN.matcher(text).find()) ||
                    (desc != null && SUGGESTED_LABEL_PATTERN.matcher(desc).find())
                if (isSuggested && current.isVisibleToUser) {
                    val rect = Rect()
                    current.getBoundsInScreen(rect)
                    suggestedLabelBounds = rect
                }
            }

            // Collect post options buttons
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

        // If a suggested post was found, find the vertically closest options button
        val labelRect = suggestedLabelBounds ?: return ActionResult.None
        if (optionButtonCandidates.isEmpty()) return ActionResult.None

        val closestButtonPair = optionButtonCandidates.minByOrNull { (_, rect) ->
            Math.abs(rect.centerY() - labelRect.centerY())
        } ?: return ActionResult.None

        val (buttonNode, buttonBounds) = closestButtonPair
        // Ensure options button is within vertical proximity of the suggested post header
        if (Math.abs(buttonBounds.centerY() - labelRect.centerY()) > 450) {
            return ActionResult.None
        }

        Log.i(TAG, "Found suggested post label at $labelRect. Clicking options button at $buttonBounds")
        val clicked = performClick(buttonNode)
        if (clicked) {
            state = State.AWAITING_MENU
            sequenceStartEpochMs = currentEpochMs
            return ActionResult.ClickedMoreOptions(buttonBounds)
        }

        return ActionResult.None
    }

    private fun handleAwaitingMenuState(
        rootNode: AccessibilityNodeInfoCompat,
        currentEpochMs: Long
    ): ActionResult {
        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        var notInterestedNode: AccessibilityNodeInfoCompat? = null

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val text = current.text?.toString()
            val desc = current.contentDescription?.toString()

            // 1. Direct snooze button in menu
            val isDirectSnooze = (text != null && DIRECT_SNOOZE_PATTERN.matcher(text).find()) ||
                (desc != null && DIRECT_SNOOZE_PATTERN.matcher(desc).find())
            if (isDirectSnooze && current.isVisibleToUser) {
                Log.i(TAG, "Matched direct snooze menu item: '${text ?: desc}'. Clicking...")
                val clicked = performClick(current)
                if (clicked) {
                    resetState()
                    lastSuccessfulSnoozeEpochMs = currentEpochMs
                    return ActionResult.SnoozeCompleted(ReelsInterceptEntity.TRIGGER_FEED_AUTO_SNOOZE)
                }
            }

            // 2. 'Not interested' option
            val isNotInterested = (text != null && NOT_INTERESTED_PATTERN.matcher(text).find()) ||
                (desc != null && NOT_INTERESTED_PATTERN.matcher(desc).find())
            if (isNotInterested && current.isVisibleToUser && notInterestedNode == null) {
                notInterestedNode = current
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        if (notInterestedNode != null) {
            val label = notInterestedNode.text?.toString() ?: notInterestedNode.contentDescription?.toString() ?: "Not interested"
            Log.i(TAG, "Matched '$label' menu item. Clicking to advance to snooze confirmation...")
            val clicked = performClick(notInterestedNode)
            if (clicked) {
                state = State.AWAITING_CONFIRMATION
                sequenceStartEpochMs = currentEpochMs // Reset watchdog for confirmation step
                return ActionResult.ClickedNotInterested(label)
            }
        }

        return ActionResult.None
    }

    private fun handleAwaitingConfirmationState(
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

            val isConfirmationSnooze = (text != null && CONFIRMATION_SNOOZE_PATTERN.matcher(text).find()) ||
                (desc != null && CONFIRMATION_SNOOZE_PATTERN.matcher(desc).find())
            if (isConfirmationSnooze && current.isVisibleToUser) {
                Log.i(TAG, "Matched confirmation snooze option: '${text ?: desc}'. Clicking...")
                val clicked = performClick(current)
                if (clicked) {
                    resetState()
                    lastSuccessfulSnoozeEpochMs = currentEpochMs
                    return ActionResult.SnoozeCompleted(ReelsInterceptEntity.TRIGGER_FEED_AUTO_SNOOZE)
                }
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        return ActionResult.None
    }

    private fun checkAndToggleSettingsSnooze(
        rootNode: AccessibilityNodeInfoCompat,
        currentEpochMs: Long
    ): ActionResult {
        val queue = ArrayDeque<AccessibilityNodeInfoCompat>()
        queue.add(rootNode)
        var visitedCount = 0

        var snoozeLabelBounds: Rect? = null
        val toggleCandidates = mutableListOf<Pair<AccessibilityNodeInfoCompat, Rect>>()

        while (queue.isNotEmpty() && visitedCount < MAX_NODE_TRAVERSAL_LIMIT) {
            val current = queue.poll() ?: continue
            visitedCount++

            val text = current.text?.toString()
            val desc = current.contentDescription?.toString()
            val className = current.className?.toString()

            // Check if this node is 'Snooze suggested posts' setting label
            if (snoozeLabelBounds == null) {
                val isSnoozeSetting = (text != null && SETTINGS_SNOOZE_PATTERN.matcher(text).find()) ||
                    (desc != null && SETTINGS_SNOOZE_PATTERN.matcher(desc).find())
                if (isSnoozeSetting && current.isVisibleToUser) {
                    val rect = Rect()
                    current.getBoundsInScreen(rect)
                    snoozeLabelBounds = rect
                }
            }

            // Check if node is a ToggleButton or Switch
            val isToggle = current.isCheckable ||
                (className != null && (className.contains("ToggleButton") || className.contains("Switch")))
            if (isToggle && current.isVisibleToUser) {
                val rect = Rect()
                current.getBoundsInScreen(rect)
                toggleCandidates.add(Pair(current, rect))
            }

            val childCount = current.childCount
            for (i in 0 until childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        val labelRect = snoozeLabelBounds ?: return ActionResult.None
        val matchingTogglePair = toggleCandidates.firstOrNull { (_, toggleRect) ->
            Math.abs(toggleRect.centerY() - labelRect.centerY()) < 150
        } ?: return ActionResult.None

        val (toggleNode, toggleRect) = matchingTogglePair
        if (!toggleNode.isChecked) {
            Log.i(TAG, "Settings snooze toggle is OFF at $toggleRect. Turning ON...")
            val clicked = performClick(toggleNode)
            if (clicked) {
                resetState()
                lastSuccessfulSnoozeEpochMs = currentEpochMs
                return ActionResult.SnoozeCompleted(ReelsInterceptEntity.TRIGGER_SETTINGS_AUTO_SNOOZE)
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
        const val TAG = "InstagramSnoozer"
        private const val MAX_NODE_TRAVERSAL_LIMIT = 150

        val SUGGESTED_LABEL_PATTERN = Pattern.compile(
            "(?i)\\b(suggested for you|suggested post|suggested posts|suggested reels?|because you (follow|liked|watch|saved))\\b"
        )
        val OPTIONS_BUTTON_IDS = setOf(
            "com.instagram.android:id/media_option_button",
            "com.instagram.android:id/row_feed_button_options"
        )
        val OPTIONS_BUTTON_DESC_PATTERN = Pattern.compile(
            "(?i)(more actions for this post|options|more options)"
        )
        val DIRECT_SNOOZE_PATTERN = Pattern.compile(
            "(?i)(snooze all suggested posts in feed for 30 days|snooze suggested posts in feed for 30 days|snooze all suggested posts|snooze suggested posts|hide suggested posts in feed for 30 days)"
        )
        val NOT_INTERESTED_PATTERN = Pattern.compile(
            "(?i)\\bnot interested\\b"
        )
        val CONFIRMATION_SNOOZE_PATTERN = Pattern.compile(
            "(?i)snooze.*(suggested|30\\s*days)"
        )
        val SETTINGS_SNOOZE_PATTERN = Pattern.compile(
            "(?i)snooze suggested posts.*"
        )
    }
}
