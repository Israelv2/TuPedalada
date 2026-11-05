package com.tupedalada.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tupedalada.app.data.db.AppDatabase
import com.tupedalada.app.data.db.Sport
import com.tupedalada.app.importer.excel.Formulas
import com.tupedalada.app.ui.components.BackBar
import com.tupedalada.app.ui.components.Label
import com.tupedalada.app.ui.components.QualityBadge
import com.tupedalada.app.ui.components.Stat
import com.tupedalada.app.ui.components.TPCard
import com.tupedalada.app.ui.dayLabel
import com.tupedalada.app.ui.int
import com.tupedalada.app.ui.km
import com.tupedalada.app.ui.theme.Condensed
import com.tupedalada.app.ui.theme.TP
import com.tupedalada.app.ui.timeLabel

/** Pantalla 10: detalle de actividad, con distintivo M/C/E por dato. */
@Composable
fun DetailScreen(db: AppDatabase, id: Long, onBack: () -> Unit, onAfterActivity: (Long) -> Unit) {
    val a by remember(id) { db.activityDao().observe(id) }.collectAsStateWithLifecycle(initialValue = null)
    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        BackBar("Detalle de actividad", onBack)
        val act = a ?: run { Text("Cargando…", color = TP.Muted, modifier = Modifier.padding(16.dp)); return@Column }
        val q = act.qualityFlags.split(';').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1].firstOrNull() } }.toMap()
        fun qual(field: String, present: Boolean): Char? = if (!present) null else q[field] ?: 'M'
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.background(TP.sport(act.sport), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(TP.sportName(act.sport).uppercase(), color = TP.Bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text(dayLabel(act.startEpochMs) + " · " + timeLabel(act.startEpochMs, act.startTimeKnown), color = TP.Muted, fontSize = 13.sp)
            }
            Text(act.routeName.ifBlank { TP.sportName(act.sport) }, fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text(listOfNotNull(act.equipmentName, act.sourceRef.takeIf { it.isNotBlank() }).joinToString(" · "), color = TP.Muted, fontSize = 13.sp)
            TPCard {
                val rows = buildList<Triple<String, Pair<String, String?>, Char?>> {
                    add(Triple("Distancia", km(act.distanceKm, 2) to "km", qual("distanceKm", act.distanceKm != null)))
                    add(Triple("En movimiento", Formulas.formatDuration(act.movingSec) to null, qual("movingSec", act.movingSec != null)))
                    add(Triple("Transcurrido", Formulas.formatDuration(act.elapsedSec) to null, qual("elapsedSec", act.elapsedSec != null)))
                    if (act.sport == Sport.BIKE) {
                        add(Triple("Vel. media", km(act.avgSpeedKmh) to "km/h", qual("avgSpeedKmh", act.avgSpeedKmh != null)))
                        add(Triple("Vel. máx.", km(act.maxSpeedKmh) to "km/h", qual("maxSpeedKmh", act.maxSpeedKmh != null)))
                    } else {
                        add(Triple("Ritmo medio", Formulas.formatPace(act.avgPaceSecPerKm) to "/km", qual("avgPaceSecPerKm", act.avgPaceSecPerKm != null)))
                        add(Triple("Pasos", int(act.steps) to null, qual("steps", act.steps != null)))
                    }
                    add(Triple("Calorías", int(act.calories) to "kcal", qual("calories", act.calories != null)))
                    add(Triple("FC media", int(act.avgHr) to "ppm", qual("avgHr", act.avgHr != null)))
                    add(Triple("FC máx.", int(act.maxHr) to "ppm", qual("maxHr", act.maxHr != null)))
                    add(Triple("Desnivel +", int(act.ascentM) to "m", qual("ascentM", act.ascentM != null)))
                    add(Triple("Altitud", "${int(act.altMinM)}–${int(act.altMaxM)}" to "m", qual("altMinM", act.altMinM != null)))
                    add(Triple("Temperatura", "${int(act.tempMinC)}–${int(act.tempMaxC)}" to "°C", qual("tempMinC", act.tempMinC != null)))
                    add(Triple("Potencia", int(act.avgPowerW) to "W", qual("avgPowerW", act.avgPowerW != null)))
                }
                rows.chunked(3).forEach { r ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        r.forEach { (label, v, quality) -> Stat(label, v.first, v.second?.takeIf { v.first != "—" }, Modifier.weight(1f), quality) }
                        repeat(3 - r.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { QualityBadge('M'); Text("medido", color = TP.Muted, fontSize = 11.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { QualityBadge('C'); Text("calculado", color = TP.Muted, fontSize = 11.sp) }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { QualityBadge('E'); Text("estimado", color = TP.Muted, fontSize = 11.sp) }
                }
                Text("«—» = el origen no tiene ese dato; no se rellena con estimaciones.", color = TP.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
            if (!act.startTimeKnown) TPCard { Label("Aviso"); Text("El Excel no incluye la hora de inicio de esta actividad.", color = TP.Muted, fontSize = 14.sp) }
            TPCard {
                Label("Mapa y zonas cardíacas")
                Text("Disponibles cuando la actividad venga de un archivo FIT/GPX/TCX con recorrido GPS y muestras. Las del Excel solo tienen totales.", color = TP.Muted, fontSize = 14.sp)
            }
            OutlinedButton(onClick = { onAfterActivity(act.id) }) { Text("Registrar hidratación y recuperación") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
