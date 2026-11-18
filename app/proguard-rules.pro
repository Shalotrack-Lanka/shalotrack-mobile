# ---- Keep line numbers for Play Console crash de-obfuscation ----
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# ---- Gson DTOs (reflection-based JSON) ----
-keep class com.example.letstracklanka.data.model.** { *; }

# ---- Retrofit service interfaces (ApiService, AccountApi, ...) ----
-keep interface com.example.letstracklanka.data.remote.** { *; }
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# ---- SignalR (typed hub args) ----
-keep class com.microsoft.signalr.** { *; }
-dontwarn org.slf4j.**
-dontwarn com.microsoft.signalr.**

# ---- OkHttp / Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ---- Strip ALL logging from release ----
# Release builds ship no crash-reporting service that reads logcat, so a log line in release only helps
# someone with the phone on a cable. Some lines included server error bodies and vehicle ids; none of that
# may leave a release build. Debug builds keep every log.
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static java.lang.String getStackTraceString(java.lang.Throwable);
}