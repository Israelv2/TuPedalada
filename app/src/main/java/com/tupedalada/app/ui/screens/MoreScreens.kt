package com.tupedalada.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tupedalada.app.BuildConfig
import com.tupedalada.app.data.db.AppDatabase
import com.tupedalada.app.data.db.FoodEntity
import com.tupedalada.app.data.db.HydrationEntity
import com.tupedalada.app.data.db.ProfileEntity
import com.tupedalada.app.data.db.RecoveryEntity
import com.tupedalada.app.data.db.WeightEntity
import com.tupedalada.app.importer.excel.Formulas
import com.tupedalada.app.security.PinManager
import com.tupedalada.app.ui.components.BackBar
import com.tupedalada.app.ui.components.Banner
import com.tupedalada.app.ui.components.Label
import com.tupedalada.app.ui.components.TPCard
import com.tupedalada.app.ui.km
import com.tupedalada.app.ui.theme.Condensed
import com.tupedalada.app.ui.theme.TP
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Pantalla 15: perfil personal. */
@Composable
fun ProfileScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val stored by db.profileDao().observe().collectAsStateWithLifecycle(initialValue = null)
    val weight by db.weightDao().latest().collectAsStateWithLifecycle(initialValue = null)
    var p by remember { mutableStateOf(ProfileEntity()) }
    var loaded by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    LaunchedEffect(stored) { if (!loaded && stored != null) { p = stored!!; loaded = true } }
    var birth by remember(loaded) { mutableStateOf(p.birthDateEpochDay?.let { LocalDate.ofEpochDay(it).toString() } ?: "") }

    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        BackBar("Perfil", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("Datos personales")
            Field("Nombre", p.name) { p = p.copy(name = it) }
            Field("Fecha de nacimiento (aaaa-mm-dd)", birth) { birth = it }
            Field("Nacionalidad", p.nationality) { p = p.copy(nationality = it) }
            Field("Altura (cm)", p.heightCm?.toString() ?: "", KeyboardType.Decimal) { p = p.copy(heightCm = it.replace(',', '.').toDoubleOrNull()) }
            Label("Salud")
            Field("Enfermedades / condiciones", p.conditions) { p = p.copy(conditions = it) }
            Field("Medicación relevante", p.medication) { p = p.copy(medication = it) }
            Label("Parámetros deportivos")
            Field("FC máxima (ppm)", p.maxHr?.toString() ?: "", KeyboardType.Number) { p = p.copy(maxHr = it.toIntOrNull()) }
            Field("FC en reposo (ppm)", p.restHr?.toString() ?: "", KeyboardType.Number) { p = p.copy(restHr = it.toIntOrNull()) }
            Field("FTP bici (W)", p.ftpWatts?.toString() ?: "", KeyboardType.Number) { p = p.copy(ftpWatts = it.toIntOrNull()) }
            Field("Objetivo de pasos", p.stepGoal.toString(), KeyboardType.Number) { p = p.copy(stepGoal = it.toIntOrNull() ?: 10_000) }
            val bmi = weight?.kg?.let { w -> p.heightCm?.let { h -> Formulas.bmi(w, h) } }
            TPCard { Label("IMC (calculado)"); Text(bmi?.let { km(it) } ?: "Faltan peso o altura", fontFamily = Condensed, fontSize = 22.sp) }
            Text("Los datos se guardan solo en este teléfono.", color = TP.Muted, fontSize = 12.sp)
            if (saved) Banner("Perfil guardado.", TP.Walk)
            Button(onClick = {
                val b = runCatching { LocalDate.parse(birth.trim()).toEpochDay() }.getOrNull()
                scope.launch { db.profileDao().upsert(p.copy(birthDateEpochDay = b)); saved = true }
            }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar perfil") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) =
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = type), modifier = Modifier.fillMaxWidth())

/** Pantalla auxiliar A04: peso una vez al día. */
@Composable
fun WeightScreen(db: AppDatabase, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val latest by db.weightDao().latest().collectAsStateWithLifecycle(initialValue = null)
    val all by db.weightDao().all().collectAsStateWithLifecycle(initialValue = emptyList())
    val profile by db.profileDao().observe().collectAsStateWithLifecycle(initialValue = null)
    var tenths by remember { mutableIntStateOf(0) }
    LaunchedEffect(latest) { if (tenths == 0) tenths = ((latest?.kg ?: 70.0) * 10).toInt() }
    var saved by remember { mutableStateOf(false) }
    val today = LocalDate.now().toEpochDay()
    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        BackBar("Peso de hoy", onBack)
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Se pide una sola vez al día. Último registro: " + (latest?.let { "${km(it.kg)} kg (${LocalDate.ofEpochDay(it.epochDay)})" } ?: "ninguno"), color = TP.Muted)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = { tenths -= 1; saved = false }, shape = CircleShape, modifier = Modifier.size(56.dp)) { Text("−", fontSize = 24.sp) }
                Text("  ${km(tenths / 10.0)} kg  ", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 48.sp)
                FilledTonalButton(onClick = { tenths += 1; saved = false }, shape = CircleShape, modifier = Modifier.size(56.dp)) { Text("+", fontSize = 24.sp) }
            }
            profile?.heightCm?.let { h -> Text("IMC: ${km(Formulas.bmi(tenths / 10.0, h))}", Modifier.fillMaxWidth(), color = TP.Muted) }
            if (saved) Banner("Peso guardado para hoy.", TP.Walk)
            Button(onClick = { scope.launch { db.weightDao().upsert(WeightEntity(epochDay = today, kg = tenths / 10.0)); saved = true } }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (all.any { it.epochDay == today }) "Actualizar peso de hoy" else "Guardar peso")
            }
            if (all.size >= 2) TPCard {
                Label("Evolución (últimos registros)")
                all.takeLast(10).reversed().forEach { Text("${LocalDate.ofEpochDay(it.epochDay)} · ${km(it.kg)} kg", fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
            }
        }
    }
}

