package com.bookzzang.android.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only a user-approved access token. The encryption key never leaves Android Keystore. */
class SessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("bookzzang_session", Context.MODE_PRIVATE)

    fun save(session: AuthSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(JSONObject().put("accessToken", session.accessToken).put("refreshToken", session.refreshToken).toString().toByteArray(Charsets.UTF_8))
        val payload = ByteBuffer.allocate(1 + cipher.iv.size + encrypted.size).put(cipher.iv.size.toByte()).put(cipher.iv).put(encrypted).array()
        preferences.edit().putString(TOKEN_KEY, Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun read(): AuthSession? = runCatching {
        val payload = preferences.getString(TOKEN_KEY, null) ?: return null
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        val ivLength = bytes.first().toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val cipherText = bytes.copyOfRange(1 + ivLength, bytes.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        JSONObject(cipher.doFinal(cipherText).toString(Charsets.UTF_8)).let { AuthSession(it.getString("accessToken"), it.getString("refreshToken")) }
    }.getOrElse { clear(); null }

    fun clear() { preferences.edit().remove(TOKEN_KEY).apply() }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private companion object { const val KEY_ALIAS = "bookzzang_session_key"; const val TOKEN_KEY = "access_token" }
}
