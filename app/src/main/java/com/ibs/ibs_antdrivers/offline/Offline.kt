package com.ibs.ibs_antdrivers.offline

import android.content.Context
import android.view.View
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar

/** Small helpers to keep offline UX consistent across the app. */
object Offline {

    fun isOfflineError(t: Throwable): Boolean {
        // Covers the most common 'no internet' cases across Firebase/HTTP stacks.
        return t is java.io.IOException
    }

    fun userMessage(t: Throwable): String {
        return if (isOfflineError(t)) {
            "No internet connection"
        } else {
            t.message?.takeIf { it.isNotBlank() } ?: "Something went wrong"
        }
    }

    fun toast(context: Context, t: Throwable) {
        Toast.makeText(context, userMessage(t), Toast.LENGTH_LONG).show()
    }

    fun snackbar(view: View, t: Throwable) {
        Snackbar.make(view, userMessage(t), Snackbar.LENGTH_LONG).show()
    }
}

