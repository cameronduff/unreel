package org.unreel.android.overlay

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

class TouchAbsorberOverlayService : Service() {

    internal var canDrawOverlaysCheck: (() -> Boolean)? = null
    internal var windowManagerOverride: WindowManager? = null
    internal var boundsCalculator: ((screenWidth: Int, screenHeight: Int) -> Rect)? = null
    internal var onTouchAbsorbed: (() -> Unit)? = null

    internal var overlayView: View? = null
    internal var isOverlayAttached: Boolean = false
        private set

    internal val windowManager: WindowManager
        get() = windowManagerOverride ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)

    private fun canDrawOverlays(): Boolean {
        return canDrawOverlaysCheck?.invoke() ?: Settings.canDrawOverlays(this)
    }

    fun attachOverlay(): Boolean {
        if (!canDrawOverlays()) {
            return false
        }
        if (isOverlayAttached) {
            return true
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.coerceAtLeast(1)
        val screenHeight = displayMetrics.heightPixels.coerceAtLeast(1)
        val navBarHeight = (56 * displayMetrics.density).toInt().coerceAtLeast(1)

        val rect = boundsCalculator?.invoke(screenWidth, screenHeight)
            ?: OverlayPositionCalculator.calculateTabBounds(
                screenWidthPx = screenWidth,
                screenHeightPx = screenHeight,
                navBarHeightPx = navBarHeight
            )

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            rect.width(),
            rect.height(),
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
        }

        val view = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    onTouchAbsorbed?.invoke()
                    true
                } else {
                    true
                }
            }
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
            isOverlayAttached = true
            return true
        } catch (_: Exception) {
            return false
        }
    }

    fun detachOverlay() {
        if (isOverlayAttached && overlayView != null) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) {
                // View might already be detached
            } finally {
                overlayView = null
                isOverlayAttached = false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        attachOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        detachOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
