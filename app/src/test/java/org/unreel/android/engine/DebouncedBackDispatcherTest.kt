package org.unreel.android.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class DebouncedBackDispatcherTest {

    @Test
    fun testFirstDispatchInvokesAction() {
        var callCount = 0
        val dispatcher = DebouncedBackDispatcher(
            backActionExecutor = {
                callCount++
                true
            }
        )

        val result = dispatcher.dispatchBack(currentEpochMs = 1000L)
        assertTrue(result)
        assertEquals(1, callCount)
    }

    @Test
    fun testRapidSuccessiveCallsSuppressedWithinDebounceWindow() {
        var callCount = 0
        val dispatcher = DebouncedBackDispatcher(
            backActionExecutor = {
                callCount++
                true
            },
            debounceWindowMs = 300L
        )

        assertTrue(dispatcher.dispatchBack(currentEpochMs = 1000L))
        assertFalse(dispatcher.dispatchBack(currentEpochMs = 1100L))
        assertFalse(dispatcher.dispatchBack(currentEpochMs = 1250L))
        assertEquals(1, callCount)
    }

    @Test
    fun testDispatchAllowedAfterDebounceWindowElapses() {
        var callCount = 0
        val dispatcher = DebouncedBackDispatcher(
            backActionExecutor = {
                callCount++
                true
            },
            debounceWindowMs = 300L
        )

        assertTrue(dispatcher.dispatchBack(currentEpochMs = 1000L))
        assertTrue(dispatcher.dispatchBack(currentEpochMs = 1350L))
        assertEquals(2, callCount)
    }

    @Test
    fun testCustomDebounceThreshold() {
        var callCount = 0
        val dispatcher = DebouncedBackDispatcher(
            backActionExecutor = {
                callCount++
                true
            },
            debounceWindowMs = 500L
        )

        assertTrue(dispatcher.dispatchBack(currentEpochMs = 1000L))
        assertFalse(dispatcher.dispatchBack(currentEpochMs = 1400L))
        assertTrue(dispatcher.dispatchBack(currentEpochMs = 1550L))
        assertEquals(2, callCount)
    }

    @Test
    fun testThreadSafeConcurrentExecution() {
        val callCount = AtomicInteger(0)
        val dispatcher = DebouncedBackDispatcher(
            backActionExecutor = {
                callCount.incrementAndGet()
                true
            },
            debounceWindowMs = 1000L
        )

        val threadCount = 50
        val latch = CountDownLatch(threadCount)
        val successfulDispatches = AtomicInteger(0)

        for (i in 0 until threadCount) {
            Thread {
                if (dispatcher.dispatchBack(currentEpochMs = 5000L)) {
                    successfulDispatches.incrementAndGet()
                }
                latch.countDown()
            }.start()
        }

        latch.await()
        assertEquals(1, successfulDispatches.get())
        assertEquals(1, callCount.get())
    }
}
