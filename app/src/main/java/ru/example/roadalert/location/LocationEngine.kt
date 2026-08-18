package ru.example.roadalert.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import ru.example.roadalert.domain.model.GpsStatus
import ru.example.roadalert.domain.model.VehicleFix
import ru.example.roadalert.util.AppLog
import kotlin.math.abs

/**
 * Источник координат на базе системного LocationManager.
 *
 * Google Play Services сознательно не используется: приложение должно работать
 * на устройствах без GMS (ТЗ §12).
 */
class LocationEngine(private val context: Context) {

    fun interface FixListener {
        fun onFix(fix: VehicleFix)
    }

    fun interface StatusListener {
        fun onStatus(status: GpsStatus)
    }

    private val locationManager: LocationManager? =
        ContextCompat.getSystemService(context, LocationManager::class.java)

    private var fixListener: FixListener? = null
    private var statusListener: StatusListener? = null

    private var lastAcceptedLocation: Location? = null
    private var currentIntervalMs: Long = INTERVAL_STOPPED_MS
    private var activeProviders: List<String> = emptyList()

    private val listener = LocationListener { location -> handleLocation(location) }

    val hasFineLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private val hasCoarseLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    val isLocationEnabled: Boolean
        get() = locationManager?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                it.isLocationEnabled
            } else {
                it.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }
        } ?: false

    fun start(onFix: FixListener, onStatus: StatusListener): GpsStatus {
        fixListener = onFix
        statusListener = onStatus

        val status = currentStatus()
        onStatus.onStatus(status)
        if (status == GpsStatus.NO_PERMISSION || status == GpsStatus.DISABLED) {
            AppLog.event("GPS_START_REFUSED", "status" to status)
            return status
        }
        requestUpdates(INTERVAL_STOPPED_MS)
        return status
    }

    fun stop() {
        locationManager?.removeUpdates(listener)
        activeProviders = emptyList()
        lastAcceptedLocation = null
        fixListener = null
        statusListener = null
        AppLog.event("GPS_STOPPED")
    }

    private fun currentStatus(): GpsStatus = when {
        !hasFineLocationPermission && !hasCoarseLocationPermission -> GpsStatus.NO_PERMISSION
        !isLocationEnabled -> GpsStatus.DISABLED
        !hasFineLocationPermission -> GpsStatus.APPROXIMATE_ONLY
        else -> GpsStatus.WAITING
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdates(intervalMs: Long) {
        val manager = locationManager ?: return
        if (!hasFineLocationPermission && !hasCoarseLocationPermission) return

        val providers = buildList {
            if (manager.allProviders.contains(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            // Сетевой провайдер помогает быстрее получить первый fix в городе.
            if (manager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

        if (providers.isEmpty()) {
            statusListener?.onStatus(GpsStatus.DISABLED)
            return
        }

        manager.removeUpdates(listener)
        providers.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(
                    provider,
                    intervalMs,
                    MIN_DISTANCE_METERS,
                    listener,
                    Looper.getMainLooper(),
                )
            }.onFailure { AppLog.event("GPS_REQUEST_FAILED", "provider" to provider) }
        }
        activeProviders = providers
        currentIntervalMs = intervalMs
    }

    private fun handleLocation(location: Location) {
        if (!isAcceptable(location)) {
            AppLog.event("GPS_FIX_REJECTED", "accuracy" to location.accuracy)
            return
        }
        lastAcceptedLocation = location

        val speedKmh = (location.speed * MPS_TO_KMH).toDouble().coerceAtLeast(0.0)
        val bearing = if (location.hasBearing() && location.speed > MIN_SPEED_FOR_BEARING_MPS) {
            location.bearing.toDouble()
        } else {
            null
        }

        AppLog.event("GPS_FIX_ACCEPTED", "accuracy" to location.accuracy, "speed" to speedKmh.toInt())
        statusListener?.onStatus(
            if (hasFineLocationPermission) GpsStatus.READY else GpsStatus.APPROXIMATE_ONLY,
        )
        fixListener?.onFix(
            VehicleFix(
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = speedKmh,
                bearingDegrees = bearing,
                accuracyMeters = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE,
                timestampMs = location.time,
            ),
        )

        adjustInterval(speedKmh)
    }

    /**
     * Отбрасываем заведомо плохие fixes, но только если есть более свежий
     * качественный: иначе рискуем остаться вообще без координат.
     */
    private fun isAcceptable(location: Location): Boolean {
        if (location.latitude == 0.0 && location.longitude == 0.0) return false
        val previous = lastAcceptedLocation ?: return true

        val ageMs = location.time - previous.time
        if (ageMs < 0) return false

        val accuracy = if (location.hasAccuracy()) location.accuracy else Float.MAX_VALUE
        val previousAccuracy = if (previous.hasAccuracy()) previous.accuracy else Float.MAX_VALUE
        val previousIsFresh = abs(ageMs) < STALE_FIX_MS

        return !(accuracy > BAD_ACCURACY_METERS && previousIsFresh && previousAccuracy < accuracy)
    }

    /** Частота обновлений зависит от скорости (ТЗ §12): экономим батарею на стоянке. */
    private fun adjustInterval(speedKmh: Double) {
        val target = when {
            speedKmh < 5 -> INTERVAL_STOPPED_MS
            speedKmh < 30 -> INTERVAL_SLOW_MS
            else -> INTERVAL_FAST_MS
        }
        if (target != currentIntervalMs) {
            AppLog.event("GPS_INTERVAL_CHANGED", "ms" to target)
            requestUpdates(target)
        }
    }

    private companion object {
        const val MPS_TO_KMH = 3.6f
        const val MIN_DISTANCE_METERS = 0f
        const val MIN_SPEED_FOR_BEARING_MPS = 2.0f

        const val INTERVAL_STOPPED_MS = 7_000L
        const val INTERVAL_SLOW_MS = 2_500L
        const val INTERVAL_FAST_MS = 1_000L

        const val BAD_ACCURACY_METERS = 60f
        const val STALE_FIX_MS = 15_000L
    }
}
