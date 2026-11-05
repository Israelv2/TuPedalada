package com.tupedalada.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
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
import com.tupedalada.app.data.ImportRepository
import com.tupedalada.app.data.db.AppDatabase
import com.tupedalada.app.ui.components.BackBar
import com.tupedalada.app.ui.components.Banner
import com.tupedalada.app.ui.components.BigNumber
import com.tupedalada.app.ui.components.Label
import com.tupedalada.app.ui.components.TPCard
import com.tupedalada.app.ui.int
import com.tupedalada.app.ui.theme.Condensed
import com.tupedalada.app.ui.theme.TP
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private val CSV_MIMES = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/csv", "application/vnd.ms-excel", "application/octet-stream")

/** Pantalla 05: importación y sincronización. */
@Composable
fun ImportScreen(db: AppDatabase, garmin: com.tupedalada.app.garmin.GarminSync, onBack: () -> Unit, onExcelPicked: (Uri) -> Unit, onPending: () -> Unit) {
    val history by db.importDao().syncHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    val pickExcel = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(onExcelPicked) }
    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        BackBar("Importar y sincronizar", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("Garmin Connect")
            GarminCard(db, garmin, onPending)
            Label("Archivos de actividades")
            TPCard(Modifier.clickable { pickExcel.launch(CSV_MIMES) }, borderColor = TP.Walk.copy(alpha = 0.5f)) {
                Text("Importar CSV (Garmin Connect y otros)", fontWeight = FontWeight.SemiBold)
                Text("El archivo «Activities.csv» que exporta Garmin Connect, en español o inglés. Se omiten las actividades que ya estén en la app.", color = TP.Muted, fontSize = 13.sp)
            }
            TPCard(Modifier.clickable { pickExcel.launch(arrayOf(XLSX_MIME, "application/octet-stream")) }) {
                Text("Importar Excel histórico", fontWeight = FontWeight.SemiBold)
                Text("Se lee la hoja «Tabla», con vista previa, duplicados y errores. El archivo original se conserva sin cambios.", color = TP.Muted, fontSize = 13.sp)
            }
            TPCard {
                Label("Cómo sacar el CSV de Garmin Connect")
                Text("En el navegador: Garmin Connect › Actividades › Todas las actividades › «Exportar CSV» (arriba a la derecha). Para más actividades, baja hasta el final de la lista antes de exportar. Pasa el archivo al teléfono e impórtalo aquí.", color = TP.Muted, fontSize = 13.sp)
            }
            Label("Dispositivos y servicios")
            IntegrationRow("Garmin Edge 830 y Enduro 1", "Llegan a través de Garmin Connect (arriba)", "Vía Garmin")
            IntegrationRow("Recorrido GPS y archivos FIT", "Mapa de cada actividad — próxima beta", "Pendiente")
            IntegrationRow("Health Connect", "Pasos, FC y peso — próxima beta", "Pendiente")
            IntegrationRow("Archivos GPX / TCX", "Rutas y entrenamientos — próxima beta", "Pendiente")
            if (history.isNotEmpty()) {
                Label("Historial de sincronización")
                TPCard {
                    val f = SimpleDateFormat("dd/MM/yyyy HH:mm")
                    history.forEach { h ->
                        Row(Modifier.padding(vertical = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${f.format(Date(h.epochMs))} · ${h.source}", fontSize = 14.sp)
                                Text("${h.newActivities} nuevas · ${h.duplicates} duplicadas · ${h.errors} errores", color = TP.Muted, fontSize = 13.sp)
                            }
                            Text(if (h.errors == 0) "OK" else "Revisar", color = if (h.errors == 0) TP.Walk else TP.Warn, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Text("La app no graba GPS en segundo plano.", color = TP.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GarminCard(db: AppDatabase, garmin: com.tupedalada.app.garmin.GarminSync, onPending: () -> Unit) {
    val scope = rememberCoroutineScope()
    val state by garmin.state.collectAsStateWithLifecycle()
    val pending by db.activityDao().pendingCount().collectAsStateWithLifecycle(initialValue = 0)
    var hasKey by remember { mutableStateOf(garmin.client.hasKey) }
    var keyInput by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }
    var last by remember { mutableStateOf<Long?>(null) }
    androidx.compose.runtime.LaunchedEffect(state) { last = garmin.lastSyncMs() }
    val f = remember { SimpleDateFormat("dd/MM/yyyy HH:mm") }

    TPCard(borderColor = TP.Bike.copy(alpha = 0.5f)) {
        if (!hasKey) {
            Text("Conectar con tu clave personal", fontWeight = FontWeight.SemiBold)
            Text("Pega la clave que creaste en Garmin Connect web › Ajustes › Personal API Key. Se guarda cifrada en el teléfono y nunca sale de él salvo hacia Garmin.", color = TP.Muted, fontSize = 13.sp)
            androidx.compose.material3.OutlinedTextField(
                keyInput, { keyInput = it; keyError = null },
                label = { Text("Clave personal de Garmin") }, singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            keyError?.let { Text(it, color = TP.Error, fontSize = 13.sp) }
            Button(onClick = {
                runCatching { garmin.client.saveKey(keyInput) }
                    .onSuccess { hasKey = true; keyInput = ""; scope.launch { garmin.syncIfPossible(force = true) } }
                    .onFailure { keyError = it.message }
            }, enabled = keyInput.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp)) { Text("Guardar y sincronizar") }
        } else {
            Row {
                Column(Modifier.weight(1f)) {
                    Text("Conectado con tu clave personal", fontWeight = FontWeight.SemiBold)
                    Text("Última sincronización: " + (last?.let { f.format(Date(it)) } ?: "nunca"), color = TP.Muted, fontSize = 13.sp)
                }
                Text("Activo", color = TP.Walk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            when (val s = state) {
                com.tupedalada.app.garmin.GarminSync.State.Running -> Row(Modifier.padding(top = 8.dp)) { CircularProgressIndicator(Modifier.height(20.dp)); Text("  Buscando actividades nuevas…") }
                is com.tupedalada.app.garmin.GarminSync.State.Done -> Text(
                    "${s.newOnes} nuevas · ${s.duplicates} ya estaban" + if (s.skippedTypes > 0) " · ${s.skippedTypes} de otras modalidades" else "",
                    color = TP.Walk, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                is com.tupedalada.app.garmin.GarminSync.State.Failed -> Text(s.message, color = TP.Error, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                else -> Unit
            }
            if (pending > 0) Button(onClick = onPending, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Confirmar $pending actividades nuevas") }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { scope.launch { garmin.syncIfPossible(force = true) } }, enabled = state != com.tupedalada.app.garmin.GarminSync.State.Running) { Text("Sincronizar ahora") }
                OutlinedButton(onClick = { garmin.client.deleteKey(); hasKey = false }) { Text("Quitar clave") }
            }
            Text("Se sincroniza sola cada vez que abres la app (con internet).", color = TP.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun IntegrationRow(title: String, sub: String, status: String) {
    TPCard {
        Row {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(sub, color = TP.Muted, fontSize = 13.sp) }
            Text(status, color = if (status == "Pendiente") TP.Warn else TP.Walk, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Pantalla auxiliar A03: asistente de importación del Excel. */
@Composable
fun ExcelWizardScreen(repo: ImportRepository, uri: Uri, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var prepared by remember { mutableStateOf<ImportRepository.Prepared?>(null) }
    var importing by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf<Int?>(null) }
    var commitError by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(uri) { prepared = repo.prepare(uri) }

    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        BackBar(if (prepared?.format == "CSV") "Importar CSV" else "Importar archivo", onBack)
        val p = prepared
        if (p == null) {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text("Analizando el archivo…", color = TP.Muted)
            }
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TPCard {
                Text(p.fileName, fontWeight = FontWeight.SemiBold)
                Text("${"%.1f".format(p.sizeBytes / 1_048_576.0)} MB · copia original guardada sin cambios", color = TP.Muted, fontSize = 13.sp)
                if (p.sheets.isNotEmpty()) Text("Hojas: " + p.sheets.joinToString(" · "), color = TP.Muted, fontSize = 12.sp)
            }
            if (p.alreadyImported) Banner("Este mismo archivo ya se importó antes. Las filas repetidas se detectarán como duplicadas.", TP.Warn)
            p.error?.let { Banner(if (p.format == "CSV") "No se pudo leer el CSV: $it" else "No se pudo leer la hoja «Tabla»: $it") }
            val pv = p.preview ?: return@Column
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Count("válidas", pv.valid.size, TP.Walk, Modifier.weight(1f))
                Count("duplicadas", pv.duplicatesInFile.size + pv.duplicatesInDb.size, TP.Warn, Modifier.weight(1f))
                Count("con errores", pv.errors.size, TP.Error, Modifier.weight(1f))
            }
            TPCard {
                Label("Resumen")
                Text("Filas leídas: ${int(pv.totalRows)}", fontSize = 14.sp)
                Text("Periodo: ${pv.firstDate ?: "—"} → ${pv.lastDate ?: "—"}", fontSize = 14.sp)
                Text("Por modalidad: " + pv.valid.groupBy { it.sport }.entries.joinToString(" · ") { "${TP.sportName(it.key)} ${it.value.size}" }, fontSize = 14.sp)
                Text("Sin hora de inicio: ${pv.unknownStartTime} (se guardan con la fecha y se marcan)", fontSize = 14.sp)
                Text("Avisos de campos no legibles: ${pv.warnings.size} (el campo queda vacío)", fontSize = 14.sp)
                if (pv.equipmentNames.isNotEmpty()) Text("Equipos detectados: " + pv.equipmentNames.keys.joinToString(", "), fontSize = 14.sp)
                if (pv.unmappedHeaders.isNotEmpty()) Text("Columnas que se conservan solo en el original: " + pv.unmappedHeaders.joinToString(", "), color = TP.Muted, fontSize = 12.sp)
            }
            val issues = (pv.errors.map { "Error" to it } + pv.duplicatesInFile.map { "Duplicada" to it } + pv.duplicatesInDb.map { "Ya existe" to it } + pv.warnings.map { "Aviso" to it }).take(60)
            if (issues.isNotEmpty()) TPCard {
                Label("Detalle (primeras ${issues.size})")
                issues.forEach { (kind, i) -> Text("Fila ${i.row} · $kind: ${i.message}", fontSize = 13.sp, color = TP.Muted, modifier = Modifier.padding(top = 4.dp)) }
            }
            commitError?.let { Banner(it) }
            when {
                imported != null -> {
                    Banner("Importadas ${imported} actividades.", TP.Walk)
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Ir al historial") }
                }
                importing -> Row { CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("  Guardando…") }
                else -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp)) { Text("Cancelar") }
                    Button(
                        onClick = {
                            importing = true
                            scope.launch {
                                runCatching { repo.commit(p) }
                                    .onSuccess { imported = it }
                                    .onFailure { commitError = "No se guardó nada: ${it.message}" }
                                importing = false
                            }
                        },
                        enabled = pv.valid.isNotEmpty(),
                        modifier = Modifier.weight(1.4f).height(52.dp),
                    ) { Text("Importar ${int(pv.valid.size)} filas") }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Count(label: String, n: Int, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    TPCard(modifier) {
        BigNumber(int(n), color = color, size = 24)
        Text(label, color = TP.Muted, fontSize = 12.sp, fontFamily = Condensed)
    }
}
