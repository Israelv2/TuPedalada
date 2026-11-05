package com.tupedalada.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tupedalada.app.data.db.ActivityEntity
import com.tupedalada.app.data.db.AppDatabase
import com.tupedalada.app.data.db.Sport
import com.tupedalada.app.importer.excel.Formulas
import com.tupedalada.app.ui.ES
import com.tupedalada.app.ui.components.BackBar
import com.tupedalada.app.ui.components.EmptyState
import com.tupedalada.app.ui.dayLabel
import com.tupedalada.app.ui.km
import com.tupedalada.app.ui.startOfDayMs
import com.tupedalada.app.ui.theme.TP
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle

/** Pantalla 13: historial con filtros por año, mes, modalidad, equipo y ruta. */
@Composable
fun HistoryScreen(db: AppDatabase, initialSport: Sport? = null, onBack: () -> Unit, onActivity: (Long) -> Unit) {
    val dao = db.activityDao()
    val firstMs by dao.firstActivityMs().collectAsStateWithLifecycle(initialValue = null)
    val equipment by dao.equipmentNames().collectAsStateWithLifecycle(initialValue = emptyList())
    val routes by dao.routeNames().collectAsStateWithLifecycle(initialValue = emptyList())
    var year by rememberSaveable { mutableStateOf(LocalDate.now().year) }
    var month by rememberSaveable { mutableStateOf<Int?>(null) }
    var sport by rememberSaveable { mutableStateOf(initialSport) }
    var eq by rememberSaveable { mutableStateOf<String?>(null) }
    var route by rememberSaveable { mutableStateOf<String?>(null) }

    val from = if (month == null) LocalDate.of(year, 1, 1) else LocalDate.of(year, month!!, 1)
    val to = if (month == null) from.plusYears(1) else from.plusMonths(1)
    val list by remember(year, month, sport, eq, route) { dao.filtered(startOfDayMs(from), startOfDayMs(to), sport, eq, route) }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val firstYear = firstMs?.let { com.tupedalada.app.ui.dateOf(it).year } ?: LocalDate.now().year
    val years = (LocalDate.now().year downTo firstYear).toList()

    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        BackBar("Historial", onBack)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Picker("Año $year", years.map { it.toString() to it }) { year = it }
            Picker(month?.let { Month.of(it).getDisplayName(TextStyle.FULL, ES).replaceFirstChar { c -> c.uppercase() } } ?: "Todo el año",
                listOf("Todo el año" to null) + (1..12).map { Month.of(it).getDisplayName(TextStyle.FULL, ES) to it }) { month = it }
            Picker(eq ?: "Equipo", listOf("Todos los equipos" to null) + equipment.map { it to it }) { eq = it }
            Picker(route ?: "Ruta", listOf("Todas las rutas" to null) + routes.map { it to it }) { route = it }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(sport == null, { sport = null }, { Text("Todas") })
            Sport.entries.forEach { s -> FilterChip(sport == s, { sport = s }, { Text(TP.sportName(s)) }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("${list.size} actividades", color = TP.Muted, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text("${km(list.sumOf { it.distanceKm ?: 0.0 })} km", color = TP.Muted, fontSize = 13.sp)
        }
        if (list.isEmpty()) Box(Modifier.padding(16.dp)) { EmptyState("Sin actividades", "No hay actividades con estos filtros.") }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            items(list, key = { it.id }) { a -> ActivityRow(a) { onActivity(a.id) } }
        }
    }
}

@Composable
private fun <T> Picker(label: String, options: List<Pair<String, T>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(selected = false, onClick = { open = true }, label = { Text("$label ▾") })
        DropdownMenu(open, { open = false }) {
            options.forEach { (text, v) -> DropdownMenuItem(text = { Text(text) }, onClick = { onPick(v); open = false }) }
        }
    }
}

@Composable
fun ActivityRow(a: ActivityEntity, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(8.dp).height(38.dp).background(TP.sport(a.sport), RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.routeName.ifBlank { TP.sportName(a.sport) }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dayLabel(a.startEpochMs) + (a.equipmentName?.let { " · $it" } ?: ""), color = TP.Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${km(a.distanceKm, 2)} km", fontWeight = FontWeight.SemiBold)
            Text(Formulas.formatDuration(a.movingSec ?: a.elapsedSec), color = TP.Muted, fontSize = 12.sp)
        }
    }
}
