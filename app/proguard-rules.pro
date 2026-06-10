-keep class com.kopilka.android.data.model.** { *; }
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlinx Serialization
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontnote kotlinx.serialization.**

# Google Tink (used by androidx.security.crypto) — compile-time annotations only, not present at runtime
-dontwarn com.google.errorprone.annotations.**
