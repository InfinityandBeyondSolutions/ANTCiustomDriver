package com.ibs.ibs_antdrivers.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ibs.ibs_antdrivers.data.CatalogueRepository

/** Downloads catalogue categories metadata into Room so the categories page works offline. */
class RefreshCatalogueCategoriesWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            CatalogueRepository().refreshCategoriesToRoom(applicationContext)
            Result.success()
        } catch (t: Throwable) {
            Log.w("RefreshCatalogueCats", "Failed: ${t.message}")
            Result.retry()
        }
    }
}

