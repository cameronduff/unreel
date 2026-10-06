package org.unreel.android.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.regex.Pattern

class ZeroNetworkSecurityAuditTest {

    @Test
    fun testNoInternetPermissionInManifest() {
        val manifestCandidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml"),
            File("build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml"),
            File("app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml")
        )

        val existingManifests = manifestCandidates.filter { it.exists() }
        assertTrue("At least one AndroidManifest.xml must exist to audit", existingManifests.isNotEmpty())

        for (manifest in existingManifests) {
            val content = manifest.readText()
            assertFalse(
                "Manifest at ${manifest.path} MUST NOT contain android.permission.INTERNET",
                content.contains("android.permission.INTERNET")
            )
            assertFalse(
                "Manifest at ${manifest.path} MUST NOT contain ACCESS_NETWORK_STATE",
                content.contains("android.permission.ACCESS_NETWORK_STATE")
            )
            assertFalse(
                "Manifest at ${manifest.path} MUST NOT contain ACCESS_WIFI_STATE",
                content.contains("android.permission.ACCESS_WIFI_STATE")
            )
        }
    }

    @Test
    fun testOnlyAllowedPermissionsDeclared() {
        val manifestFile = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        ).first { it.exists() }

        val content = manifestFile.readText()
        val usesPermissionPattern = Pattern.compile("<uses-permission\\s+android:name=\"([^\"]+)\"")
        val matcher = usesPermissionPattern.matcher(content)

        val allowedPermissions = setOf(
            "android.permission.SYSTEM_ALERT_WINDOW"
        )

        val foundPermissions = mutableListOf<String>()
        while (matcher.find()) {
            val permission = matcher.group(1) ?: continue
            foundPermissions.add(permission)
            assertTrue(
                "Permission $permission is not in the whitelist: $allowedPermissions",
                permission in allowedPermissions
            )
        }

        // Must not contain any network permissions
        assertFalse(foundPermissions.any { it.contains("INTERNET", ignoreCase = true) })
    }

    @Test
    fun testNoNetworkLibrariesOnClasspath() {
        val forbiddenClassNames = listOf(
            "okhttp3.OkHttpClient",
            "okhttp3.Request",
            "retrofit2.Retrofit",
            "io.ktor.client.HttpClient"
        )

        for (className in forbiddenClassNames) {
            try {
                Class.forName(className)
                fail("Forbidden network class $className found on classpath! Unreel must have zero network libraries.")
            } catch (_: ClassNotFoundException) {
                // Expected: class must NOT exist
            }
        }

        val classPath = System.getProperty("java.class.path") ?: ""
        val forbiddenClasspathSubstrings = listOf(
            "okhttp",
            "retrofit",
            "ktor-client"
        )

        for (forbidden in forbiddenClasspathSubstrings) {
            val match = classPath.split(File.pathSeparator).firstOrNull { it.contains(forbidden, ignoreCase = true) }
            if (match != null) {
                fail("Forbidden networking dependency matching '$forbidden' found on classpath: $match")
            }
        }
    }
}
