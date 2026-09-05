# Retrofit + Gson: response models are only ever constructed reflectively from JSON,
# so R8 cannot see they are used and would otherwise strip or rename their fields.
-keep class com.quickpool.app.network.** { *; }

# Retrofit keeps generic signatures and annotations on service interfaces.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp / Okio ship references to optional platform APIs that are absent on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okio.**

# The STOMP client is RxJava-based and reflective in places.
-dontwarn ua.naiksoftware.stomp.**
-keep class ua.naiksoftware.stomp.** { *; }
