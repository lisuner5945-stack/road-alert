package ru.example.roadalert.debugtools

import kotlinx.coroutines.flow.MutableStateFlow
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraSource
import ru.example.roadalert.domain.model.CameraType

/**
 * Состояние debug-инструментов. В release-сборке сюда никто не пишет:
 * developer menu выключен, а вызовы обёрнуты проверкой BuildConfig.DEVELOPER_MENU.
 */
object DebugTools {

    /** Искусственные камеры, добавляемые к настоящим при поиске. */
    val fakeCameras = MutableStateFlow<List<CameraPoint>>(emptyList())

    val simulationRunning = MutableStateFlow(false)

    fun addFakeCamera(camera: CameraPoint) {
        fakeCameras.value = (fakeCameras.value + camera).takeLast(MAX_FAKE_CAMERAS)
    }

    fun clear() {
        fakeCameras.value = emptyList()
    }

    fun fakeCamera(
        latitude: Double,
        longitude: Double,
        speedLimitKmh: Int? = 60,
        directionDegrees: Double? = null,
        idSuffix: String = "fake",
    ) = CameraPoint(
        id = "debug:$idSuffix:${latitude.hashCode()}:${longitude.hashCode()}",
        latitude = latitude,
        longitude = longitude,
        type = CameraType.SPEED_CAMERA,
        speedLimitKmh = speedLimitKmh,
        directionDegrees = directionDegrees,
        osmObjectType = null,
        osmObjectId = null,
        source = CameraSource.USER,
        updatedAt = System.currentTimeMillis(),
    )

    private const val MAX_FAKE_CAMERAS = 8
}
