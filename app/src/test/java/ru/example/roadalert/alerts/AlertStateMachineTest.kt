package ru.example.roadalert.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.roadalert.domain.model.AlertStage
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraSource
import ru.example.roadalert.domain.model.CameraType
import ru.example.roadalert.domain.model.DetectedCamera

class AlertStateMachineTest {

    private val camera = CameraPoint(
        id = "cam-1",
        latitude = 55.75,
        longitude = 37.62,
        type = CameraType.SPEED_CAMERA,
        speedLimitKmh = 60,
        directionDegrees = 0.0,
        osmObjectType = "node",
        osmObjectId = 1L,
        source = CameraSource.OPENSTREETMAP,
        updatedAt = 0L,
    )

    private fun detected(distance: Double, id: String = camera.id) = DetectedCamera(
        camera = camera.copy(id = id),
        distanceMeters = distance,
        angleDifferenceDegrees = 5.0,
        confidence = 1.0,
    )

    @Test
    fun `одна камера не спамит предупреждениями на каждом обновлении`() {
        val machine = AlertStateMachine()
        val speed = 90.0
        var time = 0L
        val events = mutableListOf<AlertEvent>()
        // Приближаемся с 1200 до 400 метров шагами по 20 метров.
        var distance = 1200.0
        while (distance >= 400.0) {
            events += machine.onUpdate(listOf(detected(distance)), speed, time)
            distance -= 20.0
            time += 1000L
        }
        // Максимум две стадии: предварительная и основная.
        assertTrue("Слишком много событий: ${events.size}", events.size <= 2)
        assertEquals(AlertStage.MAIN_ALERTED, machine.stageOf(camera.id))
    }

    @Test
    fun `стадии проходят по порядку pre-main-final`() {
        val machine = AlertStateMachine()
        val speed = 90.0
        val stages = mutableListOf<AlertStage>()
        listOf(1500.0, 900.0, 450.0, 150.0).forEachIndexed { index, distance ->
            stages += machine.onUpdate(listOf(detected(distance)), speed, index * 1000L).map { it.stage }
        }
        assertEquals(
            listOf(AlertStage.PRE_ALERTED, AlertStage.MAIN_ALERTED, AlertStage.FINAL_ALERTED),
            stages,
        )
    }

    @Test
    fun `после проезда камера не предупреждает повторно`() {
        val machine = AlertStateMachine()
        val speed = 90.0
        machine.onUpdate(listOf(detected(900.0)), speed, 0L)
        machine.onUpdate(listOf(detected(300.0)), speed, 1000L)
        machine.onUpdate(listOf(detected(50.0)), speed, 2000L)
        val afterPass = machine.onUpdate(listOf(detected(400.0)), speed, 3000L)
        assertTrue(afterPass.isEmpty())
        assertEquals(AlertStage.PASSED, machine.stageOf(camera.id))

        val stillQuiet = machine.onUpdate(listOf(detected(700.0)), speed, 4000L)
        assertTrue(stillQuiet.isEmpty())
    }

    @Test
    fun `после разворота и большого удаления камера снова может предупредить`() {
        val machine = AlertStateMachine()
        val speed = 90.0
        machine.onUpdate(listOf(detected(900.0)), speed, 0L)
        machine.onUpdate(listOf(detected(100.0)), speed, 1000L)
        machine.onUpdate(listOf(detected(500.0)), speed, 2000L)
        assertEquals(AlertStage.PASSED, machine.stageOf(camera.id))

        // Уехали далеко и прошло больше cooldown.
        val farAway = 1500.0
        val later = 200_000L
        machine.onUpdate(listOf(detected(farAway)), speed, later)
        val events = machine.onUpdate(listOf(detected(1000.0)), speed, later + 1000L)
        assertEquals(listOf(AlertStage.PRE_ALERTED), events.map { it.stage })
    }

    @Test
    fun `несколько камер отслеживаются независимо`() {
        val machine = AlertStateMachine()
        val events = machine.onUpdate(
            listOf(detected(900.0, "a"), detected(300.0, "b")),
            90.0,
            0L,
        )
        assertEquals(2, events.size)
        assertEquals(AlertStage.PRE_ALERTED, machine.stageOf("a"))
        assertEquals(AlertStage.MAIN_ALERTED, machine.stageOf("b"))
    }

    @Test
    fun `reset очищает состояние`() {
        val machine = AlertStateMachine()
        machine.onUpdate(listOf(detected(300.0)), 90.0, 0L)
        machine.reset()
        assertEquals(AlertStage.NOT_SEEN, machine.stageOf(camera.id))
        val events = machine.onUpdate(listOf(detected(300.0)), 90.0, 1000L)
        assertEquals(1, events.size)
    }

    @Test
    fun `на низкой скорости предупреждение приходит позже`() {
        val slow = AlertStateMachine()
        val fast = AlertStateMachine()
        val distance = 1200.0
        assertTrue(slow.onUpdate(listOf(detected(distance)), 30.0, 0L).isEmpty())
        assertTrue(fast.onUpdate(listOf(detected(distance)), 130.0, 0L).isNotEmpty())
    }
}
