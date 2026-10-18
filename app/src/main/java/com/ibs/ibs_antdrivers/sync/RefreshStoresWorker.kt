package com.ibs.ibs_antdrivers.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.ibs.ibs_antdrivers.data.StoresRepository
import kotlinx.coroutines.CancellationException

/**
 * Downloads ALL stores from RTDB and stores them in Room for offline-first access.
 */
class RefreshStoresWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (FirebaseAuth.getInstance().currentUser == null) return Result.success()
        return try {
            StoresRepository().refreshStoresIfNeeded(applicationContext)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.w("RefreshStoresWorker", "Store cache refresh failed", error)
            Result.retry()
        }
    }
}
