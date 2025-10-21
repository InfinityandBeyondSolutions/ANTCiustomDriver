package com.ibs.ibs_antdrivers

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.google.firebase.database.FirebaseDatabase

object ThemeManager {

    private const val PREFS = "settings"
    private const val KEY_DARK = "dark_mode"
    private const val KEY_BIO = "biometrics"

    fun applySavedMode(ctx: Context) {
        val isDark = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DARK, false)
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    fun setDarkMode(ctx: Context, enabled: Boolean, uid: String? = null) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_DARK, enabled).apply()

        AppCompatDelegate.setDefaultNightMode(
            if (enabled) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )

        // mirror to Firebase if uid provided
        if (!uid.isNullOrBlank()) {
            FirebaseDatabase.getInstance().reference
                .child("users").child(uid).child("settings").child("dark_mode")
                .setValue(enabled)
        }
    }

    fun setBiometrics(ctx: Context, enabled: Boolean, uid: String? = null) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BIO, enabled).apply()

        if (!uid.isNullOrBlank()) {
            FirebaseDatabase.getInstance().reference
                .child("users").child(uid).child("settings").child("biometrics")
                .setValue(enabled)
        }
    }


    fun hydrateFromFirebase(ctx: Context, uid: String, onDone: (() -> Unit)? = null) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        FirebaseDatabase.getInstance().reference
            .child("users").child(uid).child("settings")
            .get()
            .addOnSuccessListener { snap ->
                val hasLocalDark = prefs.contains(KEY_DARK)
                val hasLocalBio = prefs.contains(KEY_BIO)

                val remoteDark = snap.child("dark_mode").getValue(Boolean::class.java)
                val remoteBio  = snap.child("biometrics").getValue(Boolean::class.java)

                val e = prefs.edit()
                if (!hasLocalDark && remoteDark != null) e.putBoolean(KEY_DARK, remoteDark)
                if (!hasLocalBio  && remoteBio  != null) e.putBoolean(KEY_BIO,  remoteBio)
                e.apply()

                // ensure UI mode matches whatever we just set
                applySavedMode(ctx)
                onDone?.invoke()
            }
            .addOnFailureListener { onDone?.invoke() }
    }
}
