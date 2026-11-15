# Keep Telecom Service, Activities, and BroadcastReceivers declared in AndroidManifest
-keep public class * extends android.telecom.InCallService
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Activity

# Keep Kotlin Serialization / DataStore / Coroutines metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep AndroidX / Compose runtime essentials
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Strip verbose Log.d / Log.v calls in release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
