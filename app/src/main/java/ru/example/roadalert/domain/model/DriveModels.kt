package ru.example.roadalert.domain.model

/** Качественный GPS-fix, уже прошедший фильтрацию. */
data class VehicleFix(
    val latitude: Double,
    val longitude: Double,
    /** Скорость в км/ч, всегда >= 0. */
    val speedKmh: Double,
    /** Курс 0..360 или null, если достоверного курса нет. */
    val bearingDegrees: Double?,
    val accuracyMeters: Float,
    val timestampMs: Long,
)

/** Стадия предупреждения об одной камере (ТЗ §18). */
enum class AlertStage {
    NOT_SEEN,
    PRE_ALERTED,
    MAIN_ALERTED,
    FINAL_ALERTED,
    PASSED,
    COOLDOWN,
}

/** Камера, признанная релевантной для текущего движения. */
data class DetectedCamera(
    val camera: CameraPoint,
    val distanceMeters: Double,
    /** Разница между курсом автомобиля и направлением на камеру, 0..180. */
    val angleDifferenceDegrees: Double,
    /** 0..1: насколько уверенно камера считается «впереди по ходу». */
    val confidence: Double,
)

/** Активное предупреждение, показываемое пользователю. */
data class ActiveAlert(
    val detected: DetectedCamera,
    val stage: AlertStage,
) {
    val camera: CameraPoint get() = detected.camera
    val distanceMeters: Double get() = detected.distanceMeters
}

/** Состояние поездки, которое видит UI (Drive, HUD, Overlay, уведомление). */
data class DriveState(
    val isTripActive: Boolean = false,
    val gpsStatus: GpsStatus = GpsStatus.WAITING,
    val speedKmh: Double? = null,
    val alert: ActiveAlert? = null,
    val isOverSpeedLimit: Boolean = false,
    val averageSpeedSection: AverageSpeedSectionState? = null,
    val tripStartedAtMs: Long? = null,
) {
    val speedLimitKmh: Int? get() = alert?.camera?.speedLimitKmh ?: averageSpeedSection?.limitKmh
}

enum class GpsStatus {
    /** Разрешение не выдано. */
    NO_PERMISSION,

    /** Геолокация выключена в системе. */
    DISABLED,

    /** Ждём первый качественный fix. */
    WAITING,

    /** Есть точные координаты. */
    READY,

    /** Выдана только приблизительная геолокация. */
    APPROXIMATE_ONLY,
}

/** Участок контроля средней скорости (ТЗ §24). */
data class AverageSpeedSectionState(
    val averageSpeedKmh: Double,
    val limitKmh: Int?,
    val remainingMeters: Double,
)
