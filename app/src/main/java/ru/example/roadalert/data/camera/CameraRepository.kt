package ru.example.roadalert.data.camera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.example.roadalert.data.database.CameraDao
import ru.example.roadalert.data.database.CameraEntity
import ru.example.roadalert.data.database.DatabaseMetaEntity
import ru.example.roadalert.detection.GeoMath
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.util.AppLog

/** Состояние локальной базы для UI. */
data class CameraDatabaseInfo(
    val cameraCount: Int = 0,
    val databaseVersion: String? = null,
    val generatedAt: String? = null,
    val isReady: Boolean = false,
)

/**
 * Доступ к базе камер.
 *
 * Room — постоянное хранилище, R-tree — рабочий индекс в памяти:
 * поиск выполняется на каждый GPS-fix, и ходить в SQLite раз в секунду
 * ради этого не нужно.
 */
class CameraRepository(private val dao: CameraDao) {

    private val loadMutex = Mutex()

    @Volatile
    private var index: CameraRTree = CameraRTree.build(emptyList())

    private val _info = MutableStateFlow(CameraDatabaseInfo())
    val info: StateFlow<CameraDatabaseInfo> = _info.asStateFlow()

    val isLoaded: Boolean get() = index.size > 0

    /** Загружает базу в память. Безопасно вызывать повторно. */
    suspend fun ensureLoaded(force: Boolean = false) = loadMutex.withLock {
        if (isLoaded && !force) return@withLock
        withContext(Dispatchers.IO) {
            val cameras = dao.loadAll().map { it.toDomain() }
            index = CameraRTree.build(cameras)
            val meta = dao.meta()
            _info.value = CameraDatabaseInfo(
                cameraCount = cameras.size,
                databaseVersion = meta?.databaseVersion,
                generatedAt = meta?.generatedAt,
                isReady = cameras.isNotEmpty(),
            )
            AppLog.event("DB_LOADED", "cameras" to cameras.size, "version" to meta?.databaseVersion)
        }
    }

    /** Камеры в радиусе (метры) от точки. Радиус проверяется точно, по Haversine. */
    fun camerasNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): List<CameraPoint> {
        val box = GeoMath.boundingBox(latitude, longitude, radiusMeters)
        return index.search(box).filter {
            GeoMath.haversineMeters(latitude, longitude, it.latitude, it.longitude) <= radiusMeters
        }
    }

    /**
     * Атомарно заменяет базу. При исключении Room откатывает транзакцию,
     * а индекс в памяти не трогается — старая рабочая база остаётся живой.
     */
    suspend fun replaceDatabase(
        cameras: List<CameraPoint>,
        meta: DatabaseMetaEntity,
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            dao.replaceAll(cameras.map(CameraEntity::fromDomain), meta)
            index = CameraRTree.build(cameras)
            _info.value = CameraDatabaseInfo(
                cameraCount = cameras.size,
                databaseVersion = meta.databaseVersion,
                generatedAt = meta.generatedAt,
                isReady = true,
            )
            AppLog.event("DB_UPDATE_SUCCESS", "version" to meta.databaseVersion, "cameras" to cameras.size)
            cameras.size
        }.onFailure {
            AppLog.event("DB_UPDATE_FAILED", "reason" to (it.message ?: it::class.java.simpleName))
        }
    }

    suspend fun currentMeta(): DatabaseMetaEntity? = withContext(Dispatchers.IO) { dao.meta() }
}
