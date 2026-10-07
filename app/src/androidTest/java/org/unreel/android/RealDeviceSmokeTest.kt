package org.unreel.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.unreel.android.data.FilterPreferencesRepository
import org.unreel.android.data.ReelsInterceptEntity
import org.unreel.android.data.UnreelDatabase

@RunWith(AndroidJUnit4::class)
class RealDeviceSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun testPackageInfoAndPermissionsOnHardware() {
        val pm = context.packageManager
        val pkgInfo = pm.getPackageInfo(context.packageName, 0)
        assertEquals("org.unreel.android", pkgInfo.packageName)

        // Verify zero internet permission on actual installed package
        val permissions = pm.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS
        ).requestedPermissions ?: emptyArray()

        assertFalse(
            "Device package must NOT have INTERNET permission",
            permissions.contains("android.permission.INTERNET")
        )
    }

    @Test
    fun testDatabasePersistenceOnHardwareStorage() = runTest {
        val database = UnreelDatabase.getInstance(context)
        val dao = database.reelsInterceptDao()

        val id = dao.insertIntercept(
            ReelsInterceptEntity(
                timestampEpochMs = System.currentTimeMillis(),
                triggerType = ReelsInterceptEntity.TRIGGER_FULLSCREEN_CLIPS
            )
        )
        assertTrue("Database row insertion must return valid primary key", id > 0L)

        val total = dao.getTotalCount().first()
        assertTrue("Total intercepts count must be >= 1", total >= 1)
    }

    @Test
    fun testPreferencesDataStoreOnHardware() = runTest {
        val repo = FilterPreferencesRepository.getInstance(context)
        val prefs = repo.filterPreferences.first()
        assertNotNull(prefs)
    }
}
