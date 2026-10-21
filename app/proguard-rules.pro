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

# ---- Strip debug logging from release ----
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}