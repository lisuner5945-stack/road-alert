package ru.example.roadalert.data.updates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Формат метаданных, публикуемых GitHub Actions (ТЗ §10). */
@Serializable
data class DatabaseMetadataJson(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("database_version") val databaseVersion: String,
    @SerialName("generated_at") val generatedAt: String,
    val source: String = "OpenStreetMap",
    val license: String = "ODbL",
    @SerialName("camera_count") val cameraCount: Int,
    val sha256: String,
    @SerialName("download_url") val downloadUrl: String? = null,
)

/** Содержимое camera_database.json.gz. */
@Serializable
data class CameraDatabaseJson(
    @SerialName("schema_version") val schemaVersion: Int,
    @SerialName("database_version") val databaseVersion: String,
    @SerialName("generated_at") val generatedAt: String,
    val source: String = "OpenStreetMap",
    val license: String = "ODbL",
    val cameras: List<CameraJson>,
)

@Serializable
data class CameraJson(
    val id: String,
    val lat: Double,
    val lon: Double,
    val type: String = "SPEED_CAMERA",
    @SerialName("speed_limit") val speedLimit: Int? = null,
    val direction: Double? = null,
    @SerialName("osm_type") val osmType: String? = null,
    @SerialName("osm_id") val osmId: Long? = null,
)

object CameraDatabaseSchema {
    /** Версия схемы, которую понимает это приложение. */
    const val SUPPORTED_SCHEMA_VERSION = 1
}