/** Pantalla 17: hidratación, alimentación y recuperación tras la actividad. */
@Composable
fun AfterActivityScreen(db: AppDatabase, activityId: Long, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var water by remember { mutableIntStateOf(0) }
    var drink by remember { mutableStateOf("") }
    var drinkMl by remember { mutableStateOf("") }
    var food by remember { mutableStateOf("") }
    var foodQty by remember { mutableStateOf("") }
    var fatigue by remember { mutableIntStateOf(3) }
    var rpe by remember { mutableFloatStateOf(5f) }
    var rec by remember { mutableStateOf("NORMAL") }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        BackBar("Después de la actividad", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TPCard {
                Label("Agua consumida")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { water = (water - 250).coerceAtLeast(0) }, shape = CircleShape) { Text("−") }
                    Text("  $water ml  ", fontFamily = Condensed, fontSize = 36.sp, color = TP.Bike)
                    FilledTonalButton(onClick = { water += 250 }, shape = CircleShape) { Text("+") }
                }
            }
            TPCard {
                Label("Otra bebida")
                Field("Bebida", drink) { drink = it }
                Field("Cantidad (ml)", drinkMl, KeyboardType.Number) { drinkMl = it }
            }
            TPCard {
                Label("Alimento")
                Field("Alimento", food) { food = it }
                Field("Cantidad", foodQty) { foodQty = it }
            }
            TPCard {
                Label("Fatiga (1 fresco · 5 agotado)")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { (1..5).forEach { n -> FilterChip(fatigue == n, { fatigue = n }, { Text("$n") }) } }
            }
            TPCard {
                Label("Esfuerzo percibido (RPE): ${rpe.toInt()} / 10")
                Slider(rpe, { rpe = it }, valueRange = 1f..10f, steps = 8)
            }
            TPCard {
                Label("Sensación de recuperación")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("MALA", "NORMAL", "BUENA").forEach { r -> FilterChip(rec == r, { rec = r }, { Text(r.lowercase().replaceFirstChar { it.uppercase() }) }) } }
            }
            if (saved) Banner("Registro guardado. El peso se pide una vez al final del día.", TP.Walk)
            Button(onClick = {
                val now = System.currentTimeMillis()
                scope.launch {
                    val dao = db.afterActivityDao()
                    val drinks = buildList {
                        if (water > 0) add(HydrationEntity(activityId = activityId, epochMs = now, drink = "Agua", ml = water))
                        drinkMl.toIntOrNull()?.let { if (drink.isNotBlank()) add(HydrationEntity(activityId = activityId, epochMs = now, drink = drink.trim(), ml = it)) }
                    }
                    if (drinks.isNotEmpty()) dao.insertHydration(drinks)
                    if (food.isNotBlank()) dao.insertFood(listOf(FoodEntity(activityId = activityId, epochMs = now, name = food.trim(), quantity = foodQty.trim())))
                    dao.upsertRecovery(RecoveryEntity(activityId = activityId, fatigue1to5 = fatigue, rpe1to10 = rpe.toInt(), recovery = rec))
                    saved = true
                }
            }, enabled = !saved, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Guardar") }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Pantalla 19 (parcial en beta 1): seguridad. */
@Composable
fun SettingsScreen(pin: PinManager, onBack: () -> Unit, onChangePin: () -> Unit) {
    var bio by remember { mutableStateOf(pin.biometricEnabled) }
    var lock by remember { mutableIntStateOf(pin.autoLockSeconds) }
    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        BackBar("Configuración", onBack)
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("Seguridad")
            TPCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Acceso con huella", Modifier.weight(1f))
                    Switch(bio, { bio = it; pin.biometricEnabled = it })
                }
                Text("Bloqueo automático tras salir de la app", color = TP.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30 to "30 s", 60 to "1 min", 300 to "5 min").forEach { (s, l) -> FilterChip(lock == s, { lock = s; pin.autoLockSeconds = s }, { Text(l) }) }
                }
                FilledTonalButton(onClick = onChangePin, modifier = Modifier.padding(top = 8.dp)) { Text("Cambiar PIN") }
            }
            Label("Versión")
            TPCard { Text("Tu Pedalada ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") }
            Text("Colores, tipografía, unidades, notificaciones, sincronización, mapas y gráficos llegan en la beta 2.", color = TP.Muted, fontSize = 13.sp)
        }
    }
}

/** Pantallas aprobadas en el prototipo que se implementan en la beta 2. Se muestran con su estado real. */
@Composable
fun PendingScreen(title: String, what: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(TP.Bg)) {
        BackBar(title, onBack)
        Column(Modifier.padding(16.dp)) {
            TPCard {
                Label("En desarrollo · beta 2")
                Text(what, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
                Text("El diseño está aprobado en el prototipo v1.", color = TP.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}
