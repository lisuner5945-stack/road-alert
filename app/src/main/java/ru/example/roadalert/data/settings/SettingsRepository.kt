package ru.example.roadalert.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "road_alert_settings")

/**
 * Единственный источник правды для пользовательских настроек.
 * Всё локально: ни один параметр не покидает устройство.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val voiceAlerts = booleanPreferencesKey("voice_alerts")
        val soundSignal = booleanPreferencesKey("sound_signal")
        val vibration = booleanPreferencesKey("vibration")
        val alertOnlyWhenSpeeding = booleanPreferencesKey("alert_only_when_speeding")
        val speedTolerance = intPreferencesKey("speed_tolerance")
        val distanceProfile = stringPreferencesKey("distance_profile")
        val overlayEnabled = booleanPreferencesKey("overlay_enabled")
        val overlayX = intPreferencesKey("overlay_x")
        val overlayY = intPreferencesKey("overlay_y")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val autoStartBluetooth = booleanPreferencesKey("auto_start_bluetooth")
        val carDeviceName = stringPreferencesKey("car_device_name")
        val carDeviceAddress = stringPreferencesKey("car_device_address")
        val autoUpdateDatabase = booleanPreferencesKey("auto_update_database")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            onboardingCompleted = prefs[Keys.onboardingCompleted] ?: defaults.onboardingCompleted,
            voiceAlerts = prefs[Keys.voiceAlerts] ?: defaults.voiceAlerts,
            soundSignal = prefs[Keys.soundSignal] ?: defaults.soundSignal,
            vibration = prefs[Keys.vibration] ?: defaults.vibration,
            alertOnlyWhenSpeeding = prefs[Keys.alertOnlyWhenSpeeding] ?: defaults.alertOnlyWhenSpeeding,
            speedToleranceKmh = prefs[Keys.speedTolerance] ?: defaults.speedToleranceKmh,
            distanceProfile = prefs[Keys.distanceProfile]
                ?.let { raw -> DistanceProfile.entries.firstOrNull { it.name == raw } }
                ?: defaults.distanceProfile,
            overlayEnabled = prefs[Keys.overlayEnabled] ?: defaults.overlayEnabled,
            overlayPositionX = prefs[Keys.overlayX] ?: defaults.overlayPositionX,
            overlayPositionY = prefs[Keys.overlayY] ?: defaults.overlayPositionY,
            keepScreenOnInDrive = prefs[Keys.keepScreenOn] ?: defaults.keepScreenOnInDrive,
            autoStartByBluetooth = prefs[Keys.autoStartBluetooth] ?: defaults.autoStartByBluetooth,
            carDeviceName = prefs[Keys.carDeviceName] ?: defaults.carDeviceName,
            carDeviceAddress = prefs[Keys.carDeviceAddress] ?: defaults.carDeviceAddress,
            autoUpdateDatabase = prefs[Keys.autoUpdateDatabase] ?: defaults.autoUpdateDatabase,
            lastUpdateCheckAtMs = prefs[Keys.lastUpdateCheck],
        )
    }

    suspend fun setOnboardingCompleted(value: Boolean) = edit { it[Keys.onboardingCompleted] = value }

    suspend fun setVoiceAlerts(value: Boolean) = edit { it[Keys.voiceAlerts] = value }

    suspend fun setSoundSignal(value: Boolean) = edit { it[Keys.soundSignal] = value }

    suspend fun setVibration(value: Boolean) = edit { it[Keys.vibration] = value }

    suspend fun setAlertOnlyWhenSpeeding(value: Boolean) = edit { it[Keys.alertOnlyWhenSpeeding] = value }

    suspend fun setSpeedTolerance(value: Int) = edit { it[Keys.speedTolerance] = value.coerceIn(0, 10) }

    suspend fun setDistanceProfile(value: DistanceProfile) = edit { it[Keys.distanceProfile] = value.name }

    suspend fun setOverlayEnabled(value: Boolean) = edit { it[Keys.overlayEnabled] = value }

    suspend fun setOverlayPosition(x: Int, y: Int) = edit {
        it[Keys.overlayX] = x
        it[Keys.overlayY] = y
    }

    suspend fun setKeepScreenOn(value: Boolean) = edit { it[Keys.keepScreenOn] = value }

    suspend fun setAutoStartByBluetooth(value: Boolean) = edit { it[Keys.autoStartBluetooth] = value }

    suspend fun setCarDevice(name: String?, address: String?) = edit {
        if (name == null) it.remove(Keys.carDeviceName) else it[Keys.carDeviceName] = name
        if (address == null) it.remove(Keys.carDeviceAddress) else it[Keys.carDeviceAddress] = address
    }

    suspend fun setAutoUpdateDatabase(value: Boolean) = edit { it[Keys.autoUpdateDatabase] = value }

    suspend fun setLastUpdateCheck(timestampMs: Long) = edit { it[Keys.lastUpdateCheck] = timestampMs }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
