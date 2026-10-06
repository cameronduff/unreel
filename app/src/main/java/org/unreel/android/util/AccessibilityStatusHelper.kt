package org.unreel.android.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import org.unreel.android.service.UnreelAccessibilityService

object AccessibilityStatusHelper {

    /**
     * Checks if the UnreelAccessibilityService is currently enabled in system settings.
     *
     * Parses the colon-delimited string from Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES.
     */
    fun isServiceEnabled(context: Context): Boolean {
        val expectedComponentName = ComponentName(context, UnreelAccessibilityService::class.java)
        val expectedFlatName = expectedComponentName.flattenToString()
        val expectedShortName = expectedComponentName.flattenToShortString()

        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        if (enabledServices.isBlank()) return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentStr = colonSplitter.next().trim()
            if (componentStr.equals(expectedFlatName, ignoreCase = true) ||
                componentStr.equals(expectedShortName, ignoreCase = true)
            ) {
                return true
            }
        }

        return false
    }

    /**
     * Pure string parsing helper for unit testing colon-delimited enabled services string.
     */
    fun isComponentInString(enabledServices: String?, targetComponentString: String): Boolean {
        if (enabledServices.isNullOrBlank()) return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val item = colonSplitter.next().trim()
            if (item.equals(targetComponentString, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
