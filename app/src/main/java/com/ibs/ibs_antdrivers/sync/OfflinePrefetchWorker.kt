package com.ibs.ibs_antdrivers.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

/**
 * Best-effort prefetch to warm Firebase RTDB disk cache.
 *
 * Note: This doesn't replace a proper Room cache, but it's a big step toward "it works offline"
 * without rewriting all screens.
 */
class OfflinePrefetchWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()

        val ref = FirebaseDatabase.getInstance().reference

        return try {
            // 1) Stores (all stores) — required for store details + order creation.
            // Using .get() will warm the disk cache during this run.
            ref.child("Stores").get().await()

            // 2) Calling cycles for this driver — "today" screens depend on this.
            ref.child("callingCycles").child("activeWeek").child(uid).get().await()
            ref.child("callingCycles").child("weeks").child(uid).get().await()
            ref.child("callingCycles").child("templates").child(uid).get().await()
            // Spontaneous calls: keep a small footprint by fetching the driver's root.
            ref.child("callingCycles").child("spontaneous").child(uid).get().await()
            // Visited state
            ref.child("callingCycles").child("visited").child(uid).get().await()

            // 3) Pricelists — required for order creation when offline.
            ref.child("priceLists").get().await()

            // 4) Orders for this driver — warm cache so dashboard works offline.
            // If there isn't an index on driverId, this can be slow but still works.
            ref.child("orders").orderByChild("driverId").equalTo(uid).get().await()

            // (Optional) other reference data could be added here.

            Result.success()
        } catch (t: Throwable) {
            Log.w("OfflinePrefetch", "Prefetch failed: ${t.message}")
            // Prefetch is best-effort. Retry so WorkManager can back off if network was flaky.
            Result.retry()
        }
    }
}

