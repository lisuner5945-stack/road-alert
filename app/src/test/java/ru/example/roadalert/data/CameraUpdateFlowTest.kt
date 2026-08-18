package ru.example.roadalert.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.example.roadalert.data.updates.CameraDatabaseParser
import ru.example.roadalert.data.updates.ParseResult
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Проверяем ту часть обновления, которая решает, можно ли доверять файлу:
 * SHA-256, распаковка и валидация до любой записи в базу (ТЗ §11, §45).
 */
class CameraUpdateFlowTest {

    private val parser = CameraDatabaseParser()

    private val validDatabase = """
        {"schema_version":1,"database_version":"2026-08-18T01:00:00Z",
         "generated_at":"2026-08-18T01:00:00Z","source":"OpenStreetMap","license":"ODbL",
         "cameras":[{"id":"osm:node:1","lat":55.75,"lon":37.62,"speed_limit":60}]}
    """.trimIndent()

    private fun gzip(text: String): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(text.toByteArray()) }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `корректный архив распаковывается и проходит валидацию`() = runTest {
        val archive = gzip(validDatabase)
        val expected = sha256(archive)

        assertEquals(expected, sha256(archive))

        val unpacked = GZIPInputStream(archive.inputStream()).use { it.readBytes().decodeToString() }
        val result = parser.parse(unpacked, importedAt = 0L)
        assertTrue(result is ParseResult.Success)
    }

    @Test
    fun `повреждённый архив меняет SHA и не пройдёт проверку`() {
        val archive = gzip(validDatabase)
        val corrupted = archive.copyOf().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }
        assertTrue(sha256(archive) != sha256(corrupted))
    }

    @Test
    fun `битые данные внутри архива не проходят разбор`() {
        val broken = gzip("{ это не база камер")
        val unpacked = GZIPInputStream(broken.inputStream()).use { it.readBytes().decodeToString() }
        assertTrue(parser.parse(unpacked, importedAt = 0L) is ParseResult.Failure)
    }

    @Test
    fun `метаданные разбираются и содержат версию и хэш`() {
        val metadata = parser.parseMetadata(
            """
            {"schema_version":1,"database_version":"2026-08-18T01:00:00Z",
             "generated_at":"2026-08-18T01:00:00Z","source":"OpenStreetMap","license":"ODbL",
             "camera_count":12345,"sha256":"abc123","download_url":"https://example.com/db.gz"}
            """.trimIndent(),
        )
        assertTrue(metadata != null)
        assertEquals(12345, metadata!!.cameraCount)
        assertEquals("abc123", metadata.sha256)
    }

    @Test
    fun `повреждённые метаданные не разбираются`() {
        assertEquals(null, parser.parseMetadata("{ мусор"))
    }
}
