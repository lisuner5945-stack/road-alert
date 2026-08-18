package ru.example.roadalert.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface CameraDao {

    @Query("SELECT COUNT(*) FROM cameras")
    suspend fun count(): Int

    @Query("SELECT * FROM cameras")
    suspend fun loadAll(): List<CameraEntity>

    @Query(
        """
        SELECT * FROM cameras
        WHERE latitude BETWEEN :minLat AND :maxLat
          AND longitude BETWEEN :minLon AND :maxLon
        """,
    )
    suspend fun loadInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
    ): List<CameraEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(cameras: List<CameraEntity>)

    @Query("DELETE FROM cameras")
    suspend fun deleteAll()

    @Query("SELECT * FROM database_meta WHERE id = 1")
    suspend fun meta(): DatabaseMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: DatabaseMetaEntity)

    /**
     * Полная замена базы в одной транзакции.
     *
     * Если что-то бросит исключение — Room откатит транзакцию и старая рабочая
     * база останется нетронутой (ТЗ §11).
     */
    @Transaction
    suspend fun replaceAll(cameras: List<CameraEntity>, meta: DatabaseMetaEntity) {
        require(cameras.isNotEmpty()) { "Отказ импортировать пустую базу камер" }
        deleteAll()
        cameras.chunked(INSERT_CHUNK).forEach { insertAll(it) }
        upsertMeta(meta.copy(cameraCount = cameras.size))
    }

    companion object {
        const val INSERT_CHUNK = 1000
    }
}
