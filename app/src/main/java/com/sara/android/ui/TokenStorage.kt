package com.sara.android.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenStorage(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_BOT_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return prefs.getString(KEY_BOT_TOKEN, null)
    }

    fun clearToken() {
        prefs.edit().remove(KEY_BOT_TOKEN).apply()
    }

    companion object {
        private const val PREFS_NAME = "sara_secure_prefs"
        private const val KEY_BOT_TOKEN = "telegram_bot_token"
    }
}
