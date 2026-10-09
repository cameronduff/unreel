package org.unreel.android.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.unreel.android.data.FilterPreferences
import org.unreel.android.data.FilterPreferencesRepository
import org.unreel.android.data.ReelsInterceptDao
import org.unreel.android.data.ReelsInterceptEntity
import org.unreel.android.data.UnreelDatabase
import org.unreel.android.engine.DebouncedBackDispatcher
import org.unreel.android.engine.InstagramBottomNavDetector
import org.unreel.android.engine.InstagramClipsDetector
import org.unreel.android.engine.InstagramFeedAdShield
import org.unreel.android.engine.InstagramHierarchyScanner
import org.unreel.android.engine.InstagramModalDetector
import org.unreel.android.engine.InstagramStoryAdDetector
import org.unreel.android.engine.InstagramSuggestedPostSnoozer
import org.unreel.android.overlay.TouchAbsorberOverlayService

class UnreelAccessibilityService : AccessibilityService() {

    internal var currentForegroundActivity: String? = null
    internal var cachedTabBounds: android.graphics.Rect? = null

    internal var backDispatcher = DebouncedBackDispatcher(this)
    internal var suggestedPostSnoozer = InstagramSuggestedPostSnoozer()
    internal var feedAdShield = InstagramFeedAdShield()
    internal var storyAdDetector = InstagramStoryAdDetector()
    internal var gestureDispatcher: ((x: Float, y: Float) -> Boolean)? = null
    internal var onReelsIntercepted: (() -> Unit)? = null
    internal var rootNodeProvider: (() -> AccessibilityNodeInfo?)? = null
    internal var serviceScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    internal var daoProvider: (() -> ReelsInterceptDao)? = null
    internal var repositoryProvider: (() -> FilterPreferencesRepository)? = null
    internal var isFilterActiveProvider: (() -> Boolean)? = null
    internal var overlayController: ((show: Boolean, bounds: android.graphics.Rect?) -> Unit)? = null

    private var cachedPreferences: FilterPreferences = FilterPreferences()
    private var lastOverlayState: Boolean? = null
    private var lastOverlayBounds: android.graphics.Rect? = null

