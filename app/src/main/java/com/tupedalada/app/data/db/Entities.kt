package com.tupedalada.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Modalidades deportivas soportadas. */
enum class Sport { BIKE, RUN, WALK }

/**
 * Calidad de un dato, para distinguir lo medido por el dispositivo, lo calculado a partir
 * de datos medidos y lo estimado con fórmulas (requisito 10 del prompt: "no inventar datos").
 */
enum class DataQuality(val code: Char) { MEASURED('M'), CALCULATED('C'), ESTIMATED('E') }

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Long = 1,
    val name: String = "",
    val birthDateEpochDay: Long? = null,
    val nationality: String = "",
    val heightCm: Double? = null,
    val conditions: String = "",
    val medication: String = "",
    val maxHr: Int? = null,
    val restHr: Int? = null,
    val ftpWatts: Int? = null,
    val stepGoal: Int = 10_000,
    val photoUri: String? = null,
    val backgroundUri: String? = null,
)

@Entity(tableName = "weight", indices = [Index(value = ["epochDay"], unique = true)])
data class WeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val kg: Double,
    val source: String = "manual",
)

@Entity(tableName = "device")
data class DeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String, // EDGE_830, ENDURO_1, HEALTH_CONNECT, OTHER
    val serial: String? = null,
)

@Entity(tableName = "equipment", indices = [Index(value = ["name"], unique = true)])
data class EquipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sport: Sport,
    val brand: String = "",
    val model: String = "",
    val acquiredEpochDay: Long? = null,
    val initialKm: Double = 0.0,
    val lifeKm: Double? = null,
    val status: String = "ACTIVE", // ACTIVE, REVIEW, RETIRED
    val photoUri: String? = null,
    val isDefault: Boolean = false,
)

@Entity(tableName = "route", indices = [Index(value = ["name"], unique = true)])
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val difficulty: Double? = null,
)

@Entity(tableName = "imported_file")
data class ImportedFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val format: String, // XLSX, FIT, GPX, TCX
    val sha256: String,
    val sizeBytes: Long,
    val storedCopyPath: String, // copia íntegra del original dentro de la app
    val importedAt: Long,
    val rowsImported: Int,
    val rowsDuplicated: Int,
    val rowsWithErrors: Int,
)

@Entity(
    tableName = "activity",
    indices = [Index("startEpochMs"), Index("sport"), Index("equipmentId"), Index("routeId"), Index(value = ["dedupeKey"])],
    foreignKeys = [
        ForeignKey(entity = EquipmentEntity::class, parentColumns = ["id"], childColumns = ["equipmentId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(entity = RouteEntity::class, parentColumns = ["id"], childColumns = ["routeId"], onDelete = ForeignKey.SET_NULL),
    ],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sport: Sport,
    /** Inicio en milisegundos UTC. Si la hora es desconocida, es la medianoche local del día. */
    val startEpochMs: Long,
    val startTimeKnown: Boolean,
    val endEpochMs: Long? = null,
    val routeId: Long? = null,
    val routeName: String = "",
    val distanceKm: Double? = null,
    val calories: Int? = null,
    val avgHr: Int? = null,
    val maxHr: Int? = null,
    val avgSpeedKmh: Double? = null,
    val maxSpeedKmh: Double? = null,
    val equipmentId: Long? = null,
    val equipmentName: String? = null,
    val tempMinC: Int? = null,
    val tempMaxC: Int? = null,
    val movingSec: Int? = null,
    val elapsedSec: Int? = null,
    val altMinM: Int? = null,
    val altMaxM: Int? = null,
    val ascentM: Int? = null,
    val descentM: Int? = null,
    val strideM: Double? = null,
    val steps: Int? = null,
    val bestLapSec: Int? = null,
    val avgPaceSecPerKm: Int? = null,
    val bestPaceSecPerKm: Int? = null,
    val avgCadence: Int? = null,
    val avgPowerW: Int? = null,
    val deviceId: Long? = null,
    val importedFileId: Long? = null,
    /** Referencia al origen, p. ej. "TU PEDALADA.xlsx › Tabla › fila 123". */
    val sourceRef: String = "",
    /** Fila/registro original sin modificar, para poder recalcular en el futuro. */
    val rawOriginal: String = "",
    /** Calidad por campo, p. ej. "distanceKm=M;avgSpeedKmh=C". */
    val qualityFlags: String = "",
    val dedupeKey: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "activity_sample",
    indices = [Index("activityId")],
    foreignKeys = [ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE)],
)
data class ActivitySampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val epochMs: Long,
    val distanceM: Double? = null,
    val hr: Int? = null,
    val speedMs: Double? = null,
    val cadence: Int? = null,
    val powerW: Int? = null,
    val altitudeM: Double? = null,
    val temperatureC: Double? = null,
)

@Entity(
    tableName = "gps_point",
    indices = [Index("activityId")],
    foreignKeys = [ForeignKey(entity = ActivityEntity::class, parentColumns = ["id"], childColumns = ["activityId"], onDelete = ForeignKey.CASCADE)],
)
data class GpsPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val seq: Int,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double? = null,
    val epochMs: Long? = null,
)

@Entity(tableName = "statistic", indices = [Index(value = ["period", "periodKey", "sport"], unique = true)])
data class StatisticEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val period: String, // WEEK, MONTH, YEAR
    val periodKey: String, // 2026-W45, 2026-11, 2026
    val sport: Sport,
    val count: Int,
    val distanceKm: Double,
    val movingSec: Long,
    val ascentM: Long,
    val calories: Long,
    val computedAt: Long,
)

@Entity(tableName = "hydration", indices = [Index("activityId")])
data class HydrationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long?,
    val epochMs: Long,
    val drink: String, // "Agua", "Isotónica", ...
    val ml: Int,
)

@Entity(tableName = "food", indices = [Index("activityId")])
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long?,
    val epochMs: Long,
    val name: String,
    val quantity: String,
)

@Entity(tableName = "recovery", indices = [Index(value = ["activityId"], unique = true)])
data class RecoveryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val activityId: Long,
    val fatigue1to5: Int,
    val rpe1to10: Int,
    val recovery: String, // MALA, NORMAL, BUENA
    val notes: String = "",
)

@Entity(tableName = "hr_zone")
data class HeartRateZoneEntity(
    @PrimaryKey val zone: Int, // 1..5
    val minBpm: Int,
    val maxBpm: Int,
)

@Entity(tableName = "sync_history")
data class SyncHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochMs: Long,
    val source: String, // EXCEL, FIT, GPX, TCX, HEALTH_CONNECT, GARMIN_PERSONAL_API
    val newActivities: Int,
    val duplicates: Int,
    val errors: Int,
    val message: String = "",
)

@Entity(tableName = "setting")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "app_version")
data class AppVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val versionName: String,
    val versionCode: Int,
    val installedAt: Long,
    val notes: String = "",
)
