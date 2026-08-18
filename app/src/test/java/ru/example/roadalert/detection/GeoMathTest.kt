package ru.example.roadalert.detection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    @Test
    fun `haversine на нулевом расстоянии равен нулю`() {
        assertEquals(0.0, GeoMath.haversineMeters(55.75, 37.62, 55.75, 37.62), 1e-9)
    }

    @Test
    fun `haversine соответствует известному расстоянию Москва - Санкт-Петербург`() {
        val distance = GeoMath.haversineMeters(55.7558, 37.6173, 59.9343, 30.3351)
        // Справочное расстояние по прямой ~634 км.
        assertEquals(634_000.0, distance, 5_000.0)
    }

    @Test
    fun `haversine на километре в средних широтах`() {
        val (lat, lon) = GeoMath.destinationPoint(55.75, 37.62, 0.0, 1000.0)
        assertEquals(1000.0, GeoMath.haversineMeters(55.75, 37.62, lat, lon), 1.0)
    }

    @Test
    fun `bearing строго на север равен нулю`() {
        val (lat, lon) = GeoMath.destinationPoint(55.75, 37.62, 0.0, 500.0)
        assertEquals(0.0, GeoMath.bearingDegrees(55.75, 37.62, lat, lon), 0.5)
    }

    @Test
    fun `bearing на восток равен девяноста`() {
        val (lat, lon) = GeoMath.destinationPoint(55.75, 37.62, 90.0, 500.0)
        assertEquals(90.0, GeoMath.bearingDegrees(55.75, 37.62, lat, lon), 0.5)
    }

    @Test
    fun `bearing на юго-запад`() {
        val (lat, lon) = GeoMath.destinationPoint(55.75, 37.62, 225.0, 800.0)
        assertEquals(225.0, GeoMath.bearingDegrees(55.75, 37.62, lat, lon), 0.5)
    }

    @Test
    fun `angularDifference корректно проходит через ноль`() {
        assertEquals(2.0, GeoMath.angularDifference(359.0, 1.0), 1e-9)
        assertEquals(2.0, GeoMath.angularDifference(1.0, 359.0), 1e-9)
    }

    @Test
    fun `angularDifference не превышает 180`() {
        assertEquals(180.0, GeoMath.angularDifference(0.0, 180.0), 1e-9)
        assertEquals(179.0, GeoMath.angularDifference(0.0, 181.0), 1e-9)
        assertTrue(GeoMath.angularDifference(10.0, 350.0) <= 180.0)
    }

    @Test
    fun `normalizeDegrees приводит отрицательные углы к 0-360`() {
        assertEquals(350.0, GeoMath.normalizeDegrees(-10.0), 1e-9)
        assertEquals(10.0, GeoMath.normalizeDegrees(370.0), 1e-9)
        assertEquals(0.0, GeoMath.normalizeDegrees(360.0), 1e-9)
    }

    @Test
    fun `boundingBox покрывает точку на границе радиуса`() {
        val box = GeoMath.boundingBox(55.75, 37.62, 1000.0)
        val (northLat, northLon) = GeoMath.destinationPoint(55.75, 37.62, 0.0, 990.0)
        val (eastLat, eastLon) = GeoMath.destinationPoint(55.75, 37.62, 90.0, 990.0)
        assertTrue(box.contains(northLat, northLon))
        assertTrue(box.contains(eastLat, eastLon))
    }

    @Test
    fun `boundingBox не покрывает далёкую точку`() {
        val box = GeoMath.boundingBox(55.75, 37.62, 1000.0)
        val (lat, lon) = GeoMath.destinationPoint(55.75, 37.62, 45.0, 5000.0)
        assertTrue(!box.contains(lat, lon))
    }
}
