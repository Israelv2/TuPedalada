package com.tupedalada.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tupedalada.app.data.db.AppDatabase
import com.tupedalada.app.data.db.Sport
import com.tupedalada.app.importer.excel.Formulas
import com.tupedalada.app.ui.components.BackBar
import com.tupedalada.app.ui.components.BigNumber
import com.tupedalada.app.ui.components.EmptyState
import com.tupedalada.app.ui.components.Label
import com.tupedalada.app.ui.components.ProgressBar
import com.tupedalada.app.ui.components.Stat
import com.tupedalada.app.ui.components.TPCard
import com.tupedalada.app.ui.hours
import com.tupedalada.app.ui.int
import com.tupedalada.app.ui.km
import com.tupedalada.app.ui.startOfDayMs
import com.tupedalada.app.ui.theme.TP
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Pantallas 07, 08 y 09: dashboard por modalidad con su paleta. */
@Composable
fun SportScreen(db: AppDatabase, sport: Sport, onBack: () -> Unit, onActivity: (Long) -> Unit, onHistory: () -> Unit) {
    val color = TP.sport(sport)
    var tab by rememberSaveable { mutableIntStateOf(1) }
    val today = LocalDate.now()
    val from = when (tab) { 0 -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); 1 -> today.withDayOfMonth(1); else -> today.withDayOfYear(1) }
    val dao = db.activityDao()
    val items by remember(tab) { dao.filtered(startOfDayMs(from), startOfDayMs(today.plusDays(1)), sport, null, null) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val recent by remember { dao.recentBySport(sport, 5) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val byEq by remember { dao.kmByEquipment(sport) }.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        BackBar(TP.sportName(sport).uppercase(), onBack)
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Semana", "Mes", "Año").forEachIndexed { i, n ->
                FilterChip(tab == i, { tab = i }, { Text(n) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color, selectedLabelColor = TP.Bg))
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (recent.isEmpty()) { EmptyState("Sin actividades de ${TP.sportName(sport)}", "Importa tus actividades para ver aquí tus estadísticas."); return@Column }
            val dist = items.sumOf { it.distanceKm ?: 0.0 }
            val moving = items.sumOf { (it.movingSec ?: 0).toLong() }
            TPCard(borderColor = color.copy(alpha = 0.4f)) {
                Label("Distancia · " + listOf("semana", "mes", "año")[tab])
                BigNumber(km(dist), "km", color = color, size = 48)
                Spacer(Modifier.height(10.dp))
                Row {
                    Stat("Actividades", items.size.toString(), modifier = Modifier.weight(1f))
                    Stat("Tiempo", hours(moving), modifier = Modifier.weight(1f))
                    Stat("Desnivel +", int(items.mapNotNull { it.ascentM }.sum()), "m", Modifier.weight(1f))
                }
                Row(Modifier.padding(top = 8.dp)) {
                    if (sport == Sport.BIKE) {
                        val speed = if (moving > 0) Formulas.speedKmh(dist, moving.toInt()) else null
                        Stat("Vel. media", km(speed), "km/h", Modifier.weight(1f), if (speed != null) 'C' else null)
                    } else {
                        val pace = if (dist > 0 && moving > 0) Formulas.paceSecPerKm(dist, moving.toInt()) else null
                        Stat("Ritmo medio", Formulas.formatPace(pace), "/km", Modifier.weight(1f), if (pace != null) 'C' else null)
                    }
                    val hr = items.mapNotNull { it.avgHr }
                    Stat("FC media", if (hr.isEmpty()) "—" else (hr.sum() / hr.size).toString(), "ppm", Modifier.weight(1f))
                    if (sport == Sport.WALK) Stat("Pasos", int(items.mapNotNull { it.steps }.sum()), modifier = Modifier.weight(1f))
                    else Stat("Calorías", int(items.mapNotNull { it.calories }.sum()), "kcal", Modifier.weight(1f))
                }
            }
            TPCard {
                Row { Label("Últimas actividades"); Spacer(Modifier.weight(1f)); TextButton(onClick = onHistory) { Text("Ver todas") } }
                recent.forEach { a -> ActivityRow(a) { onActivity(a.id) } }
            }
            if (byEq.isNotEmpty()) TPCard {
                Label("Km por equipo · histórico")
                val max = byEq.maxOf { it.km ?: 0.0 }.coerceAtLeast(1.0)
                byEq.forEach { e ->
                    Row(Modifier.padding(top = 10.dp)) { Text(e.equipmentName ?: "Sin equipo", Modifier.weight(1f)); Text("${km(e.km, 0)} km") }
                    Spacer(Modifier.height(5.dp))
                    ProgressBar(((e.km ?: 0.0) / max).toFloat(), color)
                }
            }
        }
    }
}
