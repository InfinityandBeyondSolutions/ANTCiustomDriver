package com.ibs.ibs_antdrivers

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat

/** Keeps the app's status bar white with readable dark icons in every theme. */
@Suppress("DEPRECATION")
fun ComponentActivity.enableWhiteStatusBarEdgeToEdge() {
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE)
    )
    window.statusBarColor = Color.WHITE
    WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
}
