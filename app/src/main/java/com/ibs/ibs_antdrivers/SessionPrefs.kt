package com.ibs.ibs_antdrivers

import android.content.Context

/**
 * Simple 7-day login persistence "cookie".
 *
 * Stores only an expiry timestamp (epoch millis). This is not a security feature; it’s a UX feature.
 * Real auth is still enforced by FirebaseAuth.
 */
object SessionPrefs {
    private const val FILE = "session_prefs"

    private const val KEY_EXPIRES_AT = "session_expires_at"
    private const val KEY_REMEMBER_ME = "remember_me"

    private const val SEVEN_DAYS_MS: Long = 7L * 24L * 60L * 60L * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun setRememberedSession(context: Context, nowMs: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putBoolean(KEY_REMEMBER_ME, true)
            .putLong(KEY_EXPIRES_AT, nowMs + SEVEN_DAYS_MS)
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun isRememberMeEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REMEMBER_ME, false)

    fun isSessionValid(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val expiresAt = prefs(context).getLong(KEY_EXPIRES_AT, 0L)
        return expiresAt > nowMs
    }

    /**
     * If remember-me is enabled but the session is expired, we clear it.
     * Returns whether the session is valid after the cleanup.
     */
    fun validateOrClear(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val remember = isRememberMeEnabled(context)
        if (!remember) return false
        val valid = isSessionValid(context, nowMs)
        if (!valid) clear(context)
        return valid
    }
}

