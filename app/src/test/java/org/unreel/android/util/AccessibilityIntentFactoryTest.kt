package org.unreel.android.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AccessibilityIntentFactoryTest {

    @Test
    fun testIntentActionIsAccessibilitySettings() {
        val intent = AccessibilityIntentFactory.createOpenSettingsIntent()
        assertNotNull(intent)
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
    }

    @Test
    fun testIntentFlagNewTaskIsSet() {
        val intent = AccessibilityIntentFactory.createOpenSettingsIntent()
        val hasNewTaskFlag = (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0
        assertTrue("Intent should have FLAG_ACTIVITY_NEW_TASK set", hasNewTaskFlag)
    }

    @Test
    fun testExtraFragmentArgKeyContainsComponentName() {
        val intent = AccessibilityIntentFactory.createOpenSettingsIntent()
        val extraKey = intent.getStringExtra(AccessibilityIntentFactory.EXTRA_FRAGMENT_ARG_KEY)
        assertNotNull(extraKey)
        assertEquals("org.unreel.android/.service.UnreelAccessibilityService", extraKey)
    }

    @Test
    fun testContextBasedCreation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = AccessibilityIntentFactory.createOpenSettingsIntent(context)
        val extraKey = intent.getStringExtra(AccessibilityIntentFactory.EXTRA_FRAGMENT_ARG_KEY)
        assertNotNull(extraKey)
        assertEquals("org.unreel.android/.service.UnreelAccessibilityService", extraKey)
    }
}
