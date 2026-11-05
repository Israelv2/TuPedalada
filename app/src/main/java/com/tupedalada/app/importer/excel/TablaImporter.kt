package com.tupedalada.app.importer.excel

import com.tupedalada.app.data.db.ActivityEntity
import com.tupedalada.app.data.db.DataQuality
import com.tupedalada.app.data.db.Sport
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Importa la hoja «Tabla» del Excel histórico (decisión del usuario 05/11/2026:
 * usar solo «Tabla» como fuente). Genera una vista previa con filas válidas, duplicadas y con
 * errores antes de guardar nada.
 */
class TablaImporter(
    private val zone: ZoneId = ZoneId.of("America/Lima"),
    private val sourceFileName: String = "TU PEDALADA.xlsx",
) {
    enum class Field(vararg val headers: String) {
        TYPE("tipo de actividad"), DATE("fecha"), START("hora comienzo", "comienzo"), END("hora llegada"),
        ROUTE("ruta"), DISTANCE("distancia"), CALORIES("calorias"),
        AVG_HR("frecuencia cardiaca media", "fc media"), MAX_HR("fc maxima"),
        AVG_SPEED("velocidad media", "velocidad med"), MAX_SPEED("velocidad maxima", "velocidad max"),
        EQUIPMENT("equipo"), TEMP_MIN("temperatura minima", "temp minima"), TEMP_MAX("temperatura maxima", "temp maxima"),
        MOVING("tiempo en movimiento"), ELAPSED("tiempo transcurrido"),
        ALT_MIN("altura minima", "altura min"), ALT_MAX("altura maxima", "altura max"),
        ASCENT("ascenso total"), DESCENT("descenso total"), STRIDE("longitud media de zancada"),
        STEPS("pasos"), BEST_LAP("mejor tiempo de vuelta"), PACE("ritmo medio"), BEST_PACE("ritmo optimo"),
    }

    data class RowIssue(val row: Int, val message: String)

    data class Preview(
        val sheet: String,
        val totalRows: Int,
        val valid: List<ActivityEntity>,
        val duplicatesInFile: List<RowIssue>,
        val duplicatesInDb: List<RowIssue>,
        val errors: List<RowIssue>,
        val warnings: List<RowIssue>,
        val unknownStartTime: Int,
        val equipmentNames: Map<String, Sport>,
        val firstDate: LocalDate?,
        val lastDate: LocalDate?,
        val unmappedHeaders: List<String>,
    )

    fun preview(rows: List<Map<Int, XlsxReader.Cell>>, existingKeys: Set<String>, sheet: String = "Tabla"): Preview {
        require(rows.isNotEmpty()) { "La hoja está vacía" }
        val header = rows.first()
        val colOf = HashMap<Field, Int>()
        val unmapped = ArrayList<String>()
        header.toSortedMap().forEach { (col, cell) ->
            val key = CellParsers.headerKey(cell.raw)
            val field = Field.entries.firstOrNull { f -> f.headers.any { key == it || key.startsWith("$it ") } }
            if (field != null && field !in colOf) colOf[field] = col else if (key.isNotEmpty()) unmapped += cell.raw
        }
        val missing = listOf(Field.TYPE, Field.DATE, Field.DISTANCE).filter { it !in colOf }
        require(missing.isEmpty()) { "Faltan columnas obligatorias: ${missing.joinToString()}" }

        val valid = ArrayList<ActivityEntity>()
        val dupFile = ArrayList<RowIssue>()
        val dupDb = ArrayList<RowIssue>()
        val errors = ArrayList<RowIssue>()
        val warnings = ArrayList<RowIssue>()
        val seen = HashSet<String>()
        val equipment = LinkedHashMap<String, Sport>()
        var unknownStart = 0
        var first: LocalDate? = null
        var last: LocalDate? = null
        val headerNames = header.mapValues { it.value.raw }

        rows.drop(1).forEachIndexed { i, r ->
            val excelRow = i + 2
            if (r.values.all { CellParsers.isEmpty(it) }) return@forEachIndexed
            fun c(f: Field) = colOf[f]?.let { r[it] }

            val sport = sportOf(CellParsers.text(c(Field.TYPE)))
            val date = CellParsers.date(c(Field.DATE))
            val dist = CellParsers.number(c(Field.DISTANCE))
            if (sport == null) { errors += RowIssue(excelRow, "Tipo de actividad no reconocido: '${c(Field.TYPE)?.raw ?: ""}'"); return@forEachIndexed }
            if (date == null) { errors += RowIssue(excelRow, "Fecha vacía o no válida"); return@forEachIndexed }

            val warn = ArrayList<String>()
            fun <T> parsed(f: Field, value: T?): T? {
                val cell = c(f)
                if (value == null && cell != null && !CellParsers.isEmpty(cell)) warn += "${f.name.lowercase()}='${cell.raw}'"
                return value
            }

            val startRaw = CellParsers.timeOfDay(c(Field.START))
            val startKnown = startRaw != null && startRaw != LocalTime.MIDNIGHT
            if (!startKnown) unknownStart++
            val start = date.atTime(if (startKnown) startRaw!! else LocalTime.MIDNIGHT).atZone(zone)
            val endTime = CellParsers.timeOfDay(c(Field.END))
            val endMs = if (startKnown && endTime != null && endTime != LocalTime.MIDNIGHT) {
                var end = date.atTime(endTime).atZone(zone)
                if (end.isBefore(start)) end = end.plusDays(1)
                end.toInstant().toEpochMilli()
            } else null

            val moving = parsed(Field.MOVING, CellParsers.durationSeconds(c(Field.MOVING)))
            val elapsed = parsed(Field.ELAPSED, CellParsers.durationSeconds(c(Field.ELAPSED)))
            var avgSpeed = parsed(Field.AVG_SPEED, CellParsers.number(c(Field.AVG_SPEED)))
            val q = LinkedHashMap<String, DataQuality>()
            if (dist != null) q["distanceKm"] = DataQuality.MEASURED
            if (avgSpeed != null) q["avgSpeedKmh"] = DataQuality.MEASURED
            else if (dist != null && moving != null && moving > 0 && sport == Sport.BIKE) {
                avgSpeed = Formulas.speedKmh(dist, moving); q["avgSpeedKmh"] = DataQuality.CALCULATED
            }
            var pace = parsed(Field.PACE, CellParsers.paceSecPerKm(c(Field.PACE)))
            if (pace != null) q["avgPaceSecPerKm"] = DataQuality.MEASURED
            else if (sport != Sport.BIKE && dist != null && dist > 0 && moving != null) {
                pace = Formulas.paceSecPerKm(dist, moving); q["avgPaceSecPerKm"] = DataQuality.CALCULATED
            }
            val calories = parsed(Field.CALORIES, CellParsers.int(c(Field.CALORIES)))
            if (calories != null) q["calories"] = DataQuality.MEASURED

            val eqName = CellParsers.text(c(Field.EQUIPMENT))?.takeUnless { it == "0" }?.replace(Regex("\\s+"), " ")
            if (eqName != null) equipment.putIfAbsent(eqName, sport)

            val key = dedupeKey(sport, date, if (startKnown) startRaw else null, dist, calories, elapsed)
            if (!seen.add(key)) { dupFile += RowIssue(excelRow, "Repetida dentro del Excel (${date} · ${dist ?: "?"} km)"); return@forEachIndexed }
            if (key in existingKeys) { dupDb += RowIssue(excelRow, "Ya existe en la app (${date} · ${dist ?: "?"} km)"); return@forEachIndexed }
            if (dist == null) warn += "sin distancia"
            if (warn.isNotEmpty()) warnings += RowIssue(excelRow, warn.joinToString("; "))

            val f0 = first
            val l0 = last
            first = if (f0 == null || date < f0) date else f0
            last = if (l0 == null || date > l0) date else l0

            valid += ActivityEntity(
                sport = sport,
                startEpochMs = start.toInstant().toEpochMilli(),
                startTimeKnown = startKnown,
                endEpochMs = endMs,
                routeName = CellParsers.text(c(Field.ROUTE)) ?: "",
                distanceKm = dist,
                calories = calories,
                avgHr = parsed(Field.AVG_HR, CellParsers.int(c(Field.AVG_HR))),
                maxHr = parsed(Field.MAX_HR, CellParsers.int(c(Field.MAX_HR))),
                avgSpeedKmh = avgSpeed,
                maxSpeedKmh = parsed(Field.MAX_SPEED, CellParsers.number(c(Field.MAX_SPEED))),
                equipmentName = eqName,
                tempMinC = parsed(Field.TEMP_MIN, CellParsers.int(c(Field.TEMP_MIN))),
                tempMaxC = parsed(Field.TEMP_MAX, CellParsers.int(c(Field.TEMP_MAX))),
                movingSec = moving,
                elapsedSec = elapsed,
                altMinM = parsed(Field.ALT_MIN, CellParsers.int(c(Field.ALT_MIN))),
                altMaxM = parsed(Field.ALT_MAX, CellParsers.int(c(Field.ALT_MAX))),
                ascentM = parsed(Field.ASCENT, CellParsers.int(c(Field.ASCENT))),
                descentM = parsed(Field.DESCENT, CellParsers.int(c(Field.DESCENT))),
                strideM = parsed(Field.STRIDE, CellParsers.number(c(Field.STRIDE))),
                steps = parsed(Field.STEPS, CellParsers.int(c(Field.STEPS))),
                bestLapSec = parsed(Field.BEST_LAP, CellParsers.durationSeconds(c(Field.BEST_LAP))),
                avgPaceSecPerKm = pace,
                bestPaceSecPerKm = parsed(Field.BEST_PACE, CellParsers.paceSecPerKm(c(Field.BEST_PACE))),
                sourceRef = "$sourceFileName › $sheet › fila $excelRow",
                rawOriginal = rawJson(r, headerNames),
                qualityFlags = q.entries.joinToString(";") { "${it.key}=${it.value.code}" } +
                    if (warn.isNotEmpty()) ";warn=" + warn.joinToString(",").replace(";", ",") else "",
                dedupeKey = key,
            )
        }
        return Preview(sheet, rows.size - 1, valid, dupFile, dupDb, errors, warnings, unknownStart, equipment, first, last, unmapped)
    }

    companion object {
        fun sportOf(t: String?): Sport? = when (t?.trim()?.uppercase(Locale.ROOT)) {
            "BIKE", "CICLISMO", "BICI", "CYCLING", "BIKING" -> Sport.BIKE
            "RUNNING", "CORRER", "CARRERA", "RUN" -> Sport.RUN
            "WALKING", "CAMINAR", "CAMINATA", "WALK" -> Sport.WALK
            else -> null
        }

        /**
         * Clave de duplicado: modalidad + día + hora (si se conoce) + distancia + calorías + tiempo.
         * Dos filas solo se consideran la misma actividad si coinciden en todo eso.
         */
        fun dedupeKey(sport: Sport, date: LocalDate, start: LocalTime?, km: Double?, kcal: Int?, elapsed: Int?): String =
            listOf(
                sport.name, date.toString(), start?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "?",
                km?.let { "%.2f".format(Locale.ROOT, it) } ?: "?", kcal?.toString() ?: "?", elapsed?.toString() ?: "?",
            ).joinToString("|")

        fun rawJson(r: Map<Int, XlsxReader.Cell>, headers: Map<Int, String>): String =
            r.toSortedMap().entries.joinToString(",", "{", "}") { (col, cell) ->
                "\"" + esc(headers[col] ?: "col$col") + "\":\"" + esc(cell.raw) + "\""
            }

        private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    }
}
