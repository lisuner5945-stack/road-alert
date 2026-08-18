package ru.example.roadalert.data.updates

import kotlinx.serialization.json.Json
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraSource
import ru.example.roadalert.domain.model.CameraType

/** Результат разбора и валидации скачанной базы. */
sealed interface ParseResult {
    data class Success(
        val cameras: List<CameraPoint>,
        val databaseVersion: String,
        val generatedAt: String,
        val schemaVersion: Int,
        val source: String,
        val license: String,
        val skippedCount: Int,
        val duplicateCount: Int,
    ) : ParseResult

    data class Failure(val reason: String) : ParseResult
}

/**
 * Разбор и валидация базы камер.
 *
 * Чистый Kotlin без Android — тестируется обычными unit-тестами.
 * Битая или пустая база должна отсекаться ЗДЕСЬ, до любой записи в Room.
 */
class CameraDatabaseParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {

    fun parse(rawJson: String, importedAt: Long): ParseResult {
        val parsed = runCatching { json.decodeFromString<CameraDatabaseJson>(rawJson) }
            .getOrElse { return ParseResult.Failure("Не удалось разобрать JSON: ${it.message}") }

        if (parsed.schemaVersion != CameraDatabaseSchema.SUPPORTED_SCHEMA_VERSION) {
            return ParseResult.Failure(
                "Неподдерживаемая схема: ${parsed.schemaVersion}, ожидается " +
                    "${CameraDatabaseSchema.SUPPORTED_SCHEMA_VERSION}",
            )
        }
        if (parsed.databaseVersion.isBlank()) {
            return ParseResult.Failure("Пустая версия базы")
        }

        val seen = HashSet<String>(parsed.cameras.size)
        val result = ArrayList<CameraPoint>(parsed.cameras.size)
        var skipped = 0
        var duplicates = 0

        parsed.cameras.forEach { raw ->
            if (!isValidCoordinate(raw.lat, raw.lon)) {
                skipped++
                return@forEach
            }
            if (raw.id.isBlank()) {
                skipped++
                return@forEach
            }
            if (!seen.add(raw.id)) {
                duplicates++
                return@forEach
            }
            result += CameraPoint(
                id = raw.id,
                latitude = raw.lat,
                longitude = raw.lon,
                type = CameraType.fromRaw(raw.type),
                speedLimitKmh = raw.speedLimit?.takeIf { it in MIN_SPEED_LIMIT..MAX_SPEED_LIMIT },
                directionDegrees = raw.direction?.takeIf { it.isFinite() }?.let { normalize(it) },
                osmObjectType = raw.osmType,
                osmObjectId = raw.osmId,
                source = CameraSource.OPENSTREETMAP,
                updatedAt = importedAt,
            )
        }

        if (result.isEmpty()) {
            return ParseResult.Failure("В базе нет ни одной валидной камеры")
        }

        return ParseResult.Success(
            cameras = result,
            databaseVersion = parsed.databaseVersion,
            generatedAt = parsed.generatedAt,
            schemaVersion = parsed.schemaVersion,
            source = parsed.source,
            license = parsed.license,
            skippedCount = skipped,
            duplicateCount = duplicates,
        )
    }

    fun parseMetadata(rawJson: String): DatabaseMetadataJson? =
        runCatching { json.decodeFromString<DatabaseMetadataJson>(rawJson) }.getOrNull()

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0 &&
            !(lat == 0.0 && lon == 0.0)

    private fun normalize(degrees: Double): Double {
        val mod = degrees % 360.0
        return if (mod < 0) mod + 360.0 else mod
    }

    private companion object {
        const val MIN_SPEED_LIMIT = 5
        const val MAX_SPEED_LIMIT = 200
    }
}
