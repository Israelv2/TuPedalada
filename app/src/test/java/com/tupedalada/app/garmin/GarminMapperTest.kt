package com.tupedalada.app.garmin

import com.tupedalada.app.data.db.Sport
import com.tupedalada.app.importer.excel.TablaImporter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class GarminMapperTest {
    private val lima = ZoneId.of("America/Lima")

    private val ride = JSONObject(
        """{"activityId":18123456789,"activityName":"San Luis Ciclismo",
            "startTimeLocal":"2026-11-05 07:12:00","startTimeGMT":"2026-11-05 12:12:00",
            "activityType":{"typeId":5,"typeKey":"mountain_biking","parentTypeId":2},
            "distance":48600.0,"duration":7960.0,"movingDuration":7900.0,"elapsedDuration":8100.0,
            "elevationGain":612.0,"elevationLoss":598.0,"averageSpeed":6.083,"maxSpeed":13.39,
            "averageHR":142.0,"maxHR":176.0,"calories":1240.0,"averageBikingCadenceInRevPerMinute":84.0,
            "minElevation":120.0,"maxElevation":418.0,"minTemperature":19.0,"maxTemperature":27.0}"""
    )

    @Test fun mapeaUnidadesYOrigen() {
        val a = GarminMapper.map(ride, lima)!!
        assertEquals(Sport.BIKE, a.sport)
        assertEquals(48.6, a.distanceKm!!, 1e-9)
        assertEquals(21.9, a.avgSpeedKmh!!, 0.01)
        assertEquals(7900, a.movingSec)
        assertEquals(84, a.avgCadence)
        assertEquals(1240, a.calories)
        assertTrue(a.pendingConfirm)
        assertEquals(18123456789L, a.garminActivityId)
        assertEquals("GARMIN|18123456789", a.dedupeKey)
        val t = Instant.ofEpochMilli(a.startEpochMs).atZone(lima)
        assertEquals(7, t.hour); assertEquals(12, t.minute)
    }

    @Test fun claveFlexibleCoincideConLaDelExcel() {
        val a = GarminMapper.map(ride, lima)!!
        val fromGarmin = TablaImporter.looseKey(a.sport, a.startEpochMs, true, a.distanceKm, lima)
        val fromExcel = TablaImporter.looseKey(Sport.BIKE, java.time.LocalDateTime.of(2026, 11, 5, 7, 12, 30).atZone(lima).toInstant().toEpochMilli(), true, 48.62, lima)
        assertEquals(fromExcel, fromGarmin)
    }

    @Test fun otrasModalidadesSeOmiten() {
        val swim = JSONObject("""{"activityId":1,"startTimeLocal":"2026-11-05 07:00:00","activityType":{"typeKey":"lap_swimming"},"distance":1500.0}""")
        assertNull(GarminMapper.map(swim, lima))
    }

    @Test fun caducidadDelToken() {
        // payload {"exp":1800000000}
        val jwt = "e30." + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":1800000000}""".toByteArray()) + ".x"
        assertEquals(1_800_000_000_000L, GarminClient.jwtExpiryMs(jwt))
    }
}
