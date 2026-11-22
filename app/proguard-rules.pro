# kotlinx.serialization ships its own R8 rules; these keep our @Serializable models' generated
# serializers reachable even when only accessed through reflection-free lookups.
-keepclassmembers @kotlinx.serialization.Serializable class com.sitesnap.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.sitesnap.app.**$$serializer { *; }

# OkHttp optional platform integrations that aren't on Android.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
