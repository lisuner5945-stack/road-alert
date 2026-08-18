package ru.example.roadalert.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.roadalert.data.updates.CameraDatabaseParser
import ru.example.roadalert.data.updates.ParseResult

class CameraDatabaseParserTest {

    private val parser = CameraDatabaseParser()

    private fun database(cameras: String, schemaVersion: Int = 1) = """
        {
          "schema_version": $schemaVersion,
          "database_version": "2026-08-18T01:23:00Z",
          "generated_at": "2026-08-18T01:23:00Z",
          "source": "OpenStreetMap",
          "license": "ODbL",
          "cameras": [$cameras]
        }
    """.trimIndent()

    private val validCamera = """{"id":"osm:node:1","lat":55.75,"lon":37.62,"type":"SPEED_CAMERA","speed_limit":60}"""

    @Test
    fun `валидная база импортируется`() {
        val result = parser.parse(database(validCamera), importedAt = 100L)
        assertTrue(result is ParseResult.Success)
        result as ParseResult.Success
        assertEquals(1, result.cameras.size)
        assertEquals(60, result.cameras.first().speedLimitKmh)
        assertEquals(100L, result.cameras.first().updatedAt)
    }

    @Test
    fun `битый JSON отклоняется`() {
        val result = parser.parse("{ это не json ", importedAt = 0L)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `чужая версия схемы отклоняется`() {
        val result = parser.parse(database(validCamera, schemaVersion = 99), importedAt = 0L)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `пустая база отклоняется`() {
        val result = parser.parse(database(""), importedAt = 0L)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun `невалидные координаты пропускаются`() {
        val bad = """{"id":"bad","lat":95.0,"lon":37.62}"""
        val nullIsland = """{"id":"zero","lat":0.0,"lon":0.0}"""
        val result = parser.parse(database("$validCamera,$bad,$nullIsland"), importedAt = 0L)
        result as ParseResult.Success
        assertEquals(1, result.cameras.size)
        assertEquals(2, result.skippedCount)
    }

    @Test
    fun `дубликаты по id удаляются`() {
        val result = parser.parse(database("$validCamera,$validCamera"), importedAt = 0L)
        result as ParseResult.Success
        assertEquals(1, result.cameras.size)
        assertEquals(1, result.duplicateCount)
    }

    @Test
    fun `неправдоподобное ограничение скорости отбрасывается`() {
        val weird = """{"id":"weird","lat":55.76,"lon":37.63,"speed_limit":999}"""
        val result = parser.parse(database(weird), importedAt = 0L)
        result as ParseResult.Success
        assertEquals(null, result.cameras.first().speedLimitKmh)
    }

    @Test
    fun `direction нормализуется в 0-360`() {
        val negative = """{"id":"neg","lat":55.76,"lon":37.63,"direction":-90.0}"""
        val result = parser.parse(database(negative), importedAt = 0L)
        result as ParseResult.Success
        assertEquals(270.0, result.cameras.first().directionDegrees!!, 1e-9)
    }

    @Test
    fun `неизвестный тип камеры превращается в UNKNOWN`() {
        val unknown = """{"id":"u","lat":55.76,"lon":37.63,"type":"SOMETHING_NEW"}"""
        val result = parser.parse(database(unknown), importedAt = 0L)
        result as ParseResult.Success
        assertEquals("UNKNOWN", result.cameras.first().type.name)
    }

    @Test
    fun `лишние поля в JSON не ломают разбор`() {
        val extra = """{"id":"x","lat":55.76,"lon":37.63,"unknown_field":123}"""
        assertTrue(parser.parse(database(extra), importedAt = 0L) is ParseResult.Success)
    }
}
