package com.tupedalada.app.ui

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val ES: Locale = Locale.forLanguageTag("es-PE")
val ZONE: ZoneId = ZoneId.systemDefault()

fun km(v: Double?, decimals: Int = 1): String {
    if (v == null) return "—"
    val f = NumberFormat.getNumberInstance(ES).apply { maximumFractionDigits = decimals; minimumFractionDigits = decimals }
    return f.format(v)
}

fun int(v: Number?): String = v?.let { NumberFormat.getIntegerInstance(ES).format(it) } ?: "—"

fun hours(sec: Long?): String {
    if (sec == null || sec <= 0) return "0:00"
    return "%d:%02d".format(sec / 3600, (sec % 3600) / 60)
}

fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZONE).toLocalDate()

private val DAY = DateTimeFormatter.ofPattern("EEE dd/MM/yyyy", ES)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", ES)

fun dayLabel(ms: Long): String = DAY.format(Instant.ofEpochMilli(ms).atZone(ZONE)).replaceFirstChar { it.uppercase() }
fun timeLabel(ms: Long, known: Boolean): String = if (known) TIME.format(Instant.ofEpochMilli(ms).atZone(ZONE)) else "hora desconocida"

fun startOfDayMs(d: LocalDate): Long = d.atStartOfDay(ZONE).toInstant().toEpochMilli()
