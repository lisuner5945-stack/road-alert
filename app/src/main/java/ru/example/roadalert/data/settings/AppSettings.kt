package ru.example.roadalert.data.settings

/** Профиль дистанций предупреждения (ТЗ §43). */
enum class DistanceProfile { EARLY, AUTO, LATE }

/**
 * Пользовательские настройки. Хранятся локально в DataStore, никуда не отправляются.
 */
data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val voiceAlerts: Boolean = true,
    val soundSignal: Boolean = true,
    val vibration: Boolean = false,
    /** Предупреждать только при превышении. По умолчанию выключено. */
    val alertOnlyWhenSpeeding: Boolean = false,
    /** Допуск превышения: 0 / 5 / 10 км/ч. По умолчанию 0 — не поощряем превышение. */
    val speedToleranceKmh: Int = 0,
    val distanceProfile: DistanceProfile = DistanceProfile.AUTO,
    val overlayEnabled: Boolean = false,
    val overlayPositionX: Int = 0,
    val overlayPositionY: Int = 200,
    val keepScreenOnInDrive: Boolean = true,
    val autoStartByBluetooth: Boolean = false,
    val carDeviceName: String? = null,
    val carDeviceAddress: String? = null,
    val autoUpdateDatabase: Boolean = true,
    /** Когда в последний раз проверяли обновление базы (epoch ms). */
    val lastUpdateCheckAtMs: Long? = null,
)
