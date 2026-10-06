# ProGuard / R8 configuration for Unreel

# Retain Room database and DAO schemas
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retain DataStore preferences
-keepclassmembers class * extends androidx.datastore.preferences.core.Preferences {
    *;
}

# Jetpack Compose and Kotlin Coroutines
-keepattributes *Annotation*
-dontwarn androidx.compose.**
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Keep Android Services and Components
-keep class org.unreel.android.service.UnreelAccessibilityService { *; }
-keep class org.unreel.android.service.UnreelTileService { *; }
-keep class org.unreel.android.overlay.TouchAbsorberOverlayService { *; }
-keep class org.unreel.android.ui.** { *; }
