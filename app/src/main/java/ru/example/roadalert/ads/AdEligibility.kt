package ru.example.roadalert.ads

/**
 * Правила показа полноэкранной рекламы (ТЗ §25).
 *
 * Чистая логика без Android и без SDK — покрывается unit-тестами.
 * Здесь же зафиксирован главный запрет: во время поездки fullscreen невозможен.
 */
object AdEligibility {

    /** Не чаще одного interstitial на столько завершённых поездок. */
    const val TRIPS_PER_INTERSTITIAL = 3

    /** И не чаще одного раза в этот интервал. */
    const val MIN_INTERVAL_MS = 30 * 60_000L

    data class State(
        val tripsSinceLastAd: Int,
        val lastAdShownAtMs: Long?,
        val completedTripsTotal: Int,
        val isFirstLaunch: Boolean,
    )

    /**
     * @param tripActive состояние поездки прямо сейчас
     * @return true, только если показать interstitial безопасно и уместно
     */
    fun canShowInterstitial(
        state: State,
        tripActive: Boolean,
        adsEnabled: Boolean,
        nowMs: Long,
    ): Boolean {
        // Жёсткий запрет: во время активной поездки — никакой полноэкранной рекламы.
        if (tripActive) return false
        if (!adsEnabled) return false
        if (state.isFirstLaunch) return false
        // После самой первой поездки рекламу не показываем: первое впечатление важнее.
        if (state.completedTripsTotal < 2) return false
        if (state.tripsSinceLastAd < TRIPS_PER_INTERSTITIAL) return false
        val lastShown = state.lastAdShownAtMs
        if (lastShown != null && nowMs - lastShown < MIN_INTERVAL_MS) return false
        return true
    }
}
