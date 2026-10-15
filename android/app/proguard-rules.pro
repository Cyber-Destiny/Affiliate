# kotlinx.serialization ships its own rules; keep our models' serializers just in case.
-keepclassmembers @kotlinx.serialization.Serializable class com.naijasound.keyboard.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.naijasound.keyboard.**$$serializer { *; }

# OkHttp optional dependencies
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
