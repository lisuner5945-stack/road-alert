package ru.example.roadalert.alerts

import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.roadalert.domain.model.AlertStage
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraSource
import ru.example.roadalert.domain.model.CameraType
import ru.example.roadalert.domain.model.DetectedCamera

class VoicePhraseTest {

    private fun event(
        stage: AlertStage,
        distance: Double = 500.0,
        limit: Int? = 60,
        type: CameraType = CameraType.SPEED_CAMERA,
    ) = AlertEvent(
        detected = DetectedCamera(
            camera = CameraPoint(
                id = "cam",
                latitude = 55.75,
                longitude = 37.62,
                type = type,
                speedLimitKmh = limit,
                directionDegrees = null,
                osmObjectType = "node",
                osmObjectId = null,
                source = CameraSource.OPENSTREETMAP,
                updatedAt = 0L,
            ),
            distanceMeters = distance,
            angleDifferenceDegrees = 3.0,
            confidence = 1.0,
        ),
        stage = stage,
    )

    @Test
    fun `предварительное предупреждение называет тип и ограничение`() {
        val phrase = VoiceAlertManager.buildPhrase(event(AlertStage.PRE_ALERTED), currentSpeedKmh = 50.0)
        assertTrue(phrase, phrase.contains("Впереди"))
        assertTrue(phrase, phrase.contains("камера контроля скорости"))
        assertTrue(phrase, phrase.contains("60"))
    }

    @Test
    fun `основное предупреждение без превышения называет расстояние`() {
        val phrase = VoiceAlertManager.buildPhrase(event(AlertStage.MAIN_ALERTED, distance = 480.0), 55.0)
        assertTrue(phrase, phrase.contains("500 метров"))
    }

    @Test
    fun `при превышении просит снизить скорость`() {
        val phrase = VoiceAlertManager.buildPhrase(event(AlertStage.MAIN_ALERTED), currentSpeedKmh = 88.0)
        assertTrue(phrase, phrase.contains("Снизьте скорость"))
        assertTrue(phrase, phrase.contains("60"))
    }

    @Test
    fun `финальное предупреждение короткое`() {
        val phrase = VoiceAlertManager.buildPhrase(event(AlertStage.FINAL_ALERTED, distance = 150.0), 50.0)
        assertTrue(phrase, phrase.startsWith("Внимание, камера"))
        assertTrue(phrase, phrase.length < 45)
    }

    @Test
    fun `без известного ограничения ограничение не произносится`() {
        val phrase = VoiceAlertManager.buildPhrase(event(AlertStage.PRE_ALERTED, limit = null), 60.0)
        assertTrue(phrase, !phrase.contains("Ограничение"))
    }

    @Test
    fun `камера на светофоре называется корректно`() {
        val phrase = VoiceAlertManager.buildPhrase(
            event(AlertStage.PRE_ALERTED, type = CameraType.RED_LIGHT, limit = null),
            40.0,
        )
        assertTrue(phrase, phrase.contains("камера на светофоре"))
    }
}
