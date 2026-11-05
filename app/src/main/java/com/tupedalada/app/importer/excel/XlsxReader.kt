package com.tupedalada.app.importer.excel

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import javax.xml.parsers.SAXParserFactory

/**
 * Lector de .xlsx en streaming (SAX), sin dependencias externas.
 *
 * Se usa en lugar de Apache POI porque POI necesita APIs de Java (StAX, AWT) que Android no
 * incluye y añade más de 10 MB a la APK; el Excel histórico (18 MB) se lee aquí con poca memoria.
 * El archivo original nunca se modifica.
 */
class XlsxReader(private val file: File) {

    /** Valor bruto de una celda: texto tal cual o número en su representación XML. */
    data class Cell(val raw: String, val isNumber: Boolean)

    fun sheetNames(): List<String> = ZipFile(file).use { zip -> readWorkbook(zip).map { it.first } }

    /** Lee una hoja completa como filas de celdas indexadas por columna (0 = A). */
    fun readSheet(sheetName: String, maxRows: Int = Int.MAX_VALUE): List<Map<Int, Cell>> = ZipFile(file).use { zip ->
        val sheets = readWorkbook(zip)
        val target = sheets.firstOrNull { it.first.equals(sheetName, ignoreCase = true) }
            ?: throw IllegalArgumentException("La hoja '$sheetName' no existe. Hojas: ${sheets.joinToString { it.first }}")
        val shared = zip.getEntry("xl/sharedStrings.xml")?.let { e -> zip.getInputStream(e).use { readSharedStrings(it) } } ?: emptyList()
        val entry = zip.getEntry(target.second) ?: throw IllegalStateException("No se encontró ${target.second}")
        zip.getInputStream(entry).use { readRows(it, shared, maxRows) }
    }

    private fun parser() = SAXParserFactory.newInstance().apply { isNamespaceAware = false }.newSAXParser()

    /** Devuelve (nombre de hoja, ruta del XML dentro del zip). */
    private fun readWorkbook(zip: ZipFile): List<Pair<String, String>> {
        val rels = HashMap<String, String>()
        zip.getEntry("xl/_rels/workbook.xml.rels")?.let { e ->
            zip.getInputStream(e).use { input ->
                parser().parse(input, object : DefaultHandler() {
                    override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                        if (qName.endsWith("Relationship")) {
                            val target = a.getValue("Target") ?: return
                            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
                            rels[a.getValue("Id")] = path
                        }
                    }
                })
            }
        }
        val out = ArrayList<Pair<String, String>>()
        zip.getInputStream(zip.getEntry("xl/workbook.xml")).use { input ->
            parser().parse(input, object : DefaultHandler() {
                override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                    if (qName == "sheet" || qName.endsWith(":sheet")) {
                        val name = a.getValue("name") ?: return
                        val rid = a.getValue("r:id") ?: (0 until a.length).firstOrNull { a.getQName(it).endsWith(":id") }?.let { a.getValue(it) }
                        val path = rels[rid] ?: "xl/worksheets/sheet${out.size + 1}.xml"
                        out += name to path
                    }
                }
            })
        }
        return out
    }

    private fun readSharedStrings(input: InputStream): List<String> {
        val list = ArrayList<String>(4096)
        parser().parse(input, object : DefaultHandler() {
            private val sb = StringBuilder()
            private var inSi = false
            private var inT = false
            private var inRph = false
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (qName) {
                    "si" -> { inSi = true; sb.setLength(0) }
                    "t" -> inT = true
                    "rPh" -> inRph = true // texto fonético: se ignora
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName) {
                    "si" -> { list += sb.toString(); inSi = false }
                    "t" -> inT = false
                    "rPh" -> inRph = false
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inSi && inT && !inRph) sb.append(ch, start, length)
            }
        })
        return list
    }

    private fun readRows(input: InputStream, shared: List<String>, maxRows: Int): List<Map<Int, Cell>> {
        val rows = ArrayList<Map<Int, Cell>>()
        try {
            parser().parse(input, object : DefaultHandler() {
                private var row: HashMap<Int, Cell>? = null
                private var col = 0
                private var type: String? = null
                private val value = StringBuilder()
                private var inV = false
                private var inIs = false
                private var nextCol = 0

                override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                    when (qName) {
                        "row" -> { row = HashMap(); nextCol = 0 }
                        "c" -> {
                            val ref = a.getValue("r")
                            col = if (ref != null) columnIndex(ref) else nextCol
                            nextCol = col + 1
                            type = a.getValue("t")
                            value.setLength(0)
                        }
                        "v" -> inV = true
                        "is" -> inIs = true
                    }
                }

                override fun characters(ch: CharArray, start: Int, length: Int) {
                    if (inV || inIs) value.append(ch, start, length)
                }

                override fun endElement(uri: String?, localName: String?, qName: String) {
                    when (qName) {
                        "v" -> inV = false
                        "is" -> inIs = false
                        "c" -> {
                            val text = value.toString()
                            val cell = when (type) {
                                "s" -> Cell(shared.getOrElse(text.trim().toIntOrNull() ?: -1) { "" }, false)
                                "inlineStr", "str" -> Cell(text, false)
                                "b" -> Cell(if (text == "1") "TRUE" else "FALSE", false)
                                "e" -> Cell("", false)
                                else -> Cell(text, text.isNotEmpty())
                            }
                            if (cell.raw.isNotEmpty()) row?.put(col, cell)
                        }
                        "row" -> {
                            row?.let { rows += it }
                            row = null
                            if (rows.size >= maxRows) throw StopParsing()
                        }
                    }
                }
            })
        } catch (_: StopParsing) { }
        return rows
    }

    private class StopParsing : RuntimeException()

    companion object {
        /** "AB12" → 27 (base 0). */
        fun columnIndex(ref: String): Int {
            var n = 0
            for (c in ref) {
                if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1) else if (c in 'a'..'z') n = n * 26 + (c - 'a' + 1) else break
            }
            return n - 1
        }
    }
}
