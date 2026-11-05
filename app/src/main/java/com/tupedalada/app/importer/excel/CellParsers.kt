package com.tupedalada.app.importer.excel

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Conversión de los valores del Excel histórico. Reglas observadas en TU PEDALADA.xlsx:
 * - "--", "--:--:--" y celdas vacías significan "sin dato" → null (nunca se inventa un valor).
 * - Decimales con coma en texto ("16,8") y con punto en celdas numéricas (8.28).
 * - Separador de miles con punto en enteros de texto ("12.098" pasos).
 * - Fechas y horas como número de serie de Excel (sistema 1900) o como texto "hh:mm:ss".
 */
object CellParsers {
    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)
    private val THOUSANDS = Regex("""^-?\d{1,3}(\.\d{3})+$""")

    fun isEmpty(c: XlsxReader.Cell?): Boolean {
        val t = c?.raw?.trim() ?: return true
        return t.isEmpty() || t.all { it == '-' || it == ':' || it == ' ' }
    }

    fun number(c: XlsxReader.Cell?, integerContext: Boolean = false): Double? {
        if (isEmpty(c)) return null
        val t = c!!.raw.trim().replace(" ", "").replace(" ", "")
        if (c.isNumber) return t.toDoubleOrNull()
        if (integerContext && THOUSANDS.matches(t)) return t.replace(".", "").toDoubleOrNull()
        if (integerContext && THOUSANDS_COMMA.matches(t)) return t.replace(",", "").toDoubleOrNull()
        val hasDot = t.contains('.'); val hasComma = t.contains(',')
        val normalized = when {
            // Ambos separadores: el último es el decimal ("1.234,5" o "1,234.5").
            hasDot && hasComma -> if (t.lastIndexOf(',') > t.lastIndexOf('.')) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            hasComma -> t.replace(',', '.')
            else -> t
        }
        return normalized.toDoubleOrNull()
    }

    private val THOUSANDS_COMMA = Regex("""^-?\d{1,3}(,\d{3})+$""")

    /** Hora contenida en un texto de fecha y hora: "2025-08-18 17:55:08" o "18/08/2025 17:55". */
    fun timeInDateText(c: XlsxReader.Cell?): java.time.LocalTime? {
        if (isEmpty(c) || c!!.isNumber) {
            val v = c?.raw?.toDoubleOrNull() ?: return null
            val frac = v - Math.floor(v)
            return if (frac > 0) java.time.LocalTime.ofSecondOfDay((frac * 86_400).roundToLong().coerceIn(0, 86_399)) else null
        }
        val m = Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""").find(c.raw) ?: return null
        val (h, mi, s) = m.destructured
        return runCatching { java.time.LocalTime.of(h.toInt(), mi.toInt(), s.ifEmpty { "0" }.toInt()) }.getOrNull()
    }

    fun int(c: XlsxReader.Cell?): Int? = number(c, integerContext = true)?.roundToInt()

    fun date(c: XlsxReader.Cell?): LocalDate? {
        if (isEmpty(c)) return null
        val t = c!!.raw.trim()
        if (c.isNumber) return t.toDoubleOrNull()?.let { EXCEL_EPOCH.plusDays(it.toLong()) }
        // Texto dd/mm/aaaa o aaaa-mm-dd
        Regex("""^(\d{1,2})/(\d{1,2})/(\d{4})""").find(t)?.let { m ->
            val (d, mo, y) = m.destructured
            return runCatching { LocalDate.of(y.toInt(), mo.toInt(), d.toInt()) }.getOrNull()
        }
        return runCatching { LocalDate.parse(t.take(10)) }.getOrNull()
    }

    /** Hora del día. Devuelve null si falta. Una celda que contiene fecha+hora usa solo la parte decimal. */
    fun timeOfDay(c: XlsxReader.Cell?): LocalTime? {
        if (isEmpty(c)) return null
        val t = c!!.raw.trim()
        if (c.isNumber) {
            val v = t.toDoubleOrNull() ?: return null
            val frac = v - Math.floor(v)
            val sec = (frac * 86_400).roundToLong().coerceIn(0, 86_399)
            return LocalTime.ofSecondOfDay(sec)
        }
        return durationSeconds(c)?.takeIf { it in 0..86_399 }?.let { LocalTime.ofSecondOfDay(it.toLong()) }
    }

    /** Duración en segundos desde número de serie (fracción de día) o texto "h:mm:ss" / "mm:ss". */
    fun durationSeconds(c: XlsxReader.Cell?): Int? {
        if (isEmpty(c)) return null
        val t = c!!.raw.trim()
        if (c.isNumber) {
            val v = t.toDoubleOrNull() ?: return null
            // Un valor < 2 es fracción de día; mayor, ya son segundos (algunas filas antiguas).
            return if (v < 2.0) (v * 86_400).roundToInt() else v.roundToInt()
        }
        val parts = t.split(':')
        if (parts.size !in 2..3) return t.replace(',', '.').toDoubleOrNull()?.roundToInt()
        val nums = parts.map { it.replace(',', '.').toDoubleOrNull() ?: return null }
        return if (nums.size == 3) (nums[0] * 3600 + nums[1] * 60 + nums[2]).roundToInt()
        else (nums[0] * 60 + nums[1]).roundToInt()
    }

    /** Ritmo "mm:ss" (min/km) → segundos por km. */
    fun paceSecPerKm(c: XlsxReader.Cell?): Int? = durationSeconds(c)?.takeIf { it in 60..3_600 }

    fun text(c: XlsxReader.Cell?): String? = c?.raw?.trim()?.takeUnless { isEmpty(c) }

    /** Normaliza un encabezado: minúsculas, sin tildes ni espacios extra. */
    fun headerKey(h: String): String =
        java.text.Normalizer.normalize(h, java.text.Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase()
            .replace(Regex("""[^a-z0-9]+"""), " ")
            .trim()
}
