package ru.example.roadalert.debugtools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.roadalert.detection.GeoMath

class RouteSimulatorTest {

    @Test
    fun `json маршрут разбирается`() {
        val route = RouteSimulator.parseJsonRoute(
            """[{"lat":55.75,"lon":37.62,"speed":60},{"lat":55.76,"lon":37.63,"speed":80}]""",
        )
        assertEquals(2, route.size)
        assertEquals(60.0, route.first().speedKmh, 1e-9)
        assertEquals(55.76, route.last().latitude, 1e-9)
    }

    @Test
    fun `точки с невалидными координатами отбрасываются`() {
        val route = RouteSimulator.parseJsonRoute(
            """[{"lat":95.0,"lon":37.62},{"lat":55.76,"lon":37.63}]""",
        )
        assertEquals(1, route.size)
    }

    @Test
    fun `gpx трек разбирается`() {
        val route = RouteSimulator.parseGpxRoute(
            """<gpx><trk><trkseg>
               <trkpt lat="55.7500" lon="37.6200"></trkpt>
               <trkpt lat="55.7510" lon="37.6210"></trkpt>
               </trkseg></trk></gpx>""",
        )
        assertEquals(2, route.size)
    }

    @Test
    fun `прямой маршрут идёт в заданном направлении с заданной скоростью`() {
        val route = RouteSimulator.straightRoute(bearingDegrees = 90.0, speedKmh = 72.0, points = 10)
        assertEquals(10, route.size)

        val step = GeoMath.haversineMeters(
            route[0].latitude,
            route[0].longitude,
            route[1].latitude,
            route[1].longitude,
        )
        // 72 км/ч = 20 м/с, шаг раз в секунду.
        assertEquals(20.0, step, 1.0)
    }

    @Test
    fun `fixes получают корректный курс и метки времени`() {
        val fixes = RouteSimulator.toFixes(
            RouteSimulator.straightRoute(bearingDegrees = 0.0, points = 5),
            startTimeMs = 1_000L,
        )
        assertEquals(5, fixes.size)
        assertEquals(1_000L, fixes.first().timestampMs)
        assertEquals(5_000L, fixes.last().timestampMs)
        assertEquals(0.0, fixes.first().bearingDegrees!!, 0.5)
        assertTrue(fixes.last().bearingDegrees == null)
    }
}
