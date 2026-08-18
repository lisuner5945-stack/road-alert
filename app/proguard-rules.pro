# Room / Kotlin metadata
-keep class ru.example.roadalert.data.database.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class ru.example.roadalert.** {
    *** Companion;
}
-keepclasseswithmembers class ru.example.roadalert.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Yandex Mobile Ads (SDK поставляет собственные правила, дублируем базовое)
-keep class com.yandex.mobile.ads.** { *; }
-dontwarn com.yandex.mobile.ads.**
