# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
#-keep class com.google.** { *; }
# Gemini API
# Preserve generic signatures and reflection metadata
#-keepattributes Signature
#-keepattributes *Annotation*
#-keepattributes Exceptions
#
## Gemini API SDK
#-keep class com.google.ai.client.** { *; }
#-dontwarn com.google.ai.client.**
#
## Coroutines
#-keep class kotlinx.coroutines.** { *; }
#-dontwarn kotlinx.coroutines.**
#
## Your app’s models (adjust package name)
#-keep class com.example.ble_jetpackcompose.models.** { *; }
#
## Reflection and proxies
#-keep class java.lang.reflect.** { *; }
#-dontwarn java.lang.reflect.**
#
## Gson (if used)
#-keep class com.google.gson.** { *; }
#-dontwarn com.google.gson.**

# Preserve generic signatures and reflection metadata
# Preserve generic signatures and reflection metadata

# Preserve suspend function return types and annotations
-keepclassmembers,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# ==========================================
# 1. Preserve Generics & Reflection Metadata
# ==========================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# ==========================================
# 2. Kotlin Coroutines & Continuation
# ==========================================
# Note: kotlin.coroutines (standard library) is required for suspend functions!
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ==========================================
# 3. Retrofit & OkHttp
# ==========================================
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class retrofit2.http.** { *; }
-keep class retrofit2.converter.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# ==========================================
# 4. Gson
# ==========================================
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# ==========================================
# 5. App API Service & Network Models
# ==========================================
# Keep the BackendService interface and its suspend methods
-keep interface com.blesense.app.api.BackendService { *; }
-keepclassmembers interface com.blesense.app.api.BackendService {
    <methods>;
}
# Keep the entire API package and SensorPacket data model
-keep class com.blesense.app.api.** { *; }
-keepclassmembers class com.blesense.app.api.** { *; }