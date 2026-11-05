package com.tupedalada.app.importer.excel

import java.io.File
import java.nio.charset.Charset

/**
 * Lector de CSV (exportaciones de Garmin Connect y similares) con el mismo formato de salida que
 * [XlsxReader.readSheet], para reutilizar el importador.
 *
 * - Detecta el separador (coma, punto y coma o tabulador) mirando la línea de encabezados.
 * - Respeta comillas, comillas dobles escapadas y saltos de línea dentro de comillas.
 * - Acepta UTF-8 (con o sin BOM) y, si no lo es, Windows-1252.
 */
object CsvReader {

    fun read(file: File): List<Map<Int, XlsxReader.Cell>> = parse(decode(file.readBytes()))

    fun decode(bytes: ByteArray): String {
        val utf8 = Charsets.UTF_8.newDecoder()
        val text = try { utf8.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
        catch (_: java.nio.charset.CharacterCodingException) { String(bytes, Charset.forName("windows-1252")) }
        return text.removePrefix("﻿")
    }

    fun parse(text: String): List<Map<Int, XlsxReader.Cell>> {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return emptyList()
        val sep = detectSeparator(firstLine)
        val rows = ArrayList<Map<Int, XlsxReader.Cell>>()
        var row = HashMap<Int, XlsxReader.Cell>()
        val field = StringBuilder()
        var col = 0
        var inQuotes = false
        var i = 0

        fun endField() {
            val v = field.toString().trim()
            if (v.isNotEmpty()) row[col] = XlsxReader.Cell(v, false)
            field.setLength(0); col++
        }
        fun endRow() {
            endField()
            if (row.isNotEmpty()) rows += row
            row = HashMap(); col = 0
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else inQuotes = false
                } else field.append(c)
            } else when (c) {
                '"' -> inQuotes = true
                sep -> endField()
                '\r' -> Unit
                '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty() || col > 0) endRow()
        return rows
    }

    fun detectSeparator(header: String): Char {
        var best = ','
        var bestCount = -1
        for (s in charArrayOf(',', ';', '\t')) {
            var n = 0; var q = false
            for (c in header) { if (c == '"') q = !q else if (c == s && !q) n++ }
            if (n > bestCount) { best = s; bestCount = n }
        }
        return best
    }
}
