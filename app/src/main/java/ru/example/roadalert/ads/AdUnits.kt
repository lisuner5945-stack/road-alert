package ru.example.roadalert.ads

import ru.example.roadalert.BuildConfig

/**
 * Рекламные блоки. В debug — только официальные demo-блоки Яндекса (ТЗ §26),
 * в release они запрещены и проверяются задачей verifyReleaseConfig.
 */
object AdUnits {

    const val DEMO_BANNER = "demo-banner-yandex"
    const val DEMO_INTERSTITIAL = "demo-interstitial-yandex"

    val banner: String get() = BuildConfig.AD_UNIT_BANNER

    val interstitial: String get() = BuildConfig.AD_UNIT_INTERSTITIAL

    fun isDemo(unitId: String): Boolean = unitId.startsWith("demo-")

    /** Реклама включается только если блоки заданы и сборка это разрешает. */
    val enabled: Boolean
        get() = BuildConfig.ADS_ENABLED && banner.isNotBlank() && interstitial.isNotBlank()
}
