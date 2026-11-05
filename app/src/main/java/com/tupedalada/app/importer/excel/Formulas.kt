package com.tupedalada.app.importer.excel

import kotlin.math.roundToInt

/**
 * Fórmulas documentadas (ver docs/FORMULAS.md). Todas devuelven null si faltan datos:
 * nunca se rellena un valor con una suposición.
 */
object Formulas {
    /** Velocidad media (km/h) = distancia (km) / tiempo en movimiento (h). Dato CALCULADO. */
    fun speedKmh(km: Double, movingSec: Int): Double? =
        if (movingSec <= 0) null else km / (movingSec / 3600.0)

    /** Ritmo (s/km) = tiempo en movimiento (s) / distancia (km). Dato CALCULADO. */
    fun paceSecPerKm(km: Double, movingSec: Int): Int? =
        if (km <= 0.0) null else (movingSec / km).roundToInt()

    /** IMC = peso (kg) / altura (m)². Dato CALCULADO. */
    fun bmi(kg: Double, heightCm: Double): Double? =
        if (heightCm <= 0.0) null else kg / ((heightCm / 100.0) * (heightCm / 100.0))

    /** FC máxima estimada (Tanaka): 208 − 0,7 × edad. Dato ESTIMADO, solo si el perfil no tiene FC máx. */
    fun estimatedMaxHr(ageYears: Int): Int = (208 - 0.7 * ageYears).roundToInt()

    /**
     * Zonas de FC por porcentaje de la FC máxima (50-60-70-80-90-100 %).
     * Devuelve 5 pares (mín, máx) en ppm.
     */
    fun hrZones(maxHr: Int): List<Pair<Int, Int>> {
        val p = listOf(0.50, 0.60, 0.70, 0.80, 0.90, 1.00)
        return (0 until 5).map { i -> (maxHr * p[i]).roundToInt() to ((maxHr * p[i + 1]).roundToInt() - if (i < 4) 1 else 0) }
    }

    fun formatDuration(sec: Int?): String {
        if (sec == null) return "—"
        val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun formatPace(secPerKm: Int?): String = secPerKm?.let { "%d:%02d".format(it / 60, it % 60) } ?: "—"
}
