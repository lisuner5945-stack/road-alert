package ru.example.roadalert.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraSource
import ru.example.roadalert.domain.model.CameraType

/**
 * Камера в локальной базе.
 * Индексы по координатам нужны для быстрого bbox-запроса при загрузке региона.
 */
@Entity(
    tableName = "cameras",
    indices = [Index(value = ["latitude", "longitude"])],
)
data class CameraEntity(
    @PrimaryKey val id: String,
    val latitude: Double,
    val longitude: Double,
    val type: String,
    @ColumnInfo(name = "speed_limit_kmh") val speedLimitKmh: Int?,
    @ColumnInfo(name = "direction_degrees") val directionDegrees: Double?,
    @ColumnInfo(name = "osm_object_type") val osmObjectType: String?,
    @ColumnInfo(name = "osm_object_id") val osmObjectId: Long?,
    val source: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
) {
    fun toDomain(): CameraPoint = CameraPoint(
        id = id,
        latitude = latitude,
        longitude = longitude,
        type = CameraType.fromRaw(type),
        speedLimitKmh = speedLimitKmh,
        directionDegrees = directionDegrees,
        osmObjectType = osmObjectType,
        osmObjectId = osmObjectId,
        source = CameraSource.fromRaw(source),
        updatedAt = updatedAt,
    )

    companion object {
        fun fromDomain(point: CameraPoint) = CameraEntity(
            id = point.id,
            latitude = point.latitude,
            longitude = point.longitude,
            type = point.type.name,
            speedLimitKmh = point.speedLimitKmh,
            directionDegrees = point.directionDegrees,
            osmObjectType = point.osmObjectType,
            osmObjectId = point.osmObjectId,
            source = point.source.name,
            updatedAt = point.updatedAt,
        )
    }
}

/**
 * Метаданные активной базы. Строка всегда одна (id = 1):
 * база подменяется целиком в одной транзакции.
 */
@Entity(tableName = "database_meta")
data class DatabaseMetaEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "schema_version") val schemaVersion: Int,
    @ColumnInfo(name = "database_version") val databaseVersion: String,
    @ColumnInfo(name = "generated_at") val generatedAt: String,
    @ColumnInfo(name = "camera_count") val cameraCount: Int,
    val source: String,
    val license: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
)
