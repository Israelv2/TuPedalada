package com.tupedalada.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter fun sportToString(s: Sport): String = s.name
    @TypeConverter fun stringToSport(s: String): Sport = Sport.valueOf(s)
}

@Database(
    entities = [
        ProfileEntity::class, WeightEntity::class, DeviceEntity::class, EquipmentEntity::class,
        RouteEntity::class, ImportedFileEntity::class, ActivityEntity::class, ActivitySampleEntity::class,
        GpsPointEntity::class, StatisticEntity::class, HydrationEntity::class, FoodEntity::class,
        RecoveryEntity::class, HeartRateZoneEntity::class, SyncHistoryEntity::class, SettingEntity::class,
        AppVersionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun activityDao(): ActivityDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun routeDao(): RouteDao
    abstract fun profileDao(): ProfileDao
    abstract fun weightDao(): WeightDao
    abstract fun afterActivityDao(): AfterActivityDao
    abstract fun importDao(): ImportDao
    abstract fun miscDao(): MiscDao

    companion object {
        const val NAME = "tupedalada.db"

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                // Nunca destruir datos en una migración: si falta una migración, la app falla en vez de borrar.
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }

        /** v2 (beta 3): actividades de Garmin Connect pendientes de confirmar. Conserva todos los datos. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE activity ADD COLUMN garminActivityId INTEGER")
                db.execSQL("ALTER TABLE activity ADD COLUMN pendingConfirm INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
