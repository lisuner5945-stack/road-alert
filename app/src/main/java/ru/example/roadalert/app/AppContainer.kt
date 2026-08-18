package ru.example.roadalert.app

import android.content.Context
import ru.example.roadalert.BuildConfig
import ru.example.roadalert.ads.AdStateStore
import ru.example.roadalert.ads.AdUnits
import ru.example.roadalert.ads.AdsManager
import ru.example.roadalert.ads.NoOpAdsManager
import ru.example.roadalert.ads.YandexAdsManager
import ru.example.roadalert.data.camera.CameraRepository
import ru.example.roadalert.data.database.RoadAlertDatabase
import ru.example.roadalert.data.settings.SettingsRepository
import ru.example.roadalert.data.updates.CameraUpdateManager

/**
 * Простейший ручной DI: одна точка сборки зависимостей без лишних фреймворков.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    private val database: RoadAlertDatabase by lazy { RoadAlertDatabase.get(appContext) }

    val cameraRepository: CameraRepository by lazy { CameraRepository(database.cameraDao()) }

    val cameraUpdateManager: CameraUpdateManager by lazy {
        CameraUpdateManager(
            context = appContext,
            repository = cameraRepository,
            baseUrl = BuildConfig.CAMERA_DB_BASE_URL,
        )
    }

    val adStateStore: AdStateStore by lazy { AdStateStore(appContext) }

    /** Если реклама выключена конфигурацией — приложение работает с заглушкой. */
    val adsManager: AdsManager by lazy {
        if (AdUnits.enabled) YandexAdsManager(appContext, adStateStore) else NoOpAdsManager
    }

    suspend fun recordUpdateCheck(nowMs: Long = System.currentTimeMillis()) {
        settingsRepository.setLastUpdateCheck(nowMs)
    }
}
