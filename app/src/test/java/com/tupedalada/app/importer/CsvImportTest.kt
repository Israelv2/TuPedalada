package com.tupedalada.app.importer

import com.tupedalada.app.data.db.Sport
import com.tupedalada.app.importer.excel.CellParsers
import com.tupedalada.app.importer.excel.CsvReader
import com.tupedalada.app.importer.excel.TablaImporter
import com.tupedalada.app.importer.excel.XlsxReader.Cell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CsvImportTest {
    private val lima = ZoneId.of("America/Lima")

    private val garminEs = """
        "Tipo de actividad","Fecha","Favorito","Título","Distancia","Calorías","Tiempo","Frecuencia cardiaca media","FC máxima","Velocidad media","Velocidad máxima","Ascenso total","Descenso total","Tiempo en movimiento","Tiempo transcurrido","Altura mínima","Altura máxima"
        "Ciclismo de montaña","2025-08-17 09:26:55","false","San Luis Ciclismo de montaña","48.96","929","02:31:51","137","163","19.2","43.6","612","598","02:31:51","02:50:31","23","214"
        "Caminar","2025-11-23 10:08:29","false","San Luis Caminata","12.29","903","01:53:07","115","131","--","--","65","70","01:53:07","01:55:53","169","200"
        "Natación en piscina","2025-11-24 07:00:00","false","Piscina","1,500","300","00:40:00","--","--","--","--","--","--","00:40:00","00:45:00","--","--"
    """.trimIndent()

    @Test fun leeCsvConComillas() {
        val rows = CsvReader.parse(garminEs)
        assertEquals(4, rows.size)
        assertEquals("San Luis Ciclismo de montaña", rows[1][3]!!.raw)
    }

    @Test fun separadorPuntoYComa() {
        val rows = CsvReader.parse("a;b;c\n1;\"x;y\";3\n")
        assertEquals(';', CsvReader.detectSeparator("a;b;c"))
        assertEquals("x;y", rows[1][1]!!.raw)
    }

    @Test fun importaGarminEnEspanolConHoraDentroDeLaFecha() {
        val p = TablaImporter(zone = lima, sourceFileName = "Activities.csv").preview(CsvReader.parse(garminEs), emptySet(), "CSV")
        assertEquals(2, p.valid.size)
        assertEquals(1, p.errors.size) // natación: modalidad no admitida
        val bike = p.valid.first { it.sport == Sport.BIKE }
        assertTrue(bike.startTimeKnown)
        val t = Instant.ofEpochMilli(bike.startEpochMs).atZone(lima)
        assertEquals(9, t.hour); assertEquals(26, t.minute)
        assertEquals("San Luis Ciclismo de montaña", bike.routeName)
        assertEquals(612, bike.ascentM)
        assertEquals(9111, bike.movingSec)
        val walk = p.valid.first { it.sport == Sport.WALK }
        assertEquals(null, walk.avgSpeedKmh)
    }

    @Test fun duplicadoContraLoQueYaExisteAunqueVengaDeOtraFuente() {
        val first = TablaImporter(zone = lima).preview(CsvReader.parse(garminEs), emptySet(), "CSV").valid
        val loose = first.mapNotNull { TablaImporter.looseKey(it.sport, it.startEpochMs, it.startTimeKnown, it.distanceKm, lima) }.toSet()
        val again = TablaImporter(zone = lima).preview(CsvReader.parse(garminEs), emptySet(), "CSV", loose)
        assertEquals(0, again.valid.size)
        assertEquals(2, again.duplicatesInDb.size)
    }

    @Test fun garminEnIngles() {
        val en = "Activity Type,Date,Title,Distance,Calories,Time,Avg HR,Max HR,Moving Time,Elapsed Time\n" +
            "Road Cycling,2026-10-01 07:05:00,Costa Verde,\"52.30\",\"1,240\",02:05:00,140,170,02:01:00,02:10:00\n" +
            "Running,2026-10-02 06:30:00,Malecon,8.20,520,00:46:15,151,175,00:46:15,00:47:00\n"
        val p = TablaImporter(zone = lima).preview(CsvReader.parse(en), emptySet(), "CSV")
        assertEquals(2, p.valid.size)
        assertEquals(1240, p.valid.first { it.sport == Sport.BIKE }.calories)
        assertEquals(Sport.RUN, p.valid[1].sport)
    }

    @Test fun numerosConSeparadoresMixtos() {
        assertEquals(1234.5, CellParsers.number(Cell("1.234,5", false))!!, 1e-9)
        assertEquals(1234.5, CellParsers.number(Cell("1,234.5", false))!!, 1e-9)
        assertEquals(1240, CellParsers.int(Cell("1,240", false)))
    }

    @Test fun modalidades() {
        assertEquals(Sport.BIKE, TablaImporter.sportOf("Ciclismo en ruta"))
        assertEquals(Sport.BIKE, TablaImporter.sportOf("BIKE"))
        assertEquals(Sport.RUN, TablaImporter.sportOf("Carrera por montaña"))
        assertEquals(Sport.WALK, TablaImporter.sportOf("Caminar"))
        assertEquals(null, TablaImporter.sportOf("Natación en piscina"))
    }
}
