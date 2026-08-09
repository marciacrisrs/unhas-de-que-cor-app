# Keep Room entities and Hilt-generated classes for release builds.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn com.google.errorprone.annotations.**

# Hilt / Dagger
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keepclasseswithmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}
-keepclassmembers class * {
    @dagger.Lazy <fields>;
}

# Kotlin / coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Enums used by Room / navigation (name-based persistence)
-keepclassmembers enum br.com.unhasdequecor.domain.model.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# MediaPipe Hand Landmarker
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**
-keep class com.google.common.flogger.** { *; }
-dontwarn com.google.common.flogger.**
