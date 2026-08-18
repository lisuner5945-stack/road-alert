package ru.example.roadalert.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.roadalert.data.settings.DistanceProfile

class DetectionConfigTest {

    @Test
    fun `радиус поиска растёт со скоростью`() {
        val radii = listOf(10.0, 45.0, 75.0, 110.0, 150.0).map { DetectionConfig.searchRadiusMeters(it) }
        assertEquals(radii.sorted(), radii)
        assertEquals(700.0, DetectionConfig.searchRadiusMeters(10.0), 0.0)
        assertEquals(2500.0, DetectionConfig.searchRadiusMeters(150.0), 0.0)
    }

    @Test
    fun `дистанции предупреждений упорядочены`() {
        val distances = DetectionConfig.alertDistances(90.0, DistanceProfile.AUTO)
        assertTrue(distances.pre > distances.main)
        assertTrue(distances.main > distances.final)
    }

    @Test
    fun `на большей скорости предупреждение приходит раньше`() {
        val slow = DetectionConfig.alertDistances(50.0, DistanceProfile.AUTO)
        val fast = DetectionConfig.alertDistances(130.0, DistanceProfile.AUTO)
        assertTrue(fast.pre > slow.pre)
        assertTrue(fast.main > slow.main)
    }

    @Test
    fun `профиль ранних предупреждений увеличивает дистанции`() {
        val auto = DetectionConfig.alertDistances(90.0, DistanceProfile.AUTO)
        val early = DetectionConfig.alertDistances(90.0, DistanceProfile.EARLY)
        val late = DetectionConfig.alertDistances(90.0, DistanceProfile.LATE)
        assertTrue(early.pre > auto.pre)
        assertTrue(late.pre < auto.pre)
    }

    @Test
    fun `радиус поиска покрывает дистанцию предварительного предупреждения`() {
        listOf(30.0, 60.0, 90.0, 120.0, 140.0).forEach { speed ->
            val radius = DetectionConfig.searchRadiusMeters(speed)
            val pre = DetectionConfig.alertDistances(speed, DistanceProfile.EARLY).pre
            assertTrue("скорость $speed: радиус $radius < дистанции $pre", radius >= pre)
        }
    }
}
