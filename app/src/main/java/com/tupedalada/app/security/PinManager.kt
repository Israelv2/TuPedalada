package com.tupedalada.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Gestión del PIN de 6 cifras y del código de recuperación.
 *
 * Nada se guarda en texto plano: se almacena HMAC-SHA256(clave de Android Keystore,
 * PBKDF2(PIN, sal, 120 000 iteraciones)). La clave HMAC no sale nunca del Keystore, así que
 * copiar el almacenamiento de la app a otro teléfono no permite probar PINs.
 */
class PinManager(context: Context) {
    private val prefs = context.getSharedPreferences("security", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    val hasPin: Boolean get() = prefs.contains(K_PIN_HASH)

    sealed interface Result {
        data object Ok : Result
        data class Wrong(val attemptsLeft: Int) : Result
        data class LockedOut(val secondsLeft: Long) : Result
    }

    /** Configura el PIN y devuelve el código de recuperación (se muestra una sola vez). */
    fun setPin(pin: String): String {
        require(pin.length == 6 && pin.all(Char::isDigit)) { "El PIN debe tener 6 cifras" }
        val salt = ByteArray(16).also(random::nextBytes)
        val code = newRecoveryCode()
        val codeSalt = ByteArray(16).also(random::nextBytes)
        prefs.edit()
            .putString(K_PIN_SALT, b64(salt))
            .putString(K_PIN_HASH, b64(hash(pin, salt)))
            .putString(K_REC_SALT, b64(codeSalt))
            .putString(K_REC_HASH, b64(hash(normalizeCode(code), codeSalt)))
            .putInt(K_FAILS, 0).putLong(K_LOCK_UNTIL, 0)
            .apply()
        return code
    }

    /** Cambia el PIN conservando el código de recuperación existente. */
    fun changePin(newPin: String) {
        require(newPin.length == 6 && newPin.all(Char::isDigit))
        val salt = ByteArray(16).also(random::nextBytes)
        prefs.edit().putString(K_PIN_SALT, b64(salt)).putString(K_PIN_HASH, b64(hash(newPin, salt)))
            .putInt(K_FAILS, 0).putLong(K_LOCK_UNTIL, 0).apply()
    }

    fun verify(pin: String, now: Long = System.currentTimeMillis()): Result {
        val lockUntil = prefs.getLong(K_LOCK_UNTIL, 0)
        if (now < lockUntil) return Result.LockedOut((lockUntil - now + 999) / 1000)
        val salt = unb64(prefs.getString(K_PIN_SALT, null) ?: return Result.Wrong(0))
        val expected = unb64(prefs.getString(K_PIN_HASH, null) ?: return Result.Wrong(0))
        if (constantTimeEquals(hash(pin, salt), expected)) {
            prefs.edit().putInt(K_FAILS, 0).putLong(K_LOCK_UNTIL, 0).apply()
            return Result.Ok
        }
        val fails = prefs.getInt(K_FAILS, 0) + 1
        return if (fails >= MAX_ATTEMPTS) {
            // Bloqueo progresivo: 30 s, 60 s, 120 s... hasta 15 min.
            val rounds = prefs.getInt(K_LOCK_ROUNDS, 0) + 1
            val seconds = (30L shl (rounds - 1)).coerceAtMost(900)
            prefs.edit().putInt(K_FAILS, 0).putInt(K_LOCK_ROUNDS, rounds).putLong(K_LOCK_UNTIL, now + seconds * 1000).apply()
            Result.LockedOut(seconds)
        } else {
            prefs.edit().putInt(K_FAILS, fails).apply()
            Result.Wrong(MAX_ATTEMPTS - fails)
        }
    }

    fun verifyRecoveryCode(code: String): Boolean {
        val salt = unb64(prefs.getString(K_REC_SALT, null) ?: return false)
        val expected = unb64(prefs.getString(K_REC_HASH, null) ?: return false)
        return constantTimeEquals(hash(normalizeCode(code), salt), expected)
    }

    fun onBiometricSuccess() { prefs.edit().putInt(K_FAILS, 0).putLong(K_LOCK_UNTIL, 0).putInt(K_LOCK_ROUNDS, 0).apply() }

    var biometricEnabled: Boolean
        get() = prefs.getBoolean(K_BIO, true)
        set(v) { prefs.edit().putBoolean(K_BIO, v).apply() }

    var autoLockSeconds: Int
        get() = prefs.getInt(K_AUTOLOCK, 60)
        set(v) { prefs.edit().putInt(K_AUTOLOCK, v).apply() }

    private fun hash(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 120_000, 256)
        val derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keystoreKey())
        return mac.doFinal(derived)
    }

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return gen.generateKey()
    }

    private fun newRecoveryCode(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // sin 0/O ni 1/I
        val chars = (1..12).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        return chars.chunked(4).joinToString("-")
    }

    private fun normalizeCode(c: String) = c.uppercase().filter { it.isLetterOrDigit() }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        const val MAX_ATTEMPTS = 5
        private const val KEY_ALIAS = "tupedalada_pin_hmac"
        private const val K_PIN_SALT = "pin_salt"
        private const val K_PIN_HASH = "pin_hash"
        private const val K_REC_SALT = "rec_salt"
        private const val K_REC_HASH = "rec_hash"
        private const val K_FAILS = "fails"
        private const val K_LOCK_UNTIL = "lock_until"
        private const val K_LOCK_ROUNDS = "lock_rounds"
        private const val K_BIO = "biometric"
        private const val K_AUTOLOCK = "autolock_s"
    }
}
