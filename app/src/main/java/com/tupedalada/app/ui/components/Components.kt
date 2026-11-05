package com.tupedalada.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tupedalada.app.ui.theme.Condensed
import com.tupedalada.app.ui.theme.TP

@Composable
fun TPCard(modifier: Modifier = Modifier, borderColor: Color = TP.Border, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TP.Surface),
        border = BorderStroke(1.dp, borderColor),
    ) { Column(Modifier.padding(14.dp), content = content) }
}

@Composable
fun Label(text: String, color: Color = TP.Muted) =
    Text(text.uppercase(), color = color, fontSize = 12.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Medium)

@Composable
fun BigNumber(value: String, unit: String? = null, color: Color = TP.Text, size: Int = 26) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = color, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = size.sp)
        if (unit != null) {
            Spacer(Modifier.width(3.dp))
            Text(unit, color = TP.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

@Composable
fun Stat(label: String, value: String, unit: String? = null, modifier: Modifier = Modifier, quality: Char? = null) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = TP.Muted, fontSize = 12.sp)
            if (quality != null) QualityBadge(quality)
        }
        BigNumber(value, unit, size = 21)
    }
}

/** Distintivo M (medido), C (calculado), E (estimado). */
@Composable
fun QualityBadge(q: Char) {
    val (bg, fg) = when (q) { 'M' -> TP.Muted to TP.Bg; 'E' -> Color.Transparent to TP.Warn; else -> Color.Transparent to TP.Muted }
    Box(Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
        Text(q.toString(), color = fg, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(8.dp).background(TP.Border, RoundedCornerShape(4.dp))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).background(color, RoundedCornerShape(4.dp)))
    }
}

@Composable
fun Banner(text: String, color: Color = TP.Error) {
    Box(
        Modifier.fillMaxWidth().background(color.copy(alpha = 0.15f), RoundedCornerShape(14.dp)).padding(12.dp)
    ) { Text(text, color = color, fontSize = 14.sp) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 22.sp) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = TP.Bg, titleContentColor = TP.Text, navigationIconContentColor = TP.Text),
    )
}

@Composable
fun EmptyState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    TPCard {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontFamily = Condensed, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
            Text(body, color = TP.Muted, fontSize = 14.sp)
            action?.invoke()
        }
    }
}
