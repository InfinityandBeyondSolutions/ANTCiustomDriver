package com.ibs.ibs_antdrivers.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Warm legacy call-cycle paths in Firebase's disk cache.
 * Stores, price lists, orders and catalogue metadata are already read by their dedicated Room
 * refresh workers, which warm Firebase's cache too. Fetching them again here wastes bandwidth.
 */
class OfflinePrefetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        val ref = FirebaseDatabase.getInstance().reference

        return try {
            // Stores are synchronized once by RefreshStoresWorker into both Room and Firebase's
            // disk cache. Do not download the complete directory again in this prefetch pass.

            // Calling cycles for this driver — "today" screens depend on these paths.
            ref.child("callingCycles").child("activeWeek").child(uid).get().await()
            ref.child("callingCycles").child("weeks").child(uid).get().await()
            ref.child("callingCycles").child("templates").child(uid).get().await()
            // Spontaneous calls: keep a small footprint by fetching the driver's root.
            ref.child("callingCycles").child("spontaneous").child(uid).get().await()
            // Visited state
            ref.child("callingCycles").child("visited").child(uid).get().await()

            Result.success()
        } catch (t: Throwable) {
            Log.w("OfflinePrefetch", "Prefetch failed: ${t.message}")
            // Prefetch is best-effort. Retry so WorkManager can back off if network was flaky.
            Result.retry()
        }
    }
}
