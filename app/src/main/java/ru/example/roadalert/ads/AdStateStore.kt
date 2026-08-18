package ru.example.roadalert.ads

import android.content.Context
import androidx.core.content.edit

/**
 * Счётчики частоты показов. Хранятся локально в SharedPreferences:
 * это чисто техническое состояние рекламы, к настройкам пользователя не относится.
 */
class AdStateStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("ads_state", Context.MODE_PRIVATE)

    fun snapshot(): AdEligibility.State = AdEligibility.State(
        tripsSinceLastAd = prefs.getInt(KEY_TRIPS_SINCE_AD, 0),
        lastAdShownAtMs = prefs.getLong(KEY_LAST_AD_AT, 0L).takeIf { it > 0L },
        completedTripsTotal = prefs.getInt(KEY_TRIPS_TOTAL, 0),
        // Первый запуск после установки: рекламу в этой сессии не показываем вовсе.
        isFirstLaunch = prefs.getInt(KEY_LAUNCH_COUNT, 0) <= 1,
    )

    fun markLaunched() {
        prefs.edit { putInt(KEY_LAUNCH_COUNT, prefs.getInt(KEY_LAUNCH_COUNT, 0) + 1) }
    }

    fun onTripCompleted() {
        prefs.edit {
            putInt(KEY_TRIPS_SINCE_AD, prefs.getInt(KEY_TRIPS_SINCE_AD, 0) + 1)
            putInt(KEY_TRIPS_TOTAL, prefs.getInt(KEY_TRIPS_TOTAL, 0) + 1)
        }
    }

    fun onInterstitialShown(nowMs: Long) {
        prefs.edit {
            putInt(KEY_TRIPS_SINCE_AD, 0)
            putLong(KEY_LAST_AD_AT, nowMs)
        }
    }

    private companion object {
        const val KEY_TRIPS_SINCE_AD = "trips_since_ad"
        const val KEY_TRIPS_TOTAL = "trips_total"
        const val KEY_LAST_AD_AT = "last_ad_at"
        const val KEY_LAUNCH_COUNT = "launch_count"
    }
}
