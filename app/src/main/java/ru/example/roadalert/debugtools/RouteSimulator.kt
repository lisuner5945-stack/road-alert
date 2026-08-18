package ru.example.roadalert.debugtools

import ru.example.roadalert.detection.GeoMath
import ru.example.roadalert.domain.model.VehicleFix
import kotlin.math.max

/**
 * Debug-симулятор маршрута (ТЗ §47).
 *
 * Подаёт синтетические координаты прямо в детектор, НЕ подменяя системный
 * LocationManager. Симулятор не заменяет реальную дорожную проверку.
 */
object RouteSimulator {

    data class RoutePoint(val latitude: Double, val longitude: Double, val speedKmh: Double)

    /** Разбор простого JSON-маршрута: [{"lat":..,"lon":..,"speed":..}, ...] */
    fun parseJsonRoute(text: String): List<RoutePoint> {
        val regex = Regex(
            """\{[^}]*?"lat"\s*:\s*(-?\d+(?:\.\d+)?)[^}]*?"lon"\s*:\s*(-?\d+(?:\.\d+)?)""" +
                """(?:[^}]*?"speed"\s*:\s*(-?\d+(?:\.\d+)?))?[^}]*}""",
        )
        return regex.findAll(text).mapNotNull { match ->
            val lat = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val lon = match.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
            RoutePoint(lat, lon, match.groupValues[3].toDoubleOrNull() ?: DEFAULT_SPEED_KMH)
        }.toList()
    }

    /** Разбор GPX-трека: берём только координаты точек. */
    fun parseGpxRoute(text: String, speedKmh: Double = DEFAULT_SPEED_KMH): List<RoutePoint> {
        val regex = Regex("""<(?:trkpt|rtept|wpt)[^>]*?lat="(-?\d+(?:\.\d+)?)"[^>]*?lon="(-?\d+(?:\.\d+)?)"""")
        return regex.findAll(text).mapNotNull { match ->
            val lat = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
            val lon = match.groupValues[2].toDoubleOrNull() ?: return@mapNotNull null
            RoutePoint(lat, lon, speedKmh)
        }.toList()
    }

    /**
     * Прямой участок из стартовой точки — маршрут по умолчанию,
     * если файл трека не подсунут.
     */
    fun straightRoute(
        startLatitude: Double = DEFAULT_START_LAT,
        startLongitude: Double = DEFAULT_START_LON,
        bearingDegrees: Double = 0.0,
        speedKmh: Double = DEFAULT_SPEED_KMH,
        points: Int = 120,
        stepSeconds: Double = 1.0,
    ): List<RoutePoint> {
        val stepMeters = max(1.0, speedKmh / 3.6 * stepSeconds)
        return (0 until points).map { index ->
            val (lat, lon) = GeoMath.destinationPoint(
                startLatitude,
                startLongitude,
                bearingDegrees,
                stepMeters * index,
            )
            RoutePoint(lat, lon, speedKmh)
        }
    }

    /** Превращает точки маршрута в последовательность fixes с реальными метками времени. */
    fun toFixes(route: List<RoutePoint>, startTimeMs: Long, stepMs: Long = 1000L): List<VehicleFix> =
        route.mapIndexed { index, point ->
            val bearing = if (index + 1 < route.size) {
                GeoMath.bearingDegrees(
                    point.latitude,
                    point.longitude,
                    route[index + 1].latitude,
                    route[index + 1].longitude,
                )
            } else {
                null
            }
            VehicleFix(
                latitude = point.latitude,
                longitude = point.longitude,
                speedKmh = point.speedKmh,
                bearingDegrees = bearing,
                accuracyMeters = 5f,
                timestampMs = startTimeMs + index * stepMs,
            )
        }

    const val DEFAULT_SPEED_KMH = 90.0
    const val DEFAULT_START_LAT = 55.7500
    const val DEFAULT_START_LON = 37.6200
}
