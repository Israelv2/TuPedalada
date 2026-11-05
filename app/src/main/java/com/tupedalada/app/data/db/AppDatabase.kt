package com.tupedalada.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

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
    version = 1,
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
                .build().also { instance = it }
        }
    }
}
