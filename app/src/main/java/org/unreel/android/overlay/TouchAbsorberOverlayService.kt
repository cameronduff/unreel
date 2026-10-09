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
    internal var currentBounds: Rect? = null
    internal var isOverlayAttached: Boolean = false
        private set

    internal val windowManager: WindowManager
        get() = windowManagerOverride ?: (getSystemService(Context.WINDOW_SERVICE) as WindowManager)

    private fun canDrawOverlays(): Boolean {
        return canDrawOverlaysCheck?.invoke() ?: Settings.canDrawOverlays(this)
    }

    fun attachOverlay(targetRect: Rect? = null): Boolean {
        if (!canDrawOverlays()) {
            return false
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.coerceAtLeast(1)
        val screenHeight = displayMetrics.heightPixels.coerceAtLeast(1)

        val rect = targetRect
            ?: boundsCalculator?.invoke(screenWidth, screenHeight)

        if (rect == null) {
            android.util.Log.w("TouchAbsorber", "attachOverlay requested without target bounds or calculator; skipping attachment")
            return false
        }

        android.util.Log.i("TouchAbsorber", "attachOverlay requested: targetRect=$targetRect, calculated=$rect, isAttached=$isOverlayAttached")

        if (isOverlayAttached && overlayView != null) {
            overlayView?.visibility = View.VISIBLE
            if (currentBounds == rect) {
                return true
            }
            currentBounds = rect
            try {
                val params = overlayView!!.layoutParams as WindowManager.LayoutParams
                params.x = rect.left
                params.y = rect.top
                params.width = rect.width()
                params.height = rect.height()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    params.fitInsetsTypes = 0
                }
                windowManager.updateViewLayout(overlayView, params)
                android.util.Log.i("TouchAbsorber", "Updated overlay layout to $rect")
                return true
            } catch (e: Exception) {
                android.util.Log.e("TouchAbsorber", "Failed to update overlay view layout", e)
                return false
            }
        }

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }

        val view = View(this).apply {
            setBackgroundColor(Color.parseColor("#0D1014"))
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    android.util.Log.w("TouchAbsorber", "Absorbed touch on Reels tab position!")
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
            currentBounds = rect
            isOverlayAttached = true
            android.util.Log.i("TouchAbsorber", "Successfully attached blackout overlay at $rect")
            return true
        } catch (e: Exception) {
            android.util.Log.e("TouchAbsorber", "Failed to add overlay view to WindowManager", e)
            return false
        }
    }

    fun detachOverlay() {
        android.util.Log.i("TouchAbsorber", "detachOverlay called: isAttached=$isOverlayAttached")
        if (isOverlayAttached && overlayView != null) {
            try {
                overlayView?.visibility = View.GONE
                windowManager.removeView(overlayView)
                android.util.Log.i("TouchAbsorber", "Overlay view removed from WindowManager")
            } catch (e: Exception) {
                android.util.Log.w("TouchAbsorber", "View might already be detached", e)
            } finally {
                overlayView = null
                currentBounds = null
                isOverlayAttached = false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        android.util.Log.i("TouchAbsorber", "onStartCommand: action=$action")
        if (action == ACTION_DETACH) {
            detachOverlay()
        } else {
            val bounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(EXTRA_BOUNDS, Rect::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(EXTRA_BOUNDS)
            }
            attachOverlay(bounds)
        }
        return START_NOT_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        detachOverlay()
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun dump(fd: java.io.FileDescriptor?, writer: java.io.PrintWriter?, args: Array<out String>?) {
        super.dump(fd, writer, args)
        writer?.println("TouchAbsorberOverlayService Status:")
        writer?.println("  isOverlayAttached=$isOverlayAttached")
        writer?.println("  currentBounds=$currentBounds")
        writer?.println("  overlayViewVisibility=${overlayView?.visibility}")
    }

    companion object {
        const val ACTION_ATTACH = "org.unreel.android.overlay.ATTACH"
        const val ACTION_DETACH = "org.unreel.android.overlay.DETACH"
        const val EXTRA_BOUNDS = "extra_bounds"

        @Volatile
        internal var instance: TouchAbsorberOverlayService? = null

        fun show(context: Context, bounds: Rect? = null) {
            if (bounds == null) {
                android.util.Log.w("TouchAbsorber", "Cannot show overlay without explicit target bounds")
                return
            }
            val active = instance
            if (active != null) {
                active.attachOverlay(bounds)
                return
            }
            try {
                val intent = Intent(context, TouchAbsorberOverlayService::class.java).apply {
                    action = ACTION_ATTACH
                    putExtra(EXTRA_BOUNDS, bounds)
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun hide(context: Context) {
            val active = instance
            if (active != null) {
                active.detachOverlay()
                return
            }
            try {
                val intent = Intent(context, TouchAbsorberOverlayService::class.java).apply {
                    action = ACTION_DETACH
                }
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
