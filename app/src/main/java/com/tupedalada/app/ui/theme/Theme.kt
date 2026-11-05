package com.tupedalada.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.tupedalada.app.data.db.Sport

/** Paleta aprobada en el prototipo v1 (05/11/2026). */
object TP {
    val Bg = Color(0xFF0F1214)
    val Surface = Color(0xFF1A1F23)
    val Surface2 = Color(0xFF232A2F)
    val Border = Color(0xFF262E34)
    val Text = Color(0xFFF2EEE6)
    val Muted = Color(0xFFA4ABB0)
    val Bike = Color(0xFF4EA3F0)
    val Run = Color(0xFFF2994A)
    val Walk = Color(0xFF6CC59A)
    val Warn = Color(0xFFF5C26B)
    val Error = Color(0xFFFF8A80)

    fun sport(s: Sport) = when (s) { Sport.BIKE -> Bike; Sport.RUN -> Run; Sport.WALK -> Walk }
    fun sportName(s: Sport) = when (s) { Sport.BIKE -> "Biking"; Sport.RUN -> "Running"; Sport.WALK -> "Walking" }
}

// Barlow Condensed / Barlow se incorporarán como fuentes descargables en la beta 2;
// mientras tanto se usa la sans-serif condensada del sistema para títulos y cifras.
val Condensed = FontFamily.SansSerif

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

@Composable
fun TuPedaladaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TP.Bike, onPrimary = TP.Bg, secondary = TP.Run, tertiary = TP.Walk,
            background = TP.Bg, onBackground = TP.Text, surface = TP.Surface, onSurface = TP.Text,
            surfaceVariant = TP.Surface2, onSurfaceVariant = TP.Muted, outline = TP.Border, error = TP.Error,
        ),
        typography = typography,
        content = content,
    )
}
