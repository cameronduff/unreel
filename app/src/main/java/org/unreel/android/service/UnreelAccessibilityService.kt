package org.unreel.android.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.WindowManager
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
    internal var isDirectInboxActive: Boolean = false

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

    private val settleHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var settleTicksLeft = 0
    private val settleRunnable = object : Runnable {
        override fun run() {
            if (settleTicksLeft <= 0) return
            settleTicksLeft--
            if (currentForegroundActivity?.contains("ModalActivity") == true) return
            val root = resolveRootInActiveWindow()
            if (root == null) {
                settleHandler.postDelayed(this, SETTLE_POLL_MS)
                return
            }
            val scan = InstagramHierarchyScanner.scan(AccessibilityNodeInfoCompat.wrap(root))
            applyOverlayDecision(scan)
            // Keep polling until the bar has settled (or timeout / modal took over).
            if (scan.reelsTabBounds == null && !scan.isModalOpen) {
                settleHandler.postDelayed(this, SETTLE_POLL_MS)
            }
        }
    }

    /** Events stop while Instagram's slide animation runs, so poll briefly to follow the tab bar until it settles. */
    internal fun scheduleSettleRescan() {
        settleTicksLeft = SETTLE_MAX_TICKS
        settleHandler.removeCallbacks(settleRunnable)
        settleHandler.postDelayed(settleRunnable, SETTLE_POLL_MS)
    }

    /**
     * Single source of truth for the blackout box.
     * Hide only on definitive evidence; follow the bar while it slides; otherwise hold the current state.
     */
    private fun applyOverlayDecision(scan: InstagramHierarchyScanner.ScanResult) {
        isDirectInboxActive = scan.isDirectInbox
        if (scan.isModalOpen || scan.isSplashScreenShowing) {
            Log.d(TAG, "Suppression active: isModalOpen=${scan.isModalOpen}, isSplash=${scan.isSplashScreenShowing} -> hiding overlay")
            updateOverlay(false)
        } else if (scan.reelsTabBounds != null) {
            cachedTabBounds = scan.reelsTabBounds
            updateOverlay(true, scan.reelsTabBounds)
        } else if (scan.animatingReelsTabBounds != null) {
            // Bar is sliding: glue the box to the icon's current position so it looks baked into the app.
            updateOverlay(true, scan.animatingReelsTabBounds)
        } else if (scan.isSubscreenOrComposer) {
            Log.d(TAG, "Hard subscreen/composer -> hiding overlay")
            updateOverlay(false)
        } else {
            // Nothing new to learn this frame (tree not ready): hold whatever is currently shown.
            val fallbackBounds = cachedTabBounds
            if (lastOverlayState != true && fallbackBounds != null && currentForegroundActivity?.contains("ModalActivity") != true) {
                updateOverlay(true, fallbackBounds)
            }
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
            Log.d(TAG, "Foreground package is $pkg (not Instagram), hiding overlay")
            updateOverlay(false)
            currentForegroundActivity = null
            isDirectInboxActive = false
            return
        }

        if (!isFilteringActive()) {
            updateOverlay(false)
            return
        }

        // Fast-Path 1: Instant Activity/Dialog-level detachment (0ms Binder IPC overhead)
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            val previousActivity = currentForegroundActivity
            if (className.isNotEmpty()) {
                currentForegroundActivity = className
            }
            if (className.contains("ModalActivity") ||
                className.contains("Dialog", ignoreCase = true) ||
                className.contains("BottomSheet", ignoreCase = true) ||
                className.contains("ActionSheet", ignoreCase = true)) {
                Log.d(TAG, "Fast-path: Entered modal/dialog screen ($className) -> immediate overlay detachment (0ms)")
                updateOverlay(false)
                return
            }

            // Instant pre-emptive attachment on a cold launch; on return from a modal just start following the bar.
            if (className.contains("InstagramMainActivity") || className.contains("MainActivity")) {
                val isReturningFromModal = previousActivity?.contains("ModalActivity") == true ||
                                           previousActivity == "returning-to-main" ||
                                           previousActivity?.contains("Dialog", ignoreCase = true) == true ||
                                           previousActivity?.contains("BottomSheet", ignoreCase = true) == true
                val isColdLaunch = previousActivity == null || (previousActivity.contains("Instagram") != true && previousActivity != "returning-to-main")

                if (isReturningFromModal) {
                    scheduleSettleRescan()
                } else if (isColdLaunch) {
                    val targetBounds = resolveDefaultOrCachedTabBounds()
                    if (targetBounds != null) {
                        Log.d(TAG, "Fast-path: Launch to InstagramMainActivity ($className) -> immediate pre-emptive attachment (0ms) at $targetBounds")
                        updateOverlay(true, targetBounds)
                    }
                }
            }
        }

        // Fast-Path 2: Instant Pre-Emptive Detachment or Restoration on Navigation Clicks (0ms)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_SELECTED) {
            val navResult = checkNavigationalClick(event)
            if (navResult == NavClickResult.DETACH) {
                Log.d(TAG, "Fast-path 2: Navigation to subscreen detected -> immediate overlay detachment (0ms)")
                updateOverlay(false)
                return
            } else if (navResult == NavClickResult.RESTORE) {
                // Painting the box now would cover the chat that is still sliding out (the tab bar is not on screen
                // yet). Instead: drop the stale modal marker and follow the tab bar from the first frame it exists.
                Log.d(TAG, "Fast-path 2: Back detected -> tracking tab bar until settled")
                if (currentForegroundActivity?.contains("ModalActivity") == true) {
                    currentForegroundActivity = "returning-to-main"
                }
                scheduleSettleRescan()
                return
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
            // Window is mid-transition (no tree yet). Never touch the overlay here: hiding on a null root is what
            // made it drop out for ~1s on launch / on Back. The next event with a tree will decide.
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
        applyOverlayDecision(scan)
        if (scan.reelsTabBounds == null && scan.animatingReelsTabBounds != null) {
            // Bar is mid-slide and Android will not emit further events until the next change: keep following it.
            scheduleSettleRescan()
        }

        // 5. Auto-snooze suggested posts in feed (every 30 days)
        if (cachedPreferences.isAutoSnoozeEnabled && !scan.isDirectThreadActive) {
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
        if (cachedPreferences.isFeedAdShieldEnabled && !scan.isDirectThreadActive) {
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
        if (cachedPreferences.isStoryAdShieldEnabled && !scan.isDirectThreadActive) {
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

    private fun isMicroFeedInteraction(viewId: String, desc: String): Boolean {
        val id = viewId.lowercase()
        val d = desc.lowercase()
        return id.contains("like") || d.contains("like") ||
               id.contains("save") || d.contains("save") ||
               id.contains("audio") || id.contains("sound") || d.contains("mute")
    }

    enum class NavClickResult {
        NONE,
        DETACH,
        RESTORE
    }

    internal fun resolveDefaultOrCachedTabBounds(): android.graphics.Rect? {
        val cached = cachedTabBounds
        if (cached != null) {
            return cached
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val realMetrics = android.util.DisplayMetrics()
        wm?.defaultDisplay?.getRealMetrics(realMetrics)
        val width = if (realMetrics.widthPixels > 0) realMetrics.widthPixels else resources.displayMetrics.widthPixels
        val height = if (realMetrics.heightPixels > 0) realMetrics.heightPixels else resources.displayMetrics.heightPixels
        if (width > 0 && height > 0) {
            val navBarHeight = Math.round(height * 0.05641).toInt().coerceIn(120, 160)
            val bottomInset = Math.round(height * 0.02821).toInt().coerceIn(0, 80)
            val tabWidth = width / 5
            val left = tabWidth * 1
            val right = tabWidth * 2
            val bottom = height - bottomInset
            val top = bottom - navBarHeight
            val calculated = android.graphics.Rect(left, top, right, bottom)
            cachedTabBounds = calculated
            return calculated
        }
        return null
    }

    internal fun checkNavigationalClick(event: AccessibilityEvent): NavClickResult {
        if (event.eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SELECTED) return NavClickResult.NONE

        val source = event.source ?: resolveRootInActiveWindow() ?: return NavClickResult.NONE
        val node = AccessibilityNodeInfoCompat.wrap(source)

        val desc = (node.contentDescription ?: event.contentDescription)?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""

        // 1. Navigation Back, Close, or Navigate Up buttons -> RESTORE main feed overlay immediately!
        if (desc.equals("Back", ignoreCase = true) ||
            desc.equals("Close", ignoreCase = true) ||
            desc.equals("Navigate up", ignoreCase = true) ||
            text.equals("Back", ignoreCase = true) ||
            text.equals("Close", ignoreCase = true) ||
            viewId.contains("action_bar_button_back", ignoreCase = true) ||
            viewId.contains("action_bar_back_button", ignoreCase = true) ||
            viewId.contains("action_bar_left_button", ignoreCase = true) ||
            viewId.contains("header_left_button", ignoreCase = true)
        ) {
            return NavClickResult.RESTORE
        }

        val clickBounds = android.graphics.Rect()
        node.getBoundsInScreen(clickBounds)

        // Reject full-window root containers to prevent false matches
        if (event.source == null && clickBounds.height() > 1800 && clickBounds.width() > 900) {
            return NavClickResult.NONE
        }

        // 2. Bottom navigation bar tabs - maintain bottom bar state across tab switches
        val tabTop = cachedTabBounds?.top ?: 2100
        val isBottomNavClick = (viewId.contains("feed_tab", ignoreCase = true) ||
            viewId.contains("clips_tab", ignoreCase = true) ||
            viewId.contains("direct_tab", ignoreCase = true) ||
            viewId.contains("search_tab", ignoreCase = true) ||
            viewId.contains("profile_tab", ignoreCase = true) ||
            viewId.contains("tab_bar", ignoreCase = true) ||
            viewId.contains("tab_icon", ignoreCase = true) ||
            (clickBounds.top >= (tabTop - 30) && clickBounds.height() > 0))

        if (isBottomNavClick) {
            return NavClickResult.NONE
        }

        // 3. In Direct Messages inbox: ANY tap outside the bottom navigation bar navigates to a chat or subscreen
        if (isDirectInboxActive) {
            Log.d(TAG, "Fast-path 2: Direct inbox tap outside bottom bar detected -> immediate overlay detachment (0ms)")
            return NavClickResult.DETACH
        }

        // 4. Ancestor check: Detect clicks inside inbox list (e.g. anonymous Compose/Litho thread rows)
        var isInsideInboxList = false
        var curr: AccessibilityNodeInfoCompat? = node
        var depth = 0
        while (curr != null && depth < 5) {
            val pId = curr.viewIdResourceName ?: ""
            if (pId.contains("inbox_refreshable_thread", ignoreCase = true) ||
                pId.contains("row_inbox", ignoreCase = true) ||
                pId.contains("direct_inbox", ignoreCase = true) ||
                pId.contains("thread_container", ignoreCase = true)) {
                isInsideInboxList = true
                break
            }
            curr = curr.parent
            depth++
        }

        // If clicking inside inbox thread list in the content area (above bottom bar, below header)
        if (isInsideInboxList && clickBounds.top > 250 && clickBounds.top < (tabTop - 30)) {
            return NavClickResult.DETACH
        }

        // 4. Explicit Subscreen / Options Navigation Triggers -> DETACH overlay!
        val isOptionsClick = desc.equals("Options", ignoreCase = true) ||
            viewId.contains("action_bar_button_options", ignoreCase = true) ||
            viewId.contains("options_button", ignoreCase = true) ||
            viewId.contains("menu_button", ignoreCase = true) ||
            viewId.contains("action_bar_overflow_icon", ignoreCase = true)

        val isCommentClick = desc.contains("Comment", ignoreCase = true) ||
            viewId.contains("action_bar_button_comment", ignoreCase = true) ||
            viewId.contains("button_comment", ignoreCase = true) ||
            viewId.contains("row_feed_button_comment", ignoreCase = true)

        val isDirectChatClick = viewId.contains("row_inbox", ignoreCase = true) ||
            viewId.contains("thread", ignoreCase = true) ||
            viewId.contains("inbox", ignoreCase = true) ||
            viewId.contains("pog_root_view", ignoreCase = true) ||
            viewId.contains("avatar", ignoreCase = true)

        val isHeaderDirectClick = desc.equals("Message", ignoreCase = true) && clickBounds.top < 400

        if (isOptionsClick || isCommentClick || isDirectChatClick || isHeaderDirectClick) {
            return NavClickResult.DETACH
        }

        // Standard feed interactions (photos, carousels, captions, likes, bookmarks) never detach overlay
        return NavClickResult.NONE
    }

    internal fun isNavigationalClick(event: AccessibilityEvent): Boolean {
        return checkNavigationalClick(event) == NavClickResult.DETACH
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
        private const val SETTLE_POLL_MS = 40L
        private const val SETTLE_MAX_TICKS = 40 // ~1.6s upper bound
    }
}
