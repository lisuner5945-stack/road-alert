package ru.example.roadalert.detection

import ru.example.roadalert.domain.model.VehicleFix

/**
 * Оценка курса автомобиля.
 *
 * Location.bearing на малой скорости и при слабом сигнале скачет, поэтому:
 *  1) при уверенной скорости берём курс из fix;
 *  2) иначе считаем курс по смещению между точками;
 *  3) если и этого мало — возвращаем последний известный курс.
 */
class BearingEstimator {

    private var lastLatitude: Double? = null
    private var lastLongitude: Double? = null
    private var lastBearing: Double? = null

    val currentBearing: Double? get() = lastBearing

    fun update(fix: VehicleFix): Double? {
        val previousLat = lastLatitude
        val previousLon = lastLongitude

        val computed = if (previousLat != null && previousLon != null) {
            val displacement = GeoMath.haversineMeters(previousLat, previousLon, fix.latitude, fix.longitude)
            if (displacement >= DetectionConfig.BEARING_MIN_DISPLACEMENT_METERS) {
                GeoMath.bearingDegrees(previousLat, previousLon, fix.latitude, fix.longitude)
            } else {
                null
            }
        } else {
            null
        }

        val fromFix = fix.bearingDegrees?.takeIf { fix.speedKmh >= DetectionConfig.BEARING_MIN_SPEED_KMH }

        val bearing = fromFix ?: computed ?: lastBearing
        if (computed != null || fromFix != null) {
            lastLatitude = fix.latitude
            lastLongitude = fix.longitude
        } else if (previousLat == null) {
            lastLatitude = fix.latitude
            lastLongitude = fix.longitude
        }
        lastBearing = bearing
        return bearing
    }

    fun reset() {
        lastLatitude = null
        lastLongitude = null
        lastBearing = null
    }
}
