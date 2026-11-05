package com.tupedalada.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tupedalada.app.data.db.AppDatabase
import com.tupedalada.app.data.db.PeriodTotal
import com.tupedalada.app.data.db.Sport
import com.tupedalada.app.ui.ES
import com.tupedalada.app.ui.components.Label
import com.tupedalada.app.ui.components.TPCard
import com.tupedalada.app.ui.hours
import com.tupedalada.app.ui.km
import com.tupedalada.app.ui.startOfDayMs
import com.tupedalada.app.ui.theme.Condensed
import com.tupedalada.app.ui.theme.TP
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle

/**
 * Histórico directo dentro de cada modalidad: casillas por año → casillas por mes → actividades.
 * Petición del usuario (beta 2): ver el histórico sin pasar por el menú.
 */
@Composable
fun HistoryBoxes(db: AppDatabase, sport: Sport, onActivity: (Long) -> Unit) {
    val dao = db.activityDao()
    val color = TP.sport(sport)
    val years by remember(sport) { dao.yearTotals(sport) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var year by rememberSaveable(sport) { mutableStateOf<Int?>(null) }
    var month by rememberSaveable(sport) { mutableStateOf<Int?>(null) }

    TPCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Histórico de ${TP.sportName(sport)}")
            Spacer(Modifier.weight(1f))
            if (year != null) TextButton(onClick = { if (month != null) month = null else year = null }) {
                Text(if (month != null) "‹ Meses" else "‹ Años", color = color)
            }
        }
        if (years.isEmpty()) {
            Text("Sin actividades registradas.", color = TP.Muted, fontSize = 14.sp)
            return@TPCard
        }
        val y = year
        val m = month
        when {
            y == null -> {
                Text("Toca un año", color = TP.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                BoxGrid(years, columns = 3, color = color, maxKm = years.maxOf { it.distanceKm ?: 0.0 },
                    title = { it.period.toString() }, onPick = { year = it.period; month = null })
            }
            m == null -> {
                val months by remember(sport, y) { dao.monthTotals(sport, y.toString()) }.collectAsStateWithLifecycle(initialValue = emptyList())
                val yt = years.firstOrNull { it.period == y }
                Text("$y · ${km(yt?.distanceKm)} km · ${yt?.count ?: 0} actividades · ${hours(yt?.movingSec)} h",
                    fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(bottom = 8.dp))
                // Las 12 casillas siempre visibles; los meses sin actividad aparecen apagados.
                val full = (1..12).map { mm -> months.firstOrNull { it.period == mm } ?: PeriodTotal(mm, 0, null, null) }
                BoxGrid(full, columns = 4, color = color, maxKm = full.maxOf { it.distanceKm ?: 0.0 },
                    title = { Month.of(it.period).getDisplayName(TextStyle.SHORT, ES).replace(".", "").replaceFirstChar { c -> c.uppercase() } },
                    onPick = { if (it.count > 0) month = it.period })
            }
            else -> {
                val from = LocalDate.of(y, m, 1)
                val list by remember(sport, y, m) {
                    dao.filtered(startOfDayMs(from), startOfDayMs(from.plusMonths(1)), sport, null, null)
                }.collectAsStateWithLifecycle(initialValue = emptyList())
                Text("${Month.of(m).getDisplayName(TextStyle.FULL, ES).replaceFirstChar { it.uppercase() }} $y · ${km(list.sumOf { it.distanceKm ?: 0.0 })} km · ${list.size} actividades",
                    fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                list.forEach { a -> ActivityRow(a) { onActivity(a.id) } }
            }
        }
    }
}

@Composable
private fun BoxGrid(
    items: List<PeriodTotal>,
    columns: Int,
    color: Color,
    maxKm: Double,
    title: (PeriodTotal) -> String,
    onPick: (PeriodTotal) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { p ->
                    val active = p.count > 0
                    val share = if (maxKm > 0) ((p.distanceKm ?: 0.0) / maxKm).toFloat() else 0f
                    Column(
                        Modifier.weight(1f).heightIn(min = 72.dp)
                            .background(if (active) color.copy(alpha = 0.10f + 0.25f * share) else TP.Surface2, RoundedCornerShape(12.dp))
                            .border(BorderStroke(1.dp, if (active) color.copy(alpha = 0.6f) else TP.Border), RoundedCornerShape(12.dp))
                            .clickable(enabled = active, role = Role.Button, onClick = { onPick(p) })
                            .padding(10.dp),
                    ) {
                        Text(title(p), fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = if (active) TP.Text else TP.Muted)
                        Text(if (active) "${km(p.distanceKm, 0)} km" else "—", fontSize = 13.sp, color = if (active) color else TP.Muted, fontWeight = FontWeight.SemiBold)
                        if (active) Text("${p.count} act.", fontSize = 11.sp, color = TP.Muted)
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}
