package ru.example.roadalert.ads

import android.app.Activity
import android.content.Context
import com.yandex.mobile.ads.common.AdRequest
import com.yandex.mobile.ads.common.AdRequestError
import com.yandex.mobile.ads.common.InitializationListener
import com.yandex.mobile.ads.common.YandexAds
import com.yandex.mobile.ads.interstitial.InterstitialAd
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader
import ru.example.roadalert.drive.DriveStateHolder
import ru.example.roadalert.util.AppLog

/**
 * Реализация на Yandex Mobile Ads SDK 8.x (ТЗ §27, §28).
 *
 * Важное архитектурное ограничение: сюда не передаются координаты.
 * Отслеживание местоположения в SDK выключено явно (setLocationTracking(false)).
 */
class YandexAdsManager(
    context: Context,
    private val state: AdStateStore,
) : AdsManager {

    private val appContext = context.applicationContext

    private var interstitialLoader: InterstitialAdLoader? = null
    private var loadedInterstitial: InterstitialAd? = null
    private var initialized = false

    override fun initialize() {
        if (initialized || !AdUnits.enabled) return

        // Privacy-настройки применяются ДО инициализации SDK.
        runCatching {
            YandexAds.setLocationTracking(false)
            YandexAds.setUserConsent(false)
            YandexAds.setAgeRestricted(false)
        }.onFailure { AppLog.event("ADS_PRIVACY_SETUP_FAILED", "reason" to it.message) }

        runCatching {
            YandexAds.initialize(
                appContext,
                InitializationListener {
                    initialized = true
                    AppLog.event("ADS_INITIALIZED")
                },
            )
        }.onFailure { AppLog.event("ADS_INIT_FAILED", "reason" to it.message) }
    }

    override fun preloadHomeBanner() {
        // Баннер загружается самим BannerAdView в Compose-обёртке (HomeBanner).
    }

    override fun preloadPostTripInterstitial() {
        if (!AdUnits.enabled) return
        if (loadedInterstitial != null) return

        val loader = interstitialLoader ?: InterstitialAdLoader(appContext).also {
            interstitialLoader = it
        }
        val listener = object : InterstitialAdLoadListener {
            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                loadedInterstitial = interstitialAd
                AppLog.event("AD_INTERSTITIAL_LOADED")
            }

            override fun onAdFailedToLoad(error: AdRequestError) {
                // No-fill — штатная ситуация: приложение продолжает работать без рекламы.
                AppLog.event("AD_LOAD_FAILED", "code" to error.code)
            }
        }
        runCatching { loader.loadAd(AdRequest.Builder(AdUnits.interstitial).build(), listener) }
            .onFailure { AppLog.event("AD_LOAD_EXCEPTION", "reason" to it.message) }
    }

    override fun showPostTripInterstitialIfEligible(activity: Activity) {
        // Защита на уровне бизнес-логики: даже прямой вызов ничего не покажет,
        // пока поездка активна (ТЗ §28).
        if (DriveStateHolder.isTripActive) {
            AppLog.event("AD_BLOCKED_TRIP_ACTIVE")
            return
        }
        val now = System.currentTimeMillis()
        if (!AdEligibility.canShowInterstitial(state.snapshot(), tripActive = false, adsEnabled = AdUnits.enabled, nowMs = now)) {
            return
        }
        val ad = loadedInterstitial ?: run {
            preloadPostTripInterstitial()
            return
        }

        ad.setAdEventListener(object : com.yandex.mobile.ads.interstitial.InterstitialAdEventListener {
            override fun onAdShown() {
                AppLog.event("AD_INTERSTITIAL_SHOWN")
                state.onInterstitialShown(now)
            }

            override fun onAdFailedToShow(adError: com.yandex.mobile.ads.common.AdError) {
                AppLog.event("AD_SHOW_FAILED", "reason" to adError.description)
            }

            override fun onAdDismissed() {
                loadedInterstitial = null
                preloadPostTripInterstitial()
            }

            override fun onAdClicked() = Unit

            override fun onAdImpression(impressionData: com.yandex.mobile.ads.common.ImpressionData?) = Unit
        })

        runCatching { ad.show(activity) }
            .onFailure { AppLog.event("AD_SHOW_EXCEPTION", "reason" to it.message) }
    }

    override fun onTripCompleted() {
        state.onTripCompleted()
    }

    override fun destroy() {
        runCatching { interstitialLoader?.cancelLoading() }
        interstitialLoader = null
        loadedInterstitial = null
    }
}
