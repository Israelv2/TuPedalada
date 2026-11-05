package com.tupedalada.app

import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tupedalada.app.data.db.Sport
import com.tupedalada.app.ui.screens.AfterActivityScreen
import com.tupedalada.app.ui.screens.DashboardScreen
import com.tupedalada.app.ui.screens.DetailScreen
import com.tupedalada.app.ui.screens.ExcelWizardScreen
import com.tupedalada.app.ui.screens.HistoryScreen
import com.tupedalada.app.ui.screens.ImportScreen
import com.tupedalada.app.ui.screens.LockScreen
import com.tupedalada.app.ui.screens.PendingScreen
import com.tupedalada.app.ui.screens.ProfileScreen
import com.tupedalada.app.ui.screens.SettingsScreen
import com.tupedalada.app.ui.screens.SplashVideoScreen
import com.tupedalada.app.ui.screens.SportScreen
import com.tupedalada.app.ui.screens.WeightScreen
import com.tupedalada.app.ui.theme.TP
import com.tupedalada.app.ui.theme.TuPedaladaTheme
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var backgroundSince = 0L
    private var locked by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Un giro de pantalla no vuelve a pedir el PIN; salir de la app sí (bloqueo automático).
        if (savedInstanceState?.getBoolean(KEY_UNLOCKED) == true) locked = false
        enableEdgeToEdge()
        val app = application as TuPedaladaApp
        setContent {
            TuPedaladaTheme {
                Box(Modifier.fillMaxSize().background(TP.Bg).safeDrawingPadding()) {
                    if (locked) LockScreen(app.pin) { locked = false; justUnlocked = true }
                    else AppNav(app, playSplash = justUnlocked.also { justUnlocked = false })
                }
            }
        }
    }

    private var justUnlocked = false

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, !locked)
    }

    companion object { private const val KEY_UNLOCKED = "unlocked" }

    override fun onStop() {
        super.onStop()
        backgroundSince = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val app = application as TuPedaladaApp
        if (backgroundSince > 0 && SystemClock.elapsedRealtime() - backgroundSince > app.pin.autoLockSeconds * 1000L) locked = true
    }
}

private object R0 {
    const val SPLASH = "splash"; const val HOME = "home"; const val HISTORY = "history?sport={sport}"; const val DETAIL = "detail/{id}"
    const val SPORT = "sport/{sport}"; const val IMPORT = "import"; const val EXCEL = "excel"; const val PROFILE = "profile"
    const val WEIGHT = "weight"; const val AFTER = "after/{id}"; const val SETTINGS = "settings"; const val CHANGE_PIN = "changepin"
    const val PENDING = "pending/{key}"
}

private val pending = mapOf(
    "maps" to ("Mapas y rutas" to "Recorridos GPS con MapLibre/OpenStreetMap, coloreado por velocidad, ritmo, FC, altitud o pendiente, perfil de elevación y reproducción animada. Requiere actividades importadas desde FIT/GPX/TCX."),
    "evolution" to ("Evolución deportiva" to "Gráficos de peso, IMC, distancia, tiempo, velocidad, ritmo, FC, desnivel y acumulado, e informe anual."),
    "equipment" to ("Equipamiento" to "Inventario con foto, marca, modelo, fecha de compra, km y estado. Los equipos del Excel ya se crean al importar."),
    "updates" to ("Actualizaciones" to "Selección de un APK firmado, verificación de firma, integridad y versión, y copia de seguridad previa."),
    "backup" to ("Copias de seguridad" to "Exportación a Excel/CSV/GPX y copias cifradas con restauración."),
)

