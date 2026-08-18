package ru.example.roadalert.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.example.roadalert.BuildConfig
import ru.example.roadalert.app.RoadAlertApplication
import ru.example.roadalert.bluetooth.CarConnectionManager
import ru.example.roadalert.data.updates.UpdateResult
import ru.example.roadalert.debugtools.DebugTools
import ru.example.roadalert.debugtools.RouteSimulator
import ru.example.roadalert.detection.GeoMath
import ru.example.roadalert.drive.DriveForegroundService
import ru.example.roadalert.util.AppLog

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as RoadAlertApplication).container)
    }

    /** Разрешения запрашиваются только в момент, когда пользователь сам начинает поездку. */
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (fineGranted) {
            launchTrip()
        } else {
            AppLog.event("LOCATION_PERMISSION_DENIED")
            Toast.makeText(
                this,
                "Без точной геолокации приложение не сможет предупреждать о камерах",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private val container by lazy { (application as RoadAlertApplication).container }

    private val carConnectionManager by lazy { CarConnectionManager(this) }

    /** Результат системного диалога выбора автомобиля (CompanionDeviceManager). */
    private val carDeviceLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val device = carConnectionManager.extractDevice(result.data)
        if (device == null) {
            AppLog.event("CAR_DEVICE_NOT_SELECTED")
            return@registerForActivityResult
        }
        lifecycleScope.launch {
            container.settingsRepository.setCarDevice(device.first, device.second)
            container.settingsRepository.setAutoStartByBluetooth(true)
        }
        Toast.makeText(this, "Автомобиль выбран: ${device.first}", Toast.LENGTH_SHORT).show()
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pickCarDevice()
        } else {
            Toast.makeText(
                this,
                "Без разрешения Bluetooth автозапуск в машине недоступен",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        keepScreenOnWhileDriving()
        handleStartTripIntent(intent)

        setContent {
            RoadAlertApp(
                viewModel = viewModel,
                onStartTrip = { startTrip() },
                onStopTrip = { stopTrip() },
                onCheckUpdateNow = { checkDatabaseUpdate() },
                onPickCarDevice = { requestCarDevice() },
                onOpenPrivacyPolicy = { openPrivacyPolicy() },
                onRequestOverlayPermission = { requestOverlayPermission() },
                developerActions = developerActions(),
            )
        }
    }

    private fun startTrip() {
        val missing = buildList {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
            return
        }
        launchTrip()
    }

    private fun launchTrip() {
        AppLog.event("TRIP_START_REQUESTED")
        DriveForegroundService.start(this)
        viewModel.navigateTo(Screen.DRIVE)
    }

    private fun stopTrip() {
        AppLog.event("TRIP_STOP_REQUESTED")
        DriveForegroundService.stop(this)
        viewModel.replaceStack(Screen.HOME)

        // Реклама — только ПОСЛЕ явного завершения поездки и только если это
        // разрешено правилами частоты (ТЗ §25).
        val ads = (application as RoadAlertApplication).container.adsManager
        ads.onTripCompleted()
        ads.showPostTripInterstitialIfEligible(this)
        ads.preloadPostTripInterstitial()
    }

    /** Экран не гаснет только во время поездки и только если это включено в настройках. */
    private fun keepScreenOnWhileDriving() {
        lifecycleScope.launch {
            combine(viewModel.settings, viewModel.driveState) { settings, drive ->
                settings.keepScreenOnInDrive && drive.isTripActive
            }.collect { keepOn ->
                if (keepOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    /** Ручная проверка обновления базы из настроек. */
    private fun checkDatabaseUpdate() {
        AppLog.event("DB_UPDATE_REQUESTED_MANUALLY")
        Toast.makeText(this, "Проверяем обновление базы…", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val result = container.cameraUpdateManager.checkAndUpdate(force = false)
            container.recordUpdateCheck()
            val message = when (result) {
                is UpdateResult.Updated -> "База обновлена: ${result.cameraCount} камер"
                UpdateResult.AlreadyUpToDate -> "База уже актуальна"
                is UpdateResult.Failed -> "Не удалось обновить: ${result.reason}"
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun requestCarDevice() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        ) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            return
        }
        pickCarDevice()
    }

    private fun pickCarDevice() {
        AppLog.event("CAR_DEVICE_PICKER_REQUESTED")
        carConnectionManager.requestAssociation(
            onReady = { sender ->
                runCatching { carDeviceLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                    .onFailure { AppLog.event("CAR_PICKER_LAUNCH_FAILED", "reason" to it.message) }
            },
            onFailure = { reason ->
                Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            },
        )
    }

    /** Инструменты разработчика доступны только в debug-сборке. */
    private fun developerActions(): DeveloperActions {
        if (!BuildConfig.DEVELOPER_MENU) return DeveloperActions()
        return DeveloperActions(
            onReplayRoute = { DriveForegroundService.simulate(this, start = true) },
            onStopSimulation = { DriveForegroundService.simulate(this, start = false) },
            onFakeCameraAhead = { addFakeCamera(distanceMeters = 900.0) },
            onFakeCameraBehind = { addFakeCamera(distanceMeters = -500.0) },
            onOpenAdsDebugPanel = {
                runCatching { com.yandex.mobile.ads.common.YandexAds.showDebugPanel(this) }
                    .onFailure { Toast.makeText(this, "Debug-панель недоступна", Toast.LENGTH_SHORT).show() }
            },
        )
    }

    /** Ставит тестовую камеру относительно старта маршрута симулятора. */
    private fun addFakeCamera(distanceMeters: Double) {
        val bearing = if (distanceMeters >= 0) 0.0 else 180.0
        val (lat, lon) = GeoMath.destinationPoint(
            RouteSimulator.DEFAULT_START_LAT,
            RouteSimulator.DEFAULT_START_LON,
            bearing,
            kotlin.math.abs(distanceMeters),
        )
        DebugTools.addFakeCamera(
            DebugTools.fakeCamera(
                latitude = lat,
                longitude = lon,
                directionDegrees = 0.0,
                idSuffix = if (distanceMeters >= 0) "ahead" else "behind",
            ),
        )
        Toast.makeText(this, "Тестовая камера добавлена", Toast.LENGTH_SHORT).show()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun requestOverlayPermission() {
        AppLog.event("OVERLAY_PERMISSION_REQUESTED")
        if (Settings.canDrawOverlays(this)) {
            viewModel.setOverlayEnabled(true)
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri(),
        )
        runCatching { startActivity(intent) }
            .onFailure { AppLog.event("OVERLAY_SETTINGS_UNAVAILABLE") }
    }

    override fun onResume() {
        super.onResume()
        // Пользователь мог выдать разрешение на overlay во внешнем экране настроек.
        if (viewModel.settings.value.overlayEnabled && !Settings.canDrawOverlays(this)) {
            viewModel.setOverlayEnabled(false)
        }
    }

    private fun openPrivacyPolicy() {
        val intent = Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())
        runCatching { startActivity(intent) }
            .onFailure { AppLog.event("PRIVACY_POLICY_OPEN_FAILED") }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStartTripIntent(intent)
    }

    /** Тап по уведомлению «Автомобиль подключён» — запуск поездки в один тап. */
    private fun handleStartTripIntent(intent: Intent?) {
        if (intent?.action != ACTION_START_TRIP) return
        intent.action = null
        startTrip()
    }

    companion object {

        const val ACTION_START_TRIP = "ru.example.roadalert.action.OPEN_AND_START_TRIP"

        /** Публикуется через GitHub Pages из каталога docs/ (ветка main). */
        private const val PRIVACY_POLICY_URL = "https://lisuner5945-stack.github.io/road-alert/privacy/"
    }
}
