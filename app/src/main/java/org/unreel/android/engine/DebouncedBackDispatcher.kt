package org.unreel.android.engine

import android.accessibilityservice.AccessibilityService
import java.util.concurrent.atomic.AtomicLong

class DebouncedBackDispatcher(
    private val backActionExecutor: () -> Boolean,
    private val debounceWindowMs: Long = DEFAULT_DEBOUNCE_WINDOW_MS
) {
    constructor(
        service: AccessibilityService,
        debounceWindowMs: Long = DEFAULT_DEBOUNCE_WINDOW_MS
    ) : this(
        backActionExecutor = { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) },
        debounceWindowMs = debounceWindowMs
    )

    private val lastDispatchTimestamp = AtomicLong(0L)

    /**
     * Attempts to dispatch the global back action.
     *
     * @param currentEpochMs Current timestamp in epoch milliseconds (defaults to System.currentTimeMillis()).
     * @return true if the back action was dispatched, false if suppressed due to debounce window.
     */
    fun dispatchBack(currentEpochMs: Long = System.currentTimeMillis()): Boolean {
        while (true) {
            val lastTime = lastDispatchTimestamp.get()
            if (lastTime != 0L && (currentEpochMs - lastTime) < debounceWindowMs) {
                return false
            }
            if (lastDispatchTimestamp.compareAndSet(lastTime, currentEpochMs)) {
                return backActionExecutor.invoke()
            }
        }
    }

    /**
     * Resets the debounce timestamp timer to 0.
     */
    fun reset() {
        lastDispatchTimestamp.set(0L)
    }

    companion object {
        const val DEFAULT_DEBOUNCE_WINDOW_MS = 300L
    }
}
