package ru.example.roadalert.detection

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Геометрия на сфере. Чистые функции без зависимостей от Android —
 * покрываются обычными JVM unit-тестами.
 */
object GeoMath {

    const val EARTH_RADIUS_METERS = 6_371_008.8

    private const val DEG_TO_RAD = Math.PI / 180.0
    private const val RAD_TO_DEG = 180.0 / Math.PI

    /** Расстояние по большой окружности, метры. */
    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = (lat2 - lat1) * DEG_TO_RAD
        val dLon = (lon2 - lon1) * DEG_TO_RAD
        val a = sin(dLat / 2).let { it * it } +
            cos(lat1 * DEG_TO_RAD) * cos(lat2 * DEG_TO_RAD) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    /** Начальный курс из точки 1 в точку 2, градусы 0..360 (0 = север). */
    fun bearingDegrees(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val phi1 = lat1 * DEG_TO_RAD
        val phi2 = lat2 * DEG_TO_RAD
        val dLambda = (lon2 - lon1) * DEG_TO_RAD
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return normalizeDegrees(atan2(y, x) * RAD_TO_DEG)
    }

    /** Приводит угол к диапазону 0..360. */
    fun normalizeDegrees(degrees: Double): Double {
        val mod = degrees % 360.0
        return if (mod < 0) mod + 360.0 else mod
    }

    /**
     * Наименьшая разница между двумя курсами, 0..180.
     * Корректно работает через ноль: 359° и 1° дают 2°.
     */
    fun angularDifference(a: Double, b: Double): Double {
        val diff = abs(normalizeDegrees(a) - normalizeDegrees(b)) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }

    /** Точка на заданном расстоянии и курсе от исходной. */
    fun destinationPoint(
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        distanceMeters: Double,
    ): Pair<Double, Double> {
        val angular = distanceMeters / EARTH_RADIUS_METERS
        val phi1 = lat * DEG_TO_RAD
        val lambda1 = lon * DEG_TO_RAD
        val theta = bearingDeg * DEG_TO_RAD
        val phi2 = asin(sin(phi1) * cos(angular) + cos(phi1) * sin(angular) * cos(theta))
        val lambda2 = lambda1 + atan2(
            sin(theta) * sin(angular) * cos(phi1),
            cos(angular) - sin(phi1) * sin(phi2),
        )
        val lonDeg = ((lambda2 * RAD_TO_DEG + 540.0) % 360.0) - 180.0
        return phi2 * RAD_TO_DEG to lonDeg
    }

    /** Bounding box вокруг точки: [minLat, minLon, maxLat, maxLon]. */
    fun boundingBox(lat: Double, lon: Double, radiusMeters: Double): BoundingBox {
        val latDelta = radiusMeters / EARTH_RADIUS_METERS * RAD_TO_DEG
        // На полюсах cos(lat) стремится к нулю — ограничиваем, чтобы не получить бесконечность.
        val cosLat = max(cos(lat * DEG_TO_RAD), 1e-6)
        val lonDelta = radiusMeters / (EARTH_RADIUS_METERS * cosLat) * RAD_TO_DEG
        return BoundingBox(
            minLatitude = lat - latDelta,
            minLongitude = lon - lonDelta,
            maxLatitude = lat + latDelta,
            maxLongitude = lon + lonDelta,
        )
    }
}

data class BoundingBox(
    val minLatitude: Double,
    val minLongitude: Double,
    val maxLatitude: Double,
    val maxLongitude: Double,
) {
    fun contains(lat: Double, lon: Double): Boolean =
        lat in minLatitude..maxLatitude && lon in minLongitude..maxLongitude

    fun intersects(other: BoundingBox): Boolean =
        minLatitude <= other.maxLatitude &&
            maxLatitude >= other.minLatitude &&
            minLongitude <= other.maxLongitude &&
            maxLongitude >= other.minLongitude
}
