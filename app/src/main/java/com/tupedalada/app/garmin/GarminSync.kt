package com.tupedalada.app.garmin

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.tupedalada.app.data.db.AppDatabase
import com.tupedalada.app.data.db.SettingEntity
import com.tupedalada.app.data.db.SyncHistoryEntity
import com.tupedalada.app.importer.excel.TablaImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Sincronización con Garmin Connect al abrir la app (requisito 7 del prompt: la app se activa al
 * abrirla y comprueba si hay actividades pendientes; no hay servicio en segundo plano ni GPS).
 */
class GarminSync(private val context: Context, private val db: AppDatabase, val client: GarminClient) {

    sealed interface State {
        data object Idle : State
        data object Running : State
        data class Done(val newOnes: Int, val duplicates: Int, val skippedTypes: Int, val at: Long) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val mutex = Mutex()

    suspend fun lastSyncMs(): Long? = db.miscDao().setting(K_LAST)?.toLongOrNull()

    /** Sincroniza si hay clave e internet. [force] ignora la espera mínima entre sincronizaciones. */
    suspend fun syncIfPossible(force: Boolean = false) {
        if (!client.hasKey) return
        if (!online()) { if (force) _state.value = State.Failed("Sin conexión a internet."); return }
        val last = lastSyncMs()
        if (!force && last != null && System.currentTimeMillis() - last < 10 * 60_000) return
        sync()
    }

    private suspend fun sync() = mutex.withLock {
        _state.value = State.Running
        val zone = ZoneId.systemDefault()
        try {
            val result = withContext(Dispatchers.IO) {
                val dao = db.activityDao()
                val today = LocalDate.now(zone)
                val lastSync = lastSyncMs()?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                val lastActivity = dao.lastStartMs()?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
                // Desde la última sincronización (con margen de 3 días); la primera vez, desde la última
                // actividad guardada o, si la app está vacía, los últimos 90 días.
                val from = (lastSync ?: lastActivity ?: today.minusDays(90)).minusDays(3)

                val keys = dao.allDedupeKeys().toHashSet()
                val loose = dao.allLooseRefs().mapNotNullTo(HashSet()) {
                    TablaImporter.looseKey(it.sport, it.startEpochMs, it.startTimeKnown, it.distanceKm, zone)
                }
                var newOnes = 0; var dups = 0; var skipped = 0
                var start = 0
                val page = 100
                while (start < 5_000) {
                    val arr = client.searchActivities(from, today, start, page)
                    for (i in 0 until arr.length()) {
                        val a = GarminMapper.map(arr.getJSONObject(i), zone)
                        if (a == null) { skipped++; continue }
                        val lk = TablaImporter.looseKey(a.sport, a.startEpochMs, true, a.distanceKm, zone)
                        if (a.dedupeKey in keys || (lk != null && lk in loose)) { dups++; continue }
                        val eqName = dao.lastEquipmentFor(a.sport)
                        val eqId = eqName?.let { db.equipmentDao().byName(it)?.id }
                        dao.insert(a.copy(equipmentName = eqName, equipmentId = eqId))
                        keys += a.dedupeKey; lk?.let(loose::add)
                        newOnes++
                    }
                    if (arr.length() < page) break
                    start += page
                }
                val now = System.currentTimeMillis()
                db.miscDao().upsertSetting(SettingEntity(K_LAST, now.toString()))
                db.importDao().insertSync(SyncHistoryEntity(epochMs = now, source = "GARMIN_PERSONAL_API",
                    newActivities = newOnes, duplicates = dups, errors = 0,
                    message = "Desde $from · $skipped de otras modalidades"))
                State.Done(newOnes, dups, skipped, now)
            }
            _state.value = result
        } catch (e: GarminClient.GarminException) {
            _state.value = State.Failed(e.userMessage)
            logFailure(e.userMessage)
        } catch (e: java.io.IOException) {
            _state.value = State.Failed("No se pudo conectar con Garmin (${e.javaClass.simpleName}).")
            logFailure(e.message ?: "IOException")
        } catch (e: Exception) {
            _state.value = State.Failed("Error al leer los datos de Garmin: ${e.message}")
            logFailure(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun logFailure(msg: String) {
        runCatching {
            db.importDao().insertSync(SyncHistoryEntity(epochMs = System.currentTimeMillis(), source = "GARMIN_PERSONAL_API",
                newActivities = 0, duplicates = 0, errors = 1, message = msg))
        }
    }

    private fun online(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object { private const val K_LAST = "garmin_last_sync_ms" }
}
