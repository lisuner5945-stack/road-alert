package ru.example.roadalert.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.example.roadalert.domain.model.VehicleFix

class BearingEstimatorTest {

    private fun fix(
        lat: Double,
        lon: Double,
        speedKmh: Double,
        bearing: Double? = null,
        time: Long = 0L,
    ) = VehicleFix(lat, lon, speedKmh, bearing, 5f, time)

    @Test
    fun `на уверенной скорости берётся курс из fix`() {
        val estimator = BearingEstimator()
        val bearing = estimator.update(fix(55.75, 37.62, speedKmh = 90.0, bearing = 42.0))
        assertEquals(42.0, bearing!!, 1e-9)
    }

    @Test
    fun `на малой скорости курс из fix игнорируется`() {
        val estimator = BearingEstimator()
        // 3 км/ч — курс GPS в таких условиях недостоверен.
        assertNull(estimator.update(fix(55.75, 37.62, speedKmh = 3.0, bearing = 123.0)))
    }

    @Test
    fun `курс считается по смещению, если fix его не даёт`() {
        val estimator = BearingEstimator()
        estimator.update(fix(55.75, 37.62, speedKmh = 2.0))
        val (lat, lon) = GeoMath.destinationPoint(55.75, 37.62, 90.0, 50.0)
        val bearing = estimator.update(fix(lat, lon, speedKmh = 2.0, time = 2000L))
        assertNotNull(bearing)
        assertEquals(90.0, bearing!!, 1.0)
    }

    @Test
    fun `малое смещение не меняет курс`() {
        val estimator = BearingEstimator()
        estimator.update(fix(55.75, 37.62, speedKmh = 90.0, bearing = 10.0))
        val (lat, lon) = GeoMath.destinationPoint(55.75, 37.62, 200.0, 3.0)
        val bearing = estimator.update(fix(lat, lon, speedKmh = 1.0, time = 2000L))
        assertEquals(10.0, bearing!!, 1e-9)
    }

    @Test
    fun `reset очищает состояние`() {
        val estimator = BearingEstimator()
        estimator.update(fix(55.75, 37.62, speedKmh = 90.0, bearing = 33.0))
        estimator.reset()
        assertNull(estimator.currentBearing)
        assertNull(estimator.update(fix(55.75, 37.62, speedKmh = 1.0)))
    }
}
