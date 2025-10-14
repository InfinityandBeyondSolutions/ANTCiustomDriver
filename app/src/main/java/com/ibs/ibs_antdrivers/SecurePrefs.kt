package com.ibs.ibs_antdrivers

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePrefs {
    private const val FILE = "secure_prefs"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    private fun prefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            FILE,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    fun saveCreds(context: Context, email: String, password: String) {
        prefs(context).edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .apply()
    }

    fun getEmail(context: Context): String? = prefs(context).getString(KEY_EMAIL, null)
    fun getPassword(context: Context): String? = prefs(context).getString(KEY_PASSWORD, null)

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isBiometricEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}
