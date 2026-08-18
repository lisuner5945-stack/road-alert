package ru.example.roadalert.detection

import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.DetectedCamera
import ru.example.roadalert.domain.model.VehicleFix

/**
 * Отбирает камеры, релевантные текущему движению.
 *
 * Главная задача — не «найти все камеры рядом», а отсеять встречные и
 * параллельные: именно ложные срабатывания убивают доверие к антирадару (ТЗ §17).
 */
class CameraDetectionEngine(
    private val cameraSource: (latitude: Double, longitude: Double, radiusMeters: Double) -> List<CameraPoint>,
) {

    /**
     * @param bearing курс автомобиля; null — курс неизвестен (стоим/слабый сигнал).
     * @return кандидаты, отсортированные по расстоянию; самый релевантный первый.
     */
    fun detect(fix: VehicleFix, bearing: Double?): List<DetectedCamera> {
        val radius = DetectionConfig.searchRadiusMeters(fix.speedKmh)
        val candidates = cameraSource(fix.latitude, fix.longitude, radius)
        if (candidates.isEmpty()) return emptyList()

        return candidates.mapNotNull { camera -> evaluate(fix, bearing, camera) }
            .sortedWith(compareByDescending<DetectedCamera> { it.confidence }.thenBy { it.distanceMeters })
    }

    private fun evaluate(fix: VehicleFix, bearing: Double?, camera: CameraPoint): DetectedCamera? {
        val distance = GeoMath.haversineMeters(fix.latitude, fix.longitude, camera.latitude, camera.longitude)
        val bearingToCamera = GeoMath.bearingDegrees(
            fix.latitude,
            fix.longitude,
            camera.latitude,
            camera.longitude,
        )

        // Курс неизвестен: не выдумываем направление, но и не теряем камеру рядом.
        if (bearing == null) {
            return DetectedCamera(
                camera = camera,
                distanceMeters = distance,
                angleDifferenceDegrees = 0.0,
                confidence = CONFIDENCE_NO_BEARING,
            )
        }

        val angleDifference = GeoMath.angularDifference(bearing, bearingToCamera)

        // Совсем рядом угол на камеру скачет — по нему уже нельзя судить.
        val positionConfidence = when {
            distance < DetectionConfig.ANGLE_CHECK_MIN_DISTANCE_METERS -> CONFIDENCE_VERY_CLOSE
            angleDifference <= DetectionConfig.ANGLE_AHEAD_DEGREES -> CONFIDENCE_AHEAD
            angleDifference <= DetectionConfig.ANGLE_UNCERTAIN_DEGREES -> CONFIDENCE_UNCERTAIN
            else -> return null
        }

        val directionConfidence = directionConfidence(camera, bearing) ?: return null

        return DetectedCamera(
            camera = camera,
            distanceMeters = distance,
            angleDifferenceDegrees = angleDifference,
            confidence = positionConfidence * directionConfidence,
        )
    }

    /**
     * Проверка направления самой камеры.
     *
     * OSM-тег direction означает направление, в котором камера контролирует поток;
     * оно должно примерно совпадать с курсом автомобиля. Если направление
     * неизвестно — не отбрасываем камеру, но снижаем уверенность.
     *
     * @return множитель уверенности или null, если камера точно не для нас.
     */
    private fun directionConfidence(camera: CameraPoint, vehicleBearing: Double): Double? {
        val cameraDirection = camera.directionDegrees ?: return CONFIDENCE_UNKNOWN_DIRECTION
        val difference = GeoMath.angularDifference(cameraDirection, vehicleBearing)
        return when {
            difference <= DetectionConfig.CAMERA_DIRECTION_TOLERANCE_DEGREES -> 1.0
            difference <= DetectionConfig.CAMERA_DIRECTION_TOLERANCE_DEGREES + 30.0 -> CONFIDENCE_UNCERTAIN
            else -> null
        }
    }

    private companion object {
        const val CONFIDENCE_AHEAD = 1.0
        const val CONFIDENCE_UNCERTAIN = 0.6
        const val CONFIDENCE_VERY_CLOSE = 0.9
        const val CONFIDENCE_NO_BEARING = 0.4
        const val CONFIDENCE_UNKNOWN_DIRECTION = 0.85
    }
}
