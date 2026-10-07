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
import org.unreel.android.engine.InstagramModalDetector
import org.unreel.android.engine.InstagramSuggestedPostSnoozer
import org.unreel.android.overlay.TouchAbsorberOverlayService

class UnreelAccessibilityService : AccessibilityService() {

    internal var backDispatcher = DebouncedBackDispatcher(this)
    internal var suggestedPostSnoozer = InstagramSuggestedPostSnoozer()
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
            }
            return
        }

        if (!isFilteringActive()) {
            updateOverlay(false)
            return
        }

        val root = resolveRootInActiveWindow()
        val rootCompat = if (root != null) AccessibilityNodeInfoCompat.wrap(root) else null

        if (rootCompat == null) return

        // 3. PRIORITY 0: Emergency Reels Interception
        // If the user lands on a Reel or selects the Reels tab, suppress IMMEDIATELY via Back action
        // before spending cycles on overlay/modal maintenance or IPC calls.
        val isClipsVisible = InstagramClipsDetector.isClipsContainerVisible(rootCompat)
        val isReelsTabSelected = InstagramBottomNavDetector.isReelsTabSelected(rootCompat)

        if (isClipsVisible || isReelsTabSelected) {
            val trigger = if (isClipsVisible) {
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
        // Maintain blackout touch-absorber overlay over the Reels tab button
        // Check if any modal dialog, daily limit prompt, bottom sheet, or splash screen is active
        val isSplashShowing = if (lastOverlayBounds == null || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            InstagramBottomNavDetector.isSplashScreenShowing(rootCompat)
        } else {
            false
        }
        val isModalOpen = InstagramModalDetector.isModalOrDialogPresent(rootCompat)
        val reelsBounds = if (!isModalOpen && !isSplashShowing) InstagramBottomNavDetector.findReelsTabBounds(rootCompat) else null
        Log.d(TAG, "Instagram active in foreground: isSplash=$isSplashShowing, isModalOpen=$isModalOpen, reelsBounds=$reelsBounds")

        if (reelsBounds != null) {
            updateOverlay(true, reelsBounds)
        } else {
            updateOverlay(false)
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
