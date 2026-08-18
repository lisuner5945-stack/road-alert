package ru.example.roadalert.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.example.roadalert.BuildConfig
import ru.example.roadalert.ads.HomeBanner
import ru.example.roadalert.debugtools.DebugTools
import ru.example.roadalert.domain.model.GpsStatus
import ru.example.roadalert.ui.about.AboutScreen
import ru.example.roadalert.ui.developer.DeveloperScreen
import ru.example.roadalert.ui.drive.DriveScreen
import ru.example.roadalert.ui.home.HomeScreen
import ru.example.roadalert.ui.home.HomeUiState
import ru.example.roadalert.ui.hud.HudScreen
import ru.example.roadalert.ui.onboarding.OnboardingScreen
import ru.example.roadalert.ui.settings.SettingsActions
import ru.example.roadalert.ui.settings.SettingsInfo
import ru.example.roadalert.ui.settings.SettingsScreen
import ru.example.roadalert.ui.theme.RoadAlertTheme

/** Колбэки debug-меню; в release-сборке экран недоступен. */
data class DeveloperActions(
    val onReplayRoute: () -> Unit = {},
    val onStopSimulation: () -> Unit = {},
    val onFakeCameraAhead: () -> Unit = {},
    val onFakeCameraBehind: () -> Unit = {},
    val onOpenAdsDebugPanel: () -> Unit = {},
)

/**
 * Корневой composable: выбирает экран и раздаёт колбэки.
 * Во время поездки и в HUD тема принудительно тёмная.
 */
@Composable
fun RoadAlertApp(
    viewModel: MainViewModel,
    onStartTrip: () -> Unit,
    onStopTrip: () -> Unit,
    onCheckUpdateNow: () -> Unit,
    onPickCarDevice: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    developerActions: DeveloperActions = DeveloperActions(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val driveState by viewModel.driveState.collectAsStateWithLifecycle()
    val screen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val databaseInfo by viewModel.databaseInfo.collectAsStateWithLifecycle()
    val simulationRunning by DebugTools.simulationRunning.collectAsStateWithLifecycle()

    val drivingScreen = screen == Screen.DRIVE || screen == Screen.HUD

    RoadAlertTheme(forceDark = drivingScreen) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BackHandler(enabled = screen != Screen.HOME) { viewModel.navigateBack() }

            val contentModifier = if (screen == Screen.HUD) {
                Modifier.fillMaxSize()
            } else {
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            }

            Box(contentModifier) {
                when {
                    !settings.onboardingCompleted -> OnboardingScreen(
                        onContinue = { viewModel.completeOnboarding() },
                    )

                    screen == Screen.DRIVE -> DriveScreen(
                        state = driveState,
                        onStopTrip = onStopTrip,
                        onOpenHud = { viewModel.navigateTo(Screen.HUD) },
                    )

                    screen == Screen.HUD -> HudScreen(
                        state = driveState,
                        onExit = { viewModel.navigateBack() },
                    )

                    screen == Screen.SETTINGS -> SettingsScreen(
                        settings = settings,
                        info = SettingsInfo(
                            appVersion = BuildConfig.VERSION_NAME,
                            databaseVersion = databaseInfo.databaseVersion ?: "не загружена",
                            cameraCount = databaseInfo.cameraCount,
                            lastUpdateCheck = formatUpdateCheck(settings.lastUpdateCheckAtMs),
                        ),
                        actions = SettingsActions(
                            onVoiceAlerts = viewModel::setVoiceAlerts,
                            onSoundSignal = viewModel::setSoundSignal,
                            onVibration = viewModel::setVibration,
                            onAlertOnlyWhenSpeeding = viewModel::setAlertOnlyWhenSpeeding,
                            onSpeedTolerance = viewModel::setSpeedTolerance,
                            onDistanceProfile = viewModel::setDistanceProfile,
                            onOverlayEnabled = { enabled ->
                                if (enabled) onRequestOverlayPermission() else viewModel.setOverlayEnabled(false)
                            },
                            onKeepScreenOn = viewModel::setKeepScreenOn,
                            onAutoStartBluetooth = viewModel::setAutoStartByBluetooth,
                            onPickCarDevice = onPickCarDevice,
                            onAutoUpdateDatabase = viewModel::setAutoUpdateDatabase,
                            onCheckUpdateNow = onCheckUpdateNow,
                            onOpenAbout = { viewModel.navigateTo(Screen.ABOUT) },
                            onBack = { viewModel.navigateBack() },
                        ),
                        adBannerSlot = { HomeBanner() },
                    )

                    screen == Screen.DEVELOPER && BuildConfig.DEVELOPER_MENU -> DeveloperScreen(
                        isSimulationRunning = simulationRunning,
                        onReplayRoute = developerActions.onReplayRoute,
                        onStopSimulation = developerActions.onStopSimulation,
                        onFakeCameraAhead = developerActions.onFakeCameraAhead,
                        onFakeCameraBehind = developerActions.onFakeCameraBehind,
                        onOpenAdsDebugPanel = developerActions.onOpenAdsDebugPanel,
                        onBack = { viewModel.navigateBack() },
                    )

                    screen == Screen.ABOUT -> AboutScreen(
                        appVersion = BuildConfig.VERSION_NAME,
                        databaseVersion = databaseInfo.databaseVersion ?: "не загружена",
                        cameraCount = databaseInfo.cameraCount,
                        onOpenPrivacyPolicy = onOpenPrivacyPolicy,
                        onBack = { viewModel.navigateBack() },
                    )

                    else -> HomeScreen(
                        state = HomeUiState(
                            databaseStatus = if (databaseInfo.isReady) "актуальна" else "не загружена",
                            databaseReady = databaseInfo.isReady,
                            cameraCount = databaseInfo.cameraCount,
                            gpsStatus = driveState.gpsStatus.takeIf { driveState.isTripActive }
                                ?: GpsStatus.WAITING,
                            autoStartDeviceName = settings.carDeviceName
                                .takeIf { settings.autoStartByBluetooth },
                            lastUpdateCheck = formatUpdateCheck(settings.lastUpdateCheckAtMs),
                        ),
                        onStartTrip = onStartTrip,
                        onOpenSettings = { viewModel.navigateTo(Screen.SETTINGS) },
                        onOpenAbout = { viewModel.navigateTo(Screen.ABOUT) },
                        developerMenuAvailable = BuildConfig.DEVELOPER_MENU,
                        onOpenDeveloper = { viewModel.navigateTo(Screen.DEVELOPER) },
                        adBannerSlot = { HomeBanner() },
                    )
                }
            }
        }
    }
}

/** Человекочитаемое время последней проверки обновления базы. */
private fun formatUpdateCheck(timestampMs: Long?): String? = timestampMs?.let {
    java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))
}
