package com.ibs.ibs_antdrivers.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.ibs.ibs_antdrivers.offlineupload.UploadWorkScheduler
import com.ibs.ibs_antdrivers.rtdbqueue.RtdbQueueWorkScheduler
import com.ibs.ibs_antdrivers.service.LocationTrackingService
import com.ibs.ibs_antdrivers.sync.CacheRefreshScheduler
import com.ibs.ibs_antdrivers.sync.OfflinePrefetchScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (FirebaseAuth.getInstance().currentUser != null) {
                    // Only resume tracking if the user explicitly enabled it earlier AND all permissions are still granted.
                    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    val trackingEnabled = prefs.getBoolean("tracking_active", false)
                    val disclosureAccepted = prefs.getBoolean("location_disclosure_accepted", false)

                    if (trackingEnabled && disclosureAccepted && hasRequiredLocationPermissions(context)) {
                        startLocationService(context)
                    }

                    // Re-enqueue any pending work that was interrupted by the reboot.
                    UploadWorkScheduler.enqueue(context)
                    RtdbQueueWorkScheduler.enqueue(context)

                    // Best-effort: prefetch offline data once connectivity is available.
                    OfflinePrefetchScheduler.enqueue(context)
                    CacheRefreshScheduler.refreshAll(context)
                }
            }
        }
    }

    private fun hasRequiredLocationPermissions(context: Context): Boolean {
        val fine = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val bgOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        return (fine || coarse) && bgOk
    }

    private fun startLocationService(context: Context) {
        val serviceIntent = Intent(context, LocationTrackingService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}