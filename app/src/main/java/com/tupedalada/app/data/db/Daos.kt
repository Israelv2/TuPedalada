package com.tupedalada.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

data class SportTotals(
    val sport: Sport,
    val count: Int,
    val distanceKm: Double?,
    val movingSec: Long?,
    val ascentM: Long?,
    val calories: Long?,
)

data class EquipmentKm(val equipmentName: String?, val km: Double?)

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<ActivityEntity>): List<Long>

    @Insert
    suspend fun insert(item: ActivityEntity): Long

    @Query("SELECT * FROM activity WHERE id = :id")
    fun observe(id: Long): Flow<ActivityEntity?>

    @Query("SELECT * FROM activity ORDER BY startEpochMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activity WHERE sport = :sport ORDER BY startEpochMs DESC LIMIT :limit")
    fun recentBySport(sport: Sport, limit: Int): Flow<List<ActivityEntity>>

    @Query(
        """SELECT * FROM activity
           WHERE startEpochMs >= :fromMs AND startEpochMs < :toMs
             AND (:sport IS NULL OR sport = :sport)
             AND (:equipment IS NULL OR equipmentName = :equipment)
             AND (:route IS NULL OR routeName = :route)
           ORDER BY startEpochMs DESC"""
    )
    fun filtered(fromMs: Long, toMs: Long, sport: Sport?, equipment: String?, route: String?): Flow<List<ActivityEntity>>

    @Query(
        """SELECT sport, COUNT(*) AS count, SUM(distanceKm) AS distanceKm, SUM(movingSec) AS movingSec,
                  SUM(ascentM) AS ascentM, SUM(calories) AS calories
           FROM activity WHERE startEpochMs >= :fromMs AND startEpochMs < :toMs GROUP BY sport"""
    )
    fun totals(fromMs: Long, toMs: Long): Flow<List<SportTotals>>

    @Query("SELECT equipmentName, SUM(distanceKm) AS km FROM activity WHERE sport = :sport GROUP BY equipmentName ORDER BY km DESC")
    fun kmByEquipment(sport: Sport): Flow<List<EquipmentKm>>

    @Query("SELECT dedupeKey FROM activity")
    suspend fun allDedupeKeys(): List<String>

    @Query("SELECT DISTINCT routeName FROM activity WHERE routeName != '' ORDER BY routeName")
    fun routeNames(): Flow<List<String>>

    @Query("SELECT DISTINCT equipmentName FROM activity WHERE equipmentName IS NOT NULL ORDER BY equipmentName")
    fun equipmentNames(): Flow<List<String>>

    @Query("SELECT MIN(startEpochMs) FROM activity")
    fun firstActivityMs(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM activity")
    fun count(): Flow<Int>
}

@Dao
interface EquipmentDao {
    @Query("SELECT * FROM equipment ORDER BY sport, status, name")
    fun all(): Flow<List<EquipmentEntity>>

    @Query("SELECT * FROM equipment WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): EquipmentEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: EquipmentEntity): Long

    @Upsert
    suspend fun upsert(item: EquipmentEntity)
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM route WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: RouteEntity): Long
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(p: ProfileEntity)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weight ORDER BY epochDay")
    fun all(): Flow<List<WeightEntity>>

    @Query("SELECT * FROM weight WHERE epochDay = :day LIMIT 1")
    suspend fun forDay(day: Long): WeightEntity?

    @Query("SELECT * FROM weight ORDER BY epochDay DESC LIMIT 1")
    fun latest(): Flow<WeightEntity?>

    @Upsert
    suspend fun upsert(w: WeightEntity)
}

@Dao
interface AfterActivityDao {
    @Insert suspend fun insertHydration(items: List<HydrationEntity>)
    @Insert suspend fun insertFood(items: List<FoodEntity>)
    @Upsert suspend fun upsertRecovery(r: RecoveryEntity)

    @Query("SELECT * FROM recovery WHERE activityId = :activityId")
    fun recovery(activityId: Long): Flow<RecoveryEntity?>

    @Query("SELECT COALESCE(SUM(ml),0) FROM hydration WHERE activityId = :activityId")
    fun hydrationMl(activityId: Long): Flow<Int>
}

@Dao
interface ImportDao {
    @Insert suspend fun insertFile(f: ImportedFileEntity): Long
    @Insert suspend fun insertSync(s: SyncHistoryEntity): Long

    @Query("SELECT * FROM sync_history ORDER BY epochMs DESC LIMIT 20")
    fun syncHistory(): Flow<List<SyncHistoryEntity>>

    @Query("SELECT COUNT(*) FROM imported_file WHERE sha256 = :sha")
    suspend fun countBySha(sha: String): Int
}

@Dao
interface MiscDao {
    @Upsert suspend fun upsertZones(z: List<HeartRateZoneEntity>)
    @Query("SELECT * FROM hr_zone ORDER BY zone") fun zones(): Flow<List<HeartRateZoneEntity>>
    @Upsert suspend fun upsertSetting(s: SettingEntity)
    @Query("SELECT value FROM setting WHERE `key` = :key") suspend fun setting(key: String): String?
    @Insert suspend fun insertVersion(v: AppVersionEntity)
    @Query("SELECT * FROM app_version ORDER BY installedAt DESC") fun versions(): Flow<List<AppVersionEntity>>
    @Upsert suspend fun upsertStatistic(s: StatisticEntity)
    @Insert suspend fun insertDevice(d: DeviceEntity): Long
    @Query("SELECT * FROM device") fun devices(): Flow<List<DeviceEntity>>
    @Insert suspend fun insertSamples(s: List<ActivitySampleEntity>)
    @Insert suspend fun insertGps(p: List<GpsPointEntity>)
    @Query("SELECT * FROM gps_point WHERE activityId = :id ORDER BY seq") fun gps(id: Long): Flow<List<GpsPointEntity>>
}
