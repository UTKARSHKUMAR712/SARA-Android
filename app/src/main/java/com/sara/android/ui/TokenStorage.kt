package com.sara.android.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class TokenStorage(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
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
