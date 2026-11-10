package com.vpe.hyundai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts the login session token using Android Keystore (AES-GCM).
 * Never persist passwords or OpenAI keys in the APK.
 */
object VpeSecureSession {
    private const val ALIAS = "vpe_account_session_aes_v1"
    private const val PREFS = "vpe_secure_auth"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build()
        generator.init(spec)
        return generator.generateKey()
    }
    fun save(context: Context, token: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
        }
        val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }
    fun read(context: Context): String {
        return runCatching {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val iv = Base64.decode(prefs.getString("iv", "") ?: "", Base64.NO_WRAP)
            val data = Base64.decode(prefs.getString("ciphertext", "") ?: "", Base64.NO_WRAP)
            if (iv.size != 12 || data.isEmpty()) return@runCatching ""
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            }
            String(cipher.doFinal(data), Charsets.UTF_8)
        }.getOrDefault("")
    }
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
