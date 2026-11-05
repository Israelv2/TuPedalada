package com.tupedalada.app.importer

import com.tupedalada.app.importer.excel.CellParsers
import com.tupedalada.app.importer.excel.XlsxReader.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class CellParsersTest {
    private fun t(s: String) = Cell(s, false)
    private fun n(s: String) = Cell(s, true)

    @Test fun guionesSonSinDato() {
        assertNull(CellParsers.number(t("--")))
        assertNull(CellParsers.durationSeconds(t("--:--:--")))
        assertNull(CellParsers.int(t("")))
    }

    @Test fun decimalesConComaYPunto() {
        assertEquals(16.8, CellParsers.number(t("16,8"))!!, 1e-9)
        assertEquals(8.28, CellParsers.number(n("8.28"))!!, 1e-9)
        assertEquals(8.28, CellParsers.number(t("8.28"))!!, 1e-9)
    }

    @Test fun separadorDeMilesEnPasos() {
        assertEquals(12098, CellParsers.int(t("12.098")))
        assertEquals(1522, CellParsers.int(t("1.522")))
        assertEquals(450, CellParsers.int(t("450")))
    }

    @Test fun fechasYHorasDeExcel() {
        assertEquals(LocalDate.of(2025, 8, 18), CellParsers.date(n("45887")))
        assertEquals(LocalTime.of(18, 0), CellParsers.timeOfDay(n("0.75")))
        assertEquals(LocalTime.of(17, 55, 8), CellParsers.timeOfDay(t("17:55:08")))
    }

    @Test fun duraciones() {
        assertEquals(6787, CellParsers.durationSeconds(t("01:53:07")))
        assertEquals(1781, CellParsers.durationSeconds(n("0.020613425925925925")))
        assertEquals(736, CellParsers.paceSecPerKm(t("12:16")))
    }

    @Test fun encabezadosSinTildes() {
        assertEquals("fc maxima", CellParsers.headerKey("FC máxima"))
        assertEquals("calorias", CellParsers.headerKey("Calorías"))
    }
}
