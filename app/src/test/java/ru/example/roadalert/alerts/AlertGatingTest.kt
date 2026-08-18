package ru.example.roadalert.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.roadalert.domain.model.AlertStage
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraSource
import ru.example.roadalert.domain.model.CameraType
import ru.example.roadalert.domain.model.DetectedCamera

/**
 * Правило «предупреждать только при превышении» (ТЗ §19).
 *
 * Логика продублирована из сервиса намеренно: это тест на само правило,
 * а не на Android-компонент.
 */
class AlertGatingTest {

    private fun event(limit: Int?): AlertEvent = AlertEvent(
        detected = DetectedCamera(
            camera = CameraPoint(
                id = "cam",
                latitude = 55.75,
                longitude = 37.62,
                type = CameraType.SPEED_CAMERA,
                speedLimitKmh = limit,
                directionDegrees = null,
                osmObjectType = null,
                osmObjectId = null,
                source = CameraSource.OPENSTREETMAP,
                updatedAt = 0L,
            ),
            distanceMeters = 400.0,
            angleDifferenceDegrees = 2.0,
            confidence = 1.0,
        ),
        stage = AlertStage.MAIN_ALERTED,
    )

    private fun shouldAnnounce(
        event: AlertEvent,
        speedKmh: Double,
        alertOnlyWhenSpeeding: Boolean,
        toleranceKmh: Int,
    ): Boolean {
        val limit = event.detected.camera.speedLimitKmh
        val speeding = limit?.let { speedKmh > it + toleranceKmh } ?: false
        return !(alertOnlyWhenSpeeding && limit != null && !speeding)
    }

    @Test
    fun `по умолчанию предупреждаем всегда`() {
        assertTrue(shouldAnnounce(event(60), speedKmh = 50.0, alertOnlyWhenSpeeding = false, toleranceKmh = 0))
    }

    @Test
    fun `в режиме только при превышении молчим на разрешённой скорости`() {
        assertFalse(shouldAnnounce(event(60), speedKmh = 58.0, alertOnlyWhenSpeeding = true, toleranceKmh = 0))
    }

    @Test
    fun `в режиме только при превышении предупреждаем при превышении`() {
        assertTrue(shouldAnnounce(event(60), speedKmh = 70.0, alertOnlyWhenSpeeding = true, toleranceKmh = 0))
    }

    @Test
    fun `допуск скорости учитывается`() {
        assertFalse(shouldAnnounce(event(60), speedKmh = 68.0, alertOnlyWhenSpeeding = true, toleranceKmh = 10))
        assertTrue(shouldAnnounce(event(60), speedKmh = 71.0, alertOnlyWhenSpeeding = true, toleranceKmh = 10))
    }

    @Test
    fun `камера без известного лимита предупреждает даже в режиме только при превышении`() {
        assertTrue(shouldAnnounce(event(null), speedKmh = 40.0, alertOnlyWhenSpeeding = true, toleranceKmh = 0))
    }
}
