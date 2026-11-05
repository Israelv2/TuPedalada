package com.tupedalada.app.importer

import com.tupedalada.app.data.db.Sport
import com.tupedalada.app.importer.excel.Formulas
import com.tupedalada.app.importer.excel.TablaImporter
import com.tupedalada.app.importer.excel.XlsxReader.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TablaImporterTest {
    private val headers = listOf(
        "Tipo de actividad", "Año", "Mes", "Fecha", "Hora comienzo", "Hora llegada", "Ruta", "Distancia",
        "Calorías", "Frecuencia cardiaca media", "FC máxima", "Velocidad media", "Velocidad máxima", "Equipo",
        "Temperatura mínima", "Temperatura máxima", "Tiempo en movimiento", "Tiempo transcurrido",
    )

    private fun row(vararg v: Cell?): Map<Int, Cell> = v.withIndex().mapNotNull { (i, c) -> c?.let { i to it } }.toMap()
    private fun t(s: String) = Cell(s, false)
    private fun n(s: String) = Cell(s, true)
    private val header = headers.withIndex().associate { it.index to t(it.value) }

    // 18/08/2025 = serie 45887; 17:55:08 = 0.74662...
    private val bike = row(t("BIKE"), t("2025"), t("AGOSTO"), n("45887"), n("0.7466203703703704"), n("0.7712847222222222"),
        t("Miraflores BIKE"), n("8.28"), t("270"), t("--"), t("--"), n("16.5"), n("28"), t("Aluminio 29er"),
        n("16"), n("24"), n("0.020613425925925925"), n("0.024664351851851851"))

    @Test fun filaValidaConCalidadYOrigen() {
        val p = TablaImporter().preview(listOf(header, bike), emptySet())
        assertEquals(1, p.valid.size)
        val a = p.valid.first()
        assertEquals(Sport.BIKE, a.sport)
        assertEquals(8.28, a.distanceKm!!, 1e-9)
        assertEquals(270, a.calories)
        assertEquals(null, a.avgHr) // "--" no se inventa
        assertTrue(a.startTimeKnown)
        assertEquals("Aluminio 29er", a.equipmentName)
        assertTrue(a.qualityFlags.contains("distanceKm=M"))
        assertTrue(a.sourceRef.endsWith("fila 2"))
        assertTrue(a.rawOriginal.contains("Miraflores BIKE"))
    }

    @Test fun duplicadoDentroDelArchivoYEnBaseDeDatos() {
        val p1 = TablaImporter().preview(listOf(header, bike, bike), emptySet())
        assertEquals(1, p1.valid.size)
        assertEquals(1, p1.duplicatesInFile.size)
        val p2 = TablaImporter().preview(listOf(header, bike), setOf(p1.valid.first().dedupeKey))
        assertEquals(0, p2.valid.size)
        assertEquals(1, p2.duplicatesInDb.size)
    }

    @Test fun horaCeroEsHoraDesconocida() {
        val r = bike.toMutableMap().apply { put(4, n("0")) }
        val a = TablaImporter().preview(listOf(header, r), emptySet()).valid.first()
        assertFalse(a.startTimeKnown)
    }

    @Test fun sinFechaEsError() {
        val r = bike.toMutableMap().apply { remove(3) }
        val p = TablaImporter().preview(listOf(header, r), emptySet())
        assertEquals(0, p.valid.size)
        assertEquals(1, p.errors.size)
    }

    @Test fun formulas() {
        assertEquals(20.0, Formulas.speedKmh(10.0, 1800)!!, 1e-9)
        assertEquals(330, Formulas.paceSecPerKm(10.0, 3300))
        assertEquals(23.42, Formulas.bmi(74.2, 178.0)!!, 0.01)
        assertEquals(5, Formulas.hrZones(182).size)
    }
}
