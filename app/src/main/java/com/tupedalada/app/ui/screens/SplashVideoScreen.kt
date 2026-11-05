package com.tupedalada.app.ui.screens

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.tupedalada.app.R

/** Pantalla 01: vídeo «tu pedalada» (8 s) que se reproduce tras introducir el PIN o la huella. */
@Composable
fun SplashVideoScreen(onFinished: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color(0xFFE5DCCA), 0.5f to Color(0xFF70563F))),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f),
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse("android.resource://${ctx.packageName}/${R.raw.splash}"))
                    setOnPreparedListener { mp -> mp.setVolume(0f, 0f); start() }
                    setOnCompletionListener { onFinished() }
                    setOnErrorListener { _, _, _ -> onFinished(); true }
                }
            },
        )
        TextButton(onClick = onFinished, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Text("Saltar", color = Color(0xFF3A2C20))
        }
    }
}
