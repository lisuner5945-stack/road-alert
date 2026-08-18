package ru.example.roadalert.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.roadalert.data.camera.CameraRTree
import ru.example.roadalert.detection.GeoMath
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraSource
import ru.example.roadalert.domain.model.CameraType
import kotlin.random.Random

class CameraRTreeTest {

    private fun point(id: String, lat: Double, lon: Double) = CameraPoint(
        id = id,
        latitude = lat,
        longitude = lon,
        type = CameraType.SPEED_CAMERA,
        speedLimitKmh = 60,
        directionDegrees = null,
        osmObjectType = "node",
        osmObjectId = null,
        source = CameraSource.OPENSTREETMAP,
        updatedAt = 0L,
    )

    @Test
    fun `пустое дерево ничего не возвращает`() {
        val tree = CameraRTree.build(emptyList())
        assertEquals(0, tree.size)
        assertTrue(tree.search(GeoMath.boundingBox(55.0, 37.0, 1000.0)).isEmpty())
    }

    @Test
    fun `находит только камеры внутри bounding box`() {
        val tree = CameraRTree.build(
            listOf(
                point("in", 55.7500, 37.6200),
                point("out-far", 56.5000, 38.5000),
            ),
        )
        val found = tree.search(GeoMath.boundingBox(55.75, 37.62, 500.0))
        assertEquals(listOf("in"), found.map { it.id })
    }

    @Test
    fun `результат совпадает с полным перебором на случайных данных`() {
        val random = Random(42)
        val cameras = (0 until 5_000).map {
            point("c$it", 54.0 + random.nextDouble() * 4.0, 36.0 + random.nextDouble() * 4.0)
        }
        val tree = CameraRTree.build(cameras)
        assertEquals(cameras.size, tree.size)

        repeat(25) {
            val lat = 54.0 + random.nextDouble() * 4.0
            val lon = 36.0 + random.nextDouble() * 4.0
            val box = GeoMath.boundingBox(lat, lon, 3000.0)
            val expected = cameras.filter { box.contains(it.latitude, it.longitude) }.map { it.id }.sorted()
            val actual = tree.search(box).map { it.id }.sorted()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `дубликаты координат не ломают дерево`() {
        val cameras = (0 until 100).map { point("dup$it", 55.75, 37.62) }
        val tree = CameraRTree.build(cameras)
        assertEquals(100, tree.search(GeoMath.boundingBox(55.75, 37.62, 100.0)).size)
    }
}
