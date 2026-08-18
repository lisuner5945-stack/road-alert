package ru.example.roadalert.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.example.roadalert.app.AppContainer
import ru.example.roadalert.data.camera.CameraDatabaseInfo
import ru.example.roadalert.data.settings.AppSettings
import ru.example.roadalert.data.settings.DistanceProfile
import ru.example.roadalert.drive.DriveStateHolder

/**
 * ViewModel уровня приложения: навигация + настройки + состояние поездки.
 *
 * Состояние поездки живёт в DriveStateHolder, а не здесь: сервис продолжает
 * работать, даже когда Activity уничтожена.
 */
class MainViewModel(private val container: AppContainer) : ViewModel() {

    private val backStack = MutableStateFlow(listOf(Screen.HOME))

    val currentScreen: StateFlow<Screen> = backStack
        .map { it.last() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Screen.HOME)

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val driveState = DriveStateHolder.state

    /** Состояние локальной базы камер: количество, версия, готовность. */
    val databaseInfo: StateFlow<CameraDatabaseInfo> = container.cameraRepository.info

    init {
        // База нужна и до поездки — чтобы показать на главном экране, что всё готово.
        viewModelScope.launch { container.cameraRepository.ensureLoaded() }
    }

    fun navigateTo(screen: Screen) {
        backStack.value = backStack.value + screen
    }

    /** @return false, если стек пуст и системную «назад» нужно обработать системе. */
    fun navigateBack(): Boolean {
        val stack = backStack.value
        if (stack.size <= 1) return false
        backStack.value = stack.dropLast(1)
        return true
    }

    fun replaceStack(screen: Screen) {
        backStack.value = listOf(screen)
    }

    fun completeOnboarding() = update { setOnboardingCompleted(true) }

    fun setVoiceAlerts(value: Boolean) = update { setVoiceAlerts(value) }

    fun setSoundSignal(value: Boolean) = update { setSoundSignal(value) }

    fun setVibration(value: Boolean) = update { setVibration(value) }

    fun setAlertOnlyWhenSpeeding(value: Boolean) = update { setAlertOnlyWhenSpeeding(value) }

    fun setSpeedTolerance(value: Int) = update { setSpeedTolerance(value) }

    fun setDistanceProfile(value: DistanceProfile) = update { setDistanceProfile(value) }

    fun setOverlayEnabled(value: Boolean) = update { setOverlayEnabled(value) }

    fun setKeepScreenOn(value: Boolean) = update { setKeepScreenOn(value) }

    fun setAutoStartByBluetooth(value: Boolean) = update { setAutoStartByBluetooth(value) }

    fun setAutoUpdateDatabase(value: Boolean) = update { setAutoUpdateDatabase(value) }

    private fun update(block: suspend ru.example.roadalert.data.settings.SettingsRepository.() -> Unit) {
        viewModelScope.launch { container.settingsRepository.block() }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(container) as T
    }
}
