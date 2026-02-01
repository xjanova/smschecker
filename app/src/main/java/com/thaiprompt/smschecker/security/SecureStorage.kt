package com.thaiprompt.smschecker.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Securely stores sensitive data using Android Keystore-backed encryption.
 */
@Singleton
class SecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "smschecker_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(serverId: Long, apiKey: String) {
        prefs.edit().putString("api_key_$serverId", apiKey).apply()
    }

    fun getApiKey(serverId: Long): String? {
        return prefs.getString("api_key_$serverId", null)
    }

    fun saveSecretKey(serverId: Long, secretKey: String) {
        prefs.edit().putString("secret_key_$serverId", secretKey).apply()
    }

    fun getSecretKey(serverId: Long): String? {
        return prefs.getString("secret_key_$serverId", null)
    }

    fun saveDeviceId(deviceId: String) {
        prefs.edit().putString("device_id", deviceId).apply()
    }

    fun getDeviceId(): String? {
        return prefs.getString("device_id", null)
    }

    fun deleteServerKeys(serverId: Long) {
        prefs.edit()
            .remove("api_key_$serverId")
            .remove("secret_key_$serverId")
            .apply()
    }

    fun isMonitoringEnabled(): Boolean {
        return prefs.getBoolean("monitoring_enabled", true)
    }

    fun setMonitoringEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("monitoring_enabled", enabled).apply()
    }

    fun getApprovalMode(): String {
        return prefs.getString("approval_mode", "auto") ?: "auto"
    }

    fun setApprovalMode(mode: String) {
        prefs.edit().putString("approval_mode", mode).apply()
    }

    fun getThemeMode(): String {
        return prefs.getString("theme_mode", "dark") ?: "dark"
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun getLanguage(): String {
        return prefs.getString("language", "th") ?: "th"
    }

    fun setLanguage(language: String) {
        prefs.edit().putString("language", language).apply()
    }

    fun isTtsEnabled(): Boolean {
        return prefs.getBoolean("tts_enabled", false)
    }

    fun setTtsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("tts_enabled", enabled).apply()
    }

    // TTS language: "th", "en", or "auto" (follows app language)
    fun getTtsLanguage(): String {
        return prefs.getString("tts_language", "auto") ?: "auto"
    }

    fun setTtsLanguage(lang: String) {
        prefs.edit().putString("tts_language", lang).apply()
    }

    // TTS content flags — what to announce
    fun isTtsSpeakBank(): Boolean = prefs.getBoolean("tts_speak_bank", true)
    fun setTtsSpeakBank(enabled: Boolean) { prefs.edit().putBoolean("tts_speak_bank", enabled).apply() }

    fun isTtsSpeakAmount(): Boolean = prefs.getBoolean("tts_speak_amount", true)
    fun setTtsSpeakAmount(enabled: Boolean) { prefs.edit().putBoolean("tts_speak_amount", enabled).apply() }

    fun isTtsSpeakType(): Boolean = prefs.getBoolean("tts_speak_type", true)
    fun setTtsSpeakType(enabled: Boolean) { prefs.edit().putBoolean("tts_speak_type", enabled).apply() }

    fun isTtsSpeakOrder(): Boolean = prefs.getBoolean("tts_speak_order", true)
    fun setTtsSpeakOrder(enabled: Boolean) { prefs.edit().putBoolean("tts_speak_order", enabled).apply() }

    fun isNotificationListeningEnabled(): Boolean {
        return prefs.getBoolean("notification_listening_enabled", false)
    }

    fun setNotificationListeningEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notification_listening_enabled", enabled).apply()
    }
}