@Composable
private fun AppNav(app: TuPedaladaApp, playSplash: Boolean) {
    val nav = rememberNavController()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var pickedExcel by remember { mutableStateOf<Uri?>(null) }
    val current by nav.currentBackStackEntryAsState()

    fun go(route: String) { scope.launch { drawer.close() }; nav.navigate(route) { launchSingleTop = true } }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = current?.destination?.route != R0.SPLASH,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = TP.Surface) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
                    Item("Inicio", Icons.Default.Home) { scope.launch { drawer.close() }; nav.popBackStack(R0.HOME, false) }
                    Item("Running", Icons.AutoMirrored.Filled.DirectionsRun) { go("sport/RUN") }
                    Item("Walking", Icons.AutoMirrored.Filled.DirectionsWalk) { go("sport/WALK") }
                    Item("Biking", Icons.AutoMirrored.Filled.DirectionsBike) { go("sport/BIKE") }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = TP.Border)
                    Item("Historial", Icons.Default.CalendarMonth) { go("history") }
                    Item("Evolución deportiva", Icons.AutoMirrored.Filled.ShowChart) { go("pending/evolution") }
                    Item("Mapas y rutas", Icons.Default.Map) { go("pending/maps") }
                    Item("Equipamiento", Icons.Default.Inventory2) { go("pending/equipment") }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = TP.Border)
                    Item("Importar y sincronizar", Icons.Default.Upload) { go(R0.IMPORT) }
                    Item("Peso de hoy", Icons.Default.MonitorWeight) { go(R0.WEIGHT) }
                    Item("Perfil", Icons.Default.Person) { go(R0.PROFILE) }
                    Item("Configuración", Icons.Default.Settings) { go(R0.SETTINGS) }
                    Item("Actualizaciones", Icons.Default.SystemUpdate) { go("pending/updates") }
                    Item("Copias de seguridad", Icons.Default.Backup) { go("pending/backup") }
                    Text("Versión ${BuildConfig.VERSION_NAME}", color = TP.Muted, modifier = Modifier.padding(20.dp))
                }
            }
        },
    ) {
        NavHost(nav, startDestination = if (playSplash) R0.SPLASH else R0.HOME) {
            composable(R0.SPLASH) { SplashVideoScreen { nav.navigate(R0.HOME) { popUpTo(R0.SPLASH) { inclusive = true } } } }
            composable(R0.HOME) {
                DashboardScreen(app.db,
                    onMenu = { scope.launch { drawer.open() } },
                    onImport = { nav.navigate(R0.IMPORT) },
                    onSport = { nav.navigate("sport/${it.name}") },
                    onActivity = { nav.navigate("detail/$it") },
                    onHistory = { nav.navigate("history") },
                    onWeight = { nav.navigate(R0.WEIGHT) })
            }
            composable(R0.HISTORY, arguments = listOf(navArgument("sport") { type = NavType.StringType; nullable = true; defaultValue = null })) { e ->
                HistoryScreen(app.db, e.arguments?.getString("sport")?.let(Sport::valueOf), onBack = nav.back(), onActivity = { nav.navigate("detail/$it") })
            }
            composable(R0.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                DetailScreen(app.db, e.arguments!!.getLong("id"), onBack = nav.back(), onAfterActivity = { nav.navigate("after/$it") })
            }
            composable(R0.SPORT, arguments = listOf(navArgument("sport") { type = NavType.StringType })) { e ->
                val s = Sport.valueOf(e.arguments!!.getString("sport")!!)
                SportScreen(app.db, s, onBack = nav.back(), onActivity = { nav.navigate("detail/$it") }, onHistory = { nav.navigate("history?sport=${s.name}") })
            }
            composable(R0.IMPORT) { ImportScreen(app.db, onBack = nav.back(), onExcelPicked = { pickedExcel = it; nav.navigate(R0.EXCEL) }) }
            composable(R0.EXCEL) {
                val uri = pickedExcel
                if (uri == null) androidx.compose.runtime.LaunchedEffect(Unit) { nav.popBackStack() } else ExcelWizardScreen(app.imports, uri, onBack = nav.back(), onDone = {
                    nav.navigate("history") { popUpTo(R0.HOME) }
                })
            }
            composable(R0.PROFILE) { ProfileScreen(app.db, onBack = nav.back()) }
            composable(R0.WEIGHT) { WeightScreen(app.db, onBack = nav.back()) }
            composable(R0.AFTER, arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                AfterActivityScreen(app.db, e.arguments!!.getLong("id"), onBack = nav.back())
            }
            composable(R0.SETTINGS) { SettingsScreen(app.pin, onBack = nav.back(), onChangePin = { nav.navigate(R0.CHANGE_PIN) }) }
            composable(R0.CHANGE_PIN) { LockScreen(app.pin, changePin = true) { nav.popBackStack() } }
            composable(R0.PENDING, arguments = listOf(navArgument("key") { type = NavType.StringType })) { e ->
                val (title, what) = pending[e.arguments!!.getString("key")] ?: ("Pendiente" to "")
                PendingScreen(title, what, onBack = nav.back())
            }
        }
    }
}

private fun NavHostController.back(): () -> Unit = { popBackStack() }

@Composable
private fun Item(label: String, icon: ImageVector, onClick: () -> Unit) =
    NavigationDrawerItem(label = { Text(label) }, icon = { Icon(icon, null) }, selected = false, onClick = onClick, modifier = Modifier.padding(horizontal = 12.dp))
