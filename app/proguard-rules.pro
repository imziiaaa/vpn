# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep native JNI methods and AmneziaWG Go backend
-keep class org.amnezia.awg.GoBackend {
    native <methods>;
    public static *;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Data Models for Gson reflection & deserialization
-keepclassmembers class com.veilgate.vpn.data.model.** { <fields>; }
-keep class com.veilgate.vpn.data.model.** { *; }

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Retrofit service interfaces
-keep interface com.veilgate.vpn.data.network.** { *; }

# Keep Android Security Crypto
-keepclassmembers class androidx.security.crypto.** { *; }
