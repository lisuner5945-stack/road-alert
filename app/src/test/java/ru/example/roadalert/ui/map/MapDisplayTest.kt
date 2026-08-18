package ru.example.roadalert.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraSource
import ru.example.roadalert.domain.model.CameraType

class MapDisplayTest {

    private fun camera(
        speedLimitKmh: Int? = 60,
        directionDegrees: Double? = null,
        type: CameraType = CameraType.SPEED_CAMERA,
    ) = CameraPoint(
        id = "test",
        latitude = 55.0,
        longitude = 37.0,
        type = type,
        speedLimitKmh = speedLimitKmh,
        directionDegrees = directionDegrees,
        osmObjectType = "node",
        osmObjectId = 1L,
        source = CameraSource.OPENSTREETMAP,
        updatedAt = 0L,
    )

    @Test
    fun `на мелком масштабе камеры скрыты`() {
        assertEquals(MapDisplayMode.HIDDEN, MapDisplay.modeFor(zoom = 5.0, camerasInView = 500))
        assertEquals(MapDisplayMode.HIDDEN, MapDisplay.modeFor(zoom = 7.99, camerasInView = 10))
    }

    @Test
    fun `на среднем масштабе рисуем точки`() {
        assertEquals(MapDisplayMode.DOTS, MapDisplay.modeFor(zoom = 8.0, camerasInView = 10))
        assertEquals(MapDisplayMode.DOTS, MapDisplay.modeFor(zoom = 11.99, camerasInView = 10))
    }

    @Test
    fun `на крупном масштабе рисуем знаки`() {
        assertEquals(MapDisplayMode.SIGNS, MapDisplay.modeFor(zoom = 12.0, camerasInView = 10))
        assertEquals(MapDisplayMode.SIGNS, MapDisplay.modeFor(zoom = 18.0, camerasInView = 400))
    }

    @Test
    fun `слишком много камер в кадре — обратно к точкам`() {
        assertEquals(
            MapDisplayMode.DOTS,
            MapDisplay.modeFor(zoom = 14.0, camerasInView = MapDisplay.MAX_SIGNS + 1),
        )
    }

    @Test
    fun `пустой кадр ничего не рисует`() {
        assertEquals(MapDisplayMode.HIDDEN, MapDisplay.modeFor(zoom = 16.0, camerasInView = 0))
    }

    @Test
    fun `подсказка появляется только когда есть что подсказать`() {
        assertNotNull(MapDisplay.hintFor(zoom = 5.0, camerasInView = 100))
        assertNotNull(MapDisplay.hintFor(zoom = 10.0, camerasInView = 100))
        assertNotNull(MapDisplay.hintFor(zoom = 14.0, camerasInView = MapDisplay.MAX_SIGNS + 1))
        assertNull(MapDisplay.hintFor(zoom = 14.0, camerasInView = 12))
    }

    @Test
    fun `в знаке показывается только правдоподобное ограничение`() {
        assertEquals("60", MapDisplay.signText(camera(speedLimitKmh = 60)))
        assertEquals("110", MapDisplay.signText(camera(speedLimitKmh = 110)))
        assertNull(MapDisplay.signText(camera(speedLimitKmh = null)))
        assertNull(MapDisplay.signText(camera(speedLimitKmh = 0)))
        assertNull(MapDisplay.signText(camera(speedLimitKmh = 900)))
    }

    @Test
    fun `направление переводится в стороны света`() {
        assertEquals("смотрит на север", MapDisplay.directionLabel(0.0))
        assertEquals("смотрит на север", MapDisplay.directionLabel(359.0))
        assertEquals("смотрит на восток", MapDisplay.directionLabel(90.0))
        assertEquals("смотрит на юго-запад", MapDisplay.directionLabel(225.0))
        assertEquals("направление не указано", MapDisplay.directionLabel(null))
    }

    @Test
    fun `подпись скорости читается человеком`() {
        assertEquals("60 км/ч", MapDisplay.speedLabel(camera(speedLimitKmh = 60)))
        assertEquals("ограничение неизвестно", MapDisplay.speedLabel(camera(speedLimitKmh = null)))
    }

    @Test
    fun `у каждого типа камеры есть название`() {
        CameraType.entries.forEach { type ->
            assertEquals(
                "Тип $type без подписи",
                true,
                MapDisplay.typeLabel(type).isNotBlank(),
            )
        }
    }
}
