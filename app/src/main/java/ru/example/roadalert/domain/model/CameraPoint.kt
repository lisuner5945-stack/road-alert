package ru.example.roadalert.domain.model

/**
 * Тип камеры. В 1.0 гарантированно поддерживается SPEED_CAMERA (ТЗ §7),
 * остальные показываются только при достоверных данных OSM.
 */
enum class CameraType {
    SPEED_CAMERA,
    RED_LIGHT,
    SPEED_AND_RED_LIGHT,
    AVERAGE_SPEED_START,
    AVERAGE_SPEED_END,
    LANE_CONTROL,
    UNKNOWN;

    companion object {
        fun fromRaw(raw: String?): CameraType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class CameraSource {
    OPENSTREETMAP,
    BUNDLED,
    USER;

    companion object {
        fun fromRaw(raw: String?): CameraSource =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: OPENSTREETMAP
    }
}

/**
 * Точка контроля скорости.
 *
 * @param directionDegrees направление, В КОТОРОМ камера смотрит/контролирует поток
 *        (0..360, 0 = север). null — направление неизвестно.
 */
data class CameraPoint(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val type: CameraType,
    val speedLimitKmh: Int?,
    val directionDegrees: Double?,
    val osmObjectType: String?,
    val osmObjectId: Long?,
    val source: CameraSource,
    val updatedAt: Long,
) {
    val hasKnownDirection: Boolean get() = directionDegrees != null
}