    private fun updateOverlay(show: Boolean, bounds: android.graphics.Rect? = null) {
        if (lastOverlayState == show && lastOverlayBounds == bounds) {
            return
        }
        lastOverlayState = show
        lastOverlayBounds = bounds

        if (overlayController != null) {
            overlayController?.invoke(show, bounds)
            return
        }

        if (show) {
            TouchAbsorberOverlayService.show(this, bounds)
        } else {
            TouchAbsorberOverlayService.hide(this)
        }
    }


    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Unreel Accessibility Service connected and ready")
        val repo = repositoryProvider?.invoke() ?: FilterPreferencesRepository.getInstance(this)
        serviceScope.launch {
            repo.filterPreferences.collect { prefs ->
                cachedPreferences = prefs
                suggestedPostSnoozer.lastSuccessfulSnoozeEpochMs = prefs.lastAutoSnoozeEpochMs
                Log.d(TAG, "Preferences updated: filterEnabled=${prefs.isReelsFilterEnabled}, autoSnooze=${prefs.isAutoSnoozeEnabled}, pauseUntil=${prefs.pauseUntilEpochMs}")
            }
        }
    }

    internal fun isFilteringActive(): Boolean {
        return isFilterActiveProvider?.invoke()
            ?: cachedPreferences.isFilteringActive(System.currentTimeMillis())
    }

    internal fun resolveRootInActiveWindow(): AccessibilityNodeInfo? {
        return rootNodeProvider?.invoke() ?: rootInActiveWindow
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()

        // 1. Never detach or process events from our own overlay window or SystemUI
        if (pkg == null || pkg == "org.unreel.android" || pkg == "com.android.systemui") {
            return
        }

        // 2. Hide overlay when switching away from Instagram to another app
        if (pkg != TARGET_INSTAGRAM_PACKAGE) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                Log.d(TAG, "Navigated away from Instagram to $pkg, hiding overlay")
                updateOverlay(false)
                currentForegroundActivity = null
            }
            return
        }

        if (!isFilteringActive()) {
            updateOverlay(false)
            return
        }

        // Fast-Path 1 & 2: Instant Activity-level event evaluation (0ms Binder IPC overhead)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            if (className.isNotEmpty()) {
                currentForegroundActivity = className
            }
            // Fast-Path 1: Entering ModalActivity (DMs or modal screens) -> instant detachment (0ms)
            if (className.contains("ModalActivity")) {
                Log.d(TAG, "Fast-path: Entered ModalActivity ($className) -> immediate overlay detachment (0ms)")
                updateOverlay(false)
                return
            }
            // Fast-Path 2: Returning to MainActivity with known tab bounds -> instant re-attachment (0ms)
            if (className.contains("InstagramMainActivity") && cachedTabBounds != null) {
                Log.d(TAG, "Fast-path: Returned to InstagramMainActivity with cached bounds $cachedTabBounds -> immediate re-attachment (0ms)")
                updateOverlay(true, cachedTabBounds)
            }
        }

        // Fast-Path 3: While active in ModalActivity (e.g. typing or scrolling inside DMs), skip all traversals
        if (currentForegroundActivity?.contains("ModalActivity") == true) {
            updateOverlay(false)
            return
        }

        val root = resolveRootInActiveWindow()
        val rootCompat = if (root != null) AccessibilityNodeInfoCompat.wrap(root) else null

        if (rootCompat == null) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                updateOverlay(false)
            }
            return
        }

        // Single-Pass Hierarchy Scan (Consolidates Clips, Reels Tab, Direct Thread, Modals, Splash & Bounds)
        val scan = InstagramHierarchyScanner.scan(rootCompat)

        // 3. PRIORITY 0: Emergency Reels Interception
        // If the user lands on a Reel or selects the Reels tab, suppress IMMEDIATELY via Back action
        if (scan.isClipsVisible || scan.isReelsTabSelected) {
            val trigger = if (scan.isClipsVisible) {
                ReelsInterceptEntity.TRIGGER_FULLSCREEN_CLIPS
            } else {
                ReelsInterceptEntity.TRIGGER_BOTTOM_NAV_TAB
            }
            Log.w(TAG, "REELS DETECTED ($trigger)! Suppressing via Back Action...")
            val dispatched = backDispatcher.dispatchBack()
            if (dispatched) {
                Log.i(TAG, "Successfully dispatched Back Action to eliminate Reels")
                onReelsIntercepted?.invoke()
                recordIntercept(trigger)
            }
            return
        }

        // 4. PRIORITY 1: Bottom Navigation Blackout Touch-Absorber Overlay
        // If user is inside a direct message conversation, hide overlay immediately
        if (scan.isDirectThreadActive) {
            Log.d(TAG, "Direct thread active -> hiding overlay and short-circuiting feed processing")
            updateOverlay(false)
            return
        }

        if (scan.isModalOpen || scan.isSplashScreenShowing) {
            Log.d(TAG, "Suppression active: isModalOpen=${scan.isModalOpen}, isSplash=${scan.isSplashScreenShowing} -> hiding overlay")
            updateOverlay(false)
        } else {
            val reelsBounds = scan.reelsTabBounds
            Log.d(TAG, "Instagram active in foreground: reelsBounds=$reelsBounds (visited ${scan.nodesVisited} nodes)")

            if (reelsBounds != null) {
                cachedTabBounds = reelsBounds
                updateOverlay(true, reelsBounds)
            } else {
                updateOverlay(false)
            }
        }

        // 5. Auto-snooze suggested posts in feed (every 30 days)
        if (cachedPreferences.isAutoSnoozeEnabled) {
            val snoozeResult = suggestedPostSnoozer.processHierarchy(rootCompat)
            when (snoozeResult) {
                is InstagramSuggestedPostSnoozer.ActionResult.SnoozeCompleted -> {
                    Log.i(TAG, "Auto-snooze completed successfully (${snoozeResult.trigger})")
                    recordIntercept(snoozeResult.trigger)
                    val repo = repositoryProvider?.invoke() ?: FilterPreferencesRepository.getInstance(this)
                    serviceScope.launch {
                        repo.recordAutoSnoozeTimestamp()
                    }
                    if (snoozeResult.trigger == ReelsInterceptEntity.TRIGGER_SETTINGS_AUTO_SNOOZE) {
                        backDispatcher.dispatchBack()
                    }
                }
                is InstagramSuggestedPostSnoozer.ActionResult.ClickedMoreOptions -> {
                    Log.d(TAG, "Auto-snooze: clicked post options button at ${snoozeResult.bounds}")
                }
                is InstagramSuggestedPostSnoozer.ActionResult.ClickedNotInterested -> {
                    Log.d(TAG, "Auto-snooze: clicked Not interested option (${snoozeResult.text})")
                }
                is InstagramSuggestedPostSnoozer.ActionResult.TimeoutReset -> {
                    Log.w(TAG, "Auto-snooze: sequence timed out, backed off")
                }
                is InstagramSuggestedPostSnoozer.ActionResult.None -> {
                    // Idle or cooldown active
                }
            }
        }

        // 6. Instagram AdShield: In-Feed Sponsored Post Auto-Hider
        if (cachedPreferences.isFeedAdShieldEnabled) {
            val adResult = feedAdShield.processHierarchy(rootCompat)
            when (adResult) {
                is InstagramFeedAdShield.ActionResult.AdHidden -> {
                    Log.i(TAG, "AdShield: Sponsored post hidden successfully (${adResult.trigger})")
                    recordIntercept(adResult.trigger)
                }
                is InstagramFeedAdShield.ActionResult.ClickedOptions -> {
                    Log.d(TAG, "AdShield: Clicked sponsored post options button at ${adResult.bounds}")
                }
                is InstagramFeedAdShield.ActionResult.ClickedHideAd -> {
                    Log.d(TAG, "AdShield: Clicked '${adResult.text}' in options menu")
                }
                is InstagramFeedAdShield.ActionResult.TimeoutReset -> {
                    Log.w(TAG, "AdShield: Sequence timed out, backed off to IDLE")
                }
                is InstagramFeedAdShield.ActionResult.None -> {
                    // Idle or cooldown
                }
            }
        }

        // 7. Instagram AdShield: Story Ads Auto-Fast-Forward
        if (cachedPreferences.isStoryAdShieldEnabled) {
            val storyResult = storyAdDetector.detectAndSkipAd(rootCompat)
            when (storyResult) {
                is InstagramStoryAdDetector.ActionResult.SkipAd -> {
                    Log.i(TAG, "AdShield: Skipping sponsored story (${storyResult.adIdentifier})")
                    val scrolled = storyResult.scrollableNode?.performAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) == true
                    if (!scrolled) {
                        dispatchTapGesture(storyResult.tapPoint.x.toFloat(), storyResult.tapPoint.y.toFloat())
                    }
                    recordIntercept(ReelsInterceptEntity.TRIGGER_STORY_AD_SHIELD)
                }
                is InstagramStoryAdDetector.ActionResult.InCooldown,
                is InstagramStoryAdDetector.ActionResult.OrganicStory,
                is InstagramStoryAdDetector.ActionResult.NotInStoryViewer -> {
                    // Normal story viewing or debounced
                }
            }
        }
    }

    internal fun dispatchTapGesture(x: Float, y: Float): Boolean {
        val custom = gestureDispatcher
        if (custom != null) {
            return custom(x, y)
        }
        return try {
            val path = android.graphics.Path().apply {
                moveTo(x, y)
            }
            val gesture = android.accessibilityservice.GestureDescription.Builder()
                .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 50))
                .build()
            dispatchGesture(gesture, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dispatch tap gesture at ($x, $y)", e)
            false
        }
    }

    private fun recordIntercept(trigger: String) {
        serviceScope.launch {
            try {
                val dao = daoProvider?.invoke()
                    ?: UnreelDatabase.getInstance(this@UnreelAccessibilityService).reelsInterceptDao()
                dao.insertIntercept(
                    ReelsInterceptEntity(
                        timestampEpochMs = System.currentTimeMillis(),
                        triggerType = trigger
                    )
                )
                Log.d(TAG, "Recorded intercept telemetry: $trigger")
            } catch (e: Exception) {
                Log.e(TAG, "Error recording intercept telemetry", e)
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Unreel Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        updateOverlay(false)
        serviceScope.cancel()
        Log.i(TAG, "Unreel Accessibility Service destroyed")
    }

    companion object {
        const val TAG = "UnreelService"
        const val TARGET_INSTAGRAM_PACKAGE = "com.instagram.android"
    }
}
