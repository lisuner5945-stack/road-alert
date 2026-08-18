package ru.example.roadalert.ads

import android.app.Activity

/**
 * Работа с рекламой (ТЗ §28).
 *
 * AdsManager принципиально не имеет доступа к GPS-координатам:
 * точное местоположение остаётся внутри детектора камер.
 */
interface AdsManager {

    fun initialize()

    fun preloadHomeBanner()

    fun preloadPostTripInterstitial()

    /** Показывает interstitial только если это разрешено правилами и поездка завершена. */
    fun showPostTripInterstitialIfEligible(activity: Activity)

    fun onTripCompleted()

    fun destroy()
}

/** Заглушка на случай выключенной рекламы: приложение обязано работать и без неё. */
object NoOpAdsManager : AdsManager {
    override fun initialize() = Unit
    override fun preloadHomeBanner() = Unit
    override fun preloadPostTripInterstitial() = Unit
    override fun showPostTripInterstitialIfEligible(activity: Activity) = Unit
    override fun onTripCompleted() = Unit
    override fun destroy() = Unit
}
