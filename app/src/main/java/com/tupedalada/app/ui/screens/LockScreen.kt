package com.tupedalada.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.tupedalada.app.R
import com.tupedalada.app.security.BiometricHelper
import com.tupedalada.app.security.PinManager
import com.tupedalada.app.ui.theme.Condensed
import com.tupedalada.app.ui.theme.TP

private enum class Mode { SETUP, CONFIRM, SHOW_CODE, UNLOCK, RECOVER, NEW_PIN, NEW_PIN_CONFIRM }

@Composable
fun LockScreen(pin: PinManager, changePin: Boolean = false, onUnlocked: () -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    var mode by remember { mutableStateOf(if (pin.hasPin) Mode.UNLOCK else Mode.SETUP) }
    var entry by remember { mutableStateOf("") }
    var first by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var recoveryCode by remember { mutableStateOf("") }
    var codeInput by remember { mutableStateOf("") }
    val bioAvailable = remember { BiometricHelper.isAvailable(activity) }

    fun bio() = BiometricHelper.authenticate(activity, "Entrar en Tu Pedalada",
        onSuccess = { pin.onBiometricSuccess(); onUnlocked() }, onError = { message = it })

    LaunchedEffect(Unit) { if (!changePin && mode == Mode.UNLOCK && bioAvailable && pin.biometricEnabled) bio() }

    fun onComplete(value: String) {
        when (mode) {
            Mode.SETUP -> { first = value; mode = Mode.CONFIRM; message = null }
            Mode.CONFIRM -> if (value == first) { recoveryCode = pin.setPin(value); mode = Mode.SHOW_CODE } else { mode = Mode.SETUP; message = "Los PIN no coinciden. Vuelve a empezar." }
            Mode.NEW_PIN -> { first = value; mode = Mode.NEW_PIN_CONFIRM; message = null }
            Mode.NEW_PIN_CONFIRM -> if (value == first) { pin.changePin(value); onUnlocked() } else { mode = Mode.NEW_PIN; message = "Los PIN no coinciden." }
            Mode.UNLOCK -> when (val r = pin.verify(value)) {
                PinManager.Result.Ok -> if (changePin) { mode = Mode.NEW_PIN; message = null } else onUnlocked()
                is PinManager.Result.Wrong -> message = "PIN incorrecto. Te quedan ${r.attemptsLeft} intentos."
                is PinManager.Result.LockedOut -> message = "Demasiados intentos. Espera ${r.secondsLeft} s o usa la huella."
            }
            else -> Unit
        }
        entry = ""
    }

    Column(
        Modifier.fillMaxSize().background(TP.Bg).padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).background(TP.Text, CircleShape), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = "Logotipo Tu Pedalada", modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("TU PEDALADA", fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = 1.sp)
        val subtitle = when (mode) {
            Mode.SETUP -> "Crea un PIN de 6 cifras"
            Mode.CONFIRM -> "Repite el PIN"
            Mode.UNLOCK -> "Introduce tu PIN de 6 cifras"
            Mode.NEW_PIN -> "Nuevo PIN de 6 cifras"
            Mode.NEW_PIN_CONFIRM -> "Repite el nuevo PIN"
            Mode.SHOW_CODE -> "Código de recuperación"
            Mode.RECOVER -> "Recuperar acceso"
        }
        Text(subtitle, color = TP.Muted, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))

        when (mode) {
            Mode.SHOW_CODE -> {
                Spacer(Modifier.height(28.dp))
                Text(recoveryCode, fontFamily = Condensed, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = TP.Warn)
                Spacer(Modifier.height(16.dp))
                Text("Anótalo en papel y guárdalo. Es la única forma de recuperar el acceso si olvidas el PIN y no puedes usar la huella. No se volverá a mostrar.", color = TP.Muted, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Button(onClick = onUnlocked, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Lo he anotado, continuar") }
            }
            Mode.RECOVER -> {
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(codeInput, { codeInput = it }, label = { Text("Código (XXXX-XXXX-XXXX)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                message?.let { Text(it, color = TP.Error, modifier = Modifier.padding(top = 8.dp)) }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    if (pin.verifyRecoveryCode(codeInput)) { mode = Mode.NEW_PIN; message = null } else message = "Código no válido."
                }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Verificar") }
                if (bioAvailable) TextButton(onClick = {
                    BiometricHelper.authenticate(activity, "Verifica tu identidad", onSuccess = { mode = Mode.NEW_PIN; message = null }, onError = { message = it })
                }) { Text("Verificar con huella") }
                TextButton(onClick = { mode = Mode.UNLOCK; message = null }) { Text("Volver") }
            }
            else -> {
                Row(Modifier.padding(top = 28.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    repeat(6) { i ->
                        Box(Modifier.size(14.dp).background(if (i < entry.length) TP.Text else TP.Border, CircleShape))
                    }
                }
                Box(Modifier.height(48.dp), contentAlignment = Alignment.Center) {
                    message?.let { Text(it, color = TP.Error, fontSize = 14.sp) }
                }
                val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
                keys.chunked(3).forEach { rowKeys ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        rowKeys.forEach { k ->
                            when (k) {
                                "bio" -> IconButton(
                                    onClick = { if (mode == Mode.UNLOCK && bioAvailable) bio() },
                                    enabled = mode == Mode.UNLOCK && bioAvailable,
                                    modifier = Modifier.size(64.dp),
                                ) { if (mode == Mode.UNLOCK && bioAvailable) Icon(Icons.Default.Fingerprint, "Entrar con huella", tint = TP.Bike, modifier = Modifier.size(30.dp)) }
                                "del" -> IconButton(onClick = { entry = entry.dropLast(1) }, modifier = Modifier.size(64.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, "Borrar", tint = TP.Text)
                                }
                                else -> FilledTonalButton(
                                    onClick = {
                                        if (entry.length < 6) { entry += k; if (entry.length == 6) onComplete(entry) }
                                    },
                                    modifier = Modifier.size(64.dp).semantics { contentDescription = "Cifra $k" },
                                    shape = CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = TP.Surface, contentColor = TP.Text),
                                ) { Text(k, fontSize = 26.sp, fontFamily = Condensed) }
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (mode == Mode.UNLOCK) TextButton(onClick = { mode = Mode.RECOVER; message = null }) { Text("¿Olvidaste el PIN?") }
            }
        }
    }
}
