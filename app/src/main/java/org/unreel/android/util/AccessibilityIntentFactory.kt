package org.unreel.android.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import org.unreel.android.service.UnreelAccessibilityService

object AccessibilityIntentFactory {

    const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"

    /**
     * Constructs an Intent that opens the system Accessibility Settings screen,
     * pre-highlighting the UnreelAccessibilityService entry on supported Android versions.
     */
    fun createOpenSettingsIntent(context: Context? = null): Intent {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val componentShortName = if (context != null) {
            ComponentName(context, UnreelAccessibilityService::class.java).flattenToShortString()
        } else {
            "org.unreel.android/.service.UnreelAccessibilityService"
        }

        intent.putExtra(EXTRA_FRAGMENT_ARG_KEY, componentShortName)
        return intent
    }
}
