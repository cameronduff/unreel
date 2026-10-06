package org.unreel.android.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.unreel.android.engine.DebouncedBackDispatcher
import org.unreel.android.engine.InstagramBottomNavDetector
import org.unreel.android.engine.InstagramClipsDetector

class UnreelAccessibilityService : AccessibilityService() {

    internal var backDispatcher = DebouncedBackDispatcher(this)
    internal var onReelsIntercepted: (() -> Unit)? = null
    internal var rootNodeProvider: (() -> AccessibilityNodeInfo?)? = null

    internal fun resolveRootInActiveWindow(): AccessibilityNodeInfo? {
        return rootNodeProvider?.invoke() ?: rootInActiveWindow
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()
        if (pkg != TARGET_INSTAGRAM_PACKAGE) return

        val root = resolveRootInActiveWindow() ?: return
        val rootCompat = AccessibilityNodeInfoCompat.wrap(root)

        val isClipsVisible = InstagramClipsDetector.isClipsContainerVisible(rootCompat)
        val isReelsTabSelected = InstagramBottomNavDetector.isReelsTabSelected(rootCompat)

        if (isClipsVisible || isReelsTabSelected) {
            val dispatched = backDispatcher.dispatchBack()
            if (dispatched) {
                onReelsIntercepted?.invoke()
            }
        }
    }

    override fun onInterrupt() {
        // Handle accessibility interruption
    }

    companion object {
        const val TARGET_INSTAGRAM_PACKAGE = "com.instagram.android"
    }
}
