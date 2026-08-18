package ru.example.roadalert.drive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import ru.example.roadalert.R
import ru.example.roadalert.BuildConfig
import ru.example.roadalert.alerts.AlertStateMachine
import ru.example.roadalert.alerts.VoiceAlertManager
import ru.example.roadalert.app.RoadAlertApplication
import ru.example.roadalert.data.settings.AppSettings
import ru.example.roadalert.detection.BearingEstimator
import ru.example.roadalert.debugtools.DebugTools
import ru.example.roadalert.debugtools.RouteSimulator
import ru.example.roadalert.detection.CameraDetectionEngine
import ru.example.roadalert.domain.model.ActiveAlert
import ru.example.roadalert.domain.model.GpsStatus
import ru.example.roadalert.domain.model.VehicleFix
import ru.example.roadalert.location.LocationEngine
import ru.example.roadalert.overlay.OverlayController
import ru.example.roadalert.ui.MainActivity
import ru.example.roadalert.util.AppLog

/**
 * Сердце поездки: foreground service типа location (ТЗ §12).
 *
 * Живёт, пока идёт поездка, и продолжает работать при выключенном экране.
 * Вне поездки GPS не включается никогда.
 */
class DriveForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var container: ru.example.roadalert.app.AppContainer
    private lateinit var locationEngine: LocationEngine
    private lateinit var voice: VoiceAlertManager

    private val bearingEstimator = BearingEstimator()
    private val alertStateMachine = AlertStateMachine(
        distanceProfileProvider = { settings.value.distanceProfile },
    )
    private val settings = MutableStateFlow(AppSettings())

    private lateinit var detectionEngine: CameraDetectionEngine
    private var overlayController: OverlayController? = null
    private var simulationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        container = (application as RoadAlertApplication).container
        locationEngine = LocationEngine(this)
        voice = VoiceAlertManager(this)
        detectionEngine = CameraDetectionEngine { latitude, longitude, radius ->
            val real = container.cameraRepository.camerasNear(latitude, longitude, radius)
            if (BuildConfig.DEVELOPER_MENU) real + debugCamerasNear(latitude, longitude, radius) else real
        }

        overlayController = OverlayController(
            context = this,
            onPositionChanged = { x, y ->
                scope.launch { container.settingsRepository.setOverlayPosition(x, y) }
            },
            onCloseRequested = {
                scope.launch { container.settingsRepository.setOverlayEnabled(false) }
            },
        )

        scope.launch {
            container.settingsRepository.settings.collect { settings.value = it }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTrip()
                return START_NOT_STICKY
            }

            ACTION_SIMULATE_START -> if (BuildConfig.DEVELOPER_MENU) {
                // Симуляция не требует настоящего GPS: реальные координаты не запрашиваем.
                startTrip(withRealLocation = false)
                startSimulation()
            }

            ACTION_SIMULATE_STOP -> stopSimulation()

            else -> startTrip()
        }
        return START_STICKY
    }

    private fun startTrip(withRealLocation: Boolean = true) {
        if (DriveStateHolder.isTripActive) return

        createNotificationChannel()
        val notification = buildNotification(speedKmh = null, alertText = null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        DriveStateHolder.update { it.copy(isTripActive = true, tripStartedAtMs = System.currentTimeMillis()) }
        AppLog.event("TRIP_STARTED")

        voice.initialize()
        scope.launch { container.cameraRepository.ensureLoaded() }

        if (!withRealLocation) return

        val status = locationEngine.start(
            onFix = { fix -> onFix(fix) },
            onStatus = { status -> DriveStateHolder.update { it.copy(gpsStatus = status) } },
        )
        if (status == GpsStatus.NO_PERMISSION || status == GpsStatus.DISABLED) {
            // Без координат смысла держать сервис нет: честно останавливаемся.
            AppLog.event("TRIP_ABORTED", "reason" to status)
            stopTrip()
        }
    }

    private fun stopTrip() {
        stopSimulation()
        locationEngine.stop()
        voice.shutdown()
        overlayController?.hide()
        bearingEstimator.reset()
        alertStateMachine.reset()
        DriveStateHolder.reset()
        AppLog.event("TRIP_STOPPED")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { locationEngine.stop() }
        runCatching { voice.shutdown() }
        runCatching { overlayController?.hide() }
        DriveStateHolder.reset()
        super.onDestroy()
    }

    /** Debug-камеры участвуют в поиске только в debug-сборке (ТЗ §47). */
    private fun debugCamerasNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): List<ru.example.roadalert.domain.model.CameraPoint> =
        DebugTools.fakeCameras.value.filter {
            ru.example.roadalert.detection.GeoMath.haversineMeters(
                latitude,
                longitude,
                it.latitude,
                it.longitude,
            ) <= radiusMeters
        }

    /**
     * Симулятор маршрута: синтетические точки идут ровно тем же путём,
     * что и настоящие fixes, но системный LocationManager не подменяется.
     */
    private fun startSimulation() {
        if (!BuildConfig.DEVELOPER_MENU) return
        simulationJob?.cancel()
        DebugTools.simulationRunning.value = true
        simulationJob = scope.launch {
            val route = RouteSimulator.straightRoute()
            val fixes = RouteSimulator.toFixes(route, System.currentTimeMillis())
            fixes.forEach { fix ->
                onFix(fix)
                delay(SIMULATION_STEP_MS)
            }
            DebugTools.simulationRunning.value = false
        }
        AppLog.event("SIMULATION_STARTED")
    }

    private fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        if (DebugTools.simulationRunning.value) {
            DebugTools.simulationRunning.value = false
            AppLog.event("SIMULATION_STOPPED")
        }
    }

    private fun onFix(fix: VehicleFix) {
        val bearing = bearingEstimator.update(fix)
        val candidates = detectionEngine.detect(fix, bearing)
        val current = settings.value
        val now = System.currentTimeMillis()

        val best = candidates.firstOrNull()
        AppLog.event("CAMERA_CANDIDATES", "count" to candidates.size)
        val events = alertStateMachine.onUpdate(candidates, fix.speedKmh, now)

        val limit = best?.camera?.speedLimitKmh
        val overLimit = limit != null && fix.speedKmh > limit + current.speedToleranceKmh

        DriveStateHolder.update { state ->
            state.copy(
                speedKmh = fix.speedKmh,
                alert = best?.let { ActiveAlert(it, alertStateMachine.stageOf(it.camera.id)) },
                isOverSpeedLimit = overLimit,
            )
        }

        events.forEach { event ->
            // «Предупреждать только при превышении» не должно глушить финальное
            // предупреждение о камере, если лимит вообще неизвестен.
            val limitKnown = event.detected.camera.speedLimitKmh != null
            val speeding = event.detected.camera.speedLimitKmh
                ?.let { fix.speedKmh > it + current.speedToleranceKmh } ?: false
            if (current.alertOnlyWhenSpeeding && limitKnown && !speeding) return@forEach

            voice.announce(
                event = event,
                currentSpeedKmh = fix.speedKmh,
                options = VoiceAlertManager.Options(
                    voiceEnabled = current.voiceAlerts,
                    soundEnabled = current.soundSignal,
                    vibrationEnabled = current.vibration,
                ),
                nowMs = now,
            )
            AppLog.event("ALERT", "camera_id" to event.detected.camera.id, "stage" to event.stage)
        }

        updateOverlay(best?.let { ActiveAlert(it, alertStateMachine.stageOf(it.camera.id)) })
        updateNotification(fix.speedKmh, best?.distanceMeters)
    }

    /** Overlay появляется только при активной поездке и только если есть что показать. */
    private fun updateOverlay(alert: ActiveAlert?) {
        val controller = overlayController ?: return
        val current = settings.value
        val shouldShow = current.overlayEnabled &&
            DriveStateHolder.isTripActive &&
            alert != null &&
            alert.distanceMeters <= OVERLAY_VISIBLE_DISTANCE_METERS

        if (shouldShow && alert != null) {
            controller.show(alert, current.overlayPositionX, current.overlayPositionY)
        } else {
            controller.hide()
        }
    }

    private fun updateNotification(speedKmh: Double, cameraDistanceMeters: Double?) {
        val alertText = cameraDistanceMeters?.let { "камера через ${(it / 10).toInt() * 10} м" }
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, buildNotification(speedKmh, alertText))
    }

    private fun buildNotification(speedKmh: Double?, alertText: String?) : android.app.Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, DriveForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val text = listOfNotNull(
            speedKmh?.let { "${it.toInt()} км/ч" },
            alertText,
        ).joinToString(" · ").ifEmpty { "Ожидание сигнала GPS" }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_trip_title))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.notification_stop), stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = ContextCompat.getSystemService(this, NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_trip),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_trip_description)
            setShowBadge(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {

        const val ACTION_START = "ru.example.roadalert.action.START_TRIP"
        const val ACTION_STOP = "ru.example.roadalert.action.STOP_TRIP"
        const val ACTION_SIMULATE_START = "ru.example.roadalert.action.SIMULATE_START"
        const val ACTION_SIMULATE_STOP = "ru.example.roadalert.action.SIMULATE_STOP"

        private const val CHANNEL_ID = "trip_status"
        private const val OVERLAY_VISIBLE_DISTANCE_METERS = 1500.0
        private const val SIMULATION_STEP_MS = 1000L
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, DriveForegroundService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun simulate(context: Context, start: Boolean) {
            val action = if (start) ACTION_SIMULATE_START else ACTION_SIMULATE_STOP
            val intent = Intent(context, DriveForegroundService::class.java).setAction(action)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DriveForegroundService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
