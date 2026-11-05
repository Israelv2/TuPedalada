package com.tupedalada.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tupedalada.app.data.db.ActivityEntity
import com.tupedalada.app.data.db.AppDatabase
import com.tupedalada.app.data.db.Sport
import com.tupedalada.app.importer.excel.Formulas
import com.tupedalada.app.ui.components.BackBar
import com.tupedalada.app.ui.components.EmptyState
import com.tupedalada.app.ui.components.Label
import com.tupedalada.app.ui.components.TPCard
import com.tupedalada.app.ui.dayLabel
import com.tupedalada.app.ui.km
import com.tupedalada.app.ui.theme.Condensed
import com.tupedalada.app.ui.theme.TP
import com.tupedalada.app.ui.timeLabel
import kotlinx.coroutines.launch

/** Pantalla 06: confirmar modalidad y equipo de las actividades que llegaron de Garmin. */
@Composable
fun ConfirmScreen(db: AppDatabase, onBack: () -> Unit, onAfterActivity: (Long) -> Unit) {
    val pending by db.activityDao().pending().collectAsStateWithLifecycle(initialValue = emptyList())
    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        BackBar(if (pending.size == 1) "Nueva actividad" else "Nuevas actividades (${pending.size})", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (pending.isEmpty()) EmptyState("Todo confirmado", "No hay actividades nuevas pendientes de revisar.")
            pending.forEach { a -> PendingCard(db, a, onAfterActivity) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PendingCard(db: AppDatabase, a: ActivityEntity, onAfterActivity: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    var sport by remember(a.id) { mutableStateOf(a.sport) }
    var eq by remember(a.id) { mutableStateOf(a.equipmentName) }
    val equipment by remember(sport) { db.equipmentDao().forSport(sport) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val color = TP.sport(sport)
    TPCard(borderColor = color.copy(alpha = 0.5f)) {
        Text(a.routeName.ifBlank { TP.sportName(a.sport) }, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        Text("${dayLabel(a.startEpochMs)} · ${timeLabel(a.startEpochMs, a.startTimeKnown)} · Garmin Connect", color = TP.Muted, fontSize = 13.sp)
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${km(a.distanceKm, 2)} km", fontWeight = FontWeight.SemiBold)
            Text(Formulas.formatDuration(a.movingSec ?: a.elapsedSec))
            a.avgHr?.let { Text("$it ppm") }
        }
        Label("Modalidad", TP.Muted)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Sport.entries.forEach { s ->
                FilterChip(sport == s, { sport = s; if (s != a.sport) eq = null }, { Text(TP.sportName(s)) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TP.sport(s), selectedLabelColor = TP.Bg))
            }
        }
        Label("Equipo", TP.Muted)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(eq == null, { eq = null }, { Text("Sin equipo") })
            equipment.forEach { e ->
                FilterChip(eq == e.name, { eq = e.name }, { Text(e.name) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = color, selectedLabelColor = TP.Bg))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { scope.launch { db.activityDao().discardPending(a.id) } }, modifier = Modifier.weight(1f)) { Text("Descartar") }
            Button(
                onClick = {
                    scope.launch {
                        val id = eq?.let { db.equipmentDao().byName(it)?.id }
                        db.activityDao().confirm(a.id, sport, id, eq)
                        onAfterActivity(a.id)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = TP.Bg),
                modifier = Modifier.weight(1.6f),
            ) { Text("Guardar") }
        }
    }
}
