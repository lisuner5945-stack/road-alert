package ru.example.roadalert.app

import android.app.Application
import ru.example.roadalert.util.AppLog
import ru.example.roadalert.work.DatabaseUpdateWorker

/**
 * Точка входа приложения.
 *
 * Здесь намеренно нет ни аналитики, ни трекеров, ни инициализации GPS:
 * геолокация стартует только по явному действию пользователя «Начать поездку».
 */
class RoadAlertApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppLog.event("APP_START")
        // Обновление базы — фоном, по сети, не чаще раза в сутки. GPS при этом не включается.
        runCatching { DatabaseUpdateWorker.schedule(this) }
        container.adsManager.initialize()
        container.adStateStore.markLaunched()
    }
}
