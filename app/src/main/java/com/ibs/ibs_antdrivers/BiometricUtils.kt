package com.ibs.ibs_antdrivers

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.biometric.BiometricManager

object BiometricUtils {

    // We’ll allow strong or weak biometrics (Android chooses best available)
    const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK

    fun canAuthenticate(activity: Activity): Int =
        BiometricManager.from(activity).canAuthenticate(AUTH)

    fun isAvailable(activity: Activity): Boolean =
        canAuthenticate(activity) == BiometricManager.BIOMETRIC_SUCCESS

    /** Launch system enroll if user has no biometrics set up */
    fun launchEnroll(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val enroll = Intent(android.provider.Settings.ACTION_BIOMETRIC_ENROLL).apply {
                putExtra(android.provider.Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, AUTH)
            }
            activity.startActivity(enroll)
        } else {
            activity.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))
        }
    }
}
