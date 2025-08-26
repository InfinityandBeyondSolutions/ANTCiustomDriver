package com.ibs.ibs_antdrivers.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.ibs.ibs_antdrivers.service.LocationTrackingService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                // Check if user is logged in before starting service
                if (FirebaseAuth.getInstance().currentUser != null) {
                    startLocationService(context)
                }
            }
        }
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