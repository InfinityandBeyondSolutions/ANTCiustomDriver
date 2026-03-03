package com.ibs.ibs_antdrivers.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.auth.FirebaseAuth
import com.ibs.ibs_antdrivers.offlineupload.UploadWorkScheduler
import com.ibs.ibs_antdrivers.rtdbqueue.RtdbQueueWorkScheduler
import com.ibs.ibs_antdrivers.sync.CacheRefreshScheduler
import com.ibs.ibs_antdrivers.sync.OfflinePrefetchScheduler

/**
 * Listens for connectivity changes and re-enqueues any pending work
 * the moment the device comes back online — even when the app is not running.
 */
class NetworkChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) return

        // Only proceed if the user is signed in and there is an active connection.
        if (FirebaseAuth.getInstance().currentUser == null) return
        if (!isConnected(context)) return

        UploadWorkScheduler.enqueue(context)
        RtdbQueueWorkScheduler.enqueue(context)
        OfflinePrefetchScheduler.enqueue(context)
        CacheRefreshScheduler.refreshAll(context)
    }

    private fun isConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
