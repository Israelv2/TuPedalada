package com.tupedalada.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.tupedalada.app.data.db.AppDatabase
import com.tupedalada.app.data.db.EquipmentEntity
import com.tupedalada.app.data.db.ImportedFileEntity
import com.tupedalada.app.data.db.RouteEntity
import com.tupedalada.app.data.db.SyncHistoryEntity
import com.tupedalada.app.importer.excel.TablaImporter
import com.tupedalada.app.importer.excel.XlsxReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/**
 * Importación del Excel histórico en dos pasos: [prepare] (copia el original y genera la vista
 * previa sin tocar la base de datos) y [commit] (guarda todo en una transacción).
 */
class ImportRepository(private val context: Context, private val db: AppDatabase) {

    data class Prepared(
        val fileName: String,
        val copy: File,
        val sha256: String,
        val sizeBytes: Long,
        val sheets: List<String>,
        val alreadyImported: Boolean,
        val preview: TablaImporter.Preview?,
        val error: String?,
    )

    suspend fun prepare(uri: Uri, sheet: String = "Tabla"): Prepared = withContext(Dispatchers.IO) {
        val name = displayName(uri) ?: "historico.xlsx"
        val dir = File(context.filesDir, "originales").apply { mkdirs() }
        val tmp = File(dir, "tmp_${System.currentTimeMillis()}.xlsx")
        val md = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)!!.use { input ->
            tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    md.update(buf, 0, n); out.write(buf, 0, n)
                }
            }
        }
        val sha = md.digest().joinToString("") { "%02x".format(it) }
        // Copia íntegra del original, nombrada por su huella: nunca se modifica ni se sobrescribe.
        val copy = File(dir, "$sha.xlsx")
        if (copy.exists()) tmp.delete() else tmp.renameTo(copy)
        val already = db.importDao().countBySha(sha) > 0
        try {
            val reader = XlsxReader(copy)
            val sheets = reader.sheetNames()
            val rows = reader.readSheet(sheet)
            val keys = db.activityDao().allDedupeKeys().toHashSet()
            val preview = TablaImporter(sourceFileName = name).preview(rows, keys, sheet)
            Prepared(name, copy, sha, copy.length(), sheets, already, preview, null)
        } catch (e: Exception) {
            Prepared(name, copy, sha, copy.length(), emptyList(), already, null, e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun commit(p: Prepared): Int = withContext(Dispatchers.IO) {
        val preview = requireNotNull(p.preview)
        db.withTransaction {
            val eqIds = HashMap<String, Long>()
            preview.equipmentNames.forEach { (name, sport) ->
                val existing = db.equipmentDao().byName(name)
                eqIds[name] = existing?.id ?: db.equipmentDao().insert(EquipmentEntity(name = name, sport = sport))
            }
            val routeIds = HashMap<String, Long>()
            preview.valid.map { it.routeName }.filter { it.isNotBlank() }.distinct().forEach { r ->
                routeIds[r] = db.routeDao().byName(r)?.id ?: db.routeDao().insert(RouteEntity(name = r))
            }
            val fileId = db.importDao().insertFile(
                ImportedFileEntity(
                    fileName = p.fileName, format = "XLSX", sha256 = p.sha256, sizeBytes = p.sizeBytes,
                    storedCopyPath = p.copy.absolutePath, importedAt = System.currentTimeMillis(),
                    rowsImported = preview.valid.size,
                    rowsDuplicated = preview.duplicatesInFile.size + preview.duplicatesInDb.size,
                    rowsWithErrors = preview.errors.size,
                )
            )
            db.activityDao().insertAll(preview.valid.map {
                it.copy(
                    importedFileId = fileId,
                    equipmentId = it.equipmentName?.let(eqIds::get),
                    routeId = routeIds[it.routeName],
                )
            })
            db.importDao().insertSync(
                SyncHistoryEntity(
                    epochMs = System.currentTimeMillis(), source = "EXCEL",
                    newActivities = preview.valid.size,
                    duplicates = preview.duplicatesInFile.size + preview.duplicatesInDb.size,
                    errors = preview.errors.size,
                    message = "${p.fileName} › ${preview.sheet}",
                )
            )
        }
        preview.valid.size
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
