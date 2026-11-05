package com.tupedalada.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tupedalada.app.data.db.ActivityEntity
import com.tupedalada.app.data.db.AppDatabase
import com.tupedalada.app.data.db.Sport
import com.tupedalada.app.ui.ES
import com.tupedalada.app.ui.components.BigNumber
import com.tupedalada.app.ui.components.EmptyState
import com.tupedalada.app.ui.components.Label
import com.tupedalada.app.ui.components.ProgressBar
import com.tupedalada.app.ui.components.TPCard
import com.tupedalada.app.ui.dateOf
import com.tupedalada.app.ui.dayLabel
import com.tupedalada.app.ui.hours
import com.tupedalada.app.ui.int
import com.tupedalada.app.ui.km
import com.tupedalada.app.ui.startOfDayMs
import com.tupedalada.app.ui.theme.Condensed
import com.tupedalada.app.ui.theme.TP
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun DashboardScreen(
    db: AppDatabase,
    garmin: com.tupedalada.app.garmin.GarminSync,
    onPending: () -> Unit,
    onMenu: () -> Unit,
    onImport: () -> Unit,
    onSport: (Sport) -> Unit,
    onActivity: (Long) -> Unit,
    onHistory: () -> Unit,
    onWeight: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val monthStart = remember { today.withDayOfMonth(1) }
    val prevMonthStart = remember { monthStart.minusMonths(1) }
    val weekStart = remember { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
    val from = remember { minOf(prevMonthStart, weekStart) }
    val dao = db.activityDao()
    val count by dao.count().collectAsStateWithLifecycle(initialValue = -1)
    val recent by remember { dao.filtered(startOfDayMs(from), startOfDayMs(today.plusDays(1)), null, null, null) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val last by remember { dao.recent(1) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by remember { db.profileDao().observe() }.collectAsStateWithLifecycle(initialValue = null)
    val weight by remember { db.weightDao().latest() }.collectAsStateWithLifecycle(initialValue = null)

    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Abrir menú", tint = TP.Text) }
            Column(Modifier.weight(1f)) {
                Text(today.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ES)).replaceFirstChar { it.uppercase() }, color = TP.Muted, fontSize = 13.sp)
                Text("Hola" + (profile?.name?.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""), fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
            }
            IconButton(onClick = onImport) { Icon(Icons.Default.Sync, "Importar y sincronizar", tint = TP.Text) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val pendingCount by db.activityDao().pendingCount().collectAsStateWithLifecycle(initialValue = 0)
            val sync by garmin.state.collectAsStateWithLifecycle()
            if (pendingCount > 0) {
                TPCard(Modifier.clickable(onClick = onPending), borderColor = TP.Bike) {
                    Text("$pendingCount ${if (pendingCount == 1) "actividad nueva" else "actividades nuevas"} de Garmin Connect", fontWeight = FontWeight.SemiBold)
                    Text("Toca para confirmar modalidad y equipo", color = TP.Muted, fontSize = 13.sp)
                }
            }
            when (val s = sync) {
                com.tupedalada.app.garmin.GarminSync.State.Running -> Text("Sincronizando con Garmin Connect…", color = TP.Muted, fontSize = 13.sp)
                is com.tupedalada.app.garmin.GarminSync.State.Failed -> com.tupedalada.app.ui.components.Banner("Garmin: ${s.message}", TP.Warn)
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SportShortcut("Running", Icons.AutoMirrored.Filled.DirectionsRun, Sport.RUN, Modifier.weight(1f), onSport)
                SportShortcut("Walking", Icons.AutoMirrored.Filled.DirectionsWalk, Sport.WALK, Modifier.weight(1f), onSport)
                SportShortcut("Biking", Icons.AutoMirrored.Filled.DirectionsBike, Sport.BIKE, Modifier.weight(1f), onSport)
            }
            if (count == 0) {
                EmptyState("Todavía no hay actividades", "Importa tu histórico de Excel o el CSV que exporta Garmin Connect.") {
                    Button(onClick = onImport) { Text("Importar actividades") }
                }
                return@Column
            }
            val todays = recent.filter { dateOf(it.startEpochMs) == today }
            TPCard {
                Row { Label("Resumen de hoy"); Spacer(Modifier.weight(1f)); Text("${todays.size} actividades", color = TP.Muted, fontSize = 12.sp) }
                Spacer(Modifier.height(10.dp))
                if (todays.isEmpty()) Text("Sin actividad registrada hoy.", color = TP.Muted)
                else Row {
                    Column(Modifier.weight(1f)) { BigNumber(km(todays.sumOf { it.distanceKm ?: 0.0 })); Text("km", color = TP.Muted, fontSize = 12.sp) }
                    Column(Modifier.weight(1f)) { BigNumber(hours(todays.sumOf { (it.movingSec ?: 0).toLong() })); Text("h:min", color = TP.Muted, fontSize = 12.sp) }
                    Column(Modifier.weight(1f)) { BigNumber(int(todays.mapNotNull { it.calories }.sum())); Text("kcal", color = TP.Muted, fontSize = 12.sp) }
                    Column(Modifier.weight(1f)) { BigNumber("+" + int(todays.mapNotNull { it.ascentM }.sum())); Text("m desnivel", color = TP.Muted, fontSize = 12.sp) }
                }
            }
            last.firstOrNull()?.let { a -> LastActivityCard(a) { onActivity(a.id) } }
            WeekCard(recent.filter { !dateOf(it.startEpochMs).isBefore(weekStart) }, weekStart)
            MonthCard(recent, monthStart, prevMonthStart)
            TPCard(Modifier.clickable(onClick = onWeight)) {
                Label("Evolución del peso")
                Spacer(Modifier.height(6.dp))
                if (weight == null) Text("Sin registros. Toca para anotar tu peso de hoy.", color = TP.Muted)
                else Row(verticalAlignment = Alignment.Bottom) {
                    BigNumber(km(weight!!.kg), "kg")
                    Spacer(Modifier.width(10.dp))
                    Text("último registro: " + LocalDate.ofEpochDay(weight!!.epochDay).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), color = TP.Muted, fontSize = 13.sp)
                }
            }
            CalendarCard(recent.filter { !dateOf(it.startEpochMs).isBefore(monthStart) }, monthStart, today, onHistory)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SportShortcut(name: String, icon: ImageVector, sport: Sport, modifier: Modifier, onSport: (Sport) -> Unit) {
    Column(
        modifier.background(TP.Surface, RoundedCornerShape(14.dp)).clickable { onSport(sport) }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, null, tint = TP.sport(sport), modifier = Modifier.size(28.dp))
        Text(name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun LastActivityCard(a: ActivityEntity, onClick: () -> Unit) {
    TPCard(Modifier.clickable(onClick = onClick), borderColor = TP.sport(a.sport).copy(alpha = 0.4f)) {
        Row { Label("Última actividad"); Spacer(Modifier.weight(1f)); Text(TP.sportName(a.sport).uppercase(), color = TP.sport(a.sport), fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(6.dp))
        Text(a.routeName.ifBlank { TP.sportName(a.sport) }, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
        Text(dayLabel(a.startEpochMs) + (a.equipmentName?.let { " · $it" } ?: ""), color = TP.Muted, fontSize = 13.sp)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${km(a.distanceKm, 2)} km", fontWeight = FontWeight.SemiBold)
            a.avgSpeedKmh?.let { Text("${km(it)} km/h") }
            a.avgHr?.let { Text("$it ppm") }
        }
    }
}

@Composable
private fun WeekCard(items: List<ActivityEntity>, weekStart: LocalDate) {
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val byDay = days.map { d -> items.filter { dateOf(it.startEpochMs) == d } }
    val maxKm = byDay.maxOf { l -> l.sumOf { it.distanceKm ?: 0.0 } }.coerceAtLeast(1.0)
    TPCard {
        Row { Label("Esta semana"); Spacer(Modifier.weight(1f)); Text("${km(items.sumOf { it.distanceKm ?: 0.0 })} km · ${items.size} act.", fontFamily = Condensed, fontWeight = FontWeight.SemiBold) }
        Row(Modifier.fillMaxWidth().height(100.dp).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            byDay.forEachIndexed { i, l ->
                val total = l.sumOf { it.distanceKm ?: 0.0 }
                val main = l.groupBy { it.sport }.maxByOrNull { e -> e.value.sumOf { it.distanceKm ?: 0.0 } }?.key
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    val h = if (total > 0) (6 + 64 * (total / maxKm)).dp else 4.dp
                    Box(Modifier.fillMaxWidth().height(h).background(main?.let(TP::sport) ?: TP.Border, RoundedCornerShape(6.dp)))
                    Spacer(Modifier.height(6.dp))
                    Text(listOf("L", "M", "X", "J", "V", "S", "D")[i], color = TP.Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun MonthCard(items: List<ActivityEntity>, monthStart: LocalDate, prevStart: LocalDate) {
    val cur = items.filter { !dateOf(it.startEpochMs).isBefore(monthStart) }
    val prev = items.filter { val d = dateOf(it.startEpochMs); !d.isBefore(prevStart) && d.isBefore(monthStart) }
    val maxKm = Sport.entries.maxOf { s -> cur.filter { it.sport == s }.sumOf { it.distanceKm ?: 0.0 } }.coerceAtLeast(1.0)
    TPCard {
        Row { Label(monthStart.format(DateTimeFormatter.ofPattern("MMMM", ES))); Spacer(Modifier.weight(1f)); Text("vs. mes anterior", color = TP.Muted, fontSize = 12.sp) }
        Spacer(Modifier.height(10.dp))
        listOf(Sport.BIKE, Sport.RUN, Sport.WALK).forEach { s ->
            val c = cur.filter { it.sport == s }.sumOf { it.distanceKm ?: 0.0 }
            val p = prev.filter { it.sport == s }.sumOf { it.distanceKm ?: 0.0 }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(TP.sportName(s), Modifier.weight(1f))
                Text("${km(c)} km", fontWeight = FontWeight.SemiBold)
                if (p > 0) {
                    val pct = ((c - p) / p * 100).toInt()
                    Text("  ${if (pct >= 0) "+" else "−"}${kotlin.math.abs(pct)} %", color = if (pct >= 0) TP.Walk else TP.Error)
                }
            }
            Spacer(Modifier.height(5.dp))
            ProgressBar((c / maxKm).toFloat(), TP.sport(s))
        }
    }
}

@Composable
private fun CalendarCard(items: List<ActivityEntity>, monthStart: LocalDate, today: LocalDate, onClick: () -> Unit) {
    val bySport = items.groupBy { dateOf(it.startEpochMs) }.mapValues { e -> e.value.maxByOrNull { it.distanceKm ?: 0.0 }!!.sport }
    val offset = monthStart.dayOfWeek.value - 1
    val days = monthStart.lengthOfMonth()
    TPCard(Modifier.clickable(onClick = onClick)) {
        Row { Label("Calendario deportivo"); Spacer(Modifier.weight(1f)); Text(monthStart.format(DateTimeFormatter.ofPattern("MMMM yyyy", ES)), fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        Spacer(Modifier.height(8.dp))
        val cells = List(offset) { null } + (1..days).map { monthStart.withDayOfMonth(it) }
        Row {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                Text(it, Modifier.weight(1f), color = TP.Muted, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.padding(top = 4.dp)) {
                (week + List(7 - week.size) { null }).forEach { d ->
                    Column(Modifier.weight(1f).height(34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        if (d != null) {
                            Text(d.dayOfMonth.toString(), fontSize = 12.sp, color = if (d.isAfter(today)) TP.Muted else TP.Text, fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
                            Box(Modifier.size(5.dp).background(bySport[d]?.let(TP::sport) ?: androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
        }
    }
}
