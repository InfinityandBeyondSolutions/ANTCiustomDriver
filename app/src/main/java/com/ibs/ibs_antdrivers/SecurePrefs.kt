package com.ibs.ibs_antdrivers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

object SecurePrefs {
    private const val TAG = "SecurePrefs"
    private const val FILE = "secure_prefs"
    private const val KEY_EMAIL = "email"
    private const val KEY_PASSWORD = "password"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"

    private fun createPrefs(context: Context): SharedPreferences =
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE,
            MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    /**
     * Encrypted preference files cannot be restored onto another device because their Android
     * Keystore key is device-bound. Older releases allowed this file into cloud/device-transfer
     * backups, so recover by discarding only the unreadable credential store and starting clean.
     */
    private fun <T> access(
        context: Context,
        fallback: T,
        operation: (SharedPreferences) -> T
    ): T = synchronized(this) {
        try {
            operation(createPrefs(context))
        } catch (firstError: Exception) {
            Log.w(TAG, "Encrypted preferences were unreadable; resetting device-bound credentials", firstError)
            resetEncryptedStorage(context)

            try {
                operation(createPrefs(context))
            } catch (retryError: Exception) {
                // Authentication can still continue with email/password entry. A damaged keystore
                // must never turn the login screen into a startup crash loop.
                Log.e(TAG, "Unable to recreate encrypted preferences", retryError)
                fallback
            }
        }
    }

    private fun resetEncryptedStorage(context: Context) {
        val appContext = context.applicationContext
        appContext.deleteSharedPreferences(FILE)

        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(MasterKey.DEFAULT_MASTER_KEY_ALIAS)) {
                keyStore.deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to remove the invalid master-key entry", error)
        }
    }

    fun saveCreds(context: Context, email: String, password: String) {
        access(context, Unit) { prefs ->
            prefs.edit()
                .putString(KEY_EMAIL, email)
                .putString(KEY_PASSWORD, password)
                .apply()
        }
    }

    fun getEmail(context: Context): String? =
        access(context, null) { it.getString(KEY_EMAIL, null) }

    fun getPassword(context: Context): String? =
        access(context, null) { it.getString(KEY_PASSWORD, null) }

    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        access(context, Unit) {
            it.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
        }
    }

    fun isBiometricEnabled(context: Context): Boolean =
        access(context, false) { it.getBoolean(KEY_BIOMETRIC_ENABLED, false) }

    fun clear(context: Context) {
        access(context, Unit) { it.edit().clear().apply() }
    }
}
