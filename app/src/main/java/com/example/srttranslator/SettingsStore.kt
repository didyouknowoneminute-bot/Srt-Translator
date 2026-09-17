package com.example.srttranslator

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SettingsStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(context, "settings", MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(), EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    private val supportedModels = setOf("gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite")
    var apiKey: String get() = prefs.getString("api_key", "") ?: ""; set(v) = prefs.edit().putString("api_key", v).apply()
    var model: String
        get() {
            val saved = prefs.getString("model", null)
            return if (saved in supportedModels) saved!! else "gemini-3.5-flash"
        }
        set(v) = prefs.edit().putString("model", v).apply()
}
