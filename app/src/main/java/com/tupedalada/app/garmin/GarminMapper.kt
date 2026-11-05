package com.tupedalada.app.garmin

import com.tupedalada.app.data.db.ActivityEntity
import com.tupedalada.app.data.db.DataQuality
import com.tupedalada.app.data.db.Sport
import com.tupedalada.app.importer.excel.TablaImporter
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Convierte una actividad de la API personal de Garmin Connect
 * (GET /activitylist-service/activities/search/activities) en una actividad de la app.
 *
 * Unidades de Garmin: distancia en metros, duraciones en segundos, velocidades en m/s,
 * FC en ppm, calorías en kcal, altitudes en metros, temperaturas en °C.
 */
object GarminMapper {
    private val FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** Devuelve null si la modalidad no es bici, carrera o caminata. */
    fun map(o: JSONObject, zone: ZoneId): ActivityEntity? {
        val id = o.optLong("activityId", -1).takeIf { it > 0 } ?: return null
        val typeKey = o.optJSONObject("activityType")?.optString("typeKey").orEmpty()
        val sport = TablaImporter.sportOf(typeKey) ?: return null

        val start = when {
            o.has("startTimeGMT") && !o.isNull("startTimeGMT") ->
                LocalDateTime.parse(o.getString("startTimeGMT"), FMT).toInstant(ZoneOffset.UTC).toEpochMilli()
            o.has("startTimeLocal") && !o.isNull("startTimeLocal") ->
                LocalDateTime.parse(o.getString("startTimeLocal"), FMT).atZone(zone).toInstant().toEpochMilli()
            else -> return null
        }
        fun d(k: String): Double? = if (o.has(k) && !o.isNull(k)) o.optDouble(k).takeUnless { it.isNaN() } else null
        fun i(k: String): Int? = d(k)?.roundToInt()

        val km = d("distance")?.let { it / 1000.0 }
        val moving = i("movingDuration") ?: i("duration")
        val elapsed = i("elapsedDuration") ?: i("duration")
        val q = LinkedHashMap<String, DataQuality>()
        fun m(field: String, v: Any?) { if (v != null) q[field] = DataQuality.MEASURED }

        val avgSpeed = d("averageSpeed")?.let { it * 3.6 }
        val maxSpeed = d("maxSpeed")?.let { it * 3.6 }
        val pace = if (sport != Sport.BIKE && km != null && km > 0 && moving != null) (moving / km).roundToInt() else null
        val cadence = if (sport == Sport.BIKE) i("averageBikingCadenceInRevPerMinute") else i("averageRunningCadenceInStepsPerMinute")
        val calories = i("calories")
        listOf("distanceKm" to km, "avgSpeedKmh" to avgSpeed, "calories" to calories, "avgHr" to i("averageHR"),
            "maxHr" to i("maxHR"), "ascentM" to i("elevationGain"), "avgCadence" to cadence, "avgPowerW" to i("avgPower"))
            .forEach { (f, v) -> m(f, v) }
        if (pace != null) q["avgPaceSecPerKm"] = DataQuality.CALCULATED

        return ActivityEntity(
            sport = sport,
            startEpochMs = start,
            startTimeKnown = true,
            endEpochMs = elapsed?.let { start + it * 1000L },
            routeName = o.optString("activityName").takeIf { it.isNotBlank() && it != "null" } ?: "",
            distanceKm = km,
            calories = calories,
            avgHr = i("averageHR"),
            maxHr = i("maxHR"),
            avgSpeedKmh = avgSpeed,
            maxSpeedKmh = maxSpeed,
            tempMinC = i("minTemperature"),
            tempMaxC = i("maxTemperature"),
            movingSec = moving,
            elapsedSec = elapsed,
            altMinM = i("minElevation"),
            altMaxM = i("maxElevation"),
            ascentM = i("elevationGain"),
            descentM = i("elevationLoss"),
            strideM = d("avgStrideLength")?.let { it / 100.0 },
            steps = i("steps"),
            avgPaceSecPerKm = pace,
            avgCadence = cadence,
            avgPowerW = i("avgPower"),
            sourceRef = "Garmin Connect › actividad $id ($typeKey)",
            rawOriginal = o.toString(),
            qualityFlags = q.entries.joinToString(";") { "${it.key}=${it.value.code}" },
            dedupeKey = "GARMIN|$id",
            garminActivityId = id,
            pendingConfirm = true,
        )
    }
}
