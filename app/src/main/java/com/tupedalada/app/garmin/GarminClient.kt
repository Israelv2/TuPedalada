package com.tupedalada.app.garmin

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cliente de la API personal de Garmin Connect (vía oficial, sin contraseña de Garmin).
 *
 * 1. La clave personal se guarda cifrada con AES-GCM y una clave de Android Keystore.
 * 2. Se cambia por un token de acceso (válido 24 h): POST …/personal-api-key/token, cabecera "ApiKey <clave>".
 * 3. Las actividades se piden con GET /activitylist-service/activities/search/activities, cabecera "Bearer <token>".
 */
class GarminClient(context: Context) {
    private val prefs = context.getSharedPreferences("garmin", Context.MODE_PRIVATE)
    @Volatile private var token: String? = null
    @Volatile private var tokenExpiresAt = 0L

    class GarminException(val userMessage: String, val httpCode: Int? = null) : IOException(userMessage)

    val hasKey: Boolean get() = prefs.contains(K_KEY)

    fun saveKey(apiKey: String) {
        val clean = apiKey.trim()
        require(clean.length >= 10) { "La clave parece incompleta" }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, aesKey()) }
        val enc = c.doFinal(clean.toByteArray())
        prefs.edit().putString(K_KEY, b64(c.iv) + ":" + b64(enc)).apply()
        token = null; tokenExpiresAt = 0
    }

    fun deleteKey() { prefs.edit().remove(K_KEY).apply(); token = null; tokenExpiresAt = 0 }

    private fun readKey(): String {
        val s = prefs.getString(K_KEY, null) ?: throw GarminException("No hay clave de Garmin guardada.")
        val (iv, data) = s.split(":").let { unb64(it[0]) to unb64(it[1]) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, aesKey(), GCMParameterSpec(128, iv)) }
        return String(c.doFinal(data))
    }

    private fun accessToken(): String {
        val now = System.currentTimeMillis()
        token?.let { if (now < tokenExpiresAt - 10 * 60_000) return it }
        val conn = (URL(BASE + TOKEN_PATH).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 20_000; readTimeout = 30_000; doOutput = true
            setRequestProperty("Authorization", "ApiKey ${readKey()}")
            setRequestProperty("Accept", "*/*")
            setFixedLengthStreamingMode(0)
        }
        try {
            conn.outputStream.close()
            val code = conn.responseCode
            if (code == 401 || code == 403) throw GarminException("Garmin rechazó la clave: puede haber caducado o haberse borrado. Crea una nueva en Garmin Connect.", code)
            if (code !in 200..299) throw GarminException("Garmin respondió con error $code al pedir el acceso.", code)
            val t = conn.inputStream.bufferedReader().readText().trim().removeSurrounding("\"")
            token = t
            tokenExpiresAt = jwtExpiryMs(t) ?: (now + 23 * 3_600_000L)
            return t
        } finally { conn.disconnect() }
    }

    /** Una página de actividades entre dos fechas (incluidas). */
    fun searchActivities(from: LocalDate, to: LocalDate, start: Int, limit: Int): JSONArray {
        val q = listOf("start" to "$start", "limit" to "$limit", "startDate" to from.toString(), "endDate" to to.toString())
            .joinToString("&") { "${it.first}=${URLEncoder.encode(it.second, "UTF-8")}" }
        val body = get("/activitylist-service/activities/search/activities?$q")
        val trimmed = body.trim()
        return when {
            trimmed.isEmpty() -> JSONArray()
            trimmed.startsWith("[") -> JSONArray(trimmed)
            else -> JSONObject(trimmed).optJSONArray("activities") ?: JSONArray()
        }
    }

    private fun get(path: String): String {
        repeat(2) { attempt ->
            val conn = (URL(BASE + path).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000; readTimeout = 60_000
                setRequestProperty("Authorization", "Bearer ${accessToken()}")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = conn.responseCode
                if (code == 401 && attempt == 0) { token = null; tokenExpiresAt = 0; return@repeat }
                if (code == 429) throw GarminException("Garmin limita las consultas por ahora. Inténtalo más tarde.", code)
                if (code !in 200..299) throw GarminException("Garmin respondió con error $code.", code)
                return conn.inputStream.bufferedReader().readText()
            } finally { conn.disconnect() }
        }
        throw GarminException("No se pudo autenticar con Garmin.")
    }

    private fun aesKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        const val BASE = "https://personal.connectapi.garmin.com"
        const val TOKEN_PATH = "/personalapitoken-service/personal-api/personal-api-key/token"
        private const val ALIAS = "tupedalada_garmin_key"
        private const val K_KEY = "api_key_enc"

        /** Lee "exp" del token JWT (segundos) y lo devuelve en milisegundos. */
        fun jwtExpiryMs(jwt: String): Long? = runCatching {
            val payload = jwt.split(".")[1]
            val json = String(java.util.Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '=')))
            Regex("\"exp\"\\s*:\\s*(\\d+)").find(json)!!.groupValues[1].toLong() * 1000
        }.getOrNull()
    }
}
