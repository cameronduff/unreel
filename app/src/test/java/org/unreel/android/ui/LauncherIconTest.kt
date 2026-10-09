package org.unreel.android.ui

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LauncherIconTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testLauncherIconDrawablesExistAndInflate() {
        val backgroundResId = context.resources.getIdentifier("ic_launcher_background", "drawable", context.packageName)
        assertTrue("ic_launcher_background must exist in drawable resources", backgroundResId != 0)
        val backgroundDrawable = ContextCompat.getDrawable(context, backgroundResId)
        assertNotNull("ic_launcher_background must inflate cleanly", backgroundDrawable)

        val foregroundResId = context.resources.getIdentifier("ic_launcher_foreground", "drawable", context.packageName)
        assertTrue("ic_launcher_foreground must exist in drawable resources", foregroundResId != 0)
        val foregroundDrawable = ContextCompat.getDrawable(context, foregroundResId)
        assertNotNull("ic_launcher_foreground must inflate cleanly", foregroundDrawable)

        val monochromeResId = context.resources.getIdentifier("ic_launcher_monochrome", "drawable", context.packageName)
        assertTrue("ic_launcher_monochrome must exist in drawable resources", monochromeResId != 0)
        val monochromeDrawable = ContextCompat.getDrawable(context, monochromeResId)
        assertNotNull("ic_launcher_monochrome must inflate cleanly", monochromeDrawable)
    }

    @Test
    fun testLauncherAdaptiveMipmapIconsExistAndInflate() {
        val launcherResId = context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
        assertTrue("ic_launcher must exist in mipmap resources", launcherResId != 0)
        val launcherDrawable = ContextCompat.getDrawable(context, launcherResId)
        assertNotNull("ic_launcher must inflate cleanly", launcherDrawable)

        val roundResId = context.resources.getIdentifier("ic_launcher_round", "mipmap", context.packageName)
        assertTrue("ic_launcher_round must exist in mipmap resources", roundResId != 0)
        val roundDrawable = ContextCompat.getDrawable(context, roundResId)
        assertNotNull("ic_launcher_round must inflate cleanly", roundDrawable)
    }

    @Test
    fun testApplicationInfoConfiguresLauncherIcon() {
        val appInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        val expectedLauncherId = context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
        assertTrue("Application icon must be configured to ic_launcher", appInfo.icon == expectedLauncherId)
    }

    @Test
    fun testMainActivityConfiguresLauncherIcon() {
        val component = android.content.ComponentName(context, MainActivity::class.java)
        val activityInfo = context.packageManager.getActivityInfo(component, 0)
        val expectedLauncherId = context.resources.getIdentifier("ic_launcher", "mipmap", context.packageName)
        assertTrue("MainActivity icon must be configured to ic_launcher", activityInfo.icon == expectedLauncherId)
    }
}
