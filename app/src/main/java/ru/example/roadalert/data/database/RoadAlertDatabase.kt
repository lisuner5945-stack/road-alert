package ru.example.roadalert.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CameraEntity::class, DatabaseMetaEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class RoadAlertDatabase : RoomDatabase() {

    abstract fun cameraDao(): CameraDao

    companion object {

        private const val NAME = "road_alert_cameras.db"

        @Volatile
        private var instance: RoadAlertDatabase? = null

        fun get(context: Context): RoadAlertDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                RoadAlertDatabase::class.java,
                NAME,
            )
                // Схема камер простая; при её изменении база просто перекачивается заново.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}
