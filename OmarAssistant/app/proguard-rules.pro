# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep Omar Assistant classes
-keep class com.omar.assistant.** { *; }

# Keep speech recognition classes
-keep class android.speech.** { *; }

# Keep TTS classes
-keep class android.speech.tts.** { *; }

# Keep audio recording classes
-keep class android.media.AudioRecord { *; }
-keep class android.media.AudioManager { *; }

# Keep reflection used classes
-keepattributes Signature
-keepattributes *Annotation*

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Gson classes if using JSON parsing
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep interface methods
-keep interface com.omar.assistant.toolbox.Tool { *; }

# Don't obfuscate tool implementations to make debugging easier
-keep class com.omar.assistant.toolbox.tools.** { *; }

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Remove logging in release builds for privacy
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}
