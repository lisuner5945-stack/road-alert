package ru.example.roadalert.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraSource
import ru.example.roadalert.domain.model.CameraType
import ru.example.roadalert.domain.model.VehicleFix

/**
 * Сценарии из ТЗ §17: камера впереди, сзади, сбоку, встречная, параллельная,
 * без direction, разворот и скачущий bearing.
 */
class CameraDetectionEngineTest {

    private val originLat = 55.75
    private val originLon = 37.62

    private fun camera(
        id: String,
        bearingFromOrigin: Double,
        distanceMeters: Double,
        direction: Double? = null,
        speedLimit: Int? = 60,
    ): CameraPoint {
        val (lat, lon) = GeoMath.destinationPoint(originLat, originLon, bearingFromOrigin, distanceMeters)
        return CameraPoint(
            id = id,
            latitude = lat,
            longitude = lon,
            type = CameraType.SPEED_CAMERA,
            speedLimitKmh = speedLimit,
            directionDegrees = direction,
            osmObjectType = "node",
            osmObjectId = 1L,
            source = CameraSource.OPENSTREETMAP,
            updatedAt = 0L,
        )
    }

    private fun engine(cameras: List<CameraPoint>) = CameraDetectionEngine { _, _, radius ->
        cameras.filter {
            GeoMath.haversineMeters(originLat, originLon, it.latitude, it.longitude) <= radius
        }
    }

    private fun fix(speedKmh: Double = 90.0) = VehicleFix(
        latitude = originLat,
        longitude = originLon,
        speedKmh = speedKmh,
        bearingDegrees = 0.0,
        accuracyMeters = 5f,
        timestampMs = 0L,
    )

    @Test
    fun `камера прямо впереди обнаруживается`() {
        val ahead = camera("ahead", bearingFromOrigin = 0.0, distanceMeters = 800.0, direction = 0.0)
        val result = engine(listOf(ahead)).detect(fix(), bearing = 0.0)
        assertEquals(1, result.size)
        assertEquals("ahead", result.first().camera.id)
        assertEquals(800.0, result.first().distanceMeters, 5.0)
    }

    @Test
    fun `камера сзади игнорируется`() {
        val behind = camera("behind", bearingFromOrigin = 180.0, distanceMeters = 500.0, direction = 0.0)
        val result = engine(listOf(behind)).detect(fix(), bearing = 0.0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `камера сбоку под 90 градусов игнорируется`() {
        val left = camera("left", bearingFromOrigin = 270.0, distanceMeters = 400.0)
        val right = camera("right", bearingFromOrigin = 90.0, distanceMeters = 400.0)
        val result = engine(listOf(left, right)).detect(fix(), bearing = 0.0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `встречная камера отбрасывается по direction`() {
        // Камера впереди, но контролирует встречный поток (смотрит на юг).
        val oncoming = camera("oncoming", bearingFromOrigin = 0.0, distanceMeters = 700.0, direction = 180.0)
        val result = engine(listOf(oncoming)).detect(fix(), bearing = 0.0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `камера на параллельной дороге сбоку не попадает в кандидаты`() {
        // Параллельная дорога в 150 м справа, камера почти на траверзе.
        val parallel = camera("parallel", bearingFromOrigin = 80.0, distanceMeters = 150.0, direction = 0.0)
        val result = engine(listOf(parallel)).detect(fix(), bearing = 0.0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `камера без direction принимается со сниженной уверенностью`() {
        val known = camera("with-direction", bearingFromOrigin = 0.0, distanceMeters = 600.0, direction = 0.0)
        val unknown = camera("no-direction", bearingFromOrigin = 0.0, distanceMeters = 600.0, direction = null)
        val withDirection = engine(listOf(known)).detect(fix(), bearing = 0.0).first()
        val withoutDirection = engine(listOf(unknown)).detect(fix(), bearing = 0.0).first()
        assertTrue(withoutDirection.confidence < withDirection.confidence)
        assertTrue(withoutDirection.confidence > 0.5)
    }

    @Test
    fun `после разворота камера сзади становится камерой впереди`() {
        val camera = camera("turn", bearingFromOrigin = 180.0, distanceMeters = 600.0, direction = 180.0)
        val engine = engine(listOf(camera))
        assertTrue(engine.detect(fix(), bearing = 0.0).isEmpty())
        assertEquals(1, engine.detect(fix(), bearing = 180.0).size)
    }

    @Test
    fun `при неизвестном курсе камера рядом не теряется`() {
        val nearby = camera("nearby", bearingFromOrigin = 45.0, distanceMeters = 300.0, direction = 90.0)
        val result = engine(listOf(nearby)).detect(fix(speedKmh = 0.0), bearing = null)
        assertEquals(1, result.size)
        assertTrue(result.first().confidence < 0.5)
    }

    @Test
    fun `несколько камер сортируются по уверенности и расстоянию`() {
        val near = camera("near", bearingFromOrigin = 5.0, distanceMeters = 400.0, direction = 0.0)
        val far = camera("far", bearingFromOrigin = 0.0, distanceMeters = 1200.0, direction = 0.0)
        val result = engine(listOf(far, near)).detect(fix(), bearing = 0.0)
        assertEquals(listOf("near", "far"), result.map { it.camera.id })
    }

    @Test
    fun `камера без ограничения скорости всё равно обнаруживается`() {
        val noLimit = camera("no-limit", bearingFromOrigin = 0.0, distanceMeters = 500.0, speedLimit = null)
        val detected = engine(listOf(noLimit)).detect(fix(), bearing = 0.0).firstOrNull()
        assertNotNull(detected)
        assertNull(detected!!.camera.speedLimitKmh)
    }

    @Test
    fun `камера дальше радиуса поиска не рассматривается`() {
        val far = camera("very-far", bearingFromOrigin = 0.0, distanceMeters = 4000.0, direction = 0.0)
        val result = engine(listOf(far)).detect(fix(speedKmh = 60.0), bearing = 0.0)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `совсем близкая камера не отбрасывается из-за шума угла`() {
        val veryClose = camera("very-close", bearingFromOrigin = 120.0, distanceMeters = 30.0, direction = 0.0)
        val result = engine(listOf(veryClose)).detect(fix(), bearing = 0.0)
        assertEquals(1, result.size)
    }
}
