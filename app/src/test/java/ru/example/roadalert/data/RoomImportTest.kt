package ru.example.roadalert.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.example.roadalert.data.camera.CameraRepository
import ru.example.roadalert.data.database.DatabaseMetaEntity
import ru.example.roadalert.data.database.RoadAlertDatabase
import ru.example.roadalert.domain.model.CameraPoint
import ru.example.roadalert.domain.model.CameraSource
import ru.example.roadalert.domain.model.CameraType

/**
 * Импорт базы в Room: подмена, откат транзакции и поиск по радиусу (ТЗ §45).
 */
@RunWith(RobolectricTestRunner::class)
class RoomImportTest {

    private lateinit var database: RoadAlertDatabase
    private lateinit var repository: CameraRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            RoadAlertDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = CameraRepository(database.cameraDao())
    }

    @After
    fun tearDown() = database.close()

    private fun camera(id: String, lat: Double, lon: Double) = CameraPoint(
        id = id,
        latitude = lat,
        longitude = lon,
        type = CameraType.SPEED_CAMERA,
        speedLimitKmh = 60,
        directionDegrees = null,
        osmObjectType = "node",
        osmObjectId = null,
        source = CameraSource.OPENSTREETMAP,
        updatedAt = 0L,
    )

    private fun meta(version: String, count: Int) = DatabaseMetaEntity(
        schemaVersion = 1,
        databaseVersion = version,
        generatedAt = version,
        cameraCount = count,
        source = "OpenStreetMap",
        license = "ODbL",
        importedAt = 0L,
    )

    @Test
    fun `импорт сохраняет камеры и метаданные`() = runTest {
        val cameras = listOf(camera("a", 55.75, 37.62), camera("b", 55.76, 37.63))
        val result = repository.replaceDatabase(cameras, meta("v1", cameras.size))

        assertTrue(result.isSuccess)
        assertEquals(2, database.cameraDao().count())
        assertEquals("v1", repository.currentMeta()?.databaseVersion)
        assertEquals(2, repository.info.value.cameraCount)
    }

    @Test
    fun `повторный импорт полностью заменяет базу`() = runTest {
        repository.replaceDatabase(List(5) { camera("old$it", 55.0 + it * 0.01, 37.0) }, meta("v1", 5))
        repository.replaceDatabase(listOf(camera("new", 56.0, 38.0)), meta("v2", 1))

        assertEquals(1, database.cameraDao().count())
        assertEquals("v2", repository.currentMeta()?.databaseVersion)
    }

    @Test
    fun `пустая база не может затереть рабочую`() = runTest {
        repository.replaceDatabase(listOf(camera("a", 55.75, 37.62)), meta("v1", 1))

        val result = repository.replaceDatabase(emptyList(), meta("v2", 0))

        assertTrue(result.isFailure)
        assertEquals(1, database.cameraDao().count())
        assertEquals("v1", repository.currentMeta()?.databaseVersion)
    }

    @Test
    fun `поиск возвращает только камеры в радиусе`() = runTest {
        val cameras = listOf(
            camera("near", 55.7500, 37.6200),
            camera("far", 56.5000, 38.5000),
        )
        repository.replaceDatabase(cameras, meta("v1", cameras.size))

        val found = repository.camerasNear(55.75, 37.62, radiusMeters = 1000.0)
        assertEquals(listOf("near"), found.map { it.id })
    }

    @Test
    fun `после загрузки базы из хранилища индекс работает`() = runTest {
        repository.replaceDatabase(listOf(camera("a", 55.75, 37.62)), meta("v1", 1))

        val fresh = CameraRepository(database.cameraDao())
        assertTrue(fresh.camerasNear(55.75, 37.62, 500.0).isEmpty())

        fresh.ensureLoaded()
        assertEquals(1, fresh.camerasNear(55.75, 37.62, 500.0).size)
        assertTrue(fresh.info.value.isReady)
    }
}
