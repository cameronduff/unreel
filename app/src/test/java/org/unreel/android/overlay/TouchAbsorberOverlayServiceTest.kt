package org.unreel.android.overlay

import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TouchAbsorberOverlayServiceTest {

    private lateinit var service: TouchAbsorberOverlayService

    @Before
    fun setUp() {
        service = Robolectric.buildService(TouchAbsorberOverlayService::class.java).create().get()
    }

    @After
    fun tearDown() {
        service.onDestroy()
    }

    @Test
    fun testAttachOverlayFailsWhenPermissionDenied() {
        service.canDrawOverlaysCheck = { false }

        val result = service.attachOverlay()

        assertFalse(result)
        assertFalse(service.isOverlayAttached)
        assertNull(service.overlayView)
    }

    @Test
    fun testAttachOverlaySucceedsAndSetsLayoutParams() {
        service.canDrawOverlaysCheck = { true }
        val testBounds = Rect(648, 2250, 864, 2400)
        service.boundsCalculator = { _, _ -> testBounds }

        val result = service.attachOverlay()

        assertTrue(result)
        assertTrue(service.isOverlayAttached)
        val view = service.overlayView
        assertNotNull(view)

        val params = view!!.layoutParams as WindowManager.LayoutParams
        // Verify FLAG_NOT_FOCUSABLE is set
        assertTrue(
            "FLAG_NOT_FOCUSABLE must be present",
            (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0
        )
        // Verify TYPE_APPLICATION_OVERLAY
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
        assertEquals(testBounds.width(), params.width)
        assertEquals(testBounds.height(), params.height)
        assertEquals(testBounds.left, params.x)
        assertEquals(testBounds.top, params.y)
    }

    @Test
    fun testTouchSinkConsumesActionDown() {
        service.canDrawOverlaysCheck = { true }
        var callbackCalled = false
        service.onTouchAbsorbed = { callbackCalled = true }
        service.attachOverlay()

        val view = service.overlayView!!
        val now = SystemClock.uptimeMillis()
        val downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 10f, 10f, 0)
        val consumed = view.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        assertTrue("Action DOWN must be consumed by touch sink", consumed)
        assertTrue("Callback should be invoked on touch absorption", callbackCalled)
    }

    @Test
    fun testOnDestroyRemovesViewAndCleansUp() {
        service.canDrawOverlaysCheck = { true }
        service.attachOverlay()
        assertTrue(service.isOverlayAttached)
        assertNotNull(service.overlayView)

        service.onDestroy()

        assertFalse(service.isOverlayAttached)
        assertNull(service.overlayView)
    }
}
