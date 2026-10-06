package org.unreel.android.util

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AccessibilityStatusHelperTest {

    private val targetComponent = "org.unreel.android/org.unreel.android.service.UnreelAccessibilityService"
    private val targetShortComponent = "org.unreel.android/.service.UnreelAccessibilityService"

    @Test
    fun testReturnsTrueWhenComponentPresent() {
        assertTrue(AccessibilityStatusHelper.isComponentInString(targetComponent, targetComponent))
        assertTrue(AccessibilityStatusHelper.isComponentInString(targetShortComponent, targetShortComponent))
    }

    @Test
    fun testReturnsFalseWhenNullOrEmpty() {
        assertFalse(AccessibilityStatusHelper.isComponentInString(null, targetComponent))
        assertFalse(AccessibilityStatusHelper.isComponentInString("", targetComponent))
        assertFalse(AccessibilityStatusHelper.isComponentInString("   ", targetComponent))
    }

    @Test
    fun testReturnsFalseWhenOnlyOtherServicesPresent() {
        val otherServices = "com.google.android.marvin.talkback/.TalkBackService:com.example.service/.MyService"
        assertFalse(AccessibilityStatusHelper.isComponentInString(otherServices, targetComponent))
    }

    @Test
    fun testHandlesColonSeparatedMultipleServices() {
        val multiString = "com.google.android.marvin.talkback/.TalkBackService:$targetComponent:com.android.switchaccess/.SwitchAccessService"
        assertTrue(AccessibilityStatusHelper.isComponentInString(multiString, targetComponent))
    }

    @Test
    fun testIntegrationWithContextSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Initially empty
        Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
        assertFalse(AccessibilityStatusHelper.isServiceEnabled(context))

        // Set to Unreel service
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            targetShortComponent
        )
        assertTrue(AccessibilityStatusHelper.isServiceEnabled(context))

        // Set to other service
        Settings.Secure.putString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            "com.other.app/.OtherService"
        )
        assertFalse(AccessibilityStatusHelper.isServiceEnabled(context))
    }
}
